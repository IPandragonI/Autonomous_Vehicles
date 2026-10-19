package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * Tournament of size k: k distinct individuals are drawn at random and the fittest wins.
 * k = 1 is a random choice; k = population size always returns the best. Weak individuals keep a small chance.
 */
public class TournamentSelection implements SelectionStrategy {

    private final int size;
    private final Rng rng;

    public TournamentSelection(int size, Rng rng) {
        if (size < 1) {
            throw new IllegalArgumentException("Tournament size must be at least 1.");
        }

        if (rng == null) {
            throw new IllegalArgumentException("Rng must not be null.");
        }

        this.size = size;
        this.rng = rng;
    }

    @Override
    public Individual select(List<Individual> rankedPopulation) {
        int n = rankedPopulation.size();
        int k = Math.min(size, n);
        int[] indices = new int[n];

        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        // Partial Fisher-Yates shuffle: the first k entries are k distinct random indices.
        int bestIndex = Integer.MAX_VALUE;

        for (int i = 0; i < k; i++) {
            int j = i + rng.nextInt(n - i);
            int chosen = indices[j];
            indices[j] = indices[i];
            indices[i] = chosen;
            bestIndex = Math.min(bestIndex, chosen);
        }

        // The list is sorted by decreasing fitness, so the smallest index is the fittest contestant.
        return rankedPopulation.get(bestIndex);
    }

    @Override
    public String name() {
        return "Tournament k=" + size;
    }

    public int getSize() {
        return size;
    }
}
