package org.autonomous_vehicles.neural;

public class GradientDescent {

    public static double optimize(double initialW, double learningRate, int iterations) {
        double w = initialW;

        for (int i = 0; i < iterations; i++) {
            double gradient = Loss.mseDerivative(w, 3.0);
            w -= learningRate * gradient;
        }

        return w;
    }
}