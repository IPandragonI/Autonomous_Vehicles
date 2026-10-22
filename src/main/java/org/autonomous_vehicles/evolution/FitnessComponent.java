package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.DeathCause;

/**
 * Elementary criteria of the composite fitness. Rewards are positive, penalties negative,
 * so every weight of {@link CompositeFitness} is a non-negative importance.
 */
public enum FitnessComponent implements FitnessFunction {

    /** Checkpoints passed plus the fraction towards the next one (dominant term). */
    PROGRESS("Progress", 1.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.progress();
        }
    },
    /** Completed laps. */
    LAPS("Laps", 5.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.laps();
        }
    },
    /** Σ (1 - lapTime / maxTime) over completed laps: at equal laps, faster is better. */
    LAP_TIME("Lap time", 10.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.lapTimes().stream().mapToDouble(time -> 1.0 - Math.min(1.0, time / result.maxTime())).sum();
        }
    },
    /** Mean speed relative to the top speed of the car model, in [0, 1]. */
    SPEED("Speed", 1.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.meanSpeed() / result.topSpeed();
        }
    },
    /**
     * -1 if the car hit a wall. Its weight must not exceed the stagnation weight: otherwise turning
     * around before a hard corner (stagnation) beats trying it (collision), and the population learns
     * to retreat (observed on the hairpin track with weight 3: progress stuck at 3.65 checkpoints).
     */
    COLLISION("Collision penalty", 1.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.deathCause() == DeathCause.CRASHED ? -1.0 : 0.0;
        }
    },
    /** -1 if the car was stopped for not progressing (stuck, circling or reversing). */
    STAGNATION("Stagnation penalty", 1.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.deathCause() == DeathCause.STAGNATED ? -1.0 : 0.0;
        }
    },
    /** Minus the mean distance to the centre line, relative to the half width, in [-1, 0]. */
    CENTERLINE("Centre-line deviation", 0.5) {
        @Override
        public double evaluate(AgentResult result) {
            return -result.meanDeviation();
        }
    },
    /** Minus the mean steering change per step, in [-1, 0]: penalises a twitchy wheel. */
    STEERING("Steering oscillation", 0.5) {
        @Override
        public double evaluate(AgentResult result) {
            return -result.meanSteeringChange() / 2.0;
        }
    },
    /**
     * Σ (maxTime - lapTime) over completed laps, in seconds: one second saved on a lap is worth one checkpoint at
     * weight 1, whatever the time limit. {@link #LAP_TIME} divides by the time limit (470 s on a 4 km circuit for a
     * grip-limited car), so one second per lap is worth 0.02 there — less than the centre-line penalty a racing line
     * costs (0.5 × ~0.5): a car that uses the track width to go faster scored lower. Off by default (the assignment's
     * weights); {@code QuickSetup} turns it on, and the centre-line penalty off, for grip-limited cars.
     */
    TIME_SAVED("Time saved (s)", 0.0) {
        @Override
        public double evaluate(AgentResult result) {
            return result.lapTimes().stream().mapToDouble(time -> Math.max(0.0, result.maxTime() - time)).sum();
        }
    };

    private final String label;
    private final double defaultWeight;

    FitnessComponent(String label, double defaultWeight) {
        this.label = label;
        this.defaultWeight = defaultWeight;
    }

    public String label() {
        return label;
    }

    public double defaultWeight() {
        return defaultWeight;
    }
}
