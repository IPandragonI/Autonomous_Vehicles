package org.autonomous_vehicles.neural;

public class Tanh implements ActivationFunction{
    @Override
    public double activate(double input) {
        return Math.tanh(input);
    }

    @Override
    public double derivative(double input) {
        double tanh = activate(input);
        return 1 - tanh * tanh;
    }
}
