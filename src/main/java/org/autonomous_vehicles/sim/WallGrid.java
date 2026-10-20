package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uniform grid over the walls of a track (broad phase): a query only tests the walls
 * stored in the cells it touches instead of every wall. Immutable, hence safe to share
 * between simulations running in parallel.
 */
final class WallGrid {

    private static final double CELL_SIZE = 10.0;
    private static final double EPSILON = 1e-9;
    static final double BOX_MARGIN = 1e-3;
    static final double MIN_FILTERED_MOVE = 1e-3;

    private final double[] x1;
    private final double[] y1;
    private final double[] x2;
    private final double[] y2;
    private final List<Segment> walls;

    private final double minX;
    private final double minY;
    private final int columns;
    private final int rows;
    private final int[][] cells;

    WallGrid(List<Segment> walls) {
        this.walls = walls;
        int n = walls.size();
        x1 = new double[n];
        y1 = new double[n];
        x2 = new double[n];
        y2 = new double[n];

        double lowX = Double.POSITIVE_INFINITY, lowY = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY, highY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            Segment wall = walls.get(i);
            x1[i] = wall.start().x();
            y1[i] = wall.start().y();
            x2[i] = wall.end().x();
            y2[i] = wall.end().y();
            lowX = Math.min(lowX, Math.min(x1[i], x2[i]));
            lowY = Math.min(lowY, Math.min(y1[i], y2[i]));
            highX = Math.max(highX, Math.max(x1[i], x2[i]));
            highY = Math.max(highY, Math.max(y1[i], y2[i]));
        }

        minX = lowX - CELL_SIZE;
        minY = lowY - CELL_SIZE;
        columns = (int) Math.ceil((highX + CELL_SIZE - minX) / CELL_SIZE) + 1;
        rows = (int) Math.ceil((highY + CELL_SIZE - minY) / CELL_SIZE) + 1;

        List<List<Integer>> buckets = new ArrayList<>(columns * rows);

        for (int i = 0; i < columns * rows; i++) {
            buckets.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            int c0 = column(Math.min(x1[i], x2[i])), c1 = column(Math.max(x1[i], x2[i]));
            int r0 = row(Math.min(y1[i], y2[i])), r1 = row(Math.max(y1[i], y2[i]));

            for (int c = c0; c <= c1; c++) {
                for (int r = r0; r <= r1; r++) {
                    buckets.get(r * columns + c).add(i);
                }
            }
        }

        cells = new int[columns * rows][];

