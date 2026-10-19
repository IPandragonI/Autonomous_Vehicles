package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless simulation of a set of cars on one track (no JavaFX import, no randomness).
 * Each step runs, for every living car: sensors → network → physics → collision → checkpoints.
 * Cars do not interact, so a car's result does not depend on the other cars.
 */
public class Simulation {

    private final Track track;
    private final SimulationSettings settings;
    private final List<CarAgent> agents;
    private final VehicleModel vehicle;

    private double time;
    private long stepCount;

    public Simulation(Track track, List<NeuralNetwork> networks, RaySensor sensor, boolean useSpeed,
                      SimulationSettings settings) {
        this(track, networks, sensor, useSpeed, settings, KinematicModel.INSTANCE);
    }

    public Simulation(Track track, List<NeuralNetwork> networks, RaySensor sensor, boolean useSpeed,
                      SimulationSettings settings, VehicleModel vehicle) {
        if (track == null || networks == null || sensor == null || settings == null || vehicle == null) {
            throw new IllegalArgumentException("Simulation arguments must not be null.");
        }

        this.track = track;
        this.settings = settings;
        this.vehicle = vehicle;

        List<CarAgent> created = new ArrayList<>(networks.size());

        for (int i = 0; i < networks.size(); i++) {
            CarBrain brain = new CarBrain(networks.get(i), sensor, useSpeed, vehicle.topSpeed());
            created.add(new CarAgent(i, track, brain, settings, vehicle));
        }

        this.agents = List.copyOf(created);
    }

    public void step() {
        step(settings.dt());
    }

    public void step(double dt) {
        for (CarAgent agent : agents) {
            agent.step(dt);
        }

        time += dt;
        stepCount++;
    }

    /**
     * Runs until every car is stopped. Terminates because every car is stopped at {@code maxTime}.
     */
    public void runUntilAllDead() {
        while (!isFinished()) {
            step();
        }
    }

    public boolean isFinished() {
        return getAliveCount() == 0;
    }

    public int getAliveCount() {
        int alive = 0;

        for (CarAgent agent : agents) {
            if (agent.getCar().isAlive()) {
                alive++;
            }
        }

        return alive;
    }

    public List<AgentResult> getResults() {
        return agents.stream().map(CarAgent::result).toList();
    }

    public List<CarAgent> getAgents() {
        return agents;
    }

    public Track getTrack() {
        return track;
    }

    public VehicleModel getVehicle() {
        return vehicle;
    }

    public SimulationSettings getSettings() {
        return settings;
    }

    public double getTime() {
        return time;
    }

    public long getStepCount() {
        return stepCount;
    }
}
