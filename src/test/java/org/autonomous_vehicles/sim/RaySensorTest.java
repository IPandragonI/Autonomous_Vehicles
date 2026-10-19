package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RaySensorTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void testStandardSensorHasFiveRaysFromRightToLeft() {
        double[] angles = RaySensor.standard().getAngles();

        assertArrayEquals(new double[]{-Math.PI / 2, -Math.PI / 4, 0, Math.PI / 4, Math.PI / 2}, angles, EPSILON);
    }

    @Test
    public void testKnownCorridorDistances() {
        Track track = TestDrivers.squareRing();
        Car car = new Car(30, 0, Math.PI / 2, 0);

        double[] distances = RaySensor.standard().distances(car, track);

        assertEquals(20.0, distances[0], EPSILON, "Right (-90°): outer wall x = 50.");
        assertEquals(20.0 * Math.sqrt(2), distances[1], EPSILON, "Right-forward (-45°): outer wall x = 50.");
        assertEquals(50.0, distances[2], EPSILON, "Forward: outer wall y = 50.");
        assertEquals(50.0 * Math.sqrt(2), distances[3], EPSILON, "Left-forward (+45°): passes above the inner square.");
        assertEquals(20.0, distances[4], EPSILON, "Left (+90°): inner wall x = 10.");
    }

    @Test
    public void testReadingsAreNormalisedByRange() {
        Track track = TestDrivers.squareRing();
        Car car = new Car(30, 0, Math.PI / 2, 0);

        double[] readings = RaySensor.standard().sense(car, track);

        assertEquals(20.0 / RaySensor.DEFAULT_RANGE, readings[0], EPSILON);
        assertEquals(50.0 / RaySensor.DEFAULT_RANGE, readings[2], EPSILON);

        for (double reading : readings) {
            assertTrue(reading >= 0.0 && reading <= 1.0);
        }
    }

    @Test
    public void testRayWithoutObstacleReadsOne() {
        Track track = TestDrivers.squareRing();
        RaySensor shortSensor = new RaySensor(new double[]{0.0}, 10.0);

        assertEquals(1.0, shortSensor.sense(new Car(30, 0, Math.PI / 2, 0), track)[0], EPSILON);
    }

    @Test
    public void testRaysTurnWithTheCar() {
        Track track = Tracks.oval();
        RaySensor sensor = new RaySensor(new double[]{0.0, Math.PI / 2}, 80.0);
        Rng rng = new Rng(9);

        for (int i = 0; i < 50; i++) {
            double heading = 2 * Math.PI * rng.nextDouble();
            Car facing = new Car(0, -45, heading + Math.PI / 2, 0);
            Car rotated = new Car(0, -45, heading, 0);

            assertEquals(sensor.sense(facing, track)[0], sensor.sense(rotated, track)[1], EPSILON);
        }
    }

    @Test
    public void testHitPointsLieAtTheMeasuredDistance() {
        Track track = TestDrivers.squareRing();
        Car car = new Car(30, 0, Math.PI / 2, 0);

        Vec2[] points = RaySensor.standard().hitPoints(car, track);

        assertEquals(new Vec2(50, 0).x(), points[0].x(), EPSILON);
        assertEquals(50.0, points[2].y(), EPSILON);
    }

    @Test
    public void testGridRaycastMatchesBruteForce() {
        Rng rng = new Rng(5);

        for (String name : Tracks.BUILT_IN) {
            Track track = Tracks.byName(name);
            int checked = 0;

            while (checked < 500) {
                Vec2 origin = new Vec2(-170 + 340 * rng.nextDouble(), -80 + 160 * rng.nextDouble());

                if (!track.isOnTrack(origin)) {
                    continue;
                }

                Vec2 direction = Vec2.fromAngle(2 * Math.PI * rng.nextDouble());
                double expected = 80.0;

                for (Segment wall : track.getWalls()) {
                    Optional<Vec2> hit = Geometry.intersectRaySegment(origin, direction, wall);

                    if (hit.isPresent()) {
                        expected = Math.min(expected, origin.distanceTo(hit.get()));
                    }
                }

                assertEquals(expected, track.distanceToWall(origin, direction, 80.0), 1e-7, name + " from " + origin);
                checked++;
            }
        }
    }

    @Test
    public void testInvalidSensorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RaySensor(new double[0], 10));
        assertThrows(IllegalArgumentException.class, () -> new RaySensor(new double[]{0}, 0));
        assertThrows(IllegalArgumentException.class, () -> new RaySensor(new double[]{Double.NaN}, 10));
    }
}
