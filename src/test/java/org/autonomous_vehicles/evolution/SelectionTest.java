package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SelectionTest {

    private static final int DRAWS = 40000;

    private static Map<Individual, Integer> frequencies(SelectionStrategy strategy, List<Individual> ranked) {
        Map<Individual, Integer> counts = new HashMap<>();

        for (int i = 0; i < DRAWS; i++) {
            counts.merge(strategy.select(ranked), 1, Integer::sum);
        }

        return counts;
    }

    private static List<Individual> twenty() {
        double[] fitness = new double[20];

        for (int i = 0; i < 20; i++) {
            fitness[i] = i + 1;
        }

        return Results.ranked(fitness);
    }

    @Test
    public void testTruncationOnlyPicksTheBest() {
        List<Individual> ranked = twenty();
        Map<Individual, Integer> counts = frequencies(new TruncationSelection(0.10, new Rng(1)), ranked);

        assertEquals(2, counts.size(), "10 % of 20 = the 2 best.");
        assertTrue(counts.containsKey(ranked.get(0)));
        assertTrue(counts.containsKey(ranked.get(1)));
    }

    @Test
    public void testRouletteFrequenciesAreProportionalToFitness() {
        List<Individual> ranked = Results.ranked(1, 2, 3, 4);
        Map<Individual, Integer> counts = frequencies(new RouletteSelection(new Rng(2)), ranked);

        for (Individual individual : ranked) {
            double expected = individual.getFitness() / 10.0;
            assertEquals(expected, counts.getOrDefault(individual, 0) / (double) DRAWS, 0.01);
        }
    }

    @Test
    public void testRouletteHandlesNegativeAndZeroFitness() {
        List<Individual> negative = Results.ranked(-3, -1, 1);
        Map<Individual, Integer> counts = frequencies(new RouletteSelection(new Rng(3)), negative);

        assertNull(counts.get(negative.get(2)), "After shifting, the worst gets weight 0.");

        List<Individual> zeros = Results.ranked(0, 0, 0, 0);
        assertEquals(4, frequencies(new RouletteSelection(new Rng(3)), zeros).size(), "All zero: uniform choice.");
    }

    @Test
    public void testTournamentOfOneIsRandom() {
        List<Individual> ranked = twenty();
        Map<Individual, Integer> counts = frequencies(new TournamentSelection(1, new Rng(4)), ranked);

        for (Individual individual : ranked) {
            assertEquals(1.0 / 20, counts.getOrDefault(individual, 0) / (double) DRAWS, 0.01);
        }
    }

    @Test
    public void testTournamentOfWholePopulationAlwaysPicksTheBest() {
        List<Individual> ranked = twenty();
        Map<Individual, Integer> counts = frequencies(new TournamentSelection(20, new Rng(5)), ranked);

        assertEquals(Map.of(ranked.getFirst(), DRAWS), counts);
    }

    @Test
    public void testLargerTournamentsIncreasePressure() {
        List<Individual> ranked = twenty();
        double k2 = frequencies(new TournamentSelection(2, new Rng(6)), ranked).getOrDefault(ranked.getFirst(), 0);
        double k5 = frequencies(new TournamentSelection(5, new Rng(6)), ranked).getOrDefault(ranked.getFirst(), 0);

        assertEquals(2.0 / 20, k2 / DRAWS, 0.01, "P(best) with k=2 is k/n.");
        assertEquals(5.0 / 20, k5 / DRAWS, 0.01, "P(best) with k=5 is k/n.");
    }

    @Test
    public void testRandomSelectionIsUniform() {
        List<Individual> ranked = twenty();
        Map<Individual, Integer> counts = frequencies(new RandomSelection(new Rng(7)), ranked);

        for (Individual individual : ranked) {
            assertEquals(1.0 / 20, counts.getOrDefault(individual, 0) / (double) DRAWS, 0.01);
        }
    }

    @Test
    public void testSelectionTypeCreatesTheConfiguredStrategy() {
        SimulationConfig config = SimulationConfig.defaults().toBuilder().tournamentSize(7).build();

        assertInstanceOf(TournamentSelection.class, SelectionType.TOURNAMENT.create(config, new Rng(1)));
        assertEquals(7, ((TournamentSelection) SelectionType.TOURNAMENT.create(config, new Rng(1))).getSize());
        assertInstanceOf(TruncationSelection.class, SelectionType.TRUNCATION.create(config, new Rng(1)));
        assertInstanceOf(RouletteSelection.class, SelectionType.ROULETTE.create(config, new Rng(1)));
        assertInstanceOf(RandomSelection.class, SelectionType.RANDOM.create(config, new Rng(1)));
    }
}
