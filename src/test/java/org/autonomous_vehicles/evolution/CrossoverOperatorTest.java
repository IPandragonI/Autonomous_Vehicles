package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrossoverOperatorTest {

    private static final double[] FIRST = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final double[] SECOND = {-1, -2, -3, -4, -5, -6, -7, -8, -9, -10};

    @Test
    public void testEveryGeneComesFromAParent() {
        for (CrossoverOperator.Type type : CrossoverOperator.Type.values()) {
            CrossoverOperator operator = new CrossoverOperator(type, new Rng(1));

            for (int trial = 0; trial < 100; trial++) {
                double[] child = operator.crossover(FIRST, SECOND);

                for (int i = 0; i < child.length; i++) {
                    assertTrue(child[i] == FIRST[i] || child[i] == SECOND[i], type + " gene " + i);
                }
            }
        }
    }

    @Test
    public void testIdenticalParentsGiveIdenticalChild() {
        for (CrossoverOperator.Type type : CrossoverOperator.Type.values()) {
            assertArrayEquals(FIRST, new CrossoverOperator(type, new Rng(2)).crossover(FIRST, FIRST.clone()), 0.0);
        }
    }

    @Test
    public void testOnePointKeepsAPrefixAndASuffix() {
        CrossoverOperator operator = new CrossoverOperator(CrossoverOperator.Type.ONE_POINT, new Rng(3));

        for (int trial = 0; trial < 100; trial++) {
            double[] child = operator.crossover(FIRST, SECOND);
            int cut = 0;

            while (cut < child.length && child[cut] == FIRST[cut]) {
                cut++;
            }

            assertTrue(cut >= 1 && cut <= child.length - 1, "Both parents contribute.");

            for (int i = cut; i < child.length; i++) {
                assertEquals(SECOND[i], child[i], 0.0);
            }
        }
    }

    @Test
    public void testUniformMixesBothParents() {
        double[] first = new double[1000];
        double[] second = new double[1000];
        java.util.Arrays.fill(second, 1.0);

        double[] child = new CrossoverOperator(CrossoverOperator.Type.UNIFORM, new Rng(4)).crossover(first, second);

        assertEquals(0.5, Statistics.mean(child), 0.05);
    }

    @Test
    public void testParentsMustHaveTheSameLength() {
        CrossoverOperator operator = new CrossoverOperator(CrossoverOperator.Type.UNIFORM, new Rng(1));

        assertThrows(IllegalArgumentException.class, () -> operator.crossover(new double[3], new double[4]));
    }
}
