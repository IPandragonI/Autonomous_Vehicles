package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.util.Arrays;

/**
 * Recording of one complete lap: the pose of the car at every simulation step.
 * Used to draw the "ghost" of the best lap and to compare two trajectories.
 */
public final class LapReplay {

    private final String trackName;
    private final double dt;
    private final double lapTime;
    private final double[] xs;
    private final double[] ys;
    private final double[] angles;

    public LapReplay(String trackName, double dt, double lapTime, double[] xs, double[] ys, double[] angles) {
        if (trackName == null || xs == null || ys == null || angles == null || xs.length == 0
                || xs.length != ys.length || xs.length != angles.length) {
            throw new IllegalArgumentException("A replay needs matching, non-empty pose arrays.");
        }

        if (!Double.isFinite(dt) || dt <= 0.0 || !Double.isFinite(lapTime) || lapTime <= 0.0) {
            throw new IllegalArgumentException("dt and lap time must be positive.");
        }

        this.trackName = trackName;
        this.dt = dt;
        this.lapTime = lapTime;
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.angles = angles.clone();
    }

    public int getFrameCount() {
        return xs.length;
    }

    public Vec2 positionAt(int frame) {
        return new Vec2(xs[frame], ys[frame]);
    }

    public double angleAt(int frame) {
        return angles[frame];
    }

    /**
     * Frame displayed at time {@code t} since the start of the lap (clamped to the lap).
     */
    public int frameAtTime(double t) {
        return Math.clamp((long) Math.floor(t / dt), 0, xs.length - 1);
    }

    /**
     * Mean distance between the two trajectories, both sampled at the same fractions of their lap.
     */
    public double meanDistanceTo(LapReplay other) {
        int samples = Math.max(getFrameCount(), other.getFrameCount());
        double total = 0.0;

        for (int i = 0; i < samples; i++) {
            double fraction = samples == 1 ? 0.0 : (double) i / (samples - 1);
            total += positionAtFraction(fraction).distanceTo(other.positionAtFraction(fraction));
        }

        return total / samples;
    }

    private Vec2 positionAtFraction(double fraction) {
        return positionAt((int) Math.round(fraction * (xs.length - 1)));
    }

    public boolean sameTrajectory(LapReplay other) {
        return other != null && Arrays.equals(xs, other.xs) && Arrays.equals(ys, other.ys)
                && Arrays.equals(angles, other.angles);
    }

    public String getTrackName() {
        return trackName;
    }

    public double getDt() {
        return dt;
    }

    public double getLapTime() {
        return lapTime;
    }
}
