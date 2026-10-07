package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NeuronTest {

    @Test
    public void testCompute() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias, new Sigmoid());

        double[] inputs = {1.0, 2.0, 3.0};
        double output = neuron.compute(inputs);

        // Weighted sum is 0.7, then sigmoid activation is applied.
        assertEquals(0.6681877721681662, output, 1e-9);
    }

    @Test
    public void testComputeWithNegativeInputs() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias, new Sigmoid());

        double[] inputs = {-1.0, -2.0, -3.0};
        double output = neuron.compute(inputs);

        // Weighted sum is -0.1, then sigmoid activation is applied.
        assertEquals(0.47502081252106, output, 1e-9);
    }

    @Test
    public void testComputeWithZeroInputs() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias, new Sigmoid());

        double[] inputs = {0.0, 0.0, 0.0};
        double output = neuron.compute(inputs);

        // Weighted sum is 0.3, then sigmoid activation is applied.
        assertEquals(0.574442516811659, output, 1e-9);
    }

    @Test
    public void testComputeUsesReLUActivation() {
        double[] weights = {1.0, 1.0};
        Neuron neuron = new Neuron(weights, -3.0, new ReLU());

        assertEquals(0.0, neuron.compute(new double[]{1.0, 1.0}), 1e-9);
        assertEquals(1.0, neuron.compute(new double[]{2.0, 2.0}), 1e-9);
    }

    @Test
    public void testComputeUsesTanhActivation() {
        double[] weights = {1.0};
        Neuron neuron = new Neuron(weights, 0.0, new Tanh());

        assertEquals(Math.tanh(1.0), neuron.compute(new double[]{1.0}), 1e-9);
    }

    @Test
    public void testWrongInputLength() {
        double[] weights = {0.5, -0.2, 0.1};
        double bias = 0.3;
        Neuron neuron = new Neuron(weights, bias, new Sigmoid());

        double[] inputs = {1.0, 2.0}; // Incorrect length
        assertThrows(IllegalArgumentException.class, () -> neuron.compute(inputs));
    }

    @Test
    public void testRandomNeuronGeneration() {
        Rng rng = new Rng(42);
        Neuron neuron = Neuron.random(3, new Sigmoid(), rng);

        assertEquals(3, neuron.weights().length);
        for (double weight : neuron.weights()) {
            assertTrue(weight >= -1 && weight <= 1);
        }
        assertTrue(neuron.bias() >= -1 && neuron.bias() <= 1);
    }

    @Test
    public void testRandomNeuronGenerationWithSameSeed() {
        Rng rng1 = new Rng(42);
        Neuron neuron1 = Neuron.random(3, new Sigmoid(), rng1);

        Rng rng2 = new Rng(42);
        Neuron neuron2 = Neuron.random(3, new Sigmoid(), rng2);

        // Ensure that neurons generated with the same seed are the same
        assertEquals(neuron1.bias(), neuron2.bias());
        assertEquals(neuron1.weights()[0], neuron2.weights()[0]);
        assertEquals(neuron1.weights()[1], neuron2.weights()[1]);
        assertEquals(neuron1.weights()[2], neuron2.weights()[2]);
    }
}
