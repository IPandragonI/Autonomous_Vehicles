package org.autonomous_vehicles.evolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Statistics of every generation of a run, in order.
 */
public class StatsHistory {

    private final List<GenerationStats> generations = new ArrayList<>();

    public void add(GenerationStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException("Stats must not be null.");
        }

        generations.add(stats);
    }

    public List<GenerationStats> all() {
        return List.copyOf(generations);
    }

    public int size() {
        return generations.size();
    }

    public GenerationStats last() {
        return generations.isEmpty() ? null : generations.getLast();
    }

    public double bestFitnessEver() {
        return generations.stream().mapToDouble(GenerationStats::bestFitness).max().orElse(Double.NaN);
    }

    /**
     * Fastest lap over all generations, NaN if no lap was ever completed.
     */
    public double bestLapTimeEver() {
        return generations.stream().mapToDouble(GenerationStats::bestLapTime)
                .filter(time -> !Double.isNaN(time)).min().orElse(Double.NaN);
    }

    /**
     * First generation in which at least one car completed a lap, or -1.
     */
    public int firstLapGeneration() {
        return generations.stream().filter(stats -> stats.lapsCompleted() > 0)
                .mapToInt(GenerationStats::generation).findFirst().orElse(-1);
    }

    public void clear() {
        generations.clear();
    }
}
