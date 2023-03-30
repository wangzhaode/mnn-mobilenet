# Usage

## 1. 环境准备

### 开发工具
- `xcode`


### 下载`MNN.framework`
从[MNN Release](https://github.com/alibaba/MNN/releases)中下载最新的`mnn_xxx_ios_armv82_cpu_metal_coreml.zip`并解压，将`MNN.framework`库文件拷贝到`ios/mobilenet_demo`目录下；

示例：
```
wget https://github.com/alibaba/MNN/releases/download/2.4.0/mnn_2.4.0_ios_armv82_cpu_metal_coreml.zip
unzip mnn_2.4.0_ios_armv82_cpu_metal_coreml.zip
cp -r ios_build/Release-iphoneos/MNN.framework /path/to/MNNExample/mobilenet/ios/mobilenet_demo
```

### 下载模型：
从[Release](https://github.com/wangzhaode/mobilenet-mnn/releases/tag/v1.0)下载模型文件。

## 2. 编译运行

使用`xcode`打开`mobilnet_demo.xcodeproj`目录，连接手机编译运行即可。
