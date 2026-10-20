package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retraining: a population bred from a saved champion instead of random weights.
 */
public class RetrainTest {

    private static SimulationConfig config(long seed) {
        return SimulationConfig.defaults().toBuilder()
                .tracks("hairpin")
                .populationSize(30)
                .eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(30))
                .seed(seed)
                .build();
    }

    private static Champion trained() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(config(1));
        algorithm.run(6);
        return Champion.of(algorithm);
    }

    @Test
    public void testSeededPopulationIsTheChampionPlusMutantsOfIt() {
        NeuralNetwork seed = new NeuralNetwork(new Rng(5), 5, 6, 2);
        MutationOperator mutation = new MutationOperator(0.08, 0.25, new Rng(9));

        Population population = Population.seeded(20, seed, mutation);

        assertEquals(20, population.size());
        assertArrayEquals(seed.getGenome(), population.get(0).getGenome(), "Individual 0 is an exact copy.");
        assertNotSame(seed, population.get(0).getNetwork(), "The seed itself is never mutated.");

        int changed = 0;
        int genes = seed.getParameterCount();

        for (int i = 1; i < 20; i++) {
            double[] genome = population.get(i).getGenome();
            assertArrayEquals(seed.getArchitecture(), population.get(i).getNetwork().getArchitecture());

            for (int g = 0; g < genes; g++) {
                if (genome[g] != seed.getGenome()[g]) {
                    changed++;
                }
            }
        }

        // 19 mutants × 50 genes × 8 % ≈ 76 mutated genes: the mutants stay close to the champion
        assertTrue(changed > 35 && changed < 120, "mutated genes: " + changed);
    }

    @Test
    public void testSeededPopulationIsDeterministic() {
        NeuralNetwork seed = new NeuralNetwork(new Rng(5), 5, 6, 2);
        Population a = Population.seeded(10, seed, new MutationOperator(0.08, 0.25, new Rng(9)));
        Population b = Population.seeded(10, seed, new MutationOperator(0.08, 0.25, new Rng(9)));

        for (int i = 0; i < 10; i++) {
            assertArrayEquals(a.get(i).getGenome(), b.get(i).getGenome());
        }
    }

    @Test
    public void testRetrainingStartsAtLeastAsGoodAsTheChampion() {
        Champion champion = trained();
        // Another seed for the retraining run, so the mutants differ from the original run.
        GeneticAlgorithm retraining = new GeneticAlgorithm(champion.config().toBuilder().seed(77).build(),
                champion.network());

        GenerationStats first = retraining.runGeneration();

        // Individual 0 is the champion and the simulation is deterministic: it scores its old fitness again.
        assertTrue(first.bestFitness() >= champion.fitness() - 1e-9,
                first.bestFitness() + " >= " + champion.fitness());

        GeneticAlgorithm fromScratch = new GeneticAlgorithm(config(77));
        assertTrue(first.bestFitness() > fromScratch.runGeneration().bestFitness(),
                "Generation 0 bred from a champion beats a random generation 0.");
    }

    @Test
    public void testRetrainingKeepsImprovingWithElitism() {
        Champion champion = trained();
        GeneticAlgorithm retraining = new GeneticAlgorithm(champion.config().toBuilder().seed(3).build(),
                champion.network());

        double previous = Double.NEGATIVE_INFINITY;

        for (GenerationStats stats : retraining.run(4)) {
            assertTrue(stats.bestFitness() >= previous - 1e-9, "Elites never lose the best driver.");
            previous = stats.bestFitness();
        }
    }

    @Test
    public void testAChampionOfAnotherShapeIsRejected() {
        Champion champion = trained();
        SimulationConfig withSpeed = champion.config().toBuilder().useSpeedInput(true).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new GeneticAlgorithm(withSpeed, champion.network()));
        assertTrue(error.getMessage().contains("6-6-2"), error.getMessage());
    }
}
