package org.autonomous_vehicles.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RngTest {

    @Test
    @DisplayName("generated values stay in the [0, 1) interval")
    void generatedValuesStayInRange() {
        Rng rng = new Rng(12345);

        for (int i = 0; i < 1_000; i++) {
            double value = rng.nextDouble();
            int iteration = i;
            assertTrue(value >= 0.0 && value < 1.0, () -> "Value out of range at iteration " + iteration + ": " + value);
        }
    }

    @Test
    @DisplayName("the same seed produces the same sequence")
    void sameSeedProducesSameSequence() {
        Rng rng1 = new Rng(12345);
        Rng rng2 = new Rng(12345);

        for (int i = 0; i < 100; i++) {
            assertEquals(rng1.nextDouble(), rng2.nextDouble(), 0.0, "Sequences differ at iteration " + i);
        }
    }

    @Test
    @DisplayName("different seeds produce different initial values")
    void differentSeedsProduceDifferentValues() {
        Rng first = new Rng(12345);
        Rng second = new Rng(54321);

        assertNotEquals(first.nextDouble(), second.nextDouble());
    }

    @Test
    @DisplayName("the sequence matches the LCG reference values")
    void sequenceMatchesReferenceValues() {
        Rng rng = new Rng(12345);
        double[] expected = {
                0.020402685739099979,
                0.016547848237678409,
                0.54315579449757934,
                0.63490405608899891,
                0.91002951376140118
        };

        assertAll(
                () -> assertEquals(expected[0], rng.nextDouble(), 1e-15),
                () -> assertEquals(expected[1], rng.nextDouble(), 1e-15),
                () -> assertEquals(expected[2], rng.nextDouble(), 1e-15),
                () -> assertEquals(expected[3], rng.nextDouble(), 1e-15),
                () -> assertEquals(expected[4], rng.nextDouble(), 1e-15)
        );
    }

    @Test
    @DisplayName("a zero seed advances to the expected first value")
    void zeroSeedProducesExpectedFirstValue() {
        Rng rng = new Rng(0);

        assertEquals((double) Rng.C / Rng.M, rng.nextDouble(), 0.0);
    }
}
