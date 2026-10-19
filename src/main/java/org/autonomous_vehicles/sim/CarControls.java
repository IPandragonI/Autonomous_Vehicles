package org.autonomous_vehicles.sim;

public record CarControls(double steer, double throttle) {

    public CarControls {
        if (!Double.isFinite(steer) || !Double.isFinite(throttle) || steer < -1.0 || steer > 1.0 || throttle < -1.0 || throttle > 1.0) {
            throw new IllegalArgumentException("Steer and throttle must be between -1 and 1.");
        }
    }

    public static CarControls neutral() {
        return new CarControls(0.0, 0.0);
    }
}
