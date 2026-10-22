package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.KinematicModel;
import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.VehicleModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a population: one simulation per track, fitness = mean over the tracks,
 * then the population is sorted by decreasing fitness. Fully deterministic.
 */
public class Evaluator {

    private final List<Track> tracks;
    private final RaySensor sensor;
    private final boolean useSpeed;
    private final boolean useCarState;
    private final boolean useTrackPreview;
    private final SimulationSettings settings;
    private final VehicleModel vehicle;
    private FitnessFunction fitness;

    public Evaluator(List<Track> tracks, RaySensor sensor, boolean useSpeed, SimulationSettings settings,
                     FitnessFunction fitness) {
        this(tracks, sensor, useSpeed, settings, fitness, KinematicModel.INSTANCE);
    }

    public Evaluator(List<Track> tracks, RaySensor sensor, boolean useSpeed, SimulationSettings settings,
                     FitnessFunction fitness, VehicleModel vehicle) {
        this(tracks, sensor, useSpeed, false, settings, fitness, vehicle);
    }

    /**
     * @param useCarState the networks also read the car state (see {@code CarBrain.carState})
     */
    public Evaluator(List<Track> tracks, RaySensor sensor, boolean useSpeed, boolean useCarState,
                     SimulationSettings settings, FitnessFunction fitness, VehicleModel vehicle) {
        this(tracks, sensor, useSpeed, useCarState, false, settings, fitness, vehicle);
    }

    /**
     * @param useTrackPreview the networks also read the road ahead (see {@code TrackPreview})
     */
    public Evaluator(List<Track> tracks, RaySensor sensor, boolean useSpeed, boolean useCarState,
                     boolean useTrackPreview, SimulationSettings settings, FitnessFunction fitness,
                     VehicleModel vehicle) {
        if (tracks == null || tracks.isEmpty() || sensor == null || settings == null || fitness == null
                || vehicle == null) {
            throw new IllegalArgumentException("Evaluator needs at least one track, a sensor, settings and a fitness.");
        }

        this.tracks = List.copyOf(tracks);
        this.sensor = sensor;
        this.useSpeed = useSpeed;
        this.useCarState = useCarState;
        this.useTrackPreview = useTrackPreview;
        this.settings = settings;
        this.fitness = fitness;
        this.vehicle = vehicle;
    }

    public void evaluate(Population population) {
        List<Simulation> simulations = createSimulations(population);

        for (Simulation simulation : simulations) {
            simulation.runUntilAllDead();
        }

        assign(population, simulations);
    }

    /**
     * One fresh simulation per track; car i is driven by individual i.
     */
    public List<Simulation> createSimulations(Population population) {
        List<NeuralNetwork> networks = population.getIndividuals().stream().map(Individual::getNetwork).toList();
        List<Simulation> simulations = new ArrayList<>();

        for (Track track : tracks) {
            simulations.add(new Simulation(track, networks, sensor, useSpeed, useCarState, useTrackPreview, settings,
                    vehicle));
        }

        return simulations;
    }

    /**
     * Reads the finished simulations (in the order of {@link #createSimulations}) into the individuals, then sorts.
     */
    public void assign(Population population, List<Simulation> simulations) {
        if (simulations.size() != tracks.size()) {
            throw new IllegalArgumentException("Expected one simulation per track.");
        }

        for (Simulation simulation : simulations) {
            if (!simulation.isFinished() || simulation.getAgents().size() != population.size()) {
                throw new IllegalStateException("Simulations must be finished and match the population.");
            }
        }

        for (int i = 0; i < population.size(); i++) {
            List<AgentResult> results = new ArrayList<>();
            double total = 0.0;

            for (Simulation simulation : simulations) {
                AgentResult result = simulation.getAgents().get(i).result();
                results.add(result);
                total += fitness.evaluate(result);
            }

            Individual individual = population.get(i);
            individual.setResults(results);
            individual.setFitness(total / simulations.size());
        }

        population.sortByFitness();
    }

    /**
     * Fitness of a single network on every track (e.g. a reloaded champion, or a champion tested on another track).
     */
    public double evaluate(NeuralNetwork network) {
        Population single = new Population(List.of(new Individual(network)));
        evaluate(single);
        return single.get(0).getFitness();
    }

    public void setFitness(FitnessFunction fitness) {
        if (fitness == null) {
            throw new IllegalArgumentException("Fitness must not be null.");
        }

        this.fitness = fitness;
    }

    public FitnessFunction getFitness() {
        return fitness;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public RaySensor getSensor() {
        return sensor;
    }

    public SimulationSettings getSettings() {
        return settings;
    }

    public VehicleModel getVehicle() {
        return vehicle;
    }

    public boolean usesSpeed() {
        return useSpeed;
    }

    public boolean usesCarState() {
        return useCarState;
    }

    public boolean usesTrackPreview() {
        return useTrackPreview;
    }
}
