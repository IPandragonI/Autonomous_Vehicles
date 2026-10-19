package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PopulationTest {

    @Test
    public void testSizeAndArchitectureAreRespected() {
        Population population = new Population(30, new int[]{5, 6, 2}, new Rng(1));

        assertEquals(30, population.size());
        assertArrayEquals(new int[]{5, 6, 2}, population.getArchitecture());

        for (Individual individual : population.getIndividuals()) {
            assertEquals(5 * 6 + 6 + 6 * 2 + 2, individual.getGenome().length);
        }
    }

    @Test
    public void testGenomesAreAllDifferent() {
        Population population = new Population(100, new int[]{5, 6, 2}, new Rng(1));
        Set<String> genomes = new HashSet<>();

        for (Individual individual : population.getIndividuals()) {
            genomes.add(Arrays.toString(individual.getGenome()));
        }

        assertEquals(100, genomes.size());
    }

    @Test
    public void testCopyIsDeep() {
        Individual original = Individual.random(new Rng(2), 5, 6, 2);
        double[] before = original.getGenome();
        Individual copy = original.copy();

        double[] changed = copy.getGenome();
        changed[0] += 1.0;
        copy.getNetwork().setGenome(changed);

        assertArrayEquals(before, original.getGenome(), 0.0);
        assertNotSame(original.getNetwork(), copy.getNetwork());
    }

    @Test
    public void testNextGenerationKeepsSizeAndElitesAreIdentical() {
        List<Individual> ranked = Results.ranked(5, 1, 9, 3, 7, 2, 8, 4, 6, 0);
        Population population = new Population(ranked);
        Rng rng = new Rng(4);

        Population next = population.nextGeneration(new TournamentSelection(3, rng), new MutationOperator(0.5, 1.0, rng), 3);

        assertEquals(10, next.size());

        for (int i = 0; i < 3; i++) {
            assertArrayEquals(ranked.get(i).getGenome(), next.get(i).getGenome(), 0.0, "Elite " + i + " must be unchanged.");
            assertNotSame(ranked.get(i).getNetwork(), next.get(i).getNetwork());
        }

        for (int i = 3; i < next.size(); i++) {
            assertFalse(next.get(i).isEvaluated(), "Children start without fitness.");
        }
    }

    @Test
    public void testBreedingRequiresAnEvaluatedPopulation() {
        Population population = new Population(5, new int[]{5, 6, 2}, new Rng(1));
        Rng rng = new Rng(2);

        assertThrows(IllegalStateException.class,
                () -> population.nextGeneration(new RandomSelection(rng), new MutationOperator(0.1, 0.1, rng), 1));
    }

    @Test
    public void testBestFitnessNeverRegressesWithElitism() {
        Rng rng = new Rng(8);
        Evaluator evaluator = new Evaluator(List.of(Tracks.oval()), RaySensor.standard(), false,
                SimulationSettings.defaults().withMaxTime(20), new ProgressFitness());
        Population population = new Population(20, new int[]{5, 6, 2}, rng);
        double previousBest = Double.NEGATIVE_INFINITY;

        for (int generation = 0; generation < 6; generation++) {
            evaluator.evaluate(population);
            double best = population.best().getFitness();

            assertTrue(best >= previousBest, "Generation " + generation + ": " + best + " < " + previousBest);
            previousBest = best;
            population = population.nextGeneration(new TournamentSelection(3, rng), new MutationOperator(0.3, 0.5, rng), 2);
        }
    }

    @Test
    public void testMixedArchitecturesAreRejected() {
        Rng rng = new Rng(1);

        assertThrows(IllegalArgumentException.class, () -> new Population(List.of(
                Individual.random(rng, 5, 6, 2), Individual.random(rng, 5, 8, 2))));
    }
}
