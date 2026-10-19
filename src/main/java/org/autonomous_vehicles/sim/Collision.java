package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

/**
 * Car / wall collisions. The car is a circle: it touches a wall when the distance
 * from its centre to the wall segment is at most its radius. The track's wall grid limits
 * the test to the walls near the car.
 */
public final class Collision {

    private Collision() {
    }

    public static boolean hits(Car car, Track track) {
        if (car == null || track == null) {
            throw new IllegalArgumentException("Car and track must not be null.");
        }

        return hits(car.getPosition(), Car.RADIUS, track);
    }

    public static boolean hits(Vec2 center, double radius, Track track) {
        return track.circleTouchesWall(center, radius);
    }

    /**
     * True when the straight move from {@code from} to {@code to} crosses a wall.
     * Guards against "tunnelling" through a wall when a step is longer than the car radius.
     */
    public static boolean crossesWall(Vec2 from, Vec2 to, Track track) {
        return track.segmentCrossesWall(from, to);
    }
}
