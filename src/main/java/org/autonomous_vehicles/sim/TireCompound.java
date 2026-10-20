package org.autonomous_vehicles.sim;

/**
 * Tyre compounds: softer tyres grip more but wear faster, and grip falls as they wear.
 * Effective friction coefficient = car grip factor × compound grip × (1 - {@link #WEAR_GRIP_LOSS} × wear)
 * × {@link #temperatureGrip} (the last factor only when tyre temperature is simulated).
 * <p>
 * Softer compounds work at lower temperatures (they warm up sooner but overheat sooner), as in racing.
 */
public enum TireCompound {
    SOFT("Soft", 1.25, 0.30, 80.0, 100.0),
    MEDIUM("Medium", 1.15, 0.15, 90.0, 110.0),
    HARD("Hard", 1.05, 0.07, 100.0, 120.0);

    /** Fraction of grip lost by a completely worn tyre (wear = 1). */
    public static final double WEAR_GRIP_LOSS = 0.35;

    /** Wear multiplier while the tyres slide (wheelspin, locked brakes, understeer). */
    public static final double SLIDING_WEAR_FACTOR = 4.0;

    /** Fraction of grip lost by a tyre {@link #COLD_SPAN} °C or more below its window. */
    public static final double COLD_GRIP_LOSS = 0.25;
    public static final double COLD_SPAN = 60.0;
    /** Fraction of grip lost by a tyre {@link #HOT_SPAN} °C or more above its window (the rubber blisters). */
    public static final double HOT_GRIP_LOSS = 0.30;
    public static final double HOT_SPAN = 40.0;

    private final String label;
    private final double grip;
    private final double wearPerKilometre;
    private final double optimalLow;
    private final double optimalHigh;

    TireCompound(String label, double grip, double wearPerKilometre, double optimalLow, double optimalHigh) {
        this.label = label;
        this.grip = grip;
        this.wearPerKilometre = wearPerKilometre;
        this.optimalLow = optimalLow;
        this.optimalHigh = optimalHigh;
    }

    /**
     * Grip multiplier at a tyre temperature (°C): 1 inside the optimal window, falling linearly to
     * 1 - {@link #COLD_GRIP_LOSS} when too cold and to 1 - {@link #HOT_GRIP_LOSS} when overheated.
     */
    public double temperatureGrip(double celsius) {
        if (celsius < optimalLow) {
            return 1.0 - COLD_GRIP_LOSS * Math.min(1.0, (optimalLow - celsius) / COLD_SPAN);
        }

        if (celsius > optimalHigh) {
            return 1.0 - HOT_GRIP_LOSS * Math.min(1.0, (celsius - optimalHigh) / HOT_SPAN);
        }

        return 1.0;
    }

    /**
     * Lower bound of the temperature window where the compound grips best, °C.
     */
    public double optimalLow() {
        return optimalLow;
    }

    public double optimalHigh() {
        return optimalHigh;
    }

    public double grip() {
        return grip;
    }

    /**
     * Wear added per kilometre driven without sliding (wear goes from 0 = new to 1 = worn out).
     */
    public double wearPerKilometre() {
        return wearPerKilometre;
    }

    public double gripAt(double wear) {
        return grip * (1.0 - WEAR_GRIP_LOSS * Math.clamp(wear, 0.0, 1.0));
    }

    @Override
    public String toString() {
        return label;
    }
}
