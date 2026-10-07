package org.autonomous_vehicles.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CarTest {

    @Test
    public void testCarUpdate() {
        Car car = new Car(0, 0, Math.PI / 4, 10);
        car.update(1);

        double expectedX = 10 * Math.cos(Math.PI / 4);
        double expectedY = 10 * Math.sin(Math.PI / 4);
        double expectedAngle = Math.PI / 4;
        double expectedSpeed = 10;

        assertEquals(expectedX, car.getX(), 0.0001);
        assertEquals(expectedY, car.getY(), 0.0001);
        assertEquals(expectedAngle, car.getAngle(), 0.0001);
        assertEquals(expectedSpeed, car.getSpeed(), 0.0001);

        Car car2 = new Car(0, 0, Math.PI / 2, 5);
        car2.update(2);

        double expectedX2 = 0;
        double expectedY2 = 10;
        double expectedAngle2 = Math.PI / 2;
        double expectedSpeed2 = 5;

        assertEquals(expectedX2, car2.getX(), 0.0001);
        assertEquals(expectedY2, car2.getY(), 0.0001);
        assertEquals(expectedAngle2, car2.getAngle(), 0.0001);
        assertEquals(expectedSpeed2, car2.getSpeed(), 0.0001);
    }

    @Test
    public void testCarCrash() {
        Car car = new Car(0, 0, Math.PI / 4, 10);
        assertTrue(car.isAlive());
        car.crash();
        assertFalse(car.isAlive());
    }

    @Test
    public void testCarControl() {
        Car car = new Car(0, 0, 0, 10);
        car.control(Math.PI / 4, 2);

        assertEquals(Math.PI / 4, car.getAngle(), 0.0001);
        assertEquals(12, car.getSpeed(), 0.0001);
    }

    @Test
    public void testCarControlAndUpdate() {
        Car car = new Car(0, 0, 0, 10);
        car.control(Math.PI / 4, 2);
        car.update(1);

        double expectedX = 12 * Math.cos(Math.PI / 4);
        double expectedY = 12 * Math.sin(Math.PI / 4);

        assertEquals(expectedX, car.getX(), 0.0001);
        assertEquals(expectedY, car.getY(), 0.0001);
    }
}
