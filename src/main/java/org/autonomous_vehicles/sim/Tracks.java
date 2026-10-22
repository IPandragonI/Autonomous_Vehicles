package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Track factory: the built-in oval (track n°1), a builder from centre-line control points,
 * and lookup by name ("oval" or a resource track such as "hairpin" or "chicane").
 */
public final class Tracks {

    public static final String OVAL = "oval";
    public static final List<String> BUILT_IN = List.of(OVAL, "hairpin", "chicane");

    /**
     * Real circuits shipped in {@code /tracks/real/} (TUM racetrack-database, LGPL-3.0; Suzuka excluded because
     * it crosses itself).
     */
    public static final List<String> REAL = List.of("Austin", "BrandsHatch", "Budapest", "Catalunya", "Hockenheim",
            "IMS", "Melbourne", "MexicoCity", "Montreal", "Monza", "MoscowRaceway", "Norisring", "Nuerburgring",
            "Oschersleben", "Sakhir", "SaoPaulo", "Sepang", "Shanghai", "Silverstone", "Sochi", "Spa", "Spielberg",
            "YasMarina", "Zandvoort");

    private static final String REAL_DIRECTORY = "/tracks/real/";
    private static final int OVAL_POINTS = 64;
    private static final int OVAL_START_INDEX = 48;
    private static final int OVAL_CHECKPOINT_STEP = 4;

    /** Tracks imported at run time (e.g. from a CSV file), looked up by name before the resources. */
    private static final Map<String, Track> IMPORTED = new ConcurrentHashMap<>();

    /** Real circuits take up to a few hundred ms to build; tracks are immutable, so each is built once. */
    private static final Map<String, Track> REAL_CACHE = new ConcurrentHashMap<>();

    private Tracks() {
    }

    public static Track byName(String name) {
        if (OVAL.equals(name)) {
            return oval();
        }

        Track imported = IMPORTED.get(name);

        if (imported != null) {
            return imported;
        }

        if (REAL.contains(name)) {
            return REAL_CACHE.computeIfAbsent(name, Tracks::loadReal);
        }

        return TrackLoader.loadResource(name);
    }

