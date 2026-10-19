package org.autonomous_vehicles.sim;

/**
 * Parameters of a simulation run (no evolution, no UI).
 *
 * @param dt             fixed time step in seconds
 * @param maxTime        a car is stopped after this many seconds (TIMEOUT)
 * @param stagnationTime a car that has not improved its progress for this long is stopped (STAGNATED)
 * @param maxLaps        a car that completes this many laps is stopped (FINISHED)
 * @param trailLength    number of past positions kept per car for drawing (0 = none)
 * @param recordLaps     record the poses of each lap so the best lap can be replayed
 */
public record SimulationSettings(double dt, double maxTime, double stagnationTime, int maxLaps,
                                 int trailLength, boolean recordLaps) {

    public static final double MIN_PROGRESS = 0.05;

    public SimulationSettings {
        if (!Double.isFinite(dt) || dt <= 0.0 || dt > 0.1) {
            throw new IllegalArgumentException("dt must be in (0, 0.1].");
        }

        if (!Double.isFinite(maxTime) || maxTime <= 0.0) {
            throw new IllegalArgumentException("maxTime must be positive.");
        }

        if (!Double.isFinite(stagnationTime) || stagnationTime <= 0.0) {
            throw new IllegalArgumentException("stagnationTime must be positive.");
        }

        if (maxLaps < 1) {
            throw new IllegalArgumentException("maxLaps must be at least 1.");
        }

        if (trailLength < 0) {
            throw new IllegalArgumentException("trailLength must not be negative.");
        }
    }

    /**
     * Time limit that leaves room for one full lap: 1.3 × lap length / a typical mean lap speed (17 m/s for the
     * kinematic car, 12 m/s for grip-limited cars, which slow down in bends), rounded up to 10 s, at least 60 s.
     * Measured on the hairpin: grip-limited cars need 65–75 s per lap, so a fixed 60 s let none of them finish.
     */
    public static double recommendedMaxTime(Track track, VehicleModel vehicle) {
        double meanSpeed = vehicle instanceof KinematicModel ? 17.0 : 12.0;
        double seconds = 1.3 * track.lapLength() / meanSpeed;
        return Math.max(60.0, Math.ceil(seconds / 10.0) * 10.0);
    }

    public static SimulationSettings defaults() {
        return new SimulationSettings(1.0 / 60.0, 60.0, 4.0, 3, 0, true);
    }

    public SimulationSettings withTrailLength(int trailLength) {
        return new SimulationSettings(dt, maxTime, stagnationTime, maxLaps, trailLength, recordLaps);
    }

    public SimulationSettings withMaxTime(double maxTime) {
        return new SimulationSettings(dt, maxTime, stagnationTime, maxLaps, trailLength, recordLaps);
    }

    public SimulationSettings withMaxLaps(int maxLaps) {
        return new SimulationSettings(dt, maxTime, stagnationTime, maxLaps, trailLength, recordLaps);
    }
}
