package org.autonomous_vehicles.neural;

public class Sigmoid implements ActivationFunction {

    @Override
    public double activate(double input) {
        return 1.0 / (1.0 + Math.exp(-input));
    }

    @Override
    public double derivative(double input) {
        double sigmoid = activate(input);
        return sigmoid * (1 - sigmoid);
    }
}
