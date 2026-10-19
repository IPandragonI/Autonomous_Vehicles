package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

/**
 * Crossover of two parent genomes. Every gene of the child comes from one of the parents.
 * Crossing networks is delicate: two parents may compute the same thing with their hidden neurons
 * in a different order, so mixing them can break both solutions (the permutation problem).
 */
public class CrossoverOperator {

    public enum Type { UNIFORM, ONE_POINT }

    private final Type type;
    private final Rng rng;

    public CrossoverOperator(Type type, Rng rng) {
        if (type == null || rng == null) {
            throw new IllegalArgumentException("Type and rng must not be null.");
        }

        this.type = type;
        this.rng = rng;
    }

    public double[] crossover(double[] first, double[] second) {
        if (first == null || second == null || first.length != second.length) {
            throw new IllegalArgumentException("Parents must have genomes of the same length.");
        }

        double[] child = new double[first.length];

        if (type == Type.UNIFORM) {
            for (int i = 0; i < child.length; i++) {
                child[i] = rng.nextDouble() < 0.5 ? first[i] : second[i];
            }
        } else {
            int cut = child.length < 2 ? child.length : 1 + rng.nextInt(child.length - 1);

            for (int i = 0; i < child.length; i++) {
                child[i] = i < cut ? first[i] : second[i];
            }
        }

        return child;
    }

    public Type getType() {
        return type;
    }
}
