package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CollisionTest {

    @Test
    public void testCarLaunchedIntoWallDies() {
        Track track = Tracks.oval();
        CarAgent agent = new CarAgent(0, track, new CarBrain(TestDrivers.zeroNetwork(), RaySensor.standard(), false),
                SimulationSettings.defaults());
        Car car = agent.getCar();
        car.setAngle(-Math.PI / 2);
        car.setSpeed(CarPhysics.MAX_SPEED);

        boolean hit = false;

        for (int i = 0; i < 120 && !hit; i++) {
            CarPhysics.step(car, 0, 1, 1.0 / 60.0);
            hit = Collision.hits(car, track);
        }

        assertTrue(hit, "Driving straight to the outer wall must end in a collision.");
        assertTrue(car.getY() < -55.0, "The collision happens at the outer wall (y = -60).");
    }

    @Test
    public void testCarInTheMiddleIsAlive() {
        Track track = Tracks.oval();

        assertFalse(Collision.hits(Car.atStart(track), track));
    }

    @Test
    public void testGrazingTheWall() {
        Track track = TestDrivers.squareRing();
        double justOutside = 50.0 - Car.RADIUS - 0.01;
        double justInside = 50.0 - Car.RADIUS + 0.01;

        assertFalse(Collision.hits(new Car(justOutside, 0, 0, 0), track), "Radius + 1 cm from the wall: no contact.");
        assertTrue(Collision.hits(new Car(justInside, 0, 0, 0), track), "Radius - 1 cm from the wall: contact.");
    }

    @Test
    public void testCornerIsDetectedByDistanceNotBoundingBox() {
        Track track = TestDrivers.squareRing();
        double offset = Car.RADIUS * 0.75;
        Car nearCorner = new Car(50.0 - offset, 50.0 - offset, 0, 0);

        assertTrue(Collision.hits(nearCorner, track));
    }

    @Test
    public void testMoveThroughWallIsDetected() {
        Track track = TestDrivers.squareRing();

        assertTrue(Collision.crossesWall(new Vec2(45, 0), new Vec2(55, 0), track));
        assertFalse(Collision.crossesWall(new Vec2(25, 0), new Vec2(35, 0), track));
    }

    @Test
    public void testCrashStopsTheAgent() {
        Track track = Tracks.oval();
        Simulation simulation = new Simulation(track, List.of(TestDrivers.zeroNetwork()), RaySensor.standard(), false,
                SimulationSettings.defaults());
        Car car = simulation.getAgents().getFirst().getCar();
        car.setAngle(-Math.PI / 2);
        car.setSpeed(CarPhysics.MAX_SPEED);

        simulation.runUntilAllDead();

        assertEquals(DeathCause.CRASHED, simulation.getAgents().getFirst().getDeathCause());
        assertFalse(car.isAlive());
    }

    @Test
    public void testGridMatchesBruteForceOnRandomPoints() {
        Rng rng = new Rng(3);

        for (String name : Tracks.BUILT_IN) {
            Track track = Tracks.byName(name);

            for (int i = 0; i < 2000; i++) {
                Vec2 point = new Vec2(-170 + 340 * rng.nextDouble(), -80 + 160 * rng.nextDouble());
                double radius = 0.5 + 5 * rng.nextDouble();
                boolean bruteForce = track.getWalls().stream()
                        .anyMatch(wall -> Geometry.distancePointToSegment(point, wall) <= radius);

                assertEquals(bruteForce, Collision.hits(point, radius, track), name + " at " + point);
            }
        }
    }

    @Test
    public void testSegmentDistance() {
        Segment wall = new Segment(new Vec2(0, 0), new Vec2(10, 0));

        assertEquals(3.0, Geometry.distancePointToSegment(new Vec2(5, 3), wall), 1e-12);
        assertEquals(5.0, Geometry.distancePointToSegment(new Vec2(13, 4), wall), 1e-12);
    }
}
