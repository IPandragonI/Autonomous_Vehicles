package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * Fitness-proportional ("roulette wheel") selection: P(i) = f(i) / Σ f.
 * Sensitive to the scale of the scores: negative fitnesses are shifted so the worst gets 0,
 * and if every weight is 0 the choice is uniform.
 */
public class RouletteSelection implements SelectionStrategy {

    private final Rng rng;

    public RouletteSelection(Rng rng) {
        if (rng == null) {
            throw new IllegalArgumentException("Rng must not be null.");
        }

        this.rng = rng;
    }

    @Override
    public Individual select(List<Individual> rankedPopulation) {
        double minimum = rankedPopulation.stream().mapToDouble(Individual::getFitness).min().orElseThrow();
        double shift = Math.min(0.0, minimum);
        double total = 0.0;

        for (Individual individual : rankedPopulation) {
            total += individual.getFitness() - shift;
        }

        if (total <= 0.0) {
            return rankedPopulation.get(rng.nextInt(rankedPopulation.size()));
        }

        double target = rng.nextDouble() * total;
        double cumulative = 0.0;

        for (Individual individual : rankedPopulation) {
            cumulative += individual.getFitness() - shift;

            if (target < cumulative) {
                return individual;
            }
        }

        return rankedPopulation.getLast();
    }

    @Override
    public String name() {
        return "Roulette";
    }
}
