package org.autonomous_vehicles.neural;

/**
 * Represents a single neuron in a neural network.
 * A neuron computes a weighted sum of its inputs and adds a bias term.
 */

public class Neuron {

    private final double[] weights;
    private final double bias;

    public Neuron(double[] weights, double bias) {
        this.weights = weights;
        this.bias = bias;
    }

    public double compute(double[] inputs) {
        if (inputs.length != weights.length) {
            throw new IllegalArgumentException("Input length must match weights length.");
        }

        double sum = bias;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * inputs[i];
        }
        return sum;
    }
}