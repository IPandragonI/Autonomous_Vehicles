package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.FitnessFunction;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.CarAgent;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The extra information of the Car, Progress and Brain tabs, and the lap records the interface announces.
 */
public class RaceInfoTest {

    /** The oval: random networks lap it within a few generations (see CLAUDE.md), so records are guaranteed. */
    private static SimulationConfig oval() {
        return SimulationConfig.defaults().toBuilder()
                .populationSize(40)
                .eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(40).withTrailLength(30))
                .seed(5)
                .build();
    }

    @Test
    public void testLapRecordLabel() {
        LapRecord first = new LapRecord(17.42, Double.NaN, 3, 12);
        LapRecord better = new LapRecord(17.11, 17.42, 7, -1);

        assertTrue(first.isFirstLap());
        assertEquals("0:17.42 · generation 3 · car #12", first.label());
        assertEquals(0.31, better.gain(), 1e-12);
        assertEquals("0:17.11 · −0.31 s · generation 7", better.label());
        assertThrows(IllegalArgumentException.class, () -> new LapRecord(17.5, 17.42, 7, 1),
                "A slower lap is not a record.");
    }

    @Test
    public void testRecordsAreAnnouncedOnceAndOnlyGetFaster() {
        SimulationController controller = new SimulationController(oval());
        int announced = 0;
        double previous = Double.POSITIVE_INFINITY;

        while (controller.getGeneration() < 6) {
            controller.step();
            LapRecord record = controller.takeNewLapRecord();

            if (record != null) {
                announced++;
                assertTrue(record.lapTime() < previous - SimulationController.RECORD_TOLERANCE,
                        "Every record beats the previous one by more than rounding.");
                assertEquals(record.lapTime(), controller.getLapRecord());
                assertNull(controller.takeNewLapRecord(), "A record is announced once.");
                previous = record.lapTime();
            }
        }

        List<LapRecord> records = controller.getLapRecords();
        assertTrue(announced > 0, "Cars lap the oval within 6 generations.");
        assertEquals(announced, records.size());
        assertTrue(records.getFirst().isFirstLap());
        assertTrue(records.getFirst().carId() >= 0, "Seen live, when the car crosses the line.");
        assertEquals(controller.getAlgorithm().getFastestLapTime(), controller.getLapRecord(),
                SimulationController.RECORD_TOLERANCE,
                "The live record is the lap the algorithm remembers once the generation is evaluated.");

        controller.reset();
        assertTrue(Double.isNaN(controller.getLapRecord()), "A new run starts a new record.");
        assertTrue(controller.getLapRecords().isEmpty());
    }

    @Test
    public void testReplaysSetNoRecord() {
        SimulationController controller = new SimulationController(oval());

        while (controller.getAlgorithm().getFastestLap() == null) {
            controller.step();
        }

        controller.setChampionKind(SimulationController.ChampionKind.FASTEST_LAP);
        controller.takeNewLapRecord();
        int records = controller.getLapRecords().size();
        controller.startReplay(controller.getCurrentChampion());

        for (int i = 0; i < 3000; i++) {
            controller.step();
        }

        assertNull(controller.takeNewLapRecord());
        assertEquals(records, controller.getLapRecords().size());
    }

    @Test
    public void testDeathCountsAddUpToThePopulation() {
        SimulationController controller = new SimulationController(oval());

        for (int i = 0; i < 400; i++) {
            controller.step();
        }

        Map<DeathCause, Integer> counts = controller.getDeathCounts();
        assertEquals(40, counts.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(controller.getAliveCount(), counts.get(DeathCause.ALIVE));
    }

    @Test
    public void testCarDetailsRankAndScoreMatchBruteForce() {
        SimulationController controller = new SimulationController(oval());

        for (int i = 0; i < 300; i++) {
            controller.step();
        }

        List<CarAgent> agents = controller.getDisplayedSimulation().getAgents();
        FitnessFunction fitness = oval().createFitness();

        for (CarAgent agent : agents) {
            CarDetails details = CarDetails.of(agent, agents, fitness, Double.NaN);
            long ahead = agents.stream().filter(other -> other.getProgress() > agent.getProgress()).count();

            assertEquals(ahead + 1, details.rank());
            assertEquals(40, details.carCount());
            assertEquals(fitness.evaluate(agent.result()), details.fitness());
            assertEquals(agent.getProgress(), details.progress());
            assertTrue(Double.isNaN(details.gapToRecord()), "No record, no gap.");
        }
    }

    @Test
    public void testCarDetailsGapToRecord() {
        SimulationController controller = new SimulationController(oval());

        while (controller.getAlgorithm().getFastestLap() == null) {
            controller.step();
        }

        controller.setChampionKind(SimulationController.ChampionKind.FASTEST_LAP);
        int laps = controller.getAlgorithm().getFastestLap().getPrimaryResult().laps();
        controller.startReplay(controller.getCurrentChampion());

        // The record is often a flying lap (2nd, 3rd…): the first one starts standing.
        while (controller.getFocusedAgent().getLapTracker().getLaps() < laps) {
            controller.step();
        }

        CarDetails details = controller.getFocusedDetails();
        assertEquals(0.0, details.gapToRecord(), SimulationController.RECORD_TOLERANCE,
                "The replayed record holder drives the record lap again.");
        assertEquals(laps, details.laps());
        assertEquals(controller.getLapRecord(), details.bestLapTime(), SimulationController.RECORD_TOLERANCE);
    }

    @Test
    public void testBrainSummaryByHand() {
        // 2 inputs → 2 hidden → 2 outputs; weights[layer][neuron][input].
        double[][][] weights = {{{1, -2}, {3, 0}}, {{0, 0}, {0, 0}}};
        double[][] activations = {{0.5, -0.25}, {0.99, 0.1}, {0.35, -0.8}};
        NetworkSnapshot snapshot = new NetworkSnapshot(new int[]{2, 2, 2}, activations, weights,
                new double[][]{{0, 0}, {0, 0}});

        BrainSummary brain = BrainSummary.of(snapshot, List.of("front"));

        assertEquals("2-2-2", brain.architecture());
        assertEquals(12, brain.parameterCount(), "2×(2+1) + 2×(2+1)");
        // front: 0.5 × (1 + 3) = 2; in1: 0.25 × (2 + 0) = 0.5 → 80 % / 20 %
        assertEquals("front", brain.influences().getFirst().label());
        assertEquals(0.8, brain.influences().getFirst().share(), 1e-12);
        assertEquals("in1", brain.influences().get(1).label());
        assertEquals(0.2, brain.influences().get(1).share(), 1e-12);
        assertEquals(0.5, brain.saturated(), "0.99 is saturated, 0.1 is not.");
        assertEquals("turning left 35 % · braking 80 %", brain.decision());
    }

    @Test
    public void testBrainDecisionClampsLikeTheCar() {
        double[][][] weights = {{{1}}, {{1}, {1}}};
        double[][] activations = {{0.0}, {0.0}, {-3.0, 0.01}};
        BrainSummary brain = BrainSummary.of(new NetworkSnapshot(new int[]{1, 1, 2}, activations, weights,
                new double[][]{{0}, {0, 0}}), List.of());

        assertEquals(-1.0, brain.steer());
        assertEquals("turning right 100 % · coasting", brain.decision());
        assertEquals(0.0, brain.influences().getFirst().share(), "A zero input drives nothing.");
    }

    @Test
    public void testProgressTabTexts() {
        Map<DeathCause, Integer> counts = new EnumMap<>(DeathCause.class);
        counts.put(DeathCause.ALIVE, 12);
        counts.put(DeathCause.CRASHED, 25);
        counts.put(DeathCause.OUT_OF_FUEL, 3);
        counts.put(DeathCause.STAGNATED, 0);

        assertEquals("12 driving · 25 crashed · 3 out of fuel", StatsPanel.liveLine(counts));
        assertEquals("No lap completed yet.", StatsPanel.recordLines(List.of()));
        assertEquals("0:17.11  gen 7    −0.31 s\n0:17.42  gen 3    first lap", StatsPanel.recordLines(List.of(
                new LapRecord(17.42, Double.NaN, 3, 1), new LapRecord(17.11, 17.42, 7, 2))));
    }
}
