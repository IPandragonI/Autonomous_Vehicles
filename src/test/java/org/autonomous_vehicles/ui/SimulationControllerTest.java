package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.CarAgent;
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
