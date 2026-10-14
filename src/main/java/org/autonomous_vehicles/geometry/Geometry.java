package org.autonomous_vehicles.geometry;

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

        // Le rayon et le segment sont parallèles.
        // Vérifie s'ils sont également sur la même droite.
        if (Math.abs(offset.cross(rayDirection)) > EPSILON) {
            return Optional.empty();
        }

        double directionLengthSquared = rayDirection.lengthSquared();

        double t1 = segment.start().subtract(rayOrigin).dot(rayDirection) / directionLengthSquared;

        double t2 = segment.end().subtract(rayOrigin).dot(rayDirection) / directionLengthSquared;

        double nearest = Math.min(t1, t2);
        double farthest = Math.max(t1, t2);

        // Tout le segment se trouve derrière le rayon.
        if (farthest < -EPSILON) {
            return Optional.empty();
        }

        // Le chevauchement commence à l'origine du rayon.
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

        // Les deux segments sont réduits à des points.
        if (rLengthSquared <= EPSILON * EPSILON && sLengthSquared <= EPSILON * EPSILON) {
            return p.distanceTo(q) <= EPSILON ? Optional.of(p) : Optional.empty();
        }

        // Le premier segment est un point.
        if (rLengthSquared <= EPSILON * EPSILON) {
            return pointOnSegment(p, second) ? Optional.of(p) : Optional.empty();
        }

        // Le deuxième segment est un point.
        if (sLengthSquared <= EPSILON * EPSILON) {
            return pointOnSegment(q, first) ? Optional.of(q) : Optional.empty();
        }

        Vec2 offset = q.subtract(p);
        double denominator = r.cross(s);

        // Segments non parallèles.
        if (Math.abs(denominator) > EPSILON) {
            double t = offset.cross(s) / denominator;
            double u = offset.cross(r) / denominator;

            if (t >= -EPSILON && t <= 1.0 + EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON) {
                double boundedT = Math.clamp(t, 0.0, 1.0);
                return Optional.of(p.add(r.scale(boundedT)));
            }

            return Optional.empty();
        }

        // Parallèles mais sur des droites différentes.
        if (Math.abs(offset.cross(r)) > EPSILON) {
            return Optional.empty();
        }

        // Colinéaires : cherche le début du chevauchement.
        double t1 = offset.dot(r) / rLengthSquared;
        double t2 = q.add(s).subtract(p).dot(r) / rLengthSquared;

        double start = Math.clamp(t2, 0.0, t1);
        double end = Math.clamp(t2, t1, 1.0);

        if (start > end + EPSILON) {
            return Optional.empty();
        }

        return Optional.of(p.add(r.scale(Math.clamp(start, 0.0, 1.0))));
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
