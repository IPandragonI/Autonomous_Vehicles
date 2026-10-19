package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.sim.Vehicles;

import java.util.Map;

/**
 * How a car type is drawn (top view): real dimensions in metres and a drawing style. Visual only — every car type
 * still collides as a circle of radius {@link org.autonomous_vehicles.sim.Car#RADIUS}.
 */
public record CarAppearance(double length, double width, Style style) {

    public enum Style { KART, OPEN_WHEEL, HATCHBACK, SPORTS, GENERIC }

    private static final Map<String, CarAppearance> BY_TYPE = Map.of(
            Vehicles.KINEMATIC, new CarAppearance(4.2, 1.8, Style.GENERIC),
            "kart", new CarAppearance(1.9, 1.3, Style.KART),
            "hatchback", new CarAppearance(4.0, 1.75, Style.HATCHBACK),
            "sports", new CarAppearance(4.5, 1.9, Style.SPORTS),
            "formula", new CarAppearance(5.4, 1.9, Style.OPEN_WHEEL));

    public CarAppearance {
        if (!(length > 0.0) || !(width > 0.0) || style == null) {
            throw new IllegalArgumentException("A car appearance needs positive dimensions and a style.");
        }
    }

    /**
     * Appearance of a car type; unknown types are drawn as a generic car.
     */
    public static CarAppearance forType(String carType) {
        return BY_TYPE.getOrDefault(carType, BY_TYPE.get(Vehicles.KINEMATIC));
    }
}
