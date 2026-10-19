package org.autonomous_vehicles.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CarTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void testAccelerationInStraightLine() {
        Car car = new Car(0, 0, 0, 0);

        CarPhysics.step(car, 0, 1, 0.1);

        assertEquals(0.8, car.getSpeed(), EPSILON);

        assertEquals(0.08, car.getX(), EPSILON);
        assertEquals(0.0, car.getY(), EPSILON);
        assertEquals(0.0, car.getAngle(), EPSILON);
    }

    @Test
    public void testMovementAtConstantSpeed() {
        Car car = new Car(0, 0, Math.PI / 2, 5);

        CarPhysics.step(car, 0, 1, 0.1);

        assertEquals(5.8, car.getSpeed(), EPSILON);
        assertEquals(0.0, car.getX(), EPSILON);
        assertEquals(0.58, car.getY(), EPSILON);
    }

    @Test
    public void testFrictionSlowsDownCar() {
        Car car = new Car(0, 0, 0, 10);

        CarPhysics.step(car, 0, 0, 0.1);

        assertEquals(9.8, car.getSpeed(), EPSILON);
        assertEquals(0.98, car.getX(), EPSILON);
    }

    @Test
    public void testSpeedNeverExceedsMaximum() {
        Car car = new Car(0, 0, 0, CarPhysics.MAX_SPEED);

        CarPhysics.step(car, 0, 1, 0.1);

        assertEquals(CarPhysics.MAX_SPEED, car.getSpeed(), EPSILON);
    }

    @Test
    public void testSteeringChangesAngle() {
        Car car = new Car(0, 0, 0, 10);

        CarPhysics.step(car, 1, 1, 0.1);

        double expectedSpeed = 10 + CarPhysics.MAX_ACCELERATION * 0.1;
        double expectedAngle = expectedSpeed / CarPhysics.WHEEL_BASE * Math.tan(CarPhysics.MAX_STEERING_ANGLE) * 0.1;

        assertEquals(expectedAngle, car.getAngle(), EPSILON);
        assertTrue(car.getY() > 0);
    }

    @Test
    public void testConstantSteeringProducesCircularTrajectory() {
        double speed = CarPhysics.MAX_SPEED;
        double steer = 0.5;
        double steeringAngle = steer * CarPhysics.MAX_STEERING_ANGLE;
        double radius = CarPhysics.WHEEL_BASE / Math.tan(steeringAngle);

        Car car = new Car(0, 0, 0, speed);


        double centerX = 0;

        for (int i = 0; i < 100; i++) {
            CarPhysics.step(car, steer, 1, 0.01);
        }

        double distanceToCenter = Math.hypot(car.getX() - centerX, car.getY() - radius);

        assertEquals(radius, distanceToCenter, 0.2);
        assertEquals(speed, car.getSpeed(), EPSILON);
    }

    @Test
    public void testDeadCarDoesNotMove() {
        Car car = new Car(2, 3, 0.5, 10);
        car.crash();

        CarPhysics.step(car, 1, 1, 0.1);

        assertEquals(2, car.getX(), EPSILON);
        assertEquals(3, car.getY(), EPSILON);
        assertEquals(0.5, car.getAngle(), EPSILON);
        assertEquals(0, car.getSpeed(), EPSILON);
        assertFalse(car.isAlive());
    }

    @Test
    public void testInvalidDeltaTimeThrows() {
        Car car = new Car(0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> CarPhysics.step(car, 0, 1, 0));

        assertThrows(IllegalArgumentException.class, () -> CarPhysics.step(car, 0, 1, 0.2));
    }

    @Test
    public void testInvalidControlsThrow() {
        Car car = new Car(0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> CarPhysics.step(car, 2, 1, 0.01));

        assertThrows(IllegalArgumentException.class, () -> CarPhysics.step(car, 0, -2, 0.01));
    }

    @Test
    public void testCrash() {
        Car car = new Car(0, 0, 0, 10);

        assertTrue(car.isAlive());

        car.crash();

        assertFalse(car.isAlive());
        assertEquals(0.0, car.getSpeed(), EPSILON);
    }
}
