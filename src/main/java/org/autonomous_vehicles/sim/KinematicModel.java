package org.autonomous_vehicles.sim;

/**
 * The deliberately simple model of the assignment (step 13): {@link CarPhysics}, unlimited grip,
 * constant acceleration, speed capped at {@link CarPhysics#MAX_SPEED}. Default model, used by the
 * assignment tests and experiments.
 */
public final class KinematicModel implements VehicleModel {

    public static final KinematicModel INSTANCE = new KinematicModel();

    private KinematicModel() {
    }

    @Override
    public void step(Car car, CarControls controls, double dt) {
        CarPhysics.step(car, controls.steer(), controls.throttle(), dt);
    }

    @Override
    public double topSpeed() {
        return CarPhysics.MAX_SPEED;
    }

    @Override
    public String name() {
        return "Kinematic (assignment)";
    }
}
