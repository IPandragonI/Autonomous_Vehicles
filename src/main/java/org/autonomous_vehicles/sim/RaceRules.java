package org.autonomous_vehicles.sim;

/**
 * Optional race mechanics of the grip-limited cars ({@link DynamicModel}); the kinematic model of the assignment
 * ignores them, so its behaviour never changes.
 *
 * @param fuelAutonomy    seconds the starting fuel lasts at full power, 0 = unlimited (no consumption). A real tank
 *                        lasts far longer than a 90 s evaluation, so the load is a "sprint" fuel load that makes
 *                        fuel saving part of the task; expressed in seconds of full power, it means the same for
 *                        every car type
 * @param tyreTemperature tyres heat up with grip use and sliding and cool down in the airflow; grip is best inside
 *                        the compound's temperature window (see {@link TireCompound#temperatureGrip})
 */
public record RaceRules(double fuelAutonomy, boolean tyreTemperature) {

    /** Neither mechanic: the dynamic model behaves exactly as before they existed. */
    public static final RaceRules NONE = new RaceRules(0.0, false);

    /**
     * Measured on the hairpin: evolved champions brake for the bends and use only 17–26 % of full power on average,
     * so 25 s of full power lasts them 95–150 s (more than the ~90 s time limit) while a car that never lifts runs
     * dry after 25 s. Fuel saving matters without making a lap impossible.
     */
    public static final double DEFAULT_FUEL_AUTONOMY = 25.0;

    public static final double MAX_FUEL_AUTONOMY = 3600.0;

    public RaceRules {
        if (!Double.isFinite(fuelAutonomy) || fuelAutonomy < 0.0 || fuelAutonomy > MAX_FUEL_AUTONOMY) {
            throw new IllegalArgumentException("Fuel autonomy must be between 0 (unlimited) and " + MAX_FUEL_AUTONOMY
                    + " seconds.");
        }
    }

    /**
     * Both mechanics on, as the interface starts.
     */
    public static RaceRules defaults() {
        return new RaceRules(DEFAULT_FUEL_AUTONOMY, true);
    }

    public boolean limitedFuel() {
        return fuelAutonomy > 0.0;
    }

    public RaceRules withFuelAutonomy(double seconds) {
        return new RaceRules(seconds, tyreTemperature);
    }

    public RaceRules withTyreTemperature(boolean enabled) {
        return new RaceRules(fuelAutonomy, enabled);
    }
}
