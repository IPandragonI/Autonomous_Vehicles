package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MutationOperatorTest {

    private static double[] genome(int size) {
        double[] genome = new double[size];

        for (int i = 0; i < size; i++) {
            genome[i] = i * 0.001;
        }

        return genome;
    }

    @Test
    public void testZeroRateLeavesGenomeUnchanged() {
        double[] genome = genome(500);

        assertArrayEquals(genome, new MutationOperator(0.0, 1.0, new Rng(1)).mutate(genome), 0.0);
    }

    @Test
    public void testProportionOfMutatedGenesMatchesRate() {
        double[] genome = genome(20000);

        for (double rate : new double[]{0.05, 0.1, 0.3}) {
            double[] mutated = new MutationOperator(rate, 0.2, new Rng(2)).mutate(genome);
            int changed = 0;

            for (int i = 0; i < genome.length; i++) {
                if (mutated[i] != genome[i]) {
                    changed++;
                }
            }

            double proportion = (double) changed / genome.length;
            double tolerance = 4 * Math.sqrt(rate * (1 - rate) / genome.length);
            assertEquals(rate, proportion, tolerance, "rate " + rate);
        }
    }

    @Test
    public void testOriginalIsNotModified() {
        double[] genome = genome(100);
        double[] copy = genome.clone();

        new MutationOperator(1.0, 1.0, new Rng(3)).mutate(genome);

        assertArrayEquals(copy, genome, 0.0);
    }

    @Test
    public void testGaussianNoiseHasTheRequestedStrength() {
        double[] genome = new double[20000];
        double[] mutated = new MutationOperator(1.0, 0.25, new Rng(4)).mutate(genome);

        assertEquals(0.0, Statistics.mean(mutated), 0.01);
        assertEquals(0.25, Statistics.populationStd(mutated), 0.01);
    }

    @Test
    public void testUniformNoiseStaysWithinStrength() {
        double[] genome = new double[5000];
        double[] mutated = new MutationOperator(1.0, 0.3, MutationOperator.Noise.UNIFORM, new Rng(5)).mutate(genome);

        for (double value : mutated) {
            assertTrue(Math.abs(value) <= 0.3);
        }
    }

    @Test
    public void testWeakVersusStrongMutation() {
        double[] genome = new double[5000];
        double weak = GenomeAnalysis.distance(genome, new MutationOperator(0.02, 0.05, new Rng(6)).mutate(genome));
        double strong = GenomeAnalysis.distance(genome, new MutationOperator(0.30, 0.8, new Rng(6)).mutate(genome));

        assertTrue(strong > 20 * weak, "Strong mutation moves the genome much further: " + weak + " vs " + strong);
    }

    @Test
    public void testInvalidParametersAreRejected() {
        Rng rng = new Rng(1);

        assertThrows(IllegalArgumentException.class, () -> new MutationOperator(-0.1, 0.1, rng));
        assertThrows(IllegalArgumentException.class, () -> new MutationOperator(1.1, 0.1, rng));
        assertThrows(IllegalArgumentException.class, () -> new MutationOperator(0.1, -1, rng));
    }
}
