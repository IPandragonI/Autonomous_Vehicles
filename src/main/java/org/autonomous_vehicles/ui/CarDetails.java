package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.FitnessFunction;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.CarAgent;

import java.util.List;

/**
 * Race figures of the inspected car for the "Car" tab, next to the dashboard gauges (plain Java, unit-tested).
 *
 * @param rank          1 = most progress of the cars on this track (ties share the rank)
 * @param carCount      cars on this track
 * @param progress      checkpoints gained since the start
 * @param checkpoints   checkpoints per lap
 * @param distance      metres driven
 * @param timeAlive     seconds on track
 * @param meanSpeedKmh  mean speed so far
 * @param laps          completed laps
 * @param lastLapTime   last completed lap, NaN if none
 * @param bestLapTime   fastest lap of this car, NaN if none
 * @param gapToRecord   best lap − record of the run, NaN without a lap or a record (0 = it holds the record)
 * @param fitness       score the car would get if it stopped now
 * @param deviation     mean distance to the ideal line, relative to the half width (0 = centred)
 * @param steeringNoise mean steering change per step, 0–2 (0 = smooth)
 */
public record CarDetails(int id, int rank, int carCount, double progress, int checkpoints, double distance,
                         double timeAlive, double meanSpeedKmh, int laps, double lastLapTime, double bestLapTime,
                         double gapToRecord, double fitness, double deviation, double steeringNoise) {

    /**
     * @param others    every car of the same simulation (including {@code agent})
     * @param lapRecord fastest lap of the run so far, NaN if none
     */
    public static CarDetails of(CarAgent agent, List<CarAgent> others, FitnessFunction fitness, double lapRecord) {
        if (agent == null || others == null || fitness == null) {
            throw new IllegalArgumentException("Agent, cars and fitness must not be null.");
        }

        AgentResult result = agent.result();
        int ahead = 0;

        for (CarAgent other : others) {
            if (other.getProgress() > agent.getProgress()) {
                ahead++;
            }
        }

        List<Double> lapTimes = result.lapTimes();
        double best = result.bestLapTime();

        return new CarDetails(agent.getId(), ahead + 1, others.size(), result.progress(), result.checkpointCount(),
                result.distance(), result.timeAlive(), result.meanSpeed() * 3.6, result.laps(),
                lapTimes.isEmpty() ? Double.NaN : lapTimes.getLast(), best,
                Double.isNaN(best) || Double.isNaN(lapRecord) ? Double.NaN : best - lapRecord,
                fitness.evaluate(result), result.meanDeviation(), result.meanSteeringChange());
    }

    /**
     * Fraction of the current lap covered, 0–1.
     */
    public double lapFraction() {
        return checkpoints == 0 ? 0.0 : (progress % checkpoints) / checkpoints;
    }
}
