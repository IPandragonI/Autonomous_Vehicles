package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public class NeuralNetwork {

    private final int[] architecture;
    private final Layer[] layers;
    private double[][] lastActivations;

    public NeuralNetwork(Rng rng, int... architecture) {
        if (architecture.length < 2) {
            throw new IllegalArgumentException("Architecture must have at least two layers.");
        }
        this.architecture = architecture.clone();
        layers = new Layer[architecture.length - 1];
        for (int i = 0; i < architecture.length - 1; i++) {
            layers[i] = Layer.random(architecture[i], architecture[i + 1], new Tanh(), rng);
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
            for (Neuron neuron : layer.neurons()) {
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
            for (int i = 0; i < layer.neurons().length; i++) {
                Neuron oldNeuron = layer.neurons()[i];
                double[] weights = new double[oldNeuron.weights().length];
                System.arraycopy(genome, index, weights, 0, weights.length);

                index += weights.length;
                double bias = genome[index++];
                Neuron newNeuron = new Neuron(weights, bias, oldNeuron.activation());
                layer.setNeuron(i, newNeuron);
            }
        }
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
        return lastActivations;
    }
}
