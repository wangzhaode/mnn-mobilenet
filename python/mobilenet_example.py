#-- coding:utf8 --
import argparse

import MNN
import MNN.numpy as np
import MNN.cv as cv2

def inference(model, img, precision, backend, thread):
    config = {}
    config['precision'] = precision
    config['backend'] = backend
    config['numThread'] = thread
    rt = MNN.nn.create_runtime_manager((config,))
    net = MNN.nn.load_module_from_file(model, ['data'], ['prob'], runtime_manager=rt)
    image = cv2.imread(img)
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    image = cv2.resize(image, (224, 224))
    image = image.astype(np.float32)
    image = image - (103.94, 116.78, 123.68)
    image = image * (0.017, 0.017, 0.017)
    input_var = np.expand_dims(image, 0)
    input_var = MNN.expr.convert(input_var, MNN.expr.NC4HW4)
    output_var = net.forward(input_var)
    output_var = MNN.expr.convert(output_var, MNN.expr.NHWC)
    print("output belong to class: {}".format(np.argmax(output_var)))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', type=str, required=True, help='the mobilenet model path')
    parser.add_argument('--img', type=str, required=True, help='the input image path')
    parser.add_argument('--precision', type=str, default='normal', help='inference precision: normal, low, high, lowBF')
    parser.add_argument('--backend', type=str, default='CPU', help='inference backend: CPU, OPENCL, OPENGL, NN, VULKAN, METAL, TRT, CUDA, HIAI')
    parser.add_argument('--thread', type=int, default=4, help='inference using thread: int')
    args = parser.parse_args()
    inference(args.model, args.img, args.precision, args.backend, args.thread)
