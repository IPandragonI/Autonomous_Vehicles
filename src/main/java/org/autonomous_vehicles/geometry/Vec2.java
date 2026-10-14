
package org.autonomous_vehicles.geometry;

public record Vec2(double x, double y) {

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 subtract(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    public double cross(Vec2 other) {
        return x * other.y - y * other.x;
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distanceTo(Vec2 other) {
        return subtract(other).length();
    }

    public Vec2 normalize() {
        double length = length();

        if (length == 0.0) {
            throw new IllegalStateException("Cannot normalize a zero vector.");
        }

        return scale(1.0 / length);
    }

    public static Vec2 fromAngle(double angle) {
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }
}
