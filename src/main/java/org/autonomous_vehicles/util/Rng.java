package org.autonomous_vehicles.util;

/**
 * A simple linear congruential generator (LCG) for generating pseudo-random numbers.
 * Every random decision of the project (initialisation, mutation, selection, crossover)
 * goes through an injected instance, so a fixed seed always reproduces the same run.
 */

public class Rng {

    private long state;
    private double spareGaussian;
    private boolean hasSpareGaussian;
    public static final long A = 1664525;
    public static final long C = 1013904223;
    public static final long M = 1L << 32;

    public Rng(long seed) {
        this.state = Math.floorMod(seed, M);
    }

    public double nextDouble() {
        state = (A * state + C) % M;
        return (double) state / M;
    }

    /**
     * Uniform integer in [0, bound).
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive.");
        }

        return Math.min((int) (nextDouble() * bound), bound - 1);
    }

    /**
     * Standard normal value (mean 0, standard deviation 1), Box-Muller transform.
     */
    public double nextGaussian() {
        if (hasSpareGaussian) {
            hasSpareGaussian = false;
            return spareGaussian;
        }

        double u1 = 1.0 - nextDouble();
        double u2 = nextDouble();
        double radius = Math.sqrt(-2.0 * Math.log(u1));

        spareGaussian = radius * Math.sin(2.0 * Math.PI * u2);
        hasSpareGaussian = true;

        return radius * Math.cos(2.0 * Math.PI * u2);
    }
}
