#include <stdio.h>
#include <MNN/ImageProcess.hpp>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Executor.hpp>

#include <cv/cv.hpp>

using namespace MNN;
using namespace MNN::Express;
using namespace MNN::CV;

int main(int argc, const char* argv[]) {
    if (argc < 3) {
        MNN_PRINT("Usage: ./mobilenet_demo.out model.mnn input.jpg [forwardType] [precision] [thread]\n");
        return 0;
    }
    int thread = 4;
    int precision = 0;
    int forwardType = MNN_FORWARD_CPU;
    if (argc >= 4) {
        forwardType = atoi(argv[3]);
    }
    if (argc >= 5) {
        precision = atoi(argv[4]);
    }
    if (argc >= 6) {
        thread = atoi(argv[5]);
    }
    MNN::ScheduleConfig sConfig;
    sConfig.type = static_cast<MNNForwardType>(forwardType);
    sConfig.numThread = thread;
    BackendConfig bConfig;
    bConfig.precision = static_cast<BackendConfig::PrecisionMode>(precision);
    sConfig.backendConfig = &bConfig;
    std::shared_ptr<Executor::RuntimeManager> rtmgr = std::shared_ptr<Executor::RuntimeManager>(Executor::RuntimeManager::createRuntimeManager(sConfig));
    if(rtmgr == nullptr) {
        MNN_ERROR("Empty RuntimeManger\n");
        return 0;
    }
    rtmgr->setCache(".cachefile");
    
    std::shared_ptr<Module> net(Module::load(std::vector<std::string>{}, std::vector<std::string>{}, argv[1], rtmgr));
    auto image = imread(argv[2]);
    image = cvtColor(image, COLOR_BGR2RGB);
    image = resize(image, Size(224, 224), 0, 0, INTER_LINEAR, -1, {103.94, 116.78, 123.68}, {0.017, 0.017, 0.017});
    auto input = _Unsqueeze(image, {0});
    input = _Convert(input, NC4HW4);
    auto outputs = net->onForward({input});
    auto output = _Convert(outputs[0], NHWC);
    auto classIdx = _ArgMax(output, -1)->readMap<int>()[0];
    MNN_PRINT("output belong to class: %d\n", classIdx);
    rtmgr->updateCache();
    return 0;
}
