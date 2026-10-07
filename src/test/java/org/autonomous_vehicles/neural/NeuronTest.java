package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NeuronTest {

    @Test
    public void testCompute() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias);

        double[] inputs = {1.0, 2.0, 3.0};
        double output = neuron.compute(inputs);

        // Expected output: 0.3 + (0.5*1) + (-0.2*2) + (0.1*3) = 0.3 + 0.5 - 0.4 + 0.3 = 0.7
        assertEquals(0.7, output, 1e-9);
    }

    @Test
    public void testComputeWithNegativeInputs() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias);

        double[] inputs = {-1.0, -2.0, -3.0};
        double output = neuron.compute(inputs);

        // Expected output: 0.3 + (0.5*-1) + (-0.2*-2) + (0.1*-3) = 0.3 - 0.5 + 0.4 - 0.3 = -0.1
        assertEquals(-0.1, output, 1e-9);
    }

    @Test
    public void testComputeWithZeroInputs() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias);

        double[] inputs = {0.0, 0.0, 0.0};
        double output = neuron.compute(inputs);

        // Expected output: 0.3 + (0.5*0) + (-0.2*0) + (0.1*0) = 0.3
        assertEquals(0.3, output, 1e-9);
    }

    @Test
    public void testWrongInputLength() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias);

        double[] inputs = {1.0, 2.0}; // Incorrect length
        assertThrows(IllegalArgumentException.class, () -> neuron.compute(inputs));
    }
}
