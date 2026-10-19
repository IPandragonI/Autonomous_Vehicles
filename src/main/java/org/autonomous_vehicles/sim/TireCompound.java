package org.autonomous_vehicles.sim;

/**
 * Tyre compounds: softer tyres grip more but wear faster, and grip falls as they wear.
 * Effective friction coefficient = car grip factor × compound grip × (1 - {@link #WEAR_GRIP_LOSS} × wear).
 */
public enum TireCompound {
    SOFT("Soft", 1.25, 0.30),
    MEDIUM("Medium", 1.15, 0.15),
    HARD("Hard", 1.05, 0.07);

    /** Fraction of grip lost by a completely worn tyre (wear = 1). */
    public static final double WEAR_GRIP_LOSS = 0.35;

    /** Wear multiplier while the tyres slide (wheelspin, locked brakes, understeer). */
    public static final double SLIDING_WEAR_FACTOR = 4.0;

    private final String label;
    private final double grip;
    private final double wearPerKilometre;

    TireCompound(String label, double grip, double wearPerKilometre) {
        this.label = label;
        this.grip = grip;
        this.wearPerKilometre = wearPerKilometre;
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
