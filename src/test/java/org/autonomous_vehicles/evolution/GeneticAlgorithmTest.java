package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NetworkIO;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.Tracks;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeneticAlgorithmTest {

    private static SimulationConfig small(long seed) {
        return SimulationConfig.defaults().toBuilder()
                .populationSize(40)
                .eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(30))
                .seed(seed)
                .build();
    }

    @Test
    public void testMeanFitnessImprovesWithAFixedSeed() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(42));

        List<GenerationStats> stats = algorithm.run(31);

        assertEquals(31, stats.size());
        assertEquals(0, stats.getFirst().generation());
        assertTrue(stats.get(30).meanFitness() > stats.get(0).meanFitness(),
                "Generation 30 mean " + stats.get(30).meanFitness() + " vs generation 0 " + stats.get(0).meanFitness());
        assertTrue(stats.get(30).bestFitness() >= stats.get(0).bestFitness());
    }

    @Test
    public void testSameSeedGivesTheSameRun() {
        List<GenerationStats> first = new GeneticAlgorithm(small(7)).run(5);
        List<GenerationStats> second = new GeneticAlgorithm(small(7)).run(5);

        assertEquals(first, second);
    }

    @Test
    public void testBestFitnessNeverRegresses() {
        List<GenerationStats> stats = new GeneticAlgorithm(small(3)).run(12);

        for (int i = 1; i < stats.size(); i++) {
            assertTrue(stats.get(i).bestFitness() >= stats.get(i - 1).bestFitness() - 1e-9, "Generation " + i);
        }
    }

    @Test
    public void testReloadedChampionHasTheSameScore() throws IOException {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(42));
        algorithm.run(8);
        Path file = Files.createTempFile("champion", ".txt");

        algorithm.saveChampion(file);
        NeuralNetwork reloaded = NetworkIO.load(file);

        assertEquals(algorithm.getChampion().getFitness(), algorithm.getEvaluator().evaluate(reloaded), 0.0);
        Files.deleteIfExists(file);
    }

    @Test
    public void testChampionGenomesAreRecordedForEveryGeneration() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(5));
        algorithm.run(4);

        assertEquals(List.of(0, 1, 2, 3), List.copyOf(algorithm.getChampionGenomes().keySet()));
        assertEquals(4, algorithm.getGeneration());
        assertEquals(4, algorithm.getHistory().size());
    }

    @Test
    public void testChampionOfOneTrackIsTestedOnAnother() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(42));
        algorithm.run(10);
        SimulationConfig config = algorithm.getConfig();
        Evaluator otherTrack = new Evaluator(List.of(Tracks.byName("chicane")), config.createSensor(), false,
                config.settings(), config.createFitness());
        Population single = new Population(List.of(new Individual(algorithm.getChampion().getNetwork().copy())));

        otherTrack.evaluate(single);
        AgentResult result = single.get(0).getPrimaryResult();

        assertTrue(Double.isFinite(single.get(0).getFitness()));
        assertNotNull(result.deathCause());
    }

    @Test
    public void testHotChangesAreAppliedAndResetChangesRefused() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(1));
        SimulationConfig hot = algorithm.getConfig().toBuilder().mutationRate(0.3).selection(SelectionType.ROULETTE).build();

        algorithm.updateConfig(hot);

        assertEquals(0.3, algorithm.getConfig().mutationRate(), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.updateConfig(hot.toBuilder().populationSize(41).build()));
    }

    @Test
    public void testCrossoverRunWorks() {
        SimulationConfig config = small(2).toBuilder().crossover(SimulationConfig.Crossover.UNIFORM).build();

        List<GenerationStats> stats = new GeneticAlgorithm(config).run(3);

        assertEquals(3, stats.size());
    }

    @Test
    public void testBestLapIsKeptAcrossGenerations() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(small(42));
        algorithm.run(10);

        if (algorithm.getHistory().firstLapGeneration() >= 0) {
            assertNotNull(algorithm.getBestLap());
            assertEquals(algorithm.getHistory().bestLapTimeEver(), algorithm.getBestLap().getLapTime(), 1e-9);
        } else {
            assertNull(algorithm.getBestLap());
        }
    }
}
