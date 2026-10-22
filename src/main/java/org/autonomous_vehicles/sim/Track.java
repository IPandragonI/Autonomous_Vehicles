package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A closed track: an outer and an inner wall (closed polylines), a start pose and
 * an ordered list of checkpoints whose last element is the finish line.
 */
public class Track {

    private final String name;
    private final List<Vec2> outerWall;
    private final List<Vec2> innerWall;
    private final List<Segment> walls;
    private final Vec2 startPosition;
    private final double startAngle;
    private final List<Checkpoint> checkpoints;
    private final WallGrid wallGrid;
    /** Null for a track with fewer than three checkpoints and no centre line. */
    private final TrackPreview preview;

    public Track(List<Vec2> outerWall, List<Vec2> innerWall, Vec2 startPosition, double startAngle) {
        this("custom", outerWall, innerWall, startPosition, startAngle, List.of());
    }

    /**
     * Track whose centre line (for {@link #getPreview()}) is approximated by the loop through the checkpoint centres.
     */
    public Track(String name, List<Vec2> outerWall, List<Vec2> innerWall, Vec2 startPosition, double startAngle,
                 List<Checkpoint> checkpoints) {
        this(name, outerWall, innerWall, startPosition, startAngle, checkpoints,
                checkpoints == null ? null : checkpoints.stream().map(Checkpoint::midpoint).toList());
    }

    /**
     * @param centerline closed centre line in driving order, read by {@link TrackPreview}
     */
    public Track(String name, List<Vec2> outerWall, List<Vec2> innerWall, Vec2 startPosition, double startAngle,
                 List<Checkpoint> checkpoints, List<Vec2> centerline) {
        if (name == null || outerWall == null || innerWall == null || startPosition == null
                || !Double.isFinite(startAngle) || checkpoints == null || centerline == null) {
            throw new IllegalArgumentException("Track arguments must be valid.");
        }

        this.name = name;
        this.outerWall = List.copyOf(outerWall);
        this.innerWall = List.copyOf(innerWall);
        this.startPosition = startPosition;
        this.startAngle = startAngle;
        this.checkpoints = List.copyOf(checkpoints);

        validateWall(this.outerWall);
        validateWall(this.innerWall);

        List<Segment> outerSegments = createSegments(this.outerWall);
        List<Segment> innerSegments = createSegments(this.innerWall);

        if (selfIntersects(outerSegments) || selfIntersects(innerSegments)) {
            throw new IllegalArgumentException("A wall must not intersect itself.");
        }

        if (wallsIntersect(outerSegments, innerSegments)) {
            throw new IllegalArgumentException("Inner and outer walls must not intersect.");
        }

        if (!Geometry.isInsidePolygon(this.innerWall.getFirst(), this.outerWall)) {
            throw new IllegalArgumentException("The inner wall must be inside the outer wall.");
        }

        if (!isOnTrack(startPosition)) {
            throw new IllegalArgumentException("Start position must be between the two walls.");
        }

        validateCheckpoints();

        List<Segment> allWalls = new ArrayList<>(outerSegments);
        allWalls.addAll(innerSegments);
        this.walls = List.copyOf(allWalls);
        this.wallGrid = new WallGrid(this.walls);
        this.preview = centerline.size() >= 3 ? new TrackPreview(centerline) : null;
    }

    private static void validateWall(List<Vec2> wall) {
        if (wall.size() < 3 || wall.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A wall must contain at least three valid points.");
        }
    }

    /**
     * Checkpoints must lie on the track and follow each other in the driving direction;
     * the start must be just after the finish line (last checkpoint) and before the first one.
     */
    private void validateCheckpoints() {
        if (checkpoints.isEmpty()) {
            return;
        }

        if (checkpoints.size() < 2) {
            throw new IllegalArgumentException("A track needs at least two checkpoints.");
        }

        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint current = checkpoints.get(i);
            Checkpoint next = checkpoints.get((i + 1) % checkpoints.size());

            if (!isOnTrack(current.midpoint())) {
                throw new IllegalArgumentException("Checkpoint " + i + " is not on the track.");
            }

            if (next.midpoint().subtract(current.midpoint()).dot(current.forward()) <= 0.0) {
                throw new IllegalArgumentException("Checkpoints " + i + " and " + ((i + 1) % checkpoints.size())
                        + " are not ordered in the driving direction.");
            }
        }

