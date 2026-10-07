package org.autonomous_vehicles.neural;

/**
 * Interface for activation functions used in neural networks.
 * These functions are used to introduce non-linearity into the model, allowing it to learn complex patterns in the data.
 */

public interface ActivationFunction {
    double activate(double input);
}
