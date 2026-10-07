package org.autonomous_vehicles.sim;

public class Car {
    private double x;
    private double y;
    private double angle;
    private double speed;
    private boolean alive;

    public Car(double x, double y, double angle, double speed) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.speed = speed;
        this.alive = true;
    }

    public void update(double deltaTime) {
        x += speed * Math.cos(angle) * deltaTime;
        y += speed * Math.sin(angle) * deltaTime;
    }

    public void control(double steeringAngle, double acceleration) {
        angle += steeringAngle;
        speed = Math.max(0, speed + acceleration);
    }

    public void crash() {
        alive = false;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
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
}
