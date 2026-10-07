package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

public class NeuralNetwork {

    private final Layer[] layers;
    private double[][] lastActivations;

    public NeuralNetwork(Rng rng, int... architecture) {
        if (architecture.length < 2) {
            throw new IllegalArgumentException("Architecture must have at least two layers.");
        }

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

    public double[][] getLastActivations() {
        return lastActivations;
    }
}
