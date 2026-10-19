package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * Control strategy: parents drawn uniformly, with no link to fitness.
 */
public class RandomSelection implements SelectionStrategy {

    private final Rng rng;

    public RandomSelection(Rng rng) {
        if (rng == null) {
            throw new IllegalArgumentException("Rng must not be null.");
        }

        this.rng = rng;
    }

    @Override
    public Individual select(List<Individual> rankedPopulation) {
        return rankedPopulation.get(rng.nextInt(rankedPopulation.size()));
    }

    @Override
    public String name() {
        return "Random";
    }
}
