package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationTest {

    private static List<NeuralNetwork> randomNetworks(long seed, int count) {
        Rng rng = new Rng(seed);
        List<NeuralNetwork> networks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            networks.add(new NeuralNetwork(rng, 5, 6, 2));
        }

        return networks;
    }

    private static Simulation run(long seed) {
        Simulation simulation = new Simulation(Tracks.oval(), randomNetworks(seed, 100), RaySensor.standard(), false,
                SimulationSettings.defaults());
        simulation.runUntilAllDead();
        return simulation;
    }

    @Test
    public void testHundredRandomCarsRunWithoutErrorAndMostDieQuickly() {
        Simulation simulation = run(7);

        assertTrue(simulation.isFinished());
        assertEquals(0, simulation.getAliveCount());

        long quickDeaths = simulation.getResults().stream().filter(result -> result.timeAlive() < 10.0).count();
        assertTrue(quickDeaths > 50, "Most random cars should die within 10 s, got " + quickDeaths);
    }

    @Test
    public void testSameSeedGivesSameResult() {
        List<AgentResult> first = run(7).getResults();
        List<AgentResult> second = run(7).getResults();

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).progress(), second.get(i).progress(), 0.0);
            assertEquals(first.get(i).distance(), second.get(i).distance(), 0.0);
            assertEquals(first.get(i).timeAlive(), second.get(i).timeAlive(), 0.0);
            assertEquals(first.get(i).deathCause(), second.get(i).deathCause());
        }
    }

    @Test
    public void testCarsDoNotInfluenceEachOther() {
        List<NeuralNetwork> networks = randomNetworks(3, 20);
        Simulation group = new Simulation(Tracks.oval(), networks, RaySensor.standard(), false, SimulationSettings.defaults());
        group.runUntilAllDead();

        Simulation alone = new Simulation(Tracks.oval(), List.of(networks.get(5)), RaySensor.standard(), false,
                SimulationSettings.defaults());
        alone.runUntilAllDead();

        assertEquals(group.getResults().get(5).progress(), alone.getResults().getFirst().progress(), 0.0);
        assertEquals(group.getResults().get(5).distance(), alone.getResults().getFirst().distance(), 0.0);
    }

    @Test
    public void testHandWiredFollowerCompletesLaps() {
        Simulation simulation = new Simulation(Tracks.oval(), List.of(TestDrivers.follower()), RaySensor.standard(),
                false, SimulationSettings.defaults());

        simulation.runUntilAllDead();
        AgentResult result = simulation.getResults().getFirst();

        assertEquals(DeathCause.FINISHED, result.deathCause());
        assertEquals(3, result.laps());
        assertTrue(result.progress() > 47.0);
    }

    @Test
    public void testTimeoutStopsTheCar() {
        SimulationSettings settings = SimulationSettings.defaults().withMaxTime(5.0);
        Simulation simulation = new Simulation(Tracks.oval(), List.of(TestDrivers.follower()), RaySensor.standard(),
                false, settings);

        simulation.runUntilAllDead();

        assertEquals(DeathCause.TIMEOUT, simulation.getResults().getFirst().deathCause());
        assertEquals(5.0, simulation.getResults().getFirst().timeAlive(), 1.0 / 60.0);
    }

    @Test
    public void testStepAdvancesTimeWithFixedStep() {
        Simulation simulation = new Simulation(Tracks.oval(), randomNetworks(1, 3), RaySensor.standard(), false,
                SimulationSettings.defaults());

        simulation.step();
        simulation.step();

        assertEquals(2, simulation.getStepCount());
        assertEquals(2.0 / 60.0, simulation.getTime(), 1e-12);
    }

    @Test
    public void testTrailIsBounded() {
        Simulation simulation = new Simulation(Tracks.oval(), List.of(TestDrivers.follower()), RaySensor.standard(),
                false, SimulationSettings.defaults().withTrailLength(50));

        for (int i = 0; i < 500; i++) {
            simulation.step();
        }

        assertEquals(50, simulation.getAgents().getFirst().getTrail().size());
    }
}
