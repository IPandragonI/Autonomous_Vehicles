package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.Champion;
import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.CarAgent;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationControllerTest {

    private static SimulationConfig config() {
        return SimulationConfig.defaults().toBuilder()
                .populationSize(20)
                .eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(10).withTrailLength(30))
                .build();
    }

    private static void finishGeneration(SimulationController controller) {
        int generation = controller.getGeneration();

        while (controller.getGeneration() == generation) {
            controller.step();
        }
    }

    /** Steps the displayed simulation advanced during {@code action}, checking no generation ended meanwhile. */
    private static long stepsDuring(SimulationController controller, Runnable action) {
        int generation = controller.getGeneration();
        long before = controller.getDisplayedSimulation().getStepCount();
        action.run();
        assertEquals(generation, controller.getGeneration(), "The count is only meaningful within a generation.");
        return controller.getDisplayedSimulation().getStepCount() - before;
    }

    @Test
    public void testRealTimeTickSimulatesElapsedTimesSpeed() {
        SimulationController controller = new SimulationController(config(), () -> 0L);
        controller.play();

        assertEquals(1, stepsDuring(controller, () -> controller.tick(1.0 / 60.0)), "×1 = real time.");

        controller.setSpeed(10);
        assertEquals(40, stepsDuring(controller, () -> controller.tick(1.0 / 15.0)));
        assertEquals(10.0, controller.getEffectiveSpeed(), 1e-9);
    }

    @Test
    public void testFractionsOfAStepAreCarriedToTheNextImage() {
        SimulationController controller = new SimulationController(config(), () -> 0L);
        controller.play();

        assertEquals(0, stepsDuring(controller, () -> controller.tick(1.0 / 120.0)));
        assertEquals(1, stepsDuring(controller, () -> controller.tick(1.0 / 120.0)));
    }

    @Test
    public void testFrameBudgetStopsTheWorkAndDropsTheBacklog() {
        long[] now = {0};
        // Every clock reading is 6 ms later: the budget (10 ms) is spent after the second batch.
        SimulationController controller = new SimulationController(config(), () -> now[0] += 6_000_000L);
        controller.setSpeed(100);
        controller.play();

        assertEquals(2 * SimulationController.BATCH_STEPS, stepsDuring(controller, () -> controller.tick(0.1)));
        assertEquals(0, stepsDuring(controller, () -> controller.tick(0.0)), "The unsimulated time is dropped.");
        assertTrue(controller.getEffectiveSpeed() < 100, "The reached speed is reported: " + controller.getEffectiveSpeed());
    }

    @Test
    public void testLongPausesAreNotCaughtUpAndPausedTicksDoNothing() {
        SimulationController controller = new SimulationController(config(), () -> 0L);

        assertEquals(0, stepsDuring(controller, () -> controller.tick(1.0)));
        assertTrue(Double.isNaN(controller.getEffectiveSpeed()));

        controller.play();
        long steps = stepsDuring(controller, () -> controller.tick(5.0));
        assertEquals(Math.round(SimulationController.MAX_FRAME_GAP * 60), steps);
        assertThrows(IllegalArgumentException.class, () -> controller.tick(-1));
        assertThrows(IllegalArgumentException.class, () -> controller.tick(Double.NaN));
    }

    @Test
    public void testFastTicksBreedGenerationsLikeSingleSteps() {
        SimulationController fast = new SimulationController(config(), () -> 0L);
        SimulationController slow = new SimulationController(config());
        fast.setSpeed(1000);
        fast.play();

        fast.tick(SimulationController.MAX_FRAME_GAP);

        assertTrue(fast.getGeneration() >= 1, "15 000 steps cover several 10 s generations.");

        while (slow.getGeneration() < fast.getGeneration()) {
            slow.step();
        }

        assertEquals(slow.getHistory().last().bestFitness(), fast.getHistory().last().bestFitness(),
                "Batching never changes the results.");
    }

    @Test
    public void testRetrainingBreedsFromTheChampionAndKeepsItAcrossRestarts() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        Champion champion = Champion.of(controller.getAlgorithm());
        SimulationConfig other = config().toBuilder().seed(99).tracks("chicane").build();
        controller.applyConfig(other);

        controller.retrainFrom(champion);

        assertSame(champion, controller.getSeed());
        assertEquals(champion.config(), controller.getConfig(), "The champion's configuration is restored.");
        assertEquals(0, controller.getGeneration());
        assertArrayEquals(champion.network().getGenome(),
                controller.getDisplayedSimulation().getAgents().getFirst().getBrain().getNetwork().getGenome(),
                "Car 0 drives the champion.");

        controller.reset(); // Restart button
        assertArrayEquals(champion.network().getGenome(),
                controller.getDisplayedSimulation().getAgents().getFirst().getBrain().getNetwork().getGenome());

        controller.applyConfig(controller.getConfig().toBuilder().tracks("hairpin").build()); // same network shape
        assertSame(champion, controller.getSeed(), "Another track keeps the seed.");
        assertNull(controller.takeDroppedSeed());
    }

    @Test
    public void testNewInputsWidenTheSeedButOtherHiddenLayersDropItOnce() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        Champion champion = Champion.of(controller.getAlgorithm());
        controller.retrainFrom(champion);

        controller.applyConfig(controller.getConfig().toBuilder().useSpeedInput(true).useCarStateInputs(true).build());

        assertSame(champion, controller.getSeed(), "Speed and car state inputs are added with zero weights.");
        NeuralNetwork car0 = controller.getDisplayedSimulation().getAgents().getFirst().getBrain().getNetwork();
        assertEquals(5 + 1 + 4, car0.getArchitecture()[0]);

        controller.applyConfig(controller.getConfig().toBuilder().hiddenLayers(8).build());

        assertNull(controller.getSeed(), "Other hidden layers: the champion's network does not fit.");
        assertSame(champion, controller.takeDroppedSeed());
        assertNull(controller.takeDroppedSeed(), "Reported once.");
    }

    @Test
    public void testFailedRetrainingLeavesTheRunUntouchedAndSeedCanBeCleared() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        Champion champion = Champion.of(controller.getAlgorithm());
        Champion elsewhere = new Champion(champion.config().toBuilder().tracks("not-imported").build(),
                champion.network(), champion.generation(), champion.fitness());
        SimulationConfig before = controller.getConfig();
        int generation = controller.getGeneration();

        assertThrows(IllegalArgumentException.class, () -> controller.retrainFrom(elsewhere));
        assertEquals(before, controller.getConfig());
        assertEquals(generation, controller.getGeneration());
        assertNull(controller.getSeed());

        controller.retrainFrom(champion);
        controller.clearSeed();
        assertNull(controller.getSeed());
        assertEquals(0, controller.getGeneration());
    }

    @Test
    public void testCurrentChampionFollowsTheChosenCriterion() {
        SimulationController controller = new SimulationController(config().toBuilder().populationSize(40)
                .settings(SimulationSettings.defaults().withMaxTime(40)).build());

        assertNull(controller.getCurrentChampion(), "Nothing evaluated yet.");

        while (controller.getAlgorithm().getFastestLap() == null && controller.getGeneration() < 15) {
            finishGeneration(controller);
        }

        assertNotNull(controller.getAlgorithm().getFastestLap(), "Cars lap the oval within 15 generations.");
        assertArrayEquals(controller.getAlgorithm().getChampion().getGenome(),
                controller.getCurrentChampion().network().getGenome(), "Best score by default.");

        controller.setChampionKind(SimulationController.ChampionKind.FASTEST_LAP);

        assertArrayEquals(controller.getAlgorithm().getFastestLap().getGenome(),
                controller.getCurrentChampion().network().getGenome());

        Champion fastest = controller.getCurrentChampion();
        controller.retrainFrom(fastest);
        assertArrayEquals(fastest.network().getGenome(),
                controller.getDisplayedSimulation().getAgents().getFirst().getBrain().getNetwork().getGenome(),
                "Retraining from the fastest car puts it in car 0.");
        assertThrows(IllegalArgumentException.class, () -> controller.setChampionKind(null));
    }

    @Test
    public void testFocusedInputLabelsNameEveryNetworkInput() {
        SimulationController controller = new SimulationController(config().toBuilder().useCarStateInputs(true).build());

        assertEquals(List.of("R 90°", "R 45°", "0°", "L 45°", "L 90°", "fuel", "tyre °C", "wear", "slide"),
                controller.getFocusedInputLabels());
    }

    @Test
    public void testReplayShowsTheChampionAloneThenTrainingResumesUntouched() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        controller.step();
        Simulation training = controller.getDisplayedSimulation();
        Champion champion = Champion.of(controller.getAlgorithm());

        controller.startReplay(champion);

        assertTrue(controller.isReplaying());
        assertSame(champion, controller.getReplay());
        assertEquals(1, controller.getDisplayedSimulation().getAgents().size());
        assertEquals(0, controller.getDisplayedSimulation().getStepCount());

        controller.stopReplay();

        assertFalse(controller.isReplaying());
        assertSame(training, controller.getDisplayedSimulation());
        assertEquals(1, controller.getDisplayedSimulation().getStepCount());
        assertEquals(1, controller.getGeneration());
    }

    @Test
    public void testReplayLoopsWithoutBreedingAndMatchesTheEvaluation() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        Champion champion = Champion.of(controller.getAlgorithm());
        controller.startReplay(champion);
        Simulation first = controller.getDisplayedSimulation();

        while (controller.getDisplayedSimulation() == first) {
            controller.step();
        }

        assertTrue(first.isFinished());
        assertEquals(controller.getAlgorithm().getChampion().getPrimaryResult().distance(),
                first.getAgents().getFirst().result().distance());
        assertEquals(1, controller.getGeneration(), "A replay never breeds a generation.");
        assertEquals(0, controller.getDisplayedSimulation().getStepCount(), "The replay starts again.");
    }

    @Test
    public void testResetEndsTheReplay() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);
        controller.startReplay(Champion.of(controller.getAlgorithm()));

        controller.reset();

        assertFalse(controller.isReplaying());
        assertEquals(config().populationSize(), controller.getDisplayedSimulation().getAgents().size());
    }

    @Test
    public void testPauseFreezesTheState() {
        SimulationController controller = new SimulationController(config());
        controller.play();
        controller.tick();
        controller.pause();
        long steps = controller.getDisplayedSimulation().getStepCount();
        Vec2 position = controller.getDisplayedSimulation().getAgents().getFirst().getCar().getPosition();

        for (int i = 0; i < 10; i++) {
            controller.tick();
        }

        assertEquals(steps, controller.getDisplayedSimulation().getStepCount());
        assertEquals(position, controller.getDisplayedSimulation().getAgents().getFirst().getCar().getPosition());
    }

    @Test
    public void testSpeedIsStepsPerTick() {
        SimulationController controller = new SimulationController(config());
        controller.setSpeed(10);
        controller.play();

        controller.tick();

        assertEquals(10, controller.getDisplayedSimulation().getStepCount());
        assertThrows(IllegalArgumentException.class, () -> controller.setSpeed(0));
    }

    @Test
    public void testGenerationEndsWhenAllCarsStopAndListenersAreNotified() {
        SimulationController controller = new SimulationController(config());
        List<GenerationStats> received = new ArrayList<>();
        controller.addGenerationListener(received::add);

        finishGeneration(controller);

        assertEquals(1, controller.getGeneration());
        assertEquals(1, received.size());
        assertEquals(0, received.getFirst().generation());
        assertEquals(0, controller.getDisplayedSimulation().getStepCount(), "A fresh simulation starts.");
    }

    @Test
    public void testResetRecreatesGenerationZero() {
        SimulationController controller = new SimulationController(config());
        double[] firstGenome = controller.getAlgorithm().getPopulation().get(0).getGenome();
        finishGeneration(controller);

        controller.reset();

        assertEquals(0, controller.getGeneration());
        assertEquals(0, controller.getHistory().size());
        assertEquals(0, controller.getDisplayedSimulation().getStepCount());
        assertArrayEquals(firstGenome, controller.getAlgorithm().getPopulation().get(0).getGenome(), 0.0,
                "Same seed: the same initial population.");
    }

    @Test
    public void testHotConfigKeepsTheRunAndResetConfigRestartsIt() {
        SimulationController controller = new SimulationController(config());
        finishGeneration(controller);

        assertFalse(controller.applyConfig(controller.getConfig().toBuilder().mutationRate(0.2).build()));
        assertEquals(1, controller.getGeneration());

        assertTrue(controller.applyConfig(controller.getConfig().toBuilder().hiddenLayers(12, 8).build()));
        assertEquals(0, controller.getGeneration());
        assertArrayEquals(new int[]{5, 12, 8, 2}, controller.getAlgorithm().getPopulation().getArchitecture());
    }

    @Test
    public void testDisplayTogglesHideLayers() {
        SimulationController controller = new SimulationController(config());
        controller.step();

        SimulationFrame shown = controller.frame();
        assertNotNull(shown.rayOrigin());
        assertEquals(5, shown.rayEnds().size());
        assertEquals(20, shown.trails().size());
        assertTrue(shown.nextCheckpoint() >= 0);

        RenderOptions options = controller.getRenderOptions();
        options.setShowRays(false);
        options.setShowTrails(false);
        options.setShowCheckpoints(false);
        SimulationFrame hidden = controller.frame();

        assertNull(hidden.rayOrigin());
        assertTrue(hidden.rayEnds().isEmpty());
        assertTrue(hidden.trails().isEmpty());
        assertEquals(-1, hidden.nextCheckpoint());
    }

    @Test
    public void testDeadCarsCanBeHidden() {
        SimulationController controller = new SimulationController(config());

        for (int i = 0; i < 600 && controller.getAliveCount() == 20; i++) {
            controller.step();
        }

        int alive = controller.getAliveCount();
        assertTrue(alive < 20 && alive > 0, "Some random cars stop within 10 s, not all of them at once.");
        controller.getRenderOptions().setShowDeadCars(false);

        assertEquals(alive, controller.frame().cars().size());
        assertTrue(controller.frame().cars().stream().allMatch(SimulationFrame.CarSprite::alive));
    }

    @Test
    public void testTrailHistoryIsBounded() {
        SimulationController controller = new SimulationController(config());

        for (int i = 0; i < 200; i++) {
            controller.step();
        }

        for (List<Vec2> trail : controller.frame().trails()) {
            assertTrue(trail.size() <= 30);
        }
    }

    @Test
    public void testClickSelectsTheNearestCar() {
        SimulationController controller = new SimulationController(config());
        controller.step();
        CarAgent target = controller.getDisplayedSimulation().getAgents().get(7);

        controller.selectCarAt(target.getCar().getPosition());

        assertTrue(target.getCar().getPosition().distanceTo(controller.getFocusedAgent().getCar().getPosition()) < 1e-9);

        controller.selectCarAt(new Vec2(1000, 1000));
        assertNotNull(controller.getFocusedAgent(), "Without selection the best car is inspected.");
    }

    @Test
    public void testNetworkViewShowsTheFocusedCarActivations() {
        SimulationController controller = new SimulationController(config());
        controller.step();
        CarAgent focused = controller.getFocusedAgent();

        NetworkSnapshot snapshot = controller.getFocusedNetwork();

        assertArrayEquals(focused.getBrain().getLastInputs(), snapshot.activations()[0], 0.0);
        assertArrayEquals(focused.getBrain().getLastOutputs(), snapshot.outputs(), 0.0);
    }

    @Test
    public void testSnapshotMatchesAHandComputedNetwork() {
        NeuralNetwork network = new NeuralNetwork(new Rng(1), 2, 2, 1);
        // Hidden: h0 = tanh(1*x0 + 0*x1 + 0), h1 = tanh(0*x0 - 1*x1 + 0.5); output: tanh(2*h0 + 1*h1 - 0.5)
        network.setGenome(new double[]{1, 0, 0, 0, -1, 0.5, 2, 1, -0.5});
        double[] inputs = {0.5, 0.25};

        assertNull(NetworkSnapshot.of(network, inputs), "No forward pass yet.");
        network.forward(inputs);
        NetworkSnapshot snapshot = NetworkSnapshot.of(network, inputs);

        double h0 = Math.tanh(0.5);
        double h1 = Math.tanh(-0.25 + 0.5);
        double out = Math.tanh(2 * h0 + h1 - 0.5);

        assertEquals(0.5, snapshot.activation(0, 0), 0.0);
        assertEquals(h0, snapshot.activation(1, 0), 1e-12);
        assertEquals(h1, snapshot.activation(1, 1), 1e-12);
        assertEquals(out, snapshot.activation(2, 0), 1e-12);
        assertEquals(-1.0, snapshot.weight(0, 1, 1), 0.0);
        assertEquals(2.0, snapshot.weight(1, 0, 0), 0.0);
        assertArrayEquals(network.getLastActivations()[1], snapshot.outputs(), 0.0);
    }
}
