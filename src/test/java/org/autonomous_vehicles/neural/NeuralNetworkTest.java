package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NeuralNetworkTest {

    @Test
    public void testForward() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);
        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] outputs = network.forward(inputs);

        assertEquals(2, outputs.length);
        assertTrue(outputs[0] >= -1 && outputs[0] <= 1);
        assertTrue(outputs[1] >= -1 && outputs[1] <= 1);
    }

    @Test
    public void testForwardWithWrongInputLength() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> network.forward(new double[]{1.0})
        );
    }

    @Test
    public void testForwardWithSameSeed() {
        Rng rng1 = new Rng(12345);
        NeuralNetwork network1 = new NeuralNetwork(rng1, 5, 6, 2);

        Rng rng2 = new Rng(12345);
        NeuralNetwork network2 = new NeuralNetwork(rng2, 5, 6, 2);

        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] outputs1 = network1.forward(inputs);
        double[] outputs2 = network2.forward(inputs);

        assertArrayEquals(outputs1, outputs2);
    }

    @Test
    public void testDeeperArchitecture() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 12, 8, 2);

        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] outputs = network.forward(inputs);

        assertEquals(2, outputs.length);
    }

    @Test
    public void testParameterCount() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);

        assertEquals(50, network.getParameterCount());
    }

    @Test
    public void testGetLastActivations() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);
        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        network.forward(inputs);
        double[][] lastActivations = network.getLastActivations();

        assertEquals(2, lastActivations.length);
        assertEquals(6, lastActivations[0].length);
        assertEquals(2, lastActivations[1].length);
    }
}