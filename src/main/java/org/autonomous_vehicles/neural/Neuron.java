package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

/**
 * Represents a single neuron in a neural network.
 * A neuron computes a weighted sum of its inputs, adds a bias term,
 * and applies an activation function.
 */

public record Neuron(double[] weights, double bias, ActivationFunction activation) {

    public double compute(double[] inputs) {
        if (inputs.length != weights.length) {
            throw new IllegalArgumentException("Input length must match weights length.");
        }

        double sum = bias;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * inputs[i];
        }

        return activation.activate(sum);
    }

    public static Neuron random(int inputSize, int outputSize, ActivationFunction activation, Rng rng) {
        double[] randomWeights = new double[inputSize];
        double limit = Math.sqrt(6.0 / (inputSize + outputSize));
        for (int i = 0; i < inputSize; i++) {
            randomWeights[i] = rng.nextDouble() * 2 * limit - limit;
        }
        double randomBias = rng.nextDouble() * 2 * limit - limit;
        return new Neuron(randomWeights, randomBias, activation);
    }

    public void dump() {
        System.out.print("Weights: ");
        for (double weight : weights) {
            System.out.printf("%.4f ", weight);
        }
        System.out.printf("Bias: %.4f Activation: %s%n", bias, activation.getClass().getSimpleName());
    }
}