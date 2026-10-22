package org.autonomous_vehicles.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Car types. "kinematic" is the simple model of the assignment; the others are {@link DynamicModel}s
 * with plausible orders of magnitude (1 track unit = 1 m). Add a type by adding a {@link CarSpec} to
 * {@link #PRESETS}.
 */
public final class Vehicles {

    public static final String KINEMATIC = "kinematic";

    public static final Map<String, CarSpec> PRESETS;

    static {
        Map<String, CarSpec> presets = new LinkedHashMap<>();
        // 125 cc kart, direct drive (one gear), ~120 km/h.
        presets.put("kart", new CarSpec("Kart", 165, 20, 3000, 10000, 14000, new double[]{1.0}, 6.0,
                0.95, 0.14, 0.55, 0.0, 0.020, 1.05, 0.45, 1.00, 12.0, 0.65));
        // Front-wheel drive hatchback on road tyres, 5 gears.
        presets.put("hatchback", new CarSpec("Hatchback", 1200, 150, 900, 4000, 6500,
                new double[]{3.5, 2.0, 1.4, 1.05, 0.85}, 4.0, 0.90, 0.30, 0.65, 0.0, 0.012, 2.5, 0.60, 0.80,
                9.5, 0.60));
        // Rear-wheel drive sports car, 6 gears, a little downforce.
        presets.put("sports", new CarSpec("Sports car", 1450, 450, 1000, 4500, 7500,
                new double[]{3.2, 2.2, 1.6, 1.25, 1.0, 0.8}, 3.5, 0.90, 0.33, 0.70, 0.30, 0.012, 2.6, 0.55, 0.95,
                11.0, 0.55));
        // Single-seater, 8 gears, high downforce: grip grows with speed.
        presets.put("formula", new CarSpec("Formula", 750, 400, 4000, 10500, 15000,
                new double[]{3.0, 2.4, 1.95, 1.65, 1.42, 1.25, 1.12, 1.0}, 6.0, 0.92, 0.33, 1.00, 3.00, 0.015, 3.0,
                0.40, 1.10, 15.0, 0.60));
        PRESETS = Collections.unmodifiableMap(presets);
    }

    public static final List<String> TYPES;

    static {
        List<String> types = new ArrayList<>();
        types.add(KINEMATIC);
        types.addAll(PRESETS.keySet());
        TYPES = List.copyOf(types);
    }

    private Vehicles() {
    }

    /**
     * @param tire ignored by the kinematic model (unlimited grip)
     */
    public static VehicleModel create(String type, TireCompound tire) {
        return create(type, tire, RaceRules.NONE);
    }

    /**
     * @param rules fuel and tyre temperature, ignored by the kinematic model
     */
    public static VehicleModel create(String type, TireCompound tire, RaceRules rules) {
        return create(type, tire, rules, false);
    }

    /**
     * @param gripSteering steering relative to the grip (see {@link DynamicModel#steeringCurvature}), ignored by the
     *                     kinematic model
     */
    public static VehicleModel create(String type, TireCompound tire, RaceRules rules, boolean gripSteering) {
        if (KINEMATIC.equals(type)) {
            return KinematicModel.INSTANCE;
        }

        CarSpec spec = PRESETS.get(type);

        if (spec == null) {
            throw new IllegalArgumentException("Unknown car type '" + type + "'; known types: " + TYPES);
        }

        return new DynamicModel(spec, tire, rules, gripSteering);
    }
}
