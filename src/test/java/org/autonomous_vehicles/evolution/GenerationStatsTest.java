package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GenerationStatsTest {

    private static Individual individual(Rng rng, double fitness, double progress, List<Double> laps) {
        Individual individual = new Individual(new NeuralNetwork(rng, 5, 6, 2));
        individual.setFitness(fitness);
        individual.setResults(List.of(Results.result(progress, laps, laps.isEmpty() ? DeathCause.CRASHED : DeathCause.FINISHED)));
        return individual;
    }

    @Test
    public void testStatisticsOnKnownData() {
        Rng rng = new Rng(1);
        List<Individual> individuals = new ArrayList<>(List.of(
                individual(rng, 1.0, 1.0, List.of()),
                individual(rng, 2.0, 4.0, List.of()),
                individual(rng, 3.0, 17.0, List.of(21.5)),
                individual(rng, 6.0, 33.0, List.of(20.0, 19.25))));

        GenerationStats stats = GenerationStats.compute(4, individuals);

        assertEquals(4, stats.generation());
        assertEquals(6.0, stats.bestFitness(), 1e-12);
        assertEquals(3.0, stats.meanFitness(), 1e-12);
        assertEquals(Math.sqrt(3.5), stats.fitnessStd(), 1e-12);
        assertEquals(33.0, stats.maxProgress(), 1e-12);
        assertEquals(330.0, stats.maxDistance(), 1e-12);
        assertEquals(19.25, stats.bestLapTime(), 1e-12);
        assertEquals(2, stats.lapsCompleted());
    }

    @Test
    public void testNoLapGivesNaN() {
        Rng rng = new Rng(1);
        GenerationStats stats = GenerationStats.compute(0, List.of(individual(rng, 1.0, 1.0, List.of())));

        assertTrue(Double.isNaN(stats.bestLapTime()));
        assertEquals(0, stats.lapsCompleted());
    }

    @Test
    public void testIdenticalGenomesHaveZeroDiversity() {
        Individual original = Individual.random(new Rng(2), 5, 6, 2);
        List<Individual> clones = List.of(original, original.copy(), original.copy());

        assertEquals(0.0, GenerationStats.genomeDiversity(clones), 1e-12);
    }

    @Test
    public void testHistorySummaries() {
        StatsHistory history = new StatsHistory();
        history.add(new GenerationStats(0, 1, 0.5, 0.1, 2, 20, Double.NaN, 0, 3));
        history.add(new GenerationStats(1, 5, 1.5, 0.1, 17, 170, 30.0, 1, 3));
        history.add(new GenerationStats(2, 4, 2.5, 0.1, 17, 170, 28.0, 4, 3));

        assertEquals(5.0, history.bestFitnessEver(), 0.0);
        assertEquals(28.0, history.bestLapTimeEver(), 0.0);
        assertEquals(1, history.firstLapGeneration());
        assertEquals(2, history.last().generation());
    }
}