        for (int i = 0; i < cells.length; i++) {
            cells[i] = buckets.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private int column(double x) {
        return Math.clamp((long) Math.floor((x - minX) / CELL_SIZE), 0, columns - 1);
    }

    private int row(double y) {
        return Math.clamp((long) Math.floor((y - minY) / CELL_SIZE), 0, rows - 1);
    }

    /**
     * Distance along the unit direction (dx, dy) to the nearest wall, or {@code maxDistance} if none is closer.
     * Cells are visited in the order the ray crosses them (Amanatides-Woo), so the search stops at the first hit.
     */
    double raycast(double ox, double oy, double dx, double dy, double maxDistance) {
        int c = column(ox);
        int r = row(oy);
        int stepC = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepR = dy > 0 ? 1 : (dy < 0 ? -1 : 0);

        double nextBoundaryX = minX + (c + (stepC > 0 ? 1 : 0)) * CELL_SIZE;
        double nextBoundaryY = minY + (r + (stepR > 0 ? 1 : 0)) * CELL_SIZE;
        double tMaxX = stepC == 0 ? Double.POSITIVE_INFINITY : (nextBoundaryX - ox) / dx;
        double tMaxY = stepR == 0 ? Double.POSITIVE_INFINITY : (nextBoundaryY - oy) / dy;
        double tDeltaX = stepC == 0 ? Double.POSITIVE_INFINITY : CELL_SIZE / Math.abs(dx);
        double tDeltaY = stepR == 0 ? Double.POSITIVE_INFINITY : CELL_SIZE / Math.abs(dy);

        double best = maxDistance;

        while (true) {
            for (int wall : cells[r * columns + c]) {
                best = Math.min(best, rayHit(ox, oy, dx, dy, wall));
            }

            double cellExit = Math.min(tMaxX, tMaxY);

            if (best <= cellExit || cellExit > maxDistance) {
                return best;
            }

            if (tMaxX < tMaxY) {
                c += stepC;
                tMaxX += tDeltaX;
            } else {
                r += stepR;
                tMaxY += tDeltaY;
            }

            if (c < 0 || c >= columns || r < 0 || r >= rows) {
                return best;
            }
        }
    }

    private double rayHit(double ox, double oy, double dx, double dy, int wall) {
        double sx = x2[wall] - x1[wall];
        double sy = y2[wall] - y1[wall];
        double denominator = dx * sy - dy * sx;

        if (Math.abs(denominator) <= EPSILON) {
            Optional<Vec2> hit = Geometry.intersectRaySegment(new Vec2(ox, oy), new Vec2(dx, dy), walls.get(wall));
            return hit.map(point -> point.distanceTo(new Vec2(ox, oy))).orElse(Double.POSITIVE_INFINITY);
        }

        double offsetX = x1[wall] - ox;
        double offsetY = y1[wall] - oy;
        double t = (offsetX * sy - offsetY * sx) / denominator;
        double u = (offsetX * dy - offsetY * dx) / denominator;

        if (t >= -EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON) {
            return Math.max(0.0, t);
        }

        return Double.POSITIVE_INFINITY;
    }

    boolean circleHitsWall(double cx, double cy, double radius) {
        int c0 = column(cx - radius), c1 = column(cx + radius);
        int r0 = row(cy - radius), r1 = row(cy + radius);

        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                for (int wall : cells[r * columns + c]) {
                    if (distanceSquaredToWall(cx, cy, wall) <= radius * radius) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private double distanceSquaredToWall(double px, double py, int wall) {
        double sx = x2[wall] - x1[wall];
        double sy = y2[wall] - y1[wall];
        double lengthSquared = sx * sx + sy * sy;
        double t = lengthSquared <= EPSILON ? 0.0
                : Math.clamp(((px - x1[wall]) * sx + (py - y1[wall]) * sy) / lengthSquared, 0.0, 1.0);
        double ex = x1[wall] + t * sx - px;
        double ey = y1[wall] + t * sy - py;

        return ex * ex + ey * ey;
    }

    /**
     * Before the exact (allocating) segment test, walls whose bounding box is more than {@link #BOX_MARGIN} away
     * from the move's are skipped. {@link Geometry#intersectSegments} accepts points up to ~1e-9 / |move| off the
     * move (nearly parallel case), so the filter is only used for moves of at least {@link #MIN_FILTERED_MOVE}:
     * there the tolerance is below 1e-6 m, far inside the margin, and the answer is exactly the unfiltered one.
     */
    boolean segmentHitsWall(Vec2 from, Vec2 to) {
        Segment move = new Segment(from, to);
        boolean filter = from.distanceTo(to) >= MIN_FILTERED_MOVE;
        double lowX = Math.min(from.x(), to.x()) - BOX_MARGIN, highX = Math.max(from.x(), to.x()) + BOX_MARGIN;
        double lowY = Math.min(from.y(), to.y()) - BOX_MARGIN, highY = Math.max(from.y(), to.y()) + BOX_MARGIN;
        int c0 = column(Math.min(from.x(), to.x())), c1 = column(Math.max(from.x(), to.x()));
        int r0 = row(Math.min(from.y(), to.y())), r1 = row(Math.max(from.y(), to.y()));

        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                for (int wall : cells[r * columns + c]) {
                    if (filter && (Math.max(x1[wall], x2[wall]) < lowX || Math.min(x1[wall], x2[wall]) > highX
                            || Math.max(y1[wall], y2[wall]) < lowY || Math.min(y1[wall], y2[wall]) > highY)) {
                        continue;
                    }

                    if (Geometry.intersectSegments(move, walls.get(wall)).isPresent()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
