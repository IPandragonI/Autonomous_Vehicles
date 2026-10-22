package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

/**
 * Distance sensors: rays cast from the car centre at fixed angles relative to its heading
 * (positive angle = to the left). Each reading is the distance to the nearest wall,
 * normalised to [0, 1] by the range; a ray that hits nothing reads 1.0.
 */
public class RaySensor {

    public static final double DEFAULT_RANGE = 80.0;

    private final double[] angles;
    private final double range;

    public RaySensor(double[] anglesRadians, double range) {
        if (anglesRadians == null || anglesRadians.length == 0) {
            throw new IllegalArgumentException("A sensor needs at least one ray.");
        }

        for (double angle : anglesRadians) {
            if (!Double.isFinite(angle)) {
                throw new IllegalArgumentException("Ray angles must be finite.");
            }
        }

        if (!Double.isFinite(range) || range <= 0.0) {
            throw new IllegalArgumentException("Sensor range must be positive.");
        }

        this.angles = anglesRadians.clone();
        this.range = range;
    }

    /**
     * The five rays of the assignment: -90°, -45°, 0°, +45°, +90°.
     */
    public static RaySensor standard() {
        return evenlySpread(5, Math.PI, DEFAULT_RANGE);
    }

    public static RaySensor evenlySpread(int rayCount, double fieldOfView, double range) {
        if (rayCount < 1) {
            throw new IllegalArgumentException("A sensor needs at least one ray.");
        }

        double[] angles = new double[rayCount];

        for (int i = 0; i < rayCount; i++) {
            angles[i] = rayCount == 1 ? 0.0 : -fieldOfView / 2.0 + fieldOfView * i / (rayCount - 1);
        }

        return new RaySensor(angles, range);
    }

    /**
     * Rays packed towards the front: angle = ±(fieldOfView / 2) × u², u evenly spread in [0, 1] from the centre ray
     * outwards. 9 rays over 180° point at 0°, ±5.6°, ±22.5°, ±50.6°, ±90°: at speed what matters is where the road
     * goes far ahead, which evenly spread rays sample with a single ray. Symmetric, like {@link #evenlySpread}.
     */
    public static RaySensor forwardFocused(int rayCount, double fieldOfView, double range) {
        if (rayCount < 1) {
            throw new IllegalArgumentException("A sensor needs at least one ray.");
        }

        double[] angles = new double[rayCount];

        for (int i = 0; i < rayCount; i++) {
            double u = rayCount == 1 ? 0.0 : (2.0 * i - (rayCount - 1)) / (rayCount - 1);
            angles[i] = Math.signum(u) * u * u * fieldOfView / 2.0;
        }

        return new RaySensor(angles, range);
    }

    public double[] sense(Car car, Track track) {
        double[] distances = distances(car, track);

        for (int i = 0; i < distances.length; i++) {
            distances[i] /= range;
        }

        return distances;
    }

    /**
     * Raw distances, capped at the range.
     */
    public double[] distances(Car car, Track track) {
        if (car == null || track == null) {
            throw new IllegalArgumentException("Car and track must not be null.");
        }

        double[] distances = new double[angles.length];
        double x = car.getX(), y = car.getY(), heading = car.getAngle();

        for (int i = 0; i < angles.length; i++) {
            // Same arithmetic as Vec2.fromAngle(heading + angle), without a Vec2 per ray.
            double angle = heading + angles[i];
            distances[i] = track.distanceToWall(x, y, Math.cos(angle), Math.sin(angle), range);
        }

        return distances;
    }

    /**
     * End point of every ray (the hit point, or the end of the range), for drawing.
     */
    public Vec2[] hitPoints(Car car, Track track) {
        double[] distances = distances(car, track);
        Vec2[] points = new Vec2[angles.length];

        for (int i = 0; i < angles.length; i++) {
            points[i] = car.getPosition().add(Vec2.fromAngle(car.getAngle() + angles[i]).scale(distances[i]));
        }

        return points;
    }

    public int getRayCount() {
        return angles.length;
    }

    public double[] getAngles() {
        return angles.clone();
    }

    public double getRange() {
        return range;
    }
}
