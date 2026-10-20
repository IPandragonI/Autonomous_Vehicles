package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The car with the fastest lap of a run is remembered, whatever its generation, and usable as a champion.
 */
public class FastestLapTest {

    /** The oval: random networks lap it within a few generations (see CLAUDE.md), so laps are guaranteed. */
    private static SimulationConfig oval(long seed) {
        return SimulationConfig.defaults().toBuilder()
                .populationSize(40)
                .eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(40))
                .seed(seed)
                .build();
    }

    @Test
    public void testFastestLapIsTheBestLapEverAndNeverGetsSlower() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(oval(5));
        double previous = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            algorithm.runGeneration();
            double time = algorithm.getFastestLapTime();

            if (!Double.isNaN(time)) {
                assertTrue(time <= previous, "The remembered lap only ever gets faster.");
                previous = time;
            }
        }

        assertNotNull(algorithm.getFastestLap(), "Cars lap the oval within 8 generations.");
        assertEquals(algorithm.getHistory().bestLapTimeEver(), algorithm.getFastestLapTime());
        assertEquals(algorithm.getFastestLapTime(), algorithm.getFastestLap().getPrimaryResult().bestLapTime());
        assertTrue(algorithm.getFastestLapGeneration() >= 0 && algorithm.getFastestLapGeneration() < 8);
    }

    @Test
    public void testFastestLapChampionReplaysItsLapTime() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(oval(6));
        algorithm.run(8);
        Champion fastest = Champion.fastestLap(algorithm);

        Simulation replay = Champion.fromJson(fastest.toJson()).replay();
        replay.runUntilAllDead();

        assertEquals(algorithm.getFastestLapTime(), replay.getAgents().getFirst().result().bestLapTime(),
                "Deterministic: the saved car drives the same lap again.");
        assertEquals(algorithm.getFastestLapGeneration(), fastest.generation());
    }

    @Test
    public void testNoLapNoFastestChampion() {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(oval(7));

        assertNull(algorithm.getFastestLap());
        assertTrue(Double.isNaN(algorithm.getFastestLapTime()));
        assertThrows(IllegalStateException.class, () -> Champion.fastestLap(algorithm));
    }
}
