package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;

/**
 * A gate crossing the track, from one wall to the other.
 * {@code forward} is the driving direction: a crossing only counts when the car moves along it,
 * which is what prevents a car from scoring by driving the wrong way.
 */
public record Checkpoint(Segment gate, Vec2 forward) {

    public Checkpoint {
        if (gate == null || forward == null) {
            throw new IllegalArgumentException("Checkpoint gate and direction must not be null.");
        }

        if (gate.length() == 0.0 || forward.length() == 0.0) {
            throw new IllegalArgumentException("Checkpoint gate and direction must not be degenerate.");
        }

        forward = forward.normalize();
    }

    public Vec2 midpoint() {
        return gate.start().add(gate.end()).scale(0.5);
    }

    public boolean isCrossedForward(Vec2 from, Vec2 to) {
        Vec2 move = to.subtract(from);

        if (move.dot(forward) <= 0.0) {
            return false;
        }

        return Geometry.intersectSegments(new Segment(from, to), gate).isPresent();
    }
}
