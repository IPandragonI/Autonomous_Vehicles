package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

/**
 * Mutation: each gene is changed with probability {@code rate} by adding noise of amplitude {@code strength}
 * (Gaussian: strength is the standard deviation; uniform: noise in [-strength, strength]).
 * Too little mutation stalls the search, too much destroys what was learned.
 */
public class MutationOperator {

    public enum Noise { GAUSSIAN, UNIFORM }

    private final double rate;
    private final double strength;
    private final Noise noise;
    private final Rng rng;

    public MutationOperator(double rate, double strength, Rng rng) {
        this(rate, strength, Noise.GAUSSIAN, rng);
    }

    public MutationOperator(double rate, double strength, Noise noise, Rng rng) {
        if (!Double.isFinite(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("Mutation rate must be in [0, 1].");
        }

        if (!Double.isFinite(strength) || strength < 0.0) {
            throw new IllegalArgumentException("Mutation strength must be non-negative.");
        }

        if (noise == null || rng == null) {
            throw new IllegalArgumentException("Noise and rng must not be null.");
        }

        this.rate = rate;
        this.strength = strength;
        this.noise = noise;
        this.rng = rng;
    }

    /**
     * Returns a mutated copy; the argument is never modified.
     */
    public double[] mutate(double[] genome) {
        if (genome == null) {
            throw new IllegalArgumentException("Genome must not be null.");
        }

        double[] mutated = genome.clone();

        for (int i = 0; i < mutated.length; i++) {
            if (rng.nextDouble() < rate) {
                mutated[i] += noise == Noise.GAUSSIAN
                        ? strength * rng.nextGaussian()
                        : strength * (2.0 * rng.nextDouble() - 1.0);
            }
        }

        return mutated;
    }

    public double getRate() {
        return rate;
    }

    public double getStrength() {
        return strength;
    }

    public Noise getNoise() {
        return noise;
    }
}
