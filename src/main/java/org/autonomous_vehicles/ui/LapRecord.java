package org.autonomous_vehicles.ui;

import java.util.Locale;

/**
 * A new fastest lap of the run, reported once by {@link SimulationController#takeNewLapRecord()} so the interface
 * can announce it.
 *
 * @param lapTime    the new record, seconds
 * @param previous   the record it beats, NaN for the first lap of the run
 * @param generation generation of the car that drove it
 * @param carId      id of that car in the displayed simulation, -1 if it was only seen in the generation's results
 */
public record LapRecord(double lapTime, double previous, int generation, int carId) {

    public LapRecord {
        if (!(lapTime > 0.0)) {
            throw new IllegalArgumentException("Lap time must be positive.");
        }

        if (!Double.isNaN(previous) && !(lapTime < previous)) {
            throw new IllegalArgumentException("A record must beat the previous one.");
        }
    }

    public boolean isFirstLap() {
        return Double.isNaN(previous);
    }

    /**
     * Seconds gained on the previous record, NaN for the first lap.
     */
    public double gain() {
        return previous - lapTime;
    }

    /**
     * "0:17.42 · −0.31 s · generation 7 · car #12" (the gain is left out for the first lap).
     */
    public String label() {
        return Dashboard.lapTime(lapTime)
                + (isFirstLap() ? "" : String.format(Locale.ROOT, " · −%.2f s", gain()))
                + " · generation " + generation
                + (carId >= 0 ? " · car #" + carId : "");
    }
}
