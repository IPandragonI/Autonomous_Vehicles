package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.Checkpoint;
import org.autonomous_vehicles.sim.Track;

import java.util.ArrayList;
import java.util.List;

/**
 * Painted decoration of a track, computed once from its geometry (visual only, no effect on the simulation):
 * red-and-white kerbs (rumble strips) along the walls in bends, and a chequered start/finish line.
 * <p>
 * Kerbs follow real-circuit practice: on the inside of a corner (the wall that bends away from the track)
 * wherever the radius is below {@link #INSIDE_RADIUS}, and on the outside only in tight corners
 * (radius below {@link #OUTSIDE_RADIUS}). Plain Java, so it is unit-tested.
 */
public final class TrackDecor {

    public static final double KERB_WIDTH = 1.5;
    public static final double STRIPE_LENGTH = 3.0;
    public static final double INSIDE_RADIUS = 120.0;
    public static final double OUTSIDE_RADIUS = 50.0;
    public static final double MIN_KERB_LENGTH = 8.0;
    public static final double CHEQUER_SIZE = 1.0;

    public enum Paint { KERB_RED, KERB_WHITE, CHEQUER_BLACK, CHEQUER_WHITE }

    /** A filled quadrilateral in world coordinates. */
    public record Quad(double[] xs, double[] ys, Paint paint) {

        public Vec2 centroid() {
            double x = 0, y = 0;

            for (int i = 0; i < xs.length; i++) {
                x += xs[i];
                y += ys[i];
            }

            return new Vec2(x / xs.length, y / ys.length);
        }
    }

    private final List<Quad> kerbs;
    private final List<Quad> finishLine;

    private TrackDecor(List<Quad> kerbs, List<Quad> finishLine) {
        this.kerbs = List.copyOf(kerbs);
        this.finishLine = List.copyOf(finishLine);
    }

    public static TrackDecor of(Track track) {
        List<Quad> kerbs = new ArrayList<>();
        // The track lies inside the outer polygon and outside the inner one.
        addKerbs(track.getOuterWall(), Geometry.signedArea(track.getOuterWall()) > 0, kerbs);
        addKerbs(track.getInnerWall(), Geometry.signedArea(track.getInnerWall()) < 0, kerbs);

        List<Quad> finish = track.getCheckpoints().isEmpty() ? List.of() : chequer(track.getCheckpoints().getLast());
        return new TrackDecor(kerbs, finish);
    }

    public List<Quad> kerbs() {
        return kerbs;
    }

    public List<Quad> finishLine() {
        return finishLine;
    }

    /**
     * @param trackOnLeft true when the track is on the left of the wall in point order
     */
    private static void addKerbs(List<Vec2> wall, boolean trackOnLeft, List<Quad> out) {
        int n = wall.size();
        Vec2[] normals = new Vec2[n];
        double[] curvature = new double[n];
        double[] arc = new double[n + 1];

        for (int i = 0; i < n; i++) {
            Vec2 direction = wall.get((i + 1) % n).subtract(wall.get(i));
            double length = direction.length();
            arc[i + 1] = arc[i] + length;
            Vec2 unit = length == 0 ? new Vec2(1, 0) : direction.scale(1 / length);
            normals[i] = trackOnLeft ? new Vec2(-unit.y(), unit.x()) : new Vec2(unit.y(), -unit.x());
        }

        for (int i = 0; i < n; i++) {
            Vec2 before = wall.get(i).subtract(wall.get(Math.floorMod(i - 1, n)));
            Vec2 after = wall.get((i + 1) % n).subtract(wall.get(i));
            double turn = Math.atan2(before.cross(after), before.dot(after));
            double meanLength = (before.length() + after.length()) / 2;
            curvature[i] = meanLength == 0 ? 0 : turn / meanLength;
        }

        // Smooth the curvature over ±2 points: dense real-circuit data is noisy.
        double[] smooth = new double[n];

        for (int i = 0; i < n; i++) {
            double sum = 0;

            for (int k = -2; k <= 2; k++) {
                sum += curvature[Math.floorMod(i + k, n)];
            }

            smooth[i] = sum / 5;
        }

        // A segment carries a kerb when its mean curvature qualifies. Bending away from the track = inside of a corner.
        boolean[] kerb = new boolean[n];

        for (int i = 0; i < n; i++) {
            double k = (smooth[i] + smooth[(i + 1) % n]) / 2;
            boolean inside = trackOnLeft ? k < 0 : k > 0;
            double radius = Math.abs(k) < 1e-12 ? Double.POSITIVE_INFINITY : 1 / Math.abs(k);
            kerb[i] = inside ? radius < INSIDE_RADIUS : radius < OUTSIDE_RADIUS;
        }

        removeShortRuns(kerb, arc, n);

        for (int i = 0; i < n; i++) {
            if (!kerb[i]) {
                continue;
            }

            Vec2 a = wall.get(i);
            Vec2 b = wall.get((i + 1) % n);
            Vec2 normalA = vertexNormal(normals, i);
            Vec2 normalB = vertexNormal(normals, (i + 1) % n);
            double length = a.distanceTo(b);
            int pieces = Math.max(1, (int) Math.ceil(length / STRIPE_LENGTH));

            for (int p = 0; p < pieces; p++) {
                double t0 = (double) p / pieces, t1 = (double) (p + 1) / pieces;
                Vec2 start = lerp(a, b, t0), end = lerp(a, b, t1);
                Vec2 n0 = lerp(normalA, normalB, t0).normalize(), n1 = lerp(normalA, normalB, t1).normalize();
                double middle = arc[i] + length * (t0 + t1) / 2;
                Paint paint = ((int) Math.floor(middle / STRIPE_LENGTH)) % 2 == 0 ? Paint.KERB_RED : Paint.KERB_WHITE;
                Vec2 endIn = end.add(n1.scale(KERB_WIDTH));
                Vec2 startIn = start.add(n0.scale(KERB_WIDTH));
                out.add(new Quad(new double[]{start.x(), end.x(), endIn.x(), startIn.x()},
                        new double[]{start.y(), end.y(), endIn.y(), startIn.y()}, paint));
            }
        }
    }

