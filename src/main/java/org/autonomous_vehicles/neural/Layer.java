
package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public final class Layer {
    private final Neuron[] neurons;

    private double[] lastInput;
    private double[] lastPreActivations;
    private double[] lastOutput;

    private double[] lastDeltas;
    private double[][] lastWeightGradients;
    private double[] lastBiasGradients;


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

    public double[] backward(double[] dLossDActivations) {
        if (lastInput == null || lastPreActivations == null) {
            throw new IllegalStateException("forward() must be called before backward().");
        }

        if (dLossDActivations == null || dLossDActivations.length != neurons.length) {
            throw new IllegalArgumentException("dLossDActivations must not be null and must match the number of neurons.");
        }

        double[] dLossDInputs = new double[lastInput.length];
        lastDeltas = new double[neurons.length];
        lastWeightGradients = new double[neurons.length][lastInput.length];
        lastBiasGradients = new double[neurons.length];

        for (int i = 0; i < neurons.length; i++) {
            double dActivationDPreActivation = neurons[i].activation().derivative(lastPreActivations[i]);
            lastDeltas[i] = dLossDActivations[i] * dActivationDPreActivation;
            double[] weights = neurons[i].weights();

            for (int j = 0; j < lastInput.length; j++) {
                lastWeightGradients[i][j] = lastDeltas[i] * lastInput[j];
                dLossDInputs[j] += lastDeltas[i] * weights[j];
            }

            lastBiasGradients[i] = lastDeltas[i];
        }

        return dLossDInputs.clone();
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

    public double[] getLastDeltas() {
        return lastDeltas == null ? null : lastDeltas.clone();
    }

    public double[][] getLastWeightGradients() {
        if (lastWeightGradients == null) {
            return null;
        }

        double[][] copy = new double[lastWeightGradients.length][];

        for (int i = 0; i < lastWeightGradients.length; i++) {
            copy[i] = lastWeightGradients[i].clone();
        }

        return copy;
    }

    public double[] getLastBiasGradients() {
        return lastBiasGradients == null ? null : lastBiasGradients.clone();
    }
}