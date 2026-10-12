
package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public final class Layer {
    private final Neuron[] neurons;

    private double[] lastInput;
    private double[] lastPreActivations;
    private double[] lastOutput;

    public Layer(Neuron[] neurons) {
        if (neurons == null || neurons.length == 0) {
            throw new IllegalArgumentException("A layer must contain at least one neuron.");
        }

        this.neurons = neurons.clone();
    }

    public double[] forward(double[] inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("Inputs must not be null.");
        }

        double[] outputs = new double[neurons.length];
        double[] preActivations = new double[neurons.length];

        lastInput = inputs.clone();

        for (int i = 0; i < neurons.length; i++) {
            preActivations[i] = neurons[i].calculateWeightedSum(inputs);
            outputs[i] = neurons[i].activation().activate(preActivations[i]);
        }

        lastPreActivations = preActivations;
        lastOutput = outputs;

        return outputs.clone();
    }

    public static Layer random(int inputSize, int outputSize, ActivationFunction activation, Rng rng) {
        if (inputSize <= 0 || outputSize <= 0) {
            throw new IllegalArgumentException(
                    "Input and output sizes must be positive."
            );
        }

        Neuron[] neurons = new Neuron[outputSize];

        for (int i = 0; i < outputSize; i++) {
            neurons[i] = Neuron.random(inputSize, outputSize, activation, rng);
        }

        return new Layer(neurons);
    }

    public int getParameterCount() {
        int count = 0;

        for (Neuron neuron : neurons) {
            count += neuron.weights().length + 1;
        }

        return count;
    }

    public void setNeuron(int index, Neuron neuron) {
        if (neuron == null) {
            throw new IllegalArgumentException("Neuron must not be null.");
        }

        neurons[index] = neuron;
    }

    public void dump() {
        for (int i = 0; i < neurons.length; i++) {
            System.out.println("Neuron " + i + ":");
            neurons[i].dump();
        }
    }

    public Neuron[] getNeurons() {
        return neurons.clone();
    }

    public double[] getLastInput() {
        return lastInput == null ? null : lastInput.clone();
    }

    public double[] getLastOutput() {
        return lastOutput == null ? null : lastOutput.clone();
    }

    public double[] getLastPreActivations() {
        return lastPreActivations == null ? null : lastPreActivations.clone();
    }
}