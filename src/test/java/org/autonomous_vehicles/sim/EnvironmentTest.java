package org.autonomous_vehicles.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvironmentTest {

    @Test
    public void testIsInsideCarOnCenter() {
        Environment env = new Environment(100, 100);
        Car carInside = new Car(50, 50, 0, 10);
        assertTrue(env.isInside(carInside));
    }

    @Test
    public void testIsInsideCarOnEdge() {
        Environment env = new Environment(100, 100);
        Car carOnEdge = new Car(0, 0, 0, 10);
        assertTrue(env.isInside(carOnEdge));
    }

    @Test
    public void testIsInsideCarOutside() {
        Environment env = new Environment(100, 100);
        Car carOutside = new Car(150, 150, 0, 10);
        assertFalse(env.isInside(carOutside));
    }

    @Test
    public void testUpdateCarInside() {
        Environment env = new Environment(100, 100);
        Car car = new Car(50, 50, 0, 10);
        env.update(car, 1);
        assertTrue(car.isAlive());
    }

    @Test
    public void testUpdateCarOutside() {
        Environment env = new Environment(100, 100);
        Car car = new Car(90, 50, 0, 20);
        env.update(car, 1);
        assertFalse(car.isAlive());
    }
}
