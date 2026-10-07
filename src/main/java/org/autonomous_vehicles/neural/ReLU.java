package org.autonomous_vehicles.neural;

public class ReLU implements ActivationFunction {
    @Override
    public double activate(double input) {
        return Math.max(0, input);
    }
}