    private static Track loadReal(String name) {
        try (InputStream stream = Tracks.class.getResourceAsStream(REAL_DIRECTORY + name + TrackLoader.CSV_EXTENSION)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing circuit resource: " + name);
            }

            return TrackLoader.parseCsv(new String(stream.readAllBytes(), StandardCharsets.UTF_8), name);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read circuit " + name, e);
        }
    }

    /**
     * Human-readable name: "BrandsHatch" → "Brands Hatch", "IMS" → "IMS".
     */
    public static String displayName(String name) {
        return name.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    /**
     * Makes an imported track available to {@link #byName} (and so to configurations) under its name.
     * Tracks are immutable, so the same instance is safely shared by every simulation.
     */
    public static void register(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track must not be null.");
        }

        if (BUILT_IN.contains(track.getName()) || REAL.contains(track.getName())) {
            throw new IllegalArgumentException("'" + track.getName() + "' is already a built-in track.");
        }

        IMPORTED.put(track.getName(), track);
    }

    public static List<String> importedNames() {
        return IMPORTED.keySet().stream().sorted().toList();
    }

    public static Track oval() {
        List<Vec2> outerWall = createEllipse(100.0, 60.0, OVAL_POINTS);
        List<Vec2> innerWall = createEllipse(65.0, 30.0, OVAL_POINTS);

        Vec2 startPosition = new Vec2(0.0, -45.0);
        double startAngle = 0.0;

        // Gates join inner[j] to outer[j]; the finish line (last gate) sits just behind the start (index 48).
        List<Checkpoint> checkpoints = new ArrayList<>();
        int gateCount = OVAL_POINTS / OVAL_CHECKPOINT_STEP;

        for (int k = 0; k < gateCount; k++) {
            int j = Math.floorMod(OVAL_START_INDEX + 3 + OVAL_CHECKPOINT_STEP * k, OVAL_POINTS);
            double theta = 2.0 * Math.PI * j / OVAL_POINTS;
            Vec2 forward = new Vec2(-82.5 * Math.sin(theta), 45.0 * Math.cos(theta));
            checkpoints.add(new Checkpoint(new Segment(innerWall.get(j), outerWall.get(j)), forward));
        }

        return new Track(OVAL, outerWall, innerWall, startPosition, startAngle, checkpoints);
    }

    /**
     * Builds a constant-width closed track around a Catmull-Rom spline through {@code controlPoints}.
     * The driving direction follows the order of the control points; the start is placed just after
     * the first spline point, which is also the finish line.
     */
    public static Track fromCenterline(String name, List<Vec2> controlPoints, double width,
                                       int samplesPerSegment, int checkpointCount) {
        if (controlPoints == null || controlPoints.size() < 3) {
            throw new IllegalArgumentException("A track needs at least three control points.");
        }

        if (!Double.isFinite(width) || width <= 2.0 * Car.RADIUS) {
            throw new IllegalArgumentException("Track width must be larger than the car.");
        }

        if (samplesPerSegment < 1 || checkpointCount < 2) {
            throw new IllegalArgumentException("Invalid sampling or checkpoint count.");
        }

        List<Vec2> centerline = catmullRom(controlPoints, samplesPerSegment);
        double[] halfWidths = new double[centerline.size()];
        Arrays.fill(halfWidths, width / 2.0);

        return build(name, centerline, halfWidths, halfWidths, checkpointCount, false);
    }

    /**
     * Builds a track from a dense centre line with a width on each side of every point, as published for real
     * circuits (e.g. the TUM racetrack database: metres, points every few metres, driving order, closed implicitly).
     * Consecutive points closer than 0.5 m are merged. Where a wall offset from a tight bend folds over itself,
     * the small loop is cut out.
     *
     * @param leftWidths       distance from the centre line to the left wall, per point (driving direction)
     * @param rightWidths      distance from the centre line to the right wall, per point
     * @param checkpointSpacing approximate distance between two checkpoints, m
     */
    public static Track fromCenterlineWithWidths(String name, List<Vec2> points, double[] leftWidths,
                                                 double[] rightWidths, double checkpointSpacing) {
        if (points == null || leftWidths == null || rightWidths == null
                || points.size() != leftWidths.length || points.size() != rightWidths.length) {
            throw new IllegalArgumentException("Need one left and one right width per centre-line point.");
        }

        if (!Double.isFinite(checkpointSpacing) || checkpointSpacing <= 0.0) {
            throw new IllegalArgumentException("Checkpoint spacing must be positive.");
        }

        List<Vec2> centerline = new ArrayList<>();
        List<Double> left = new ArrayList<>();
        List<Double> right = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            if (!(leftWidths[i] > 0.0) || !(rightWidths[i] > 0.0)) {
                throw new IllegalArgumentException("Point " + (i + 1) + ": widths must be positive.");
            }

            if (!centerline.isEmpty() && centerline.getLast().distanceTo(points.get(i)) < 0.5) {
                continue;
            }

            centerline.add(points.get(i));
            left.add(leftWidths[i]);
            right.add(rightWidths[i]);
        }

        while (centerline.size() > 1 && centerline.getLast().distanceTo(centerline.getFirst()) < 0.5) {
            centerline.removeLast();
            left.removeLast();
            right.removeLast();
        }

        if (centerline.size() < 12) {
            throw new IllegalArgumentException("A centre line needs at least 12 distinct points.");
        }

        double length = 0.0;

        for (int i = 0; i < centerline.size(); i++) {
            length += centerline.get(i).distanceTo(centerline.get((i + 1) % centerline.size()));
        }

        int checkpointCount = (int) Math.clamp(Math.round(length / checkpointSpacing), 4, centerline.size() / 3);

        return build(name, centerline, left.stream().mapToDouble(Double::doubleValue).toArray(),
                right.stream().mapToDouble(Double::doubleValue).toArray(), checkpointCount, true);
    }

    /**
     * Offsets the centre line on both sides, picks inner/outer from the orientation, places the checkpoint
     * gates (finish line on point 0) and the start just after it.
     */
    private static Track build(String name, List<Vec2> centerline, double[] leftHalfWidths,
                               double[] rightHalfWidths, int checkpointCount, boolean importedData) {
        int n = centerline.size();

        if (checkpointCount * 3 > n) {
            throw new IllegalArgumentException("Too many checkpoints for the sampled centre line.");
        }

        List<Vec2> left = new ArrayList<>(n);
        List<Vec2> right = new ArrayList<>(n);
        List<Vec2> tangents = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Vec2 tangent = centerline.get((i + 1) % n).subtract(centerline.get(Math.floorMod(i - 1, n))).normalize();
            Vec2 normal = new Vec2(-tangent.y(), tangent.x());
            tangents.add(tangent);
            left.add(centerline.get(i).add(normal.scale(leftHalfWidths[i])));
            right.add(centerline.get(i).subtract(normal.scale(rightHalfWidths[i])));
        }

        boolean counterClockwise = Geometry.signedArea(centerline) > 0.0;
        List<Vec2> inner = counterClockwise ? left : right;
        List<Vec2> outer = counterClockwise ? right : left;

        // Gates come from the unrepaired offsets so that their indices match the centre line. Imported data spreads
        // them evenly; built-in tracks keep their original placement so the published experiments stay reproducible.
        int spacing = n / checkpointCount;
        List<Checkpoint> checkpoints = new ArrayList<>();

        for (int k = 1; k <= checkpointCount; k++) {
            int index = k == checkpointCount ? 0
                    : importedData ? (int) Math.round((double) k * n / checkpointCount) % n : k * spacing;
            checkpoints.add(new Checkpoint(new Segment(inner.get(index), outer.get(index)), tangents.get(index)));
        }

        if (importedData) {
            if (selfIntersects(centerline)) {
                throw new IllegalArgumentException("The centre line crosses itself (a bridge or tunnel, such as a "
                        + "figure-of-eight circuit); a flat 2D track cannot represent it.");
            }

            inner = removeLoops(inner);
            outer = removeLoops(outer);
        }

        int startIndex = Math.max(1, spacing / 3);
        Vec2 startTangent = tangents.get(startIndex);

        return new Track(name, outer, inner, centerline.get(startIndex),
                Math.atan2(startTangent.y(), startTangent.x()), checkpoints, centerline);
    }

    /**
     * Cuts the small loops a closed polyline makes when it is offset inside a bend tighter than the offset:
     * when segment i crosses a nearby later segment j, the points between them are replaced by the crossing point.
     * Run twice, the second time on the polyline rotated by half, so a loop across the start is also removed.
     */
    private static boolean selfIntersects(List<Vec2> closed) {
        int n = closed.size();

        for (int i = 0; i < n; i++) {
            Segment a = new Segment(closed.get(i), closed.get((i + 1) % n));

            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) {
                    continue;
                }

                if (Geometry.intersectSegments(a, new Segment(closed.get(j), closed.get((j + 1) % n))).isPresent()) {
                    return true;
                }
            }
        }

        return false;
    }

    static List<Vec2> removeLoops(List<Vec2> polyline) {
        List<Vec2> result = removeLoopsOnce(polyline);
        int half = result.size() / 2;
        List<Vec2> rotated = new ArrayList<>(result.subList(half, result.size()));
        rotated.addAll(result.subList(0, half));
        return removeLoopsOnce(rotated);
    }

    private static List<Vec2> removeLoopsOnce(List<Vec2> polyline) {
        List<Vec2> points = new ArrayList<>(polyline);
        int window = 60;
        boolean changed = true;

        while (changed) {
            changed = false;

            search:
            for (int i = 0; i + 2 < points.size(); i++) {
                Segment first = new Segment(points.get(i), points.get(i + 1));

                for (int j = i + 2; j < Math.min(points.size() - 1, i + window); j++) {
                    Optional<Vec2> crossing = Geometry.intersectSegments(first, new Segment(points.get(j), points.get(j + 1)));

                    if (crossing.isPresent()) {
                        points.subList(i + 1, j + 1).clear();
                        points.add(i + 1, crossing.get());
                        changed = true;
                        break search;
                    }
                }
            }
        }

        return points;
    }

    static List<Vec2> catmullRom(List<Vec2> points, int samplesPerSegment) {
        List<Vec2> result = new ArrayList<>();
        int n = points.size();

        for (int i = 0; i < n; i++) {
            Vec2 p0 = points.get(Math.floorMod(i - 1, n));
            Vec2 p1 = points.get(i);
            Vec2 p2 = points.get((i + 1) % n);
            Vec2 p3 = points.get((i + 2) % n);

            for (int s = 0; s < samplesPerSegment; s++) {
                double t = (double) s / samplesPerSegment;
                double t2 = t * t;
                double t3 = t2 * t;

                Vec2 point = p1.scale(2.0)
                        .add(p2.subtract(p0).scale(t))
                        .add(p0.scale(2.0).subtract(p1.scale(5.0)).add(p2.scale(4.0)).subtract(p3).scale(t2))
                        .add(p1.scale(3.0).subtract(p0).subtract(p2.scale(3.0)).add(p3).scale(t3))
                        .scale(0.5);
                result.add(point);
            }
        }

        return result;
    }

    private static List<Vec2> createEllipse(double radiusX, double radiusY, int pointCount) {
        List<Vec2> points = new ArrayList<>();

        for (int i = 0; i < pointCount; i++) {
            double angle = 2.0 * Math.PI * i / pointCount;

            double x = radiusX * Math.cos(angle);
            double y = radiusY * Math.sin(angle);

            points.add(new Vec2(x, y));
        }

        return points;
    }
}
