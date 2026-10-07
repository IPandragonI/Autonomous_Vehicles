package org.autonomous_vehicles.util;

/**
 * A simple linear congruential generator (LCG) for generating pseudo-random numbers.
 */

public class Rng {

    private long state;
    public static final long A = 1664525;
    public static final long C = 1013904223;
    public static final long M = 1L << 32;

    public Rng(long seed) {
        this.state = seed;
    }

    public double nextDouble() {
        state = (A * state + C) % M;
        return (double) state / M;
    }
}
