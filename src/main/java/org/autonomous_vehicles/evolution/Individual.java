package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * An individual of the genetic algorithm: a network (whose weights and biases are the genome)
 * and the fitness of its last evaluation.
 */
public class Individual {

    private final NeuralNetwork network;
    private double fitness = Double.NaN;
    private List<AgentResult> results = List.of();

    public Individual(NeuralNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("Network must not be null.");
        }

        this.network = network;
    }

    public static Individual random(Rng rng, int... architecture) {
        return new Individual(new NeuralNetwork(rng, architecture));
    }

    /**
     * Child with the same architecture and activation as this individual but another genome.
     */
    public Individual withGenome(double[] genome) {
        NeuralNetwork child = network.copy();
        child.setGenome(genome);
        return new Individual(child);
    }

    /**
     * Deep copy: the copy owns its own network, so mutating it never changes the original.
     */
    public Individual copy() {
        Individual copy = new Individual(network.copy());
        copy.fitness = fitness;
        copy.results = results;
        return copy;
    }

    public double[] getGenome() {
        return network.getGenome();
    }

    public NeuralNetwork getNetwork() {
        return network;
    }

    public double getFitness() {
        return fitness;
    }

    public boolean isEvaluated() {
        return !Double.isNaN(fitness);
    }

    public void setFitness(double fitness) {
        if (Double.isNaN(fitness) || Double.isInfinite(fitness)) {
            throw new IllegalArgumentException("Fitness must be a finite number.");
        }

        this.fitness = fitness;
    }

    /**
     * Results of the last evaluation, one per track.
     */
    public List<AgentResult> getResults() {
        return results;
    }

    public void setResults(List<AgentResult> results) {
        this.results = List.copyOf(results);
    }

    /**
     * Result on the first (displayed) track, or null if not evaluated.
     */
    public AgentResult getPrimaryResult() {
        return results.isEmpty() ? null : results.getFirst();
    }
}
