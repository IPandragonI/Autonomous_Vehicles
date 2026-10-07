package org.autonomous_vehicles.neural;

public class Layer {

    private final Neuron[] neurons;

    public Layer(Neuron[] neurons) {
        this.neurons = neurons;
    }

    public double[] forward(double[] inputs) {
        if (inputs.length != neurons.length) {
            throw new IllegalArgumentException("Input length must match number of neurons");
        }

        double[] outputs = new double[neurons.length];
        for (int i = 0; i < neurons.length; i++) {
            outputs[i] = neurons[i].compute(inputs);
        }
        return outputs;
    }
}
