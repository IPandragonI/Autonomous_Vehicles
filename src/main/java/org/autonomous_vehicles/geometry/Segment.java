
package org.autonomous_vehicles.geometry;

public record Segment(Vec2 start, Vec2 end) {

    public Segment {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Segment endpoints must not be null.");
        }
    }

    public Vec2 direction() {
        return end.subtract(start);
    }

    public double length() {
        return start.distanceTo(end);
    }
}