    /** Average of the two segment normals at a vertex, so consecutive kerb pieces join without gaps. */
    private static Vec2 vertexNormal(Vec2[] normals, int vertex) {
        Vec2 sum = normals[Math.floorMod(vertex - 1, normals.length)].add(normals[vertex]);
        return sum.length() < 1e-9 ? normals[vertex] : sum.normalize();
    }

    /** Drops kerb runs shorter than {@link #MIN_KERB_LENGTH} (isolated noisy segments). */
    private static void removeShortRuns(boolean[] kerb, double[] arc, int n) {
        int start = 0;

        // Start just after a plain segment so that a run crossing index 0 is measured in one piece.
        while (start < n && kerb[start]) {
            start++;
        }

        if (start == n) {
            return;
        }

        boolean[] keep = kerb.clone();

        for (int offset = 1; offset <= n; offset++) {
            int i = (start + offset) % n;

            if (kerb[i] && !kerb[Math.floorMod(i - 1, n)]) {
                double length = 0;
                List<Integer> run = new ArrayList<>();

                for (int j = i; kerb[j] && run.size() < n; j = (j + 1) % n) {
                    run.add(j);
                    length += arc[j + 1] - arc[j];
                }

                if (length < MIN_KERB_LENGTH) {
                    run.forEach(index -> keep[index] = false);
                }
            }
        }

        System.arraycopy(keep, 0, kerb, 0, n);
    }

    /** Two rows of black and white squares across the finish gate. */
    private static List<Quad> chequer(Checkpoint finish) {
        Segment gate = finish.gate();
        Vec2 across = gate.direction().normalize();
        Vec2 along = finish.forward();
        int squares = Math.max(2, (int) Math.round(gate.length() / CHEQUER_SIZE));
        double size = gate.length() / squares;
        List<Quad> quads = new ArrayList<>();

        for (int row = 0; row < 2; row++) {
            for (int s = 0; s < squares; s++) {
                Vec2 corner = gate.start().add(across.scale(s * size)).add(along.scale((row - 1) * size));
                Vec2 b = corner.add(across.scale(size));
                Vec2 c = b.add(along.scale(size));
                Vec2 d = corner.add(along.scale(size));
                quads.add(new Quad(new double[]{corner.x(), b.x(), c.x(), d.x()}, new double[]{corner.y(), b.y(), c.y(), d.y()},
                        (s + row) % 2 == 0 ? Paint.CHEQUER_BLACK : Paint.CHEQUER_WHITE));
            }
        }

        return quads;
    }

    private static Vec2 lerp(Vec2 a, Vec2 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }
}
