package org.autonomous_vehicles.neural;

public class Tanh implements ActivationFunction{
    @Override
    public double activate(double input) {
        return Math.tanh(input);
    }
}
