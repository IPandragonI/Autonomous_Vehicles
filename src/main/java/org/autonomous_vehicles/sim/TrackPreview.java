package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * The road ahead as a driver reads it: the direction of the centre line {@link #DISTANCES} metres ahead of the car,
 * relative to the car's heading. Rays only see the nearest wall, at most a few dozen metres away and not around a
 * bend, so a fast car cannot know where to brake; real autonomous racing stacks observe the track this way instead.
 * <p>
 * Reading i is (heading of the road {@code DISTANCES[i]} m ahead − heading of the car) / π, clamped to [-1, 1]:
 * positive = the road turns left of where the car points (same convention as the ray angles). Reading 0 is the
 * car's heading error; the differences between readings are the turning of the road, so a hairpin 150 m ahead shows
 * as a jump between the 100 m and 150 m readings. The road heading is unwrapped along the centre line, so a hairpin
 * reads ±1 instead of wrapping around.
 * <p>
 * Immutable and shared by every car of a track; the per-car search hint lives in the caller ({@link CarBrain}).
 */
public final class TrackPreview {

    /** Distances ahead along the centre line, m. 200 m covers braking from ~290 km/h at 15 m/s². */
    public static final double[] DISTANCES = {0.0, 25.0, 50.0, 100.0, 150.0, 200.0};
    public static final int INPUTS = DISTANCES.length;

    /** The nearest segment is first looked for this many segments behind and ahead of the previous one. */
    private static final int SEARCH_BEHIND = 4;
    private static final int SEARCH_AHEAD = 40;
    /** Farther than this from the segment found near the hint, the car is located again on the whole centre line. */
    private static final double LOST_DISTANCE = 30.0;

    private final double[] xs;
    private final double[] ys;
    /** Arc length at the start of each segment (segment i joins point i to point i + 1, the last one closes). */
    private final double[] starts;
    private final double[] lengths;
    /** Arc length of each segment's middle, then the first middle one lap later (n + 1 entries). */
    private final double[] middles;
    /** Unwrapped heading of each segment, then the first one after a lap (n + 1 entries). */
    private final double[] headings;
    private final double length;
    private final double lapTurn;

    /**
     * @param centerline closed centre line in driving order (the last point joins the first)
     */
    public TrackPreview(List<Vec2> centerline) {
        if (centerline == null || centerline.stream().anyMatch(point -> point == null)) {
            throw new IllegalArgumentException("Centre line must not be null.");
        }

        List<Vec2> points = new ArrayList<>();

        for (Vec2 point : centerline) {
            if (points.isEmpty() || points.getLast().distanceTo(point) > 1e-9) {
                points.add(point);
            }
        }

        while (points.size() > 1 && points.getLast().distanceTo(points.getFirst()) <= 1e-9) {
            points.removeLast();
        }

        if (points.size() < 3) {
            throw new IllegalArgumentException("A centre line needs at least three distinct points.");
        }

        int n = points.size();
        xs = new double[n];
        ys = new double[n];
        starts = new double[n];
        lengths = new double[n];
        middles = new double[n + 1];
        headings = new double[n + 1];

        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).x();
            ys[i] = points.get(i).y();
        }

        double arc = 0.0;

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double dx = xs[j] - xs[i];
            double dy = ys[j] - ys[i];
            double direction = Math.atan2(dy, dx);
            starts[i] = arc;
            lengths[i] = Math.hypot(dx, dy);
            middles[i] = arc + lengths[i] / 2.0;
            headings[i] = i == 0 ? direction : headings[i - 1] + wrap(direction - headings[i - 1]);
            arc += lengths[i];
        }

        length = arc;
        middles[n] = middles[0] + length;
        headings[n] = headings[n - 1] + wrap(headings[0] - headings[n - 1]);
        lapTurn = headings[n] - headings[0];
    }

    /**
     * Index of the centre-line segment nearest to (x, y): searched around {@code hint} (the previous answer for the
     * same car), on the whole centre line when {@code hint} is negative or the car is far from the local candidates.
     */
    public int locate(double x, double y, int hint) {
        int n = xs.length;

        if (hint >= 0 && hint < n) {
            int best = -1;
            double bestDistance = Double.POSITIVE_INFINITY;

            for (int k = -SEARCH_BEHIND; k <= SEARCH_AHEAD; k++) {
                int i = Math.floorMod(hint + k, n);
                double distance = squaredDistance(i, x, y);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }

            if (bestDistance <= LOST_DISTANCE * LOST_DISTANCE) {
                return best;
            }
        }

        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double distance = squaredDistance(i, x, y);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        return best;
    }

    /**
     * Writes the {@link #INPUTS} readings for a car at (x, y) pointing at {@code angle}, nearest to {@code segment}
     * (from {@link #locate}), into {@code out} from {@code offset}.
     */
    public void read(double x, double y, double angle, int segment, double[] out, int offset) {
        double here = arcLength(segment, x, y);
        double roadHeading = headingAt(here);
        double headingError = wrap(roadHeading - angle);

        for (int i = 0; i < INPUTS; i++) {
            double turn = headingAt(here + DISTANCES[i]) - roadHeading;
            out[offset + i] = Math.clamp((headingError + turn) / Math.PI, -1.0, 1.0);
        }
    }

    /**
     * Convenience for tests and tools: the readings of a car, located on the whole centre line.
     */
    public double[] read(double x, double y, double angle) {
        double[] readings = new double[INPUTS];
        read(x, y, angle, locate(x, y, -1), readings, 0);
        return readings;
    }

    /**
     * Unwrapped road heading at an arc length (any value: whole laps add the lap's total turning), interpolated
     * linearly between segment middles so it changes smoothly as the car drives.
     */
    double headingAt(double arc) {
        double laps = Math.floor((arc - middles[0]) / length);
        double s = arc - laps * length;
        int low = 0;
        int high = xs.length;

        while (high - low > 1) {
            int mid = (low + high) >>> 1;

            if (middles[mid] <= s) {
                low = mid;
            } else {
                high = mid;
            }
        }

        double span = middles[low + 1] - middles[low];
        double fraction = span > 0.0 ? (s - middles[low]) / span : 0.0;
        return headings[low] + fraction * (headings[low + 1] - headings[low]) + laps * lapTurn;
    }

    /**
     * Arc length of the projection of (x, y) on a segment.
     */
    double arcLength(int segment, double x, double y) {
        return starts[segment] + projection(segment, x, y) * lengths[segment];
    }

    /**
     * Point of the centre line at an arc length (any value: wrapped to one lap), as {x, y}.
     */
    double[] pointAt(double arc) {
        double s = arc - Math.floor(arc / length) * length;
        int low = 0;
        int high = xs.length;

        while (high - low > 1) {
            int mid = (low + high) >>> 1;

            if (starts[mid] <= s) {
                low = mid;
            } else {
                high = mid;
            }
        }

        int next = (low + 1) % xs.length;
        double t = lengths[low] > 0.0 ? (s - starts[low]) / lengths[low] : 0.0;
        return new double[]{xs[low] + t * (xs[next] - xs[low]), ys[low] + t * (ys[next] - ys[low])};
    }

    /**
     * Unit vector pointing left of the road at an arc length, as {x, y}.
     */
    double[] leftNormalAt(double arc) {
        double heading = headingAt(arc);
        return new double[]{-Math.sin(heading), Math.cos(heading)};
    }

    /**
     * Signed curvature (1/m, positive = left) at an arc length: the turning of the road over ±{@code halfSpan}
     * metres, which smooths the noise of measured centre lines.
     */
    double curvatureAt(double arc, double halfSpan) {
        return (headingAt(arc + halfSpan) - headingAt(arc - halfSpan)) / (2.0 * halfSpan);
    }

    private double projection(int i, double x, double y) {
        int j = (i + 1) % xs.length;
        double dx = xs[j] - xs[i];
        double dy = ys[j] - ys[i];
        return Math.clamp(((x - xs[i]) * dx + (y - ys[i]) * dy) / (dx * dx + dy * dy), 0.0, 1.0);
    }

    private double squaredDistance(int i, double x, double y) {
        int j = (i + 1) % xs.length;
        double t = projection(i, x, y);
        double px = xs[i] + t * (xs[j] - xs[i]) - x;
        double py = ys[i] + t * (ys[j] - ys[i]) - y;
        return px * px + py * py;
    }

    /**
     * Length of the centre line, m.
     */
    public double length() {
        return length;
    }

    public int segmentCount() {
        return xs.length;
    }

    /**
     * Total turning of the road over one lap: 2π for a counter-clockwise circuit, -2π for a clockwise one.
     */
    public double lapTurn() {
        return lapTurn;
    }

    private static double wrap(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