        Checkpoint finish = checkpoints.getLast();
        Checkpoint first = checkpoints.getFirst();

        if (startPosition.subtract(finish.midpoint()).dot(finish.forward()) <= 0.0
                || first.midpoint().subtract(startPosition).dot(first.forward()) <= 0.0) {
            throw new IllegalArgumentException("Start must be after the finish line and before the first checkpoint.");
        }
    }

    private static List<Segment> createSegments(List<Vec2> points) {
        List<Segment> segments = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            Vec2 start = points.get(i);
            Vec2 end = points.get((i + 1) % points.size());
            segments.add(new Segment(start, end));
        }

        return segments;
    }

    private static boolean selfIntersects(List<Segment> segments) {
        int n = segments.size();

        for (int i = 0; i < n; i++) {
            for (int j = i + 2; j < n; j++) {
                boolean adjacent = i == 0 && j == n - 1;

                if (!adjacent && Geometry.intersectSegments(segments.get(i), segments.get(j)).isPresent()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean wallsIntersect(List<Segment> outer, List<Segment> inner) {
        for (Segment a : outer) {
            for (Segment b : inner) {
                if (Geometry.intersectSegments(a, b).isPresent()) return true;
            }
        }

        return false;
    }

    /**
     * Distance from {@code origin} to the nearest wall along the unit vector {@code direction},
     * capped at {@code maxDistance}.
     */
    public double distanceToWall(Vec2 origin, Vec2 direction, double maxDistance) {
        return wallGrid.raycast(origin.x(), origin.y(), direction.x(), direction.y(), maxDistance);
    }

    /**
     * Allocation-free variant for the hot path (5+ rays per car per step): (dx, dy) must be a unit vector.
     */
    public double distanceToWall(double originX, double originY, double dx, double dy, double maxDistance) {
        return wallGrid.raycast(originX, originY, dx, dy, maxDistance);
    }

    public boolean circleTouchesWall(Vec2 center, double radius) {
        return wallGrid.circleHitsWall(center.x(), center.y(), radius);
    }

    public boolean circleTouchesWall(double centerX, double centerY, double radius) {
        return wallGrid.circleHitsWall(centerX, centerY, radius);
    }

    public boolean segmentCrossesWall(Vec2 from, Vec2 to) {
        return wallGrid.segmentHitsWall(from, to);
    }

    /**
     * Approximate lap length, m: the loop through the checkpoint centres (0 without checkpoints).
     */
    public double lapLength() {
        double length = 0.0;

        for (int i = 0; i < checkpoints.size(); i++) {
            length += checkpoints.get(i).midpoint().distanceTo(checkpoints.get((i + 1) % checkpoints.size()).midpoint());
        }

        return length;
    }

    public boolean isOnTrack(Vec2 point) {
        return Geometry.isInsidePolygon(point, outerWall) && !Geometry.isInsidePolygon(point, innerWall);
    }

    public String getName() {
        return name;
    }

    public List<Vec2> getOuterWall() {
        return outerWall;
    }

    public List<Vec2> getInnerWall() {
        return innerWall;
    }

    public List<Segment> getWalls() {
        return walls;
    }

    public Vec2 getStartPosition() {
        return startPosition;
    }

    public double getStartAngle() {
        return startAngle;
    }

    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    /**
     * The road-ahead observation of this track.
     *
     * @throws IllegalStateException if the track has no centre line (fewer than three checkpoints)
     */
    public TrackPreview getPreview() {
        if (preview == null) {
            throw new IllegalStateException("Track '" + name + "' has no centre line to preview.");
        }

        return preview;
    }
}
