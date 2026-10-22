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
 * @param tyreWear        multiplier of the compounds' wear rates ({@link TireCompound#wearPerKilometre}); 1 = the
 *                        rates as they were before this setting existed, 0 = tyres never wear
 * @param tyreHeating     tread heating at full grip use, °C per metre (see {@link DynamicModel#TYRE_HEATING})
 */
public record RaceRules(double fuelAutonomy, boolean tyreTemperature, double tyreWear, double tyreHeating) {

    /** Neither mechanic: the dynamic model behaves exactly as before they existed. */
    public static final RaceRules NONE = new RaceRules(0.0, false);

    /**
     * Measured on the hairpin: evolved champions brake for the bends and use only 17–26 % of full power on average,
     * so 25 s of full power lasts them 95–150 s (more than the ~90 s time limit) while a car that never lifts runs
     * dry after 25 s. Fuel saving matters without making a lap impossible.
     */
    public static final double DEFAULT_FUEL_AUTONOMY = 25.0;

    public static final double MAX_FUEL_AUTONOMY = 3600.0;

    /**
     * At ×1 a medium tyre loses 0.15 of its life per km without sliding: 0.65 per 4.3 km São Paulo lap, so it is
     * worn out (−35 % grip) within the 2–3 laps of a run, and sooner with the ×4 of sliding. A real F1 medium lasts
     * ~30 laps there (~0.008 per km). ×0.1 gives ~15 clean laps for a medium, ~8 for a soft, ~33 for a hard: wear
     * stays visible over a run (and still punishes sliding) without wrecking the grip on long circuits.
     */
    public static final double DEFAULT_TYRE_WEAR = 0.1;

    public static final double MAX_TYRE_WEAR = 2.0;

    /**
     * Steady state = 25 + heating × grip use / (0.0015 + 0.006 / v) °C: with 0.12, driving at the limit settles at
     * 92 °C (20 m/s) to 101 °C (80 m/s), inside every window, as real tyres do when pushed, while repeated sliding
     * (× {@link DynamicModel#SLIDING_HEAT_FACTOR}) still overheats them. The original 0.20 settled at 136–158 °C:
     * a hand-written driver at 85 % of the grip reached 190 °C on São Paulo and lost 30 % of its grip.
     */
    public static final double DEFAULT_TYRE_HEATING = 0.12;

    public RaceRules {
        if (!Double.isFinite(fuelAutonomy) || fuelAutonomy < 0.0 || fuelAutonomy > MAX_FUEL_AUTONOMY) {
            throw new IllegalArgumentException("Fuel autonomy must be between 0 (unlimited) and " + MAX_FUEL_AUTONOMY
                    + " seconds.");
        }

        if (!Double.isFinite(tyreWear) || tyreWear < 0.0 || tyreWear > MAX_TYRE_WEAR) {
            throw new IllegalArgumentException("Tyre wear must be between 0 (no wear) and " + MAX_TYRE_WEAR + ".");
        }

        if (!Double.isFinite(tyreHeating) || tyreHeating < 0.0 || tyreHeating > 1.0) {
            throw new IllegalArgumentException("Tyre heating must be between 0 and 1 °C per metre.");
        }
    }

    /**
     * Rules with the original wear and heating rates, as before those settings existed.
     */
    public RaceRules(double fuelAutonomy, boolean tyreTemperature) {
        this(fuelAutonomy, tyreTemperature, 1.0);
    }

    /**
     * Rules with the original heating rate {@link DynamicModel#TYRE_HEATING}.
     */
    public RaceRules(double fuelAutonomy, boolean tyreTemperature, double tyreWear) {
        this(fuelAutonomy, tyreTemperature, tyreWear, DynamicModel.TYRE_HEATING);
    }

    /**
     * Both mechanics on, with the reduced wear and the recalibrated heating, as the interface starts.
     */
    public static RaceRules defaults() {
        return new RaceRules(DEFAULT_FUEL_AUTONOMY, true, DEFAULT_TYRE_WEAR, DEFAULT_TYRE_HEATING);
    }

    public boolean limitedFuel() {
        return fuelAutonomy > 0.0;
    }

    public RaceRules withFuelAutonomy(double seconds) {
        return new RaceRules(seconds, tyreTemperature, tyreWear, tyreHeating);
    }

    public RaceRules withTyreTemperature(boolean enabled) {
        return new RaceRules(fuelAutonomy, enabled, tyreWear, tyreHeating);
    }

    public RaceRules withTyreWear(double multiplier) {
        return new RaceRules(fuelAutonomy, tyreTemperature, multiplier, tyreHeating);
    }
}
