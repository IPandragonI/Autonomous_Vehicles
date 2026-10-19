package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TestDrivers;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FitnessTest {

    private static Evaluator evaluator(FitnessFunction fitness) {
        return new Evaluator(List.of(Tracks.oval()), RaySensor.standard(), false, SimulationSettings.defaults(), fitness);
    }

    @Test
    public void testStillCarScoresZero() {
        Population population = new Population(List.of(new Individual(TestDrivers.zeroNetwork())));

        evaluator(new ProgressFitness()).evaluate(population);

        assertEquals(0.0, population.get(0).getFitness(), 0.0);
    }

    @Test
    public void testMoreProgressMeansMoreFitness() {
        ProgressFitness fitness = new ProgressFitness();

        assertTrue(fitness.evaluate(Results.progress(5.3)) > fitness.evaluate(Results.progress(2.1)));
        assertEquals(5.3, fitness.evaluate(Results.progress(5.3)), 0.0);
    }

    @Test
    public void testEvaluationSortsTheLappingCarFirst() {
        NeuralNetwork random = new NeuralNetwork(new Rng(5), 5, 2);
        NeuralNetwork follower = TestDrivers.follower();
        Population drivers = new Population(List.of(new Individual(random), new Individual(follower)));

        evaluator(new ProgressFitness()).evaluate(drivers);

        assertSame(follower, drivers.get(0).getNetwork(), "The follower laps the oval and must come first.");
        assertTrue(drivers.get(0).getFitness() > 40.0);
        assertTrue(drivers.get(0).getFitness() >= drivers.get(1).getFitness());
    }

    @Test
    public void testSortIsDecreasing() {
        Population population = new Population(Results.ranked(3, 9, 1, 7));
        population.sortByFitness();

        double previous = Double.POSITIVE_INFINITY;

        for (Individual individual : population.getIndividuals()) {
            assertTrue(individual.getFitness() <= previous);
            previous = individual.getFitness();
        }

        assertEquals(9.0, population.best().getFitness(), 0.0);
    }

    @Test
    public void testFitnessIsAveragedOverTracks() {
        Evaluator twoTracks = new Evaluator(List.of(Tracks.oval(), Tracks.byName("chicane")), RaySensor.standard(), false,
                SimulationSettings.defaults(), new ProgressFitness());
        Population population = new Population(List.of(new Individual(TestDrivers.follower())));

        twoTracks.evaluate(population);
        List<AgentResult> results = population.get(0).getResults();

        assertEquals(2, results.size());
        assertEquals((results.get(0).progress() + results.get(1).progress()) / 2, population.get(0).getFitness(), 1e-12);
    }

    @Test
    public void testReloadedNetworkGetsTheSameScore() {
        Evaluator evaluator = evaluator(CompositeFitness.defaults());
        NeuralNetwork network = new NeuralNetwork(new Rng(12), 5, 6, 2);

        assertEquals(evaluator.evaluate(network), evaluator.evaluate(network.copy()), 0.0);
    }

    @Test
    public void testStagnatedResultFixture() {
        assertEquals(DeathCause.STAGNATED, Results.progress(1).deathCause());
    }
}
