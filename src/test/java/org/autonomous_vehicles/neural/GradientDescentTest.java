package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GradientDescentTest {

    @Test
    public void testOptimizeNearTarget() {
        double initialW = 0.0;
        double learningRate = 0.1;
        int iterations = 100;

        double optimizedW = GradientDescent.optimize(initialW, learningRate, iterations);

        assertEquals(3.0, optimizedW, 1e-6);
    }

    @Test
    public void testOptimizeFarFromTarget() {
        double initialW = 0.0;
        double learningRate = 0.0001;
        int iterations = 100;

        double optimizedW = GradientDescent.optimize(initialW, learningRate, iterations);

        assertTrue(optimizedW < 3.0 && optimizedW > 0.0);
    }

    @Test
    public void testOptimizeDivergence() {
        double initialW = 0.0;
        double learningRate = 1.1;
        int iterations = 100;

        double optimizedW = GradientDescent.optimize(initialW, learningRate, iterations);

        assertTrue(Math.abs(optimizedW - 3.0) > 1_000_000);
    }
}
