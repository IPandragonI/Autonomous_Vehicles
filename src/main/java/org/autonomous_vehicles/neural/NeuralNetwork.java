package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public class NeuralNetwork {

    private final int[] architecture;
    private final Layer[] layers;
    private double[][] lastActivations;

    public NeuralNetwork(Rng rng, int... architecture) {
        this(rng, new Tanh(), architecture);
    }

    public NeuralNetwork(Rng rng, ActivationFunction activation, int... architecture) {
        if (rng == null) {
            throw new IllegalArgumentException("Rng must not be null.");
        }
        if (activation == null) {
            throw new IllegalArgumentException("Activation must not be null.");
        }
        if (architecture.length < 2) {
            throw new IllegalArgumentException("Architecture must have at least two layers.");
        }
        for (int size: architecture) {
            if (size <= 0) {
                throw new IllegalArgumentException("Layer sizes must be positive integers.");
            }
        }
        this.architecture = architecture.clone();
        layers = new Layer[architecture.length - 1];
        for (int i = 0; i < architecture.length - 1; i++) {
            layers[i] = Layer.random(architecture[i], architecture[i + 1], activation, rng);
        }
    }

    public double[] forward(double[] inputs) {
        double[] outputs = inputs;
        lastActivations = new double[layers.length][];

        for (int i = 0; i < layers.length; i++) {
            outputs = layers[i].forward(outputs);
            lastActivations[i] = outputs;
        }
        return outputs;
    }


    public double[][] backward(double[] dLossDOutputs) {
        if (lastActivations == null) {
            throw new IllegalStateException("forward() must be called before backward().");
        }

        if (dLossDOutputs == null || dLossDOutputs.length != architecture[architecture.length - 1]) {
            throw new IllegalArgumentException("Gradient length must match the network output size.");
        }

        double[][] dLossDInputs = new double[layers.length][];
        double[] gradient = dLossDOutputs.clone();

        for (int i = layers.length - 1; i >= 0; i--) {
            gradient = layers[i].backward(gradient);
            dLossDInputs[i] = gradient.clone();
        }

        return dLossDInputs;
    }


    public int getParameterCount() {
        int count = 0;
        for (Layer layer : layers) {
            count += layer.getParameterCount();
        }
        return count;
    }

    public double[] getGenome() {
        double[] genome = new double[getParameterCount()];
        int index = 0;
        for (Layer layer : layers) {
            for (Neuron neuron : layer.getNeurons()) {
                System.arraycopy(neuron.weights(), 0, genome, index, neuron.weights().length);
                index += neuron.weights().length;
                genome[index++] = neuron.bias();
            }
        }
        return genome;
    }

    public void setGenome(double[] genome) {
        if (genome == null) {
            throw new IllegalArgumentException("Genome cannot be null.");
        }

        if (genome.length != getParameterCount()) {
            throw new IllegalArgumentException("Genome length does not match the number of parameters in the network.");
        }

        int index = 0;

        for (Layer layer : layers) {
            for (int i = 0; i < layer.getNeurons().length; i++) {
                Neuron oldNeuron = layer.getNeurons()[i];
                double[] weights = new double[oldNeuron.weights().length];
                System.arraycopy(genome, index, weights, 0, weights.length);

                index += weights.length;
                double bias = genome[index++];
                Neuron newNeuron = new Neuron(weights, bias, oldNeuron.activation());
                layer.setNeuron(i, newNeuron);
            }
        }
    }


    public double[] getGradientGenome() {
        double[] gradients = new double[getParameterCount()];
        int index = 0;

        for (Layer layer : layers) {
            double[][] weightGradients = layer.getLastWeightGradients();
            double[] biasGradients = layer.getLastBiasGradients();

            if (weightGradients == null || biasGradients == null) {
                throw new IllegalStateException("backward() must be called before retrieving gradients.");
            }

            for (int i = 0; i < weightGradients.length; i++) {
                for (double gradient : weightGradients[i]) {
                    gradients[index++] = gradient;
                }

                gradients[index++] = biasGradients[i];
            }
        }

        return gradients;
    }

    public void applyGradients(double[] gradients, double learningRate) {
        if (gradients == null || gradients.length != getParameterCount()) {
            throw new IllegalArgumentException("Gradient count must match the number of parameters.");
        }

        if (!Double.isFinite(learningRate) || learningRate <= 0.0) {
            throw new IllegalArgumentException("Learning rate must be positive and finite.");
        }

        double[] genome = getGenome();

        for (int i = 0; i < genome.length; i++) {
            if (!Double.isFinite(gradients[i])) {
                throw new IllegalArgumentException("Gradients must be finite.");
            }

            genome[i] -= learningRate * gradients[i];
        }

        setGenome(genome);
    }

    public NeuralNetwork copy() {
        NeuralNetwork copy = new NeuralNetwork(new Rng(0), architecture);
        copy.setGenome(this.getGenome());
        return copy;
    }

    public void dump() {
        for (int i = 0; i < layers.length; i++) {
            System.out.println("Layer " + i + ":");
            layers[i].dump();
        }
    }

    public int[] getArchitecture() {
        return architecture.clone();
    }

    public double[][] getLastActivations() {
        if (lastActivations == null) {
            return null;
        }

        double[][] copy = new double[lastActivations.length][];

        for (int i = 0; i < lastActivations.length; i++) {
            copy[i] = lastActivations[i].clone();
        }

        return copy;
    }
}
