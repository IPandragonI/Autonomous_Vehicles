package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows the progress of one car along the ordered checkpoints of a track.
 * <p>
 * The car starts just past the finish line (the last checkpoint). Only the next expected
 * checkpoint can be validated, and only when crossed in the driving direction, so skipping a
 * checkpoint or driving backwards never counts. A lap is completed when the finish line is crossed.
 */
public class LapTracker {

    private final List<Checkpoint> checkpoints;
    private final List<Double> lapTimes = new ArrayList<>();

    private int nextIndex;
    private int checkpointsPassed;
    private double elapsedTime;
    private double lapStartTime;

    public LapTracker(Track track) {
        if (track == null || track.getCheckpoints().isEmpty()) {
            throw new IllegalArgumentException("Track must define checkpoints.");
        }

        this.checkpoints = track.getCheckpoints();
    }

    /**
     * Advances the clock by {@code dt} and validates the checkpoints crossed by the move.
     *
     * @return true when this move completed a lap
     */
    public boolean update(Vec2 from, Vec2 to, double dt) {
        if (from == null || to == null || !Double.isFinite(dt) || dt < 0.0) {
            throw new IllegalArgumentException("Invalid lap tracker update.");
        }

        elapsedTime += dt;
        boolean lapCompleted = false;

        while (checkpoints.get(nextIndex).isCrossedForward(from, to)) {
            checkpointsPassed++;
            nextIndex++;

            if (nextIndex == checkpoints.size()) {
                nextIndex = 0;
                lapTimes.add(elapsedTime - lapStartTime);
                lapStartTime = elapsedTime;
                lapCompleted = true;
                break;
            }
        }

        return lapCompleted;
    }

    /**
     * Fine progress: checkpoints passed plus the fraction of the way to the next one, in [0, 1).
     */
    public double progress(Vec2 position) {
        Vec2 previous = checkpoints.get(Math.floorMod(nextIndex - 1, checkpoints.size())).midpoint();
        Vec2 next = checkpoints.get(nextIndex).midpoint();
        double span = previous.distanceTo(next);
        double fraction = span == 0.0 ? 0.0 : 1.0 - position.distanceTo(next) / span;

        return checkpointsPassed + Math.clamp(fraction, 0.0, 0.999);
    }

    public int getNextCheckpointIndex() {
        return nextIndex;
    }

    public int getCheckpointsPassed() {
        return checkpointsPassed;
    }

    public int getLaps() {
        return lapTimes.size();
    }

    public List<Double> getLapTimes() {
        return List.copyOf(lapTimes);
    }

    public double getBestLapTime() {
        return lapTimes.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    public double getElapsedTime() {
        return elapsedTime;
    }

    public double getCurrentLapTime() {
        return elapsedTime - lapStartTime;
    }

    public int getCheckpointCount() {
        return checkpoints.size();
    }
}
