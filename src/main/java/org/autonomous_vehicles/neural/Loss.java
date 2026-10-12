package org.autonomous_vehicles.neural;

public class Loss {

    public static double mse(double prediction, double target) {
        return Math.pow(prediction - target, 2);
    }

    public static double mseDerivative(double prediction, double target) {
        return 2 * (prediction - target);
    }
}
