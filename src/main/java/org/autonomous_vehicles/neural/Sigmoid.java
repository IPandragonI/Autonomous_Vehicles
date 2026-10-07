package org.autonomous_vehicles.neural;

public class Sigmoid implements ActivationFunction {

    @Override
    public double activate(double input) {
        return 1.0 / (1.0 + Math.exp(-input));
    }
}
