package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * A racing line: the closed path through the track with the least curvature, so bends can be taken faster than on
 * the centre line (out–in–out). Each point sits on the normal of a centre-line station at a lateral offset;
 * repeatedly, every point moves to reduce the squared second differences of the path (its curvature) and is clamped
 * between the walls minus a margin. The usual approximation of the minimum-curvature line.
 */
public final class RacingLine {

    /** Distance between two stations along the centre line, m. */
    public static final double STATION_SPACING = 3.0;
    /** Walls further than this along a normal are ignored (a station never needs more room). */
    private static final double MAX_LATERAL = 30.0;
    /** Share of the step towards the smoothing target taken per pass (the 4th-order update overshoots at 1). */
    private static final double RELAXATION = 0.5;

    /**
     * Measured on São Paulo with the formula car's expert (corner 0.85, braking 0.75), flying lap: centre line 1:51.5;
     * racing line 1:41.9 after 4 000 passes, 1:39.4 after 20 000, 1:38.3 after 80 000 (2 s to compute) — the
     * 4th-order smoothing converges slowly. With 1.5 m from the walls the converged line crashed at the first bend.
     */
    public static final double DEFAULT_WALL_MARGIN = 2.5;
    public static final int DEFAULT_ITERATIONS = 80_000;

    private RacingLine() {
    }

    /**
     * The racing line with the default margin and number of passes.
     */
    public static List<Vec2> of(Track track) {
        return minimumCurvature(track, DEFAULT_WALL_MARGIN, DEFAULT_ITERATIONS);
    }

    /**
     * @param wallMargin distance kept between the line and each wall, m (at least the car's radius)
     * @param iterations smoothing passes; a few thousand converge on a real circuit
     */
    public static List<Vec2> minimumCurvature(Track track, double wallMargin, int iterations) {
        if (track == null || !(wallMargin >= Car.RADIUS) || iterations < 0) {
            throw new IllegalArgumentException("Need a track, a wall margin of at least the car radius and "
                    + "a non-negative number of iterations.");
        }

        TrackPreview centre = track.getPreview();
        int n = Math.max(12, (int) Math.round(centre.length() / STATION_SPACING));
        double spacing = centre.length() / n;
        double[] bx = new double[n];
        double[] by = new double[n];
        double[] nx = new double[n];
        double[] ny = new double[n];
        double[] left = new double[n];
        double[] right = new double[n];

        for (int i = 0; i < n; i++) {
            double[] p = centre.pointAt(i * spacing);
            double[] normal = centre.leftNormalAt(i * spacing);
            bx[i] = p[0];
            by[i] = p[1];
            nx[i] = normal[0];
            ny[i] = normal[1];
            left[i] = Math.max(0.0, track.distanceToWall(p[0], p[1], nx[i], ny[i], MAX_LATERAL) - wallMargin);
            right[i] = Math.max(0.0, track.distanceToWall(p[0], p[1], -nx[i], -ny[i], MAX_LATERAL) - wallMargin);
        }

        double[] offset = new double[n];
        double[] next = new double[n];
        double[] px = new double[n];
        double[] py = new double[n];

        for (int iteration = 0; iteration < iterations; iteration++) {
            for (int i = 0; i < n; i++) {
                px[i] = bx[i] + nx[i] * offset[i];
                py[i] = by[i] + ny[i] * offset[i];
            }

            for (int i = 0; i < n; i++) {
                // The point that zeroes the change of the second difference (P[i-1] - 2 P[i] + P[i+1]) around i:
                // minimising the sum of squared second differences = minimising curvature. Moving towards the
                // neighbours' midpoint instead minimises the length: the shortest path, with sharp turns at the
                // apexes, which measured slower than the centre line (profile 99 s against 97 s on São Paulo).
                int a2 = (i + n - 2) % n;
                int a1 = (i + n - 1) % n;
                int b1 = (i + 1) % n;
                int b2 = (i + 2) % n;
                double tx = (-px[a2] + 4.0 * px[a1] + 4.0 * px[b1] - px[b2]) / 6.0;
                double ty = (-py[a2] + 4.0 * py[a1] + 4.0 * py[b1] - py[b2]) / 6.0;
                double lateral = (tx - bx[i]) * nx[i] + (ty - by[i]) * ny[i];
                next[i] = Math.clamp(offset[i] + RELAXATION * (lateral - offset[i]), -right[i], left[i]);
            }

            System.arraycopy(next, 0, offset, 0, n);
        }

        List<Vec2> line = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            line.add(new Vec2(bx[i] + nx[i] * offset[i], by[i] + ny[i] * offset[i]));
        }

        return line;
    }
}
