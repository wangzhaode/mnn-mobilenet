package com.taobao.android.mnndemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.taobao.android.mnn.MNNForwardType;
import com.taobao.android.mnn.MNNImageProcess;
import com.taobao.android.mnn.MNNNetInstance;
import com.taobao.android.utils.Common;
import com.taobao.android.utils.TxtFileReader;

import java.text.DecimalFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private final String TAG = "VideoActivity";
    private final int MAX_CLZ_SIZE = 1000;

    private String mModelPath;
    private List<String> mResultNames;

    private final MNNNetInstance.Config mConfig = new MNNNetInstance.Config();// session config

    private CameraView mCameraView;
    private Spinner mForwardTypeSpinner;
    private Spinner mThreadNumSpinner;

    private TextView mFirstResult;
    private TextView mSecondResult;
    private TextView mThirdResult;
    private TextView mTimeTextView;

    private final int MobileInputWidth = 224;
    private final int MobileInputHeight = 224;

    HandlerThread mThread;
    Handler mHandle;

    private AtomicBoolean mLockUIRender = new AtomicBoolean(false);
    private AtomicBoolean mDrop = new AtomicBoolean(false);

    private MNNNetInstance mNetInstance;
    private MNNNetInstance.Session mSession;
    private MNNNetInstance.Session.Tensor mInputTensor;

    private int mRotateDegree;// 0/90/180/360

    /**
     * 监听屏幕旋转
     */
    void detectScreenRotate() {
        OrientationEventListener orientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;  //手机平放时，检测不到有效的角度
                }

                //可以根据不同角度检测处理，这里只检测四个角度的改变
                orientation = (orientation + 45) / 90 * 90;
                mRotateDegree = orientation % 360;
            }
        };


        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        } else {
            orientationListener.disable();
        }
    }

    private void prepareModels() {
        mModelPath = getCacheDir() + "mobilenet_v1.mnn";
        try {
            Common.copyAssetResource2File(getBaseContext(), "mobilenet_v1.mnn", mModelPath);
            mResultNames = TxtFileReader.getUniqueUrls(getBaseContext(), "names.txt", Integer.MAX_VALUE);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    private void prepareNet() {
        if (null != mSession) {
            mSession.release();
            mSession = null;
        }
        if (mNetInstance != null) {
            mNetInstance.release();
            mNetInstance = null;
        }

        String modelPath = mModelPath;

        // create net instance
        mNetInstance = MNNNetInstance.createFromFile(modelPath);

        // mConfig.saveTensors;
        mSession = mNetInstance.createSession(mConfig);

        // get input tensor
        mInputTensor = mSession.getInput(null);

        int[] dimensions = mInputTensor.getDimensions();
        dimensions[0] = 1; // force batch = 1  NCHW  [batch, channels, height, width]
        mInputTensor.reshape(dimensions);
        mSession.reshape();

        mLockUIRender.set(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        detectScreenRotate();

        mConfig.numThread = 4;
        mConfig.forwardType = MNNForwardType.FORWARD_CPU.type;

        // prepare mnn net models
        prepareModels();

        mForwardTypeSpinner = (Spinner) findViewById(R.id.forwardTypeSpinner);
        mThreadNumSpinner = (Spinner) findViewById(R.id.threadsSpinner);
        mThreadNumSpinner.setSelection(2);

        mFirstResult = findViewById(R.id.firstResult);
        mSecondResult = findViewById(R.id.secondResult);
        mThirdResult = findViewById(R.id.thirdResult);
        mTimeTextView = findViewById(R.id.timeTextView);

        mForwardTypeSpinner.setOnItemSelectedListener(VideoActivity.this);
        mThreadNumSpinner.setOnItemSelectedListener(VideoActivity.this);

        // init sub thread handle
        mLockUIRender.set(true);
        clearUIForPrepareNet();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);
            } else {
                handlePreViewCallBack();
            }
        } else {
            handlePreViewCallBack();
        }

        mThread = new HandlerThread("MNNNet");
        mThread.start();
        mHandle = new Handler(mThread.getLooper());

        mHandle.post(new Runnable() {
            @Override
            public void run() {
                prepareNet();
            }
        });

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (10 == requestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePreViewCallBack();
            } else {
                Toast.makeText(this, "没有获得必要的权限", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void handlePreViewCallBack() {

        ViewStub stub = (ViewStub) findViewById(R.id.stub);
        stub.inflate();

        mCameraView = (CameraView) findViewById(R.id.camera_view);

        mCameraView.setPreviewCallback(new CameraView.PreviewCallback() {
            @Override
            public void onGetPreviewOptimalSize(int optimalWidth, int optimalHeight) {

                // adjust video preview size according to screen size
                DisplayMetrics metric = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metric);
                int fixedVideoHeight = metric.widthPixels * optimalWidth / optimalHeight;

                FrameLayout layoutVideo = findViewById(R.id.videoLayout);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutVideo.getLayoutParams();
                params.height = fixedVideoHeight;
                layoutVideo.setLayoutParams(params);
            }

            @Override
            public void onPreviewFrame(final byte[] data, final int imageWidth, final int imageHeight, final int angle) {

                if (mLockUIRender.get()) {
                    return;
                }
                if (mDrop.get()) {
                    Log.w(TAG, "drop frame, net running too slow !!");
                } else {
                    mDrop.set(true);
                    mHandle.post(new Runnable() {
                        @Override
                        public void run() {
                            mDrop.set(false);
                            if (mLockUIRender.get()) {
                                return;
                            }

                            // calculate corrected angle based on camera orientation and mobile rotate degree. (back camrea)
                            int needRotateAngle = (angle + mRotateDegree) % 360;

                            /*
                             *  convert data to input tensor
                             */
                            final MNNImageProcess.Config config = new MNNImageProcess.Config();

                            // normalization params
                            config.mean = new float[]{103.94f, 116.78f, 123.68f};
                            config.normal = new float[]{0.017f, 0.017f, 0.017f};
                            config.source = MNNImageProcess.Format.YUV_NV21;// input source format
                            config.dest = MNNImageProcess.Format.BGR;// input data format

                            // matrix transform: dst to src
                            Matrix matrix = new Matrix();
                            matrix.postScale(MobileInputWidth / (float) imageWidth, MobileInputHeight / (float) imageHeight);
                            matrix.postRotate(needRotateAngle, MobileInputWidth / 2, MobileInputHeight / 2);
                            matrix.invert(matrix);

                            MNNImageProcess.convertBuffer(data, imageWidth, imageHeight, mInputTensor, config, matrix);

                            final long startTimestamp = System.nanoTime();
                            /**
                             * inference
                             */
                            mSession.run();

                            /**
                             * get output tensor
                             */
                            MNNNetInstance.Session.Tensor output = mSession.getOutput(null);

                            float[] result = output.getFloatData();// get float results
                            final long endTimestamp = System.nanoTime();
                            final float inferenceTimeCost = (endTimestamp - startTimestamp) / 1000000.0f;

                            if (result.length > MAX_CLZ_SIZE) {
                                Log.w(TAG, "session result too big (" + result.length + "), model incorrect ?");
                            }

                            final List<Map.Entry<Integer, Float>> maybes = new ArrayList<>();
                            for (int i = 0; i < result.length; i++) {
                                float confidence = result[i];
                                if (confidence > 0.01) {
                                    maybes.add(new AbstractMap.SimpleEntry<Integer, Float>(i, confidence));
                                }
                            }

                            Collections.sort(maybes, new Comparator<Map.Entry<Integer, Float>>() {
                                @Override
                                public int compare(Map.Entry<Integer, Float> o1, Map.Entry<Integer, Float> o2) {
                                    if (Math.abs(o1.getValue() - o2.getValue()) <= Float.MIN_NORMAL) {
                                        return 0;
                                    }
                                    return o1.getValue() > o2.getValue() ? -1 : 1;
                                }
                            });

                            // show results on ui
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    if (maybes.size() == 0) {
                                        mFirstResult.setText("no data");
                                        mSecondResult.setText("");
                                        mThirdResult.setText("");
                                    }
                                    if (maybes.size() > 0) {
                                        mFirstResult.setTextColor(maybes.get(0).getValue() > 0.2 ? Color.BLACK : Color.parseColor("#a4a4a4"));
                                        final Integer iKey = maybes.get(0).getKey();
                                        final Float fValue = maybes.get(0).getValue();
                                        String strWord = "unknown";
                                        if (iKey < mResultNames.size()) {
                                            strWord = mResultNames.get(iKey);
                                        }
                                        mFirstResult.setText(strWord + "：" + new DecimalFormat("0.00").format(fValue));

                                    }
                                    if (maybes.size() > 1) {
                                        final Integer iKey = maybes.get(1).getKey();
                                        final Float fValue = maybes.get(1).getValue();
                                        String strWord = "unknown";
                                        if (iKey < mResultNames.size()) {
                                            strWord = mResultNames.get(iKey);
                                        }
                                        mSecondResult.setText(strWord + "：" + new DecimalFormat("0.00").format(fValue));

                                    }
                                    if (maybes.size() > 2) {
                                        final Integer iKey = maybes.get(2).getKey();
                                        final Float fValue = maybes.get(2).getValue();
                                        String strWord = "unknown";
                                        if (iKey < mResultNames.size()) {
                                            strWord = mResultNames.get(iKey);
                                        }
                                        mThirdResult.setText(strWord + "：" + new DecimalFormat("0.00").format(fValue));
                                    }

                                    mTimeTextView.setText("cost time：" + inferenceTimeCost + "ms");
                                }
                            });

                        }
                    });
                }
            }
        });
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

        // forward type
        if (mForwardTypeSpinner.getId() == adapterView.getId()) {
            if (i == 0) {
                mConfig.forwardType = MNNForwardType.FORWARD_CPU.type;
            } else if (i == 1) {
                mConfig.forwardType = MNNForwardType.FORWARD_OPENCL.type;
            } else if (i == 2) {
                mConfig.forwardType = MNNForwardType.FORWARD_OPENGL.type;
            } else if (i == 3) {
                mConfig.forwardType = MNNForwardType.FORWARD_VULKAN.type;
            }
        }
        // threads num
        else if (mThreadNumSpinner.getId() == adapterView.getId()) {
            String[] threadList = getResources().getStringArray(R.array.thread_list);
            mConfig.numThread = Integer.parseInt(threadList[i].split(" ")[1]);
        }
        mLockUIRender.set(true);
        clearUIForPrepareNet();

        mHandle.post(new Runnable() {
            @Override
            public void run() {
                prepareNet();
            }
        });

    }

    private void clearUIForPrepareNet() {
        mFirstResult.setText("prepare net ...");
        mSecondResult.setText("");
        mThirdResult.setText("");
        mTimeTextView.setText("");
    }


    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    protected void onPause() {
        mCameraView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.onResume();
    }


    @Override
    protected void onDestroy() {
        mThread.interrupt();

        /**
         * instance release
         */
        mHandle.post(new Runnable() {
            @Override
            public void run() {
                if (mNetInstance != null) {
                    mNetInstance.release();
                }
            }
        });

        super.onDestroy();
    }
}