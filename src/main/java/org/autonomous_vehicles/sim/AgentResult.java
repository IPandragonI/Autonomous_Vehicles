package org.autonomous_vehicles.sim;

import java.util.List;

/**
 * Everything measured about one car during one simulation; fitness functions only read this.
 *
 * @param progress           checkpoints gained since the start, with the fraction towards the next one
 *                           (best value reached, so 0 for a car that never moved forward)
 * @param laps               completed laps
 * @param lapTimes           time of every completed lap, in seconds
 * @param distance           total distance driven (forwards or backwards)
 * @param timeAlive          seconds before the car was stopped
 * @param meanSpeed          mean absolute speed
 * @param meanSteeringChange mean |steer(t) - steer(t-1)|, in [0, 2]: steering oscillation
 * @param meanDeviation      mean distance to the line between checkpoint centres, relative to the half width, in [0, 1]
 * @param deathCause         why the car stopped
 * @param maxTime            time limit of the simulation
 * @param checkpointCount    checkpoints per lap on this track
 * @param bestLap            recording of the fastest lap, or null
 * @param topSpeed           top speed of the car model, m/s (scale of the speed fitness)
 */
public record AgentResult(double progress, int laps, List<Double> lapTimes, double distance, double timeAlive,
                          double meanSpeed, double meanSteeringChange, double meanDeviation,
                          DeathCause deathCause, double maxTime, int checkpointCount, LapReplay bestLap,
                          double topSpeed) {

    public AgentResult {
        lapTimes = List.copyOf(lapTimes);

        if (!(topSpeed > 0.0)) {
            throw new IllegalArgumentException("topSpeed must be positive.");
        }
    }

    /**
     * Result of a car driven by the kinematic model of the assignment.
     */
    public AgentResult(double progress, int laps, List<Double> lapTimes, double distance, double timeAlive,
                       double meanSpeed, double meanSteeringChange, double meanDeviation, DeathCause deathCause,
                       double maxTime, int checkpointCount, LapReplay bestLap) {
        this(progress, laps, lapTimes, distance, timeAlive, meanSpeed, meanSteeringChange, meanDeviation, deathCause,
                maxTime, checkpointCount, bestLap, CarPhysics.MAX_SPEED);
    }

    public double bestLapTime() {
        return lapTimes.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    public boolean crashed() {
        return deathCause == DeathCause.CRASHED;
    }
}
