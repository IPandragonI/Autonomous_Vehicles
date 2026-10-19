package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

/**
 * State of a car: position, heading, speed and whether it is still alive.
 * For collisions the car is modelled as a circle of radius {@link #RADIUS} centred on its position.
 */
public class Car {

    public static final double RADIUS = 1.2;

    private double x;
    private double y;
    private double angle;
    private double speed;
    private boolean alive;

    // Drivetrain and tyre state, used by the dynamic model (the kinematic model ignores it).
    private int gear = 1;
    private double engineRpm;
    private double tireWear;
    private boolean sliding;

    public Car(double x, double y, double angle, double speed) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(angle) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("Car state must contain finite values.");
        }

        this.x = x;
        this.y = y;
        this.angle = angle;
        this.speed = speed;
        this.alive = true;
    }

    public static Car atStart(Track track) {
        return new Car(track.getStartPosition().x(), track.getStartPosition().y(), track.getStartAngle(), 0.0);
    }

    /**
     * Removes the car from the race (wall hit, timeout, stagnation or race finished).
     */
    public void crash() {
        alive = false;
        speed = 0.0;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Vec2 getPosition() {
        return new Vec2(x, y);
    }

    public double getAngle() {
        return angle;
    }

    public double getSpeed() {
        return speed;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /**
     * Current gear: 1 = first, -1 = reverse.
     */
    public int getGear() {
        return gear;
    }

    public void setGear(int gear) {
        this.gear = gear;
    }

    public double getEngineRpm() {
        return engineRpm;
    }

    public void setEngineRpm(double engineRpm) {
        this.engineRpm = engineRpm;
    }

    /**
     * Tyre wear, from 0 (new) to 1 (worn out).
     */
    public double getTireWear() {
        return tireWear;
    }

    public void setTireWear(double tireWear) {
        this.tireWear = Math.clamp(tireWear, 0.0, 1.0);
    }

    /**
     * True when the tyres were beyond their grip during the last step (understeer, wheelspin or locking).
     */
    public boolean isSliding() {
        return sliding;
    }

    public void setSliding(boolean sliding) {
        this.sliding = sliding;
    }
}
