package org.autonomous_vehicles.geometry;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GeometryTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void testVectorOperations() {
        Vec2 a = new Vec2(2, 3);
        Vec2 b = new Vec2(4, 1);

        assertEquals(new Vec2(6, 4), a.add(b));
        assertEquals(new Vec2(-2, 2), a.subtract(b));
        assertEquals(11.0, a.dot(b), EPSILON);
        assertEquals(-10.0, a.cross(b), EPSILON);
        assertEquals(5.0, new Vec2(3, 4).length(), EPSILON);
    }

    @Test
    public void testNormalize() {
        Vec2 direction = new Vec2(3, 4).normalize();

        assertEquals(1.0, direction.length(), EPSILON);
        assertEquals(0.6, direction.x(), EPSILON);
        assertEquals(0.8, direction.y(), EPSILON);
    }

    @Test
    public void testFromAngle() {
        Vec2 direction = Vec2.fromAngle(Math.PI / 2);

        assertEquals(0.0, direction.x(), EPSILON);
        assertEquals(1.0, direction.y(), EPSILON);
    }

    @Test
    public void testCrossingSegments() {
        Segment horizontal = new Segment(new Vec2(0, 0), new Vec2(4, 0));

        Segment vertical = new Segment(new Vec2(2, -1), new Vec2(2, 1));

        Optional<Vec2> result = Geometry.intersectSegments(horizontal, vertical);

        assertTrue(result.isPresent());
        assertEquals(new Vec2(2, 0), result.get());
    }

    @Test
    public void testParallelSegmentsDoNotIntersect() {
        Segment first = new Segment(new Vec2(0, 0), new Vec2(4, 0));

        Segment second = new Segment(new Vec2(0, 2), new Vec2(4, 2));

        assertTrue(Geometry.intersectSegments(first, second).isEmpty());
    }

    @Test
    public void testCollinearOverlappingSegments() {
        Segment first = new Segment(new Vec2(0, 0), new Vec2(4, 0));

        Segment second = new Segment(new Vec2(2, 0), new Vec2(6, 0));

        Optional<Vec2> result = Geometry.intersectSegments(first, second);

        assertTrue(result.isPresent());
        assertEquals(new Vec2(2, 0), result.get());
    }

    @Test
    public void testSegmentsOutsideEachOtherDoNotIntersect() {
        Segment first = new Segment(new Vec2(0, 0), new Vec2(1, 0));

        Segment second = new Segment(new Vec2(2, -1), new Vec2(2, 1));

        assertTrue(Geometry.intersectSegments(first, second).isEmpty());
    }

    @Test
    public void testRayIntersectsSegment() {
        Vec2 origin = new Vec2(0, 0);
        Vec2 direction = new Vec2(1, 0);

        Segment wall = new Segment(new Vec2(3, -1), new Vec2(3, 1));

        Optional<Vec2> result = Geometry.intersectRaySegment(origin, direction, wall);

        assertTrue(result.isPresent());
        assertEquals(new Vec2(3, 0), result.get());
    }

    @Test
    public void testRayDoesNotHitWallBehindIt() {
        Vec2 origin = new Vec2(0, 0);
        Vec2 direction = new Vec2(1, 0);

        Segment wallBehind = new Segment(new Vec2(-3, -1), new Vec2(-3, 1));

        assertTrue(Geometry.intersectRaySegment(origin, direction, wallBehind).isEmpty());
    }

    @Test
    public void testCollinearRayAndSegment() {
        Vec2 origin = new Vec2(0, 0);
        Vec2 direction = new Vec2(1, 0);

        Segment wall = new Segment(new Vec2(3, 0), new Vec2(5, 0));

        Optional<Vec2> result = Geometry.intersectRaySegment(origin, direction, wall);

        assertTrue(result.isPresent());
        assertEquals(new Vec2(3, 0), result.get());
    }
}
