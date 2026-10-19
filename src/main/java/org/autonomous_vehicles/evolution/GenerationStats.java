package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;

import java.util.List;

/**
 * Summary of one evaluated generation.
 *
 * @param generation       index, 0 for the random initial population
 * @param bestFitness      maximum fitness
 * @param meanFitness      mean fitness
 * @param fitnessStd       population standard deviation of the fitness
 * @param maxProgress      best progress on the first track, in checkpoints
 * @param maxDistance      longest distance driven on the first track
 * @param bestLapTime      fastest lap on the first track, NaN if nobody completed a lap
 * @param lapsCompleted    number of individuals that completed at least one lap on the first track
 * @param genomeDiversity  mean distance of the genomes to their centroid (0 = all identical)
 */
public record GenerationStats(int generation, double bestFitness, double meanFitness, double fitnessStd,
                              double maxProgress, double maxDistance, double bestLapTime, int lapsCompleted,
                              double genomeDiversity) {

    public static GenerationStats compute(int generation, List<Individual> individuals) {
        if (individuals == null || individuals.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute statistics of an empty generation.");
        }

        double[] fitness = individuals.stream().mapToDouble(Individual::getFitness).toArray();
        double maxProgress = 0.0;
        double maxDistance = 0.0;
        double bestLapTime = Double.NaN;
        int lapsCompleted = 0;

        for (Individual individual : individuals) {
            AgentResult result = individual.getPrimaryResult();

            if (result == null) {
                continue;
            }

            maxProgress = Math.max(maxProgress, result.progress());
            maxDistance = Math.max(maxDistance, result.distance());

            if (result.laps() > 0) {
                lapsCompleted++;
                double lap = result.bestLapTime();
                bestLapTime = Double.isNaN(bestLapTime) ? lap : Math.min(bestLapTime, lap);
            }
        }

        return new GenerationStats(generation, Statistics.max(fitness), Statistics.mean(fitness),
                Statistics.populationStd(fitness), maxProgress, maxDistance, bestLapTime, lapsCompleted,
                genomeDiversity(individuals));
    }

    static double genomeDiversity(List<Individual> individuals) {
        double[][] genomes = individuals.stream().map(Individual::getGenome).toArray(double[][]::new);
        double[] centroid = new double[genomes[0].length];

        for (double[] genome : genomes) {
            for (int i = 0; i < centroid.length; i++) {
                centroid[i] += genome[i] / genomes.length;
            }
        }

        double total = 0.0;

        for (double[] genome : genomes) {
            total += GenomeAnalysis.distance(genome, centroid);
        }

        return total / genomes.length;
    }
}
