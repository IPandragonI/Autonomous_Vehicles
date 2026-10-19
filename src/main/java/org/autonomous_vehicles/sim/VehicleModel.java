package org.autonomous_vehicles.sim;

/**
 * Laws of motion of a car type. A model holds no per-car state (that lives in {@link Car}),
 * so one instance can drive every car of every simulation, in parallel.
 */
public interface VehicleModel {

    /**
     * Advances the car by {@code dt} seconds under the given controls; does nothing for a stopped car.
     */
    void step(Car car, CarControls controls, double dt);

    /**
     * Highest speed reachable on a straight, m/s (used to normalise the speed input and the speed fitness).
     */
    double topSpeed();

    String name();

    /**
     * Puts the car in its starting state (gear, engine speed, new tyres).
     */
    default void prepare(Car car) {
    }
}
