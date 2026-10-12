package org.autonomous_vehicles.neural;

public class ReLU implements ActivationFunction {
    @Override
    public double activate(double input) {
        return Math.max(0, input);
    }

    @Override
    public double derivative(double input) {
        return input > 0 ? 1 : 0;
    }
}
