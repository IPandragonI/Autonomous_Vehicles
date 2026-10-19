package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A fixed-size set of individuals sharing the same network architecture.
 */
public class Population {

    private final List<Individual> individuals;
    private final int[] architecture;

    /**
     * Random population: every individual gets its own random weights drawn from {@code rng}.
     */
    public Population(int size, int[] architecture, Rng rng) {
        if (size < 1) {
            throw new IllegalArgumentException("Population size must be positive.");
        }

        if (architecture == null || rng == null) {
            throw new IllegalArgumentException("Architecture and rng must not be null.");
        }

        this.architecture = architecture.clone();
        this.individuals = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            individuals.add(Individual.random(rng, architecture));
        }
    }

    public Population(List<Individual> individuals) {
        if (individuals == null || individuals.isEmpty()) {
            throw new IllegalArgumentException("A population needs at least one individual.");
        }

        this.architecture = individuals.getFirst().getNetwork().getArchitecture();

        for (Individual individual : individuals) {
            if (!Arrays.equals(architecture, individual.getNetwork().getArchitecture())) {
                throw new IllegalArgumentException("All individuals must share the same architecture.");
            }
        }

        this.individuals = new ArrayList<>(individuals);
    }

    /**
     * Sorts by decreasing fitness (individuals not evaluated last).
     */
    public void sortByFitness() {
        individuals.sort(byFitnessDescending());
    }

    static Comparator<Individual> byFitnessDescending() {
        return Comparator.comparingDouble((Individual individual) ->
                individual.isEvaluated() ? individual.getFitness() : Double.NEGATIVE_INFINITY).reversed();
    }

    public Population nextGeneration(SelectionStrategy selection, MutationOperator mutation, int eliteCount) {
        return nextGeneration(selection, mutation, null, eliteCount);
    }

    /**
     * Elites are copied unchanged; the rest are children of selected parents, optionally crossed over, then mutated.
     *
     * @param crossover null to disable crossover
     */
    public Population nextGeneration(SelectionStrategy selection, MutationOperator mutation,
                                     CrossoverOperator crossover, int eliteCount) {
        if (selection == null || mutation == null) {
            throw new IllegalArgumentException("Selection and mutation must not be null.");
        }

        if (eliteCount < 0 || eliteCount > size()) {
            throw new IllegalArgumentException("Elite count must be between 0 and the population size.");
        }

        if (individuals.stream().anyMatch(individual -> !individual.isEvaluated())) {
            throw new IllegalStateException("The population must be evaluated before breeding.");
        }

        List<Individual> ranked = new ArrayList<>(individuals);
        ranked.sort(byFitnessDescending());
        List<Individual> rankedView = Collections.unmodifiableList(ranked);

        List<Individual> next = new ArrayList<>(size());

        for (int i = 0; i < eliteCount; i++) {
            next.add(ranked.get(i).withGenome(ranked.get(i).getGenome()));
        }

        while (next.size() < size()) {
            Individual parent = selection.select(rankedView);
            double[] genome = parent.getGenome();

            if (crossover != null) {
                genome = crossover.crossover(genome, selection.select(rankedView).getGenome());
            }

            next.add(parent.withGenome(mutation.mutate(genome)));
        }

        return new Population(next);
    }

    public Individual best() {
        return individuals.stream().min(byFitnessDescending()).orElseThrow();
    }

    public int size() {
        return individuals.size();
    }

    public Individual get(int index) {
        return individuals.get(index);
    }

    public List<Individual> getIndividuals() {
        return Collections.unmodifiableList(individuals);
    }

    public int[] getArchitecture() {
        return architecture.clone();
    }
}
