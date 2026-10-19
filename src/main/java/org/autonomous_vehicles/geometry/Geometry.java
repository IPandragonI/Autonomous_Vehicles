package org.autonomous_vehicles.geometry;

import java.util.List;
import java.util.Optional;

public final class Geometry {

    private static final double EPSILON = 1e-9;

    private Geometry() {
    }

    public static Optional<Vec2> intersectRaySegment(Vec2 rayOrigin, Vec2 rayDirection, Segment segment) {
        if (rayOrigin == null || rayDirection == null || segment == null) {
            throw new IllegalArgumentException("Arguments must not be null.");
        }

        if (rayDirection.lengthSquared() <= EPSILON * EPSILON) {
            return Optional.empty();
        }

        Vec2 segmentDirection = segment.direction();
        Vec2 offset = segment.start().subtract(rayOrigin);

        double denominator = rayDirection.cross(segmentDirection);

        if (Math.abs(denominator) > EPSILON) {
            double t = offset.cross(segmentDirection) / denominator;
            double u = offset.cross(rayDirection) / denominator;

            if (t >= -EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON) {
                return Optional.of(rayOrigin.add(rayDirection.scale(Math.max(0.0, t))));
            }

            return Optional.empty();
        }

        if (Math.abs(offset.cross(rayDirection)) > EPSILON) {
            return Optional.empty();
        }

        double directionLengthSquared = rayDirection.lengthSquared();

        double t1 = segment.start().subtract(rayOrigin).dot(rayDirection) / directionLengthSquared;

        double t2 = segment.end().subtract(rayOrigin).dot(rayDirection) / directionLengthSquared;

        double nearest = Math.min(t1, t2);
        double farthest = Math.max(t1, t2);

        if (farthest < -EPSILON) {
            return Optional.empty();
        }

        double t = Math.max(0.0, nearest);

        if (t > farthest + EPSILON) {
            return Optional.empty();
        }

        return Optional.of(rayOrigin.add(rayDirection.scale(t)));
    }

    public static Optional<Vec2> intersectSegments(Segment first, Segment second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Segments must not be null.");
        }

        Vec2 p = first.start();
        Vec2 r = first.direction();
        Vec2 q = second.start();
        Vec2 s = second.direction();

        double rLengthSquared = r.lengthSquared();
        double sLengthSquared = s.lengthSquared();

        if (rLengthSquared <= EPSILON * EPSILON && sLengthSquared <= EPSILON * EPSILON) {
            return p.distanceTo(q) <= EPSILON ? Optional.of(p) : Optional.empty();
        }

        if (rLengthSquared <= EPSILON * EPSILON) {
            return pointOnSegment(p, second) ? Optional.of(p) : Optional.empty();
        }

        if (sLengthSquared <= EPSILON * EPSILON) {
            return pointOnSegment(q, first) ? Optional.of(q) : Optional.empty();
        }

        Vec2 offset = q.subtract(p);
        double denominator = r.cross(s);

        if (Math.abs(denominator) > EPSILON) {
            double t = offset.cross(s) / denominator;
            double u = offset.cross(r) / denominator;

            if (t >= -EPSILON && t <= 1.0 + EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON) {
                double boundedT = Math.clamp(t, 0.0, 1.0);
                return Optional.of(p.add(r.scale(boundedT)));
            }

            return Optional.empty();
        }

        if (Math.abs(offset.cross(r)) > EPSILON) {
            return Optional.empty();
        }

        double t1 = offset.dot(r) / rLengthSquared;
        double t2 = q.add(s).subtract(p).dot(r) / rLengthSquared;

        double start = Math.max(0.0, Math.min(t1, t2));
        double end = Math.min(1.0, Math.max(t1, t2));

        if (start > end + EPSILON) {
            return Optional.empty();
        }

        return Optional.of(p.add(r.scale(Math.min(start, 1.0))));
    }

    public static double distancePointToSegment(Vec2 point, Segment segment) {
        return point.distanceTo(closestPointOnSegment(point, segment));
    }

    public static Vec2 closestPointOnSegment(Vec2 point, Segment segment) {
        if (point == null || segment == null) {
            throw new IllegalArgumentException("Arguments must not be null.");
        }

        Vec2 direction = segment.direction();
        double lengthSquared = direction.lengthSquared();

        if (lengthSquared <= EPSILON * EPSILON) {
            return segment.start();
        }

        double t = Math.clamp(point.subtract(segment.start()).dot(direction) / lengthSquared, 0.0, 1.0);
        return segment.start().add(direction.scale(t));
    }

    /**
     * Signed area of a closed polygon (positive when the points turn counter-clockwise).
     */
    public static double signedArea(List<Vec2> polygon) {
        double area = 0.0;

        for (int i = 0; i < polygon.size(); i++) {
            Vec2 a = polygon.get(i);
            Vec2 b = polygon.get((i + 1) % polygon.size());
            area += a.cross(b);
        }

        return area / 2.0;
    }

    public static boolean isInsidePolygon(Vec2 point, List<Vec2> polygon) {
        boolean inside = false;

        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Vec2 a = polygon.get(i);
            Vec2 b = polygon.get(j);

            boolean crossesY = (a.y() > point.y()) != (b.y() > point.y());

            if (crossesY) {
                double intersectionX = a.x() + (point.y() - a.y()) * (b.x() - a.x()) / (b.y() - a.y());

                if (point.x() < intersectionX) {
                    inside = !inside;
                }
            }
        }

        return inside;
    }


    private static boolean pointOnSegment(Vec2 point, Segment segment) {
        Vec2 direction = segment.direction();
        Vec2 offset = point.subtract(segment.start());
        double lengthSquared = direction.lengthSquared();

        if (lengthSquared <= EPSILON * EPSILON) {
            return point.distanceTo(segment.start()) <= EPSILON;
        }

        if (Math.abs(direction.cross(offset)) > EPSILON) {
            return false;
        }

        double projection = offset.dot(direction);

        return projection >= -EPSILON && projection <= lengthSquared + EPSILON;
    }

}
