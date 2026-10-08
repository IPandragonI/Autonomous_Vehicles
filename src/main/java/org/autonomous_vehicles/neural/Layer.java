package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public record Layer(Neuron[] neurons) {

    public double[] forward(double[] inputs) {
        double[] outputs = new double[neurons.length];
        for (int i = 0; i < neurons.length; i++) {
            outputs[i] = neurons[i].compute(inputs);
        }
        return outputs;
    }

    public static Layer random(int inputSize, int outputSize, ActivationFunction activation, Rng rng) {
        Neuron[] neurons = new Neuron[outputSize];
        for (int i = 0; i < outputSize; i++) {
            neurons[i] = Neuron.random(inputSize, outputSize, activation, rng);
        }
        return new Layer(neurons);
    }

    public int getParameterCount() {
        int count = 0;
        for (Neuron neuron : neurons) {
            count += neuron.weights().length + 1; // +1 for the bias
        }
        return count;
    }

    public void setNeuron(int index, Neuron neuron) {
        neurons[index] = neuron;
    }

    public void dump() {
        for (int i = 0; i < neurons.length; i++) {
            System.out.println("Neuron " + i + ":");
            neurons[i].dump();
        }
    }
}
