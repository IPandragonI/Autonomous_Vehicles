package org.autonomous_vehicles.sim;

public final class CarPhysics {

    public static final double MAX_ACCELERATION = 8.0;
    public static final double MAX_SPEED = 20.0;
    public static final double FRICTION = 2.0;
    public static final double MAX_STEERING_ANGLE = 0.6;
    public static final double WHEEL_BASE = 2.5;

    private CarPhysics() {
    }

    public static void step(Car car, double steer, double throttle, double dt) {
        if (car == null) {
            throw new IllegalArgumentException("Car must not be null.");
        }

        CarControls controls = new CarControls(steer, throttle);

        if (!Double.isFinite(dt) || dt <= 0.0 || dt > 0.1) {
            throw new IllegalArgumentException("dt must be greater than 0 and at most 0.1 seconds.");
        }

        if (!car.isAlive()) return;

        double acceleration = controls.throttle() * MAX_ACCELERATION;

        if (Math.abs(controls.throttle()) < 1e-9) {
            acceleration -= Math.signum(car.getSpeed()) * FRICTION;
        }

        double newSpeed = car.getSpeed() + acceleration * dt;

        if (Math.abs(controls.throttle()) < 1e-9 && car.getSpeed() != 0.0 && Math.signum(newSpeed) != Math.signum(car.getSpeed())) {
            newSpeed = 0.0;
        }

        newSpeed = Math.clamp(newSpeed, -MAX_SPEED, MAX_SPEED);
        car.setSpeed(newSpeed);

        double steeringAngle = controls.steer() * MAX_STEERING_ANGLE;
        double angularVelocity = newSpeed / WHEEL_BASE * Math.tan(steeringAngle);

        double newAngle = car.getAngle() + angularVelocity * dt;
        car.setAngle(normalizeAngle(newAngle));

        double newX = car.getX() + newSpeed * Math.cos(car.getAngle()) * dt;
        double newY = car.getY() + newSpeed * Math.sin(car.getAngle()) * dt;

        car.setPosition(newX, newY);
    }

    private static double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
