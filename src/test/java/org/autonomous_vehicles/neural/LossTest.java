package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LossTest {

    @Test
    public void testMse() {
        double prediction = 5.0;
        double target = 3.0;

        double expectedLoss = 4.0;
        double actualLoss = Loss.mse(prediction, target);
        assertEquals(expectedLoss, actualLoss, 1e-9);

        prediction = 3.0;
        expectedLoss = 0.0;
        actualLoss = Loss.mse(prediction, target);
        assertEquals(expectedLoss, actualLoss, 1e-9);
    }

    @Test
    public void testMseDerivative() {
        double prediction = 5.0;
        double target = 3.0;

        double expectedDerivative = 4.0;
        double actualDerivative = Loss.mseDerivative(prediction, target);
        assertEquals(expectedDerivative, actualDerivative, 1e-9);

        prediction = 1.0;
        expectedDerivative = -4.0;
        actualDerivative = Loss.mseDerivative(prediction, target);
        assertEquals(expectedDerivative, actualDerivative, 1e-9);
    }
}
