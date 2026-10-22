package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NetworkIO;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.LapReplay;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.Rng;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The evolutionary loop: evaluate → statistics → selection / crossover / mutation → next generation.
 * <p>
 * Headless use: {@link #run(int)}. Step-by-step use (the UI drives the simulations itself):
 * {@link #createSimulations()}, step them until finished, then {@link #completeGeneration(List)}.
 * Every random draw comes from one {@link Rng} seeded by the configuration, so a run is reproducible.
 */
public class GeneticAlgorithm {

    private final Rng rng;
    private final List<Track> tracks;
    private final StatsHistory history = new StatsHistory();
    private final Map<Integer, double[]> championGenomes = new LinkedHashMap<>();

    private SimulationConfig config;
    private Evaluator evaluator;
    private SelectionStrategy selection;
    private MutationOperator mutation;
    private CrossoverOperator crossover;

    private Population population;
    private int generation;
    private Individual champion;
    private int championGeneration = -1;
    private LapReplay bestLap;
    private Individual fastestLap;
    private double fastestLapTime = Double.NaN;
    private int fastestLapGeneration = -1;

    public GeneticAlgorithm(SimulationConfig config) {
        this(config, null);
    }

    /**
     * @param seed network the first population is bred from — retraining a saved champion, possibly on another
     *             track or car — or null for a random population. It must have the configuration's architecture.
     * @see Population#seeded
     */
    public GeneticAlgorithm(SimulationConfig config, NeuralNetwork seed) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration must not be null.");
        }

        this.config = config;
        this.rng = new Rng(config.seed());
        this.tracks = config.tracks().stream().map(Tracks::byName).toList();
        this.evaluator = config.createEvaluator(tracks);

        if (seed == null) {
            this.population = NetworkInputs.randomPopulation(config, rng);
            createOperators();
        } else {
            if (!Arrays.equals(seed.getArchitecture(), config.architecture())) {
                throw new IllegalArgumentException("The champion's network has " + seed.getArchitecture()[0]
                        + " inputs and architecture " + Arrays.toString(seed.getArchitecture())
                        + ", the configuration needs " + config.architectureLabel() + ".");
            }

            createOperators();
            this.population = Population.seeded(config.populationSize(), seed, mutation);
        }
    }

    private void createOperators() {
        selection = config.selection().create(config, rng);
        mutation = new MutationOperator(config.mutationRate(), config.mutationStrength(), config.noise(), rng);
        crossover = switch (config.crossover()) {
            case NONE -> null;
            case UNIFORM -> new CrossoverOperator(CrossoverOperator.Type.UNIFORM, rng);
            case ONE_POINT -> new CrossoverOperator(CrossoverOperator.Type.ONE_POINT, rng);
        };
    }

    /**
     * Applies the parameters that can change during a run (mutation, selection, elitism, crossover, fitness).
     *
     * @throws IllegalArgumentException if the new configuration needs a reset
     */
    public void updateConfig(SimulationConfig newConfig) {
        if (config.requiresReset(newConfig)) {
            throw new IllegalArgumentException("This change requires a new run (reset).");
        }

        config = newConfig;
        evaluator.setFitness(newConfig.createFitness());
        createOperators();
    }

    public List<GenerationStats> run(int generations) {
        return run(generations, stats -> { });
    }

    public List<GenerationStats> run(int generations, Consumer<GenerationStats> listener) {
        if (generations < 0) {
            throw new IllegalArgumentException("Generation count must not be negative.");
        }

        List<GenerationStats> produced = new ArrayList<>();

        for (int i = 0; i < generations; i++) {
            GenerationStats stats = runGeneration();
            produced.add(stats);
            listener.accept(stats);
        }

        return produced;
    }

    public GenerationStats runGeneration() {
        List<Simulation> simulations = createSimulations();

        for (Simulation simulation : simulations) {
            simulation.runUntilAllDead();
        }

        return completeGeneration(simulations);
    }

    public List<Simulation> createSimulations() {
        return evaluator.createSimulations(population);
    }

    /**
     * Records the results of the finished simulations of the current generation, then breeds the next one.
     */
    public GenerationStats completeGeneration(List<Simulation> finishedSimulations) {
        evaluator.assign(population, finishedSimulations);

        GenerationStats stats = GenerationStats.compute(generation, population.getIndividuals());
        history.add(stats);

        Individual best = population.best();
        championGenomes.put(generation, best.getGenome());

        if (champion == null || best.getFitness() > champion.getFitness()) {
            champion = best.copy();
            championGeneration = generation;
        }

        for (Individual individual : population.getIndividuals()) {
            AgentResult result = individual.getPrimaryResult();

            if (result.bestLap() != null && (bestLap == null || result.bestLap().getLapTime() < bestLap.getLapTime())) {
                bestLap = result.bestLap();
            }

            // Strictly faster only: on a tie the earliest car stays, so the choice never depends on the sort order.
            double lapTime = result.bestLapTime();

            if (!Double.isNaN(lapTime) && (fastestLap == null || lapTime < fastestLapTime)) {
                fastestLap = individual.copy();
                fastestLapTime = lapTime;
                fastestLapGeneration = generation;
            }
        }

        population = population.nextGeneration(selection, mutation, crossover, config.eliteCount());
        generation++;

        return stats;
    }

    public void saveChampion(Path path) throws IOException {
        if (champion == null) {
            throw new IllegalStateException("No generation has been evaluated yet.");
        }

        NetworkIO.save(champion.getNetwork(), path);
    }

    /**
     * Generation number of the current (not yet evaluated) population.
     */
    public int getGeneration() {
        return generation;
    }

    public Population getPopulation() {
        return population;
    }

    public Individual getChampion() {
        return champion;
    }

    /**
     * The car (network) that drove the fastest lap of the run on the first track, whatever its generation, or null
     * while no car has completed a lap. Kept as a copy, so it survives even if evolution loses it: the fittest car
     * ({@link #getChampion()}) is often a safer but slower driver, since the fitness also rewards progress and
     * laps, not only speed.
     */
    public Individual getFastestLap() {
        return fastestLap;
    }

    /**
     * Seconds of that fastest lap, NaN while no lap was completed.
     */
    public double getFastestLapTime() {
        return fastestLapTime;
    }

    public int getFastestLapGeneration() {
        return fastestLapGeneration;
    }

    /**
     * Generation in which the champion was evaluated, -1 before the first generation.
     */
    public int getChampionGeneration() {
        return championGeneration;
    }

    /**
     * Best genome of each evaluated generation (generation → genome).
     */
    public Map<Integer, double[]> getChampionGenomes() {
        return Collections.unmodifiableMap(championGenomes);
    }

    public LapReplay getBestLap() {
        return bestLap;
    }

    public StatsHistory getHistory() {
        return history;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public List<Track> getTracks() {
        return tracks;
    }
}
