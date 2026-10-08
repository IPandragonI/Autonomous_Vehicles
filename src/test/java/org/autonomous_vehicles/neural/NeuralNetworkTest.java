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

    @Test
    public void testDump() {
        Rng rng = new Rng(12345);
        NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);

        // Just call dump to ensure it doesn't throw any exceptions
        network.dump();
    }

    @Test
    public void testDifferentSeedsProduceDifferentOutputs() {
        NeuralNetwork network1 = new NeuralNetwork(new Rng(42), 5, 6, 2);
        NeuralNetwork network2 = new NeuralNetwork(new Rng(123), 5, 6, 2);

        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] output1 = network1.forward(inputs);
        double[] output2 = network2.forward(inputs);

        assertFalse(java.util.Arrays.equals(output1, output2));
    }

    @Test
    public void testGetGenome() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 5, 6, 2);

        double[] genome = network.getGenome();
        assertEquals(50, genome.length);
    }

    @Test
    public void testSetGenome() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 5, 6, 2);
        double[] genome = network.getGenome();

        genome[0] += 0.1;
        network.setGenome(genome);

        double[] newGenome = network.getGenome();
        assertEquals(genome[0], newGenome[0], 1e-9);
    }

    @Test
    public void testGenomeRoundTrip() {
        NeuralNetwork network1 = new NeuralNetwork(new Rng(42), 5, 6, 2);
        NeuralNetwork network2 = new NeuralNetwork(new Rng(123), 5, 6, 2);
        double[] genome = network1.getGenome();
        network2.setGenome(genome);

        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] output1 = network1.forward(inputs);
        double[] output2 = network2.forward(inputs);

        assertArrayEquals(output1, output2);
    }

    @Test
    public void testCopy() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 5, 6, 2);
        NeuralNetwork copy = network.copy();

        double[] originalGenome = network.getGenome();
        double[] copyGenome = copy.getGenome();

        assertArrayEquals(originalGenome, copyGenome);

        copyGenome[0] += 1.0;
        copy.setGenome(copyGenome);

        assertNotEquals(originalGenome[0], copy.getGenome()[0]);
        assertEquals(originalGenome[0], network.getGenome()[0], 1e-9);
    }
}