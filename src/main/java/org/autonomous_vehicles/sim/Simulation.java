package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Headless simulation of a set of cars on one track (no JavaFX import, no randomness).
 * Each step runs, for every living car: sensors → network → physics → collision → checkpoints.
 * Cars do not interact, so a car's result does not depend on the other cars.
 * <p>
 * That independence is also what makes {@link #advance} and {@link #runUntilAllDead} parallel: each car runs its
 * own steps on its own thread, and the result is bit-identical to stepping all cars together (the track, sensor
 * and vehicle model are immutable; every car owns its state and its network).
 */
public class Simulation {

    /** Below this many cars the thread hand-off costs more than it saves. */
    static final int PARALLEL_THRESHOLD = 8;

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
        this(track, networks, sensor, useSpeed, false, settings, vehicle);
    }

    /**
     * @param useCarState the networks also read the car state (fuel, tyre temperature, wear, sliding)
     */
    public Simulation(Track track, List<NeuralNetwork> networks, RaySensor sensor, boolean useSpeed,
                      boolean useCarState, SimulationSettings settings, VehicleModel vehicle) {
        this(track, networks, sensor, useSpeed, useCarState, false, settings, vehicle);
    }

    /**
     * @param useTrackPreview the networks also read the road ahead ({@link TrackPreview})
     */
    public Simulation(Track track, List<NeuralNetwork> networks, RaySensor sensor, boolean useSpeed,
                      boolean useCarState, boolean useTrackPreview, SimulationSettings settings,
                      VehicleModel vehicle) {
        if (track == null || networks == null || sensor == null || settings == null || vehicle == null) {
            throw new IllegalArgumentException("Simulation arguments must not be null.");
        }

        this.track = track;
        this.settings = settings;
        this.vehicle = vehicle;

        List<CarAgent> created = new ArrayList<>(networks.size());
        Set<NeuralNetwork> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int i = 0; i < networks.size(); i++) {
            // A network keeps its last activations: a network driving two cars gets a copy, so cars stepped on
            // different threads never share mutable state.
            NeuralNetwork original = networks.get(i);
            NeuralNetwork network = original == null || seen.add(original) ? original : original.copy();
            CarBrain brain = new CarBrain(network, sensor, useSpeed, useCarState, useTrackPreview, vehicle);
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
        advance(Integer.MAX_VALUE);
    }

    /**
     * Same as calling {@link #step()} {@code steps} times, but stops early once every car is stopped, and runs
     * the cars in parallel. Afterwards the clock and step count are those of the sequential run.
     *
     * @return steps actually simulated (fewer than {@code steps} when every car stopped before)
     */
    public int advance(int steps) {
        if (steps < 0) {
            throw new IllegalArgumentException("Step count must not be negative.");
        }

        double dt = settings.dt();
        Stream<CarAgent> cars = agents.size() >= PARALLEL_THRESHOLD ? agents.parallelStream() : agents.stream();
        int done = cars.mapToInt(agent -> {
            int taken = 0;

            while (taken < steps && agent.getCar().isAlive()) {
                agent.step(dt);
                taken++;
            }

            return taken;
        }).max().orElse(0);

        // The sequential loop adds dt once per step: do the same so the clock is bit-identical.
        for (int i = 0; i < done; i++) {
            time += dt;
        }

        stepCount += done;
        return done;
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
