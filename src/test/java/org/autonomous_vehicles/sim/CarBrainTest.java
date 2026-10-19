package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CarBrainTest {

    @Test
    public void testOutputsAreAlwaysBounded() {
        Track track = Tracks.oval();
        Rng rng = new Rng(11);

        for (int i = 0; i < 200; i++) {
            NeuralNetwork network = new NeuralNetwork(rng, 5, 6, 2);
            double[] genome = network.getGenome();

            for (int g = 0; g < genome.length; g++) {
                genome[g] *= 50.0;
            }

            network.setGenome(genome);
            CarControls controls = new CarBrain(network, RaySensor.standard(), false).decide(Car.atStart(track), track);

            assertTrue(controls.steer() >= -1.0 && controls.steer() <= 1.0);
            assertTrue(controls.throttle() >= -1.0 && controls.throttle() <= 1.0);
        }
    }

    @Test
    public void testInputsAreInUnitInterval() {
        Track track = Tracks.oval();
        CarBrain brain = new CarBrain(new NeuralNetwork(new Rng(1), 6, 6, 2), RaySensor.standard(), true);

        for (double speed : new double[]{-CarPhysics.MAX_SPEED, -3.0, 0.0, 7.5, CarPhysics.MAX_SPEED}) {
            double[] inputs = brain.inputs(new Car(0, -45, 0.3, speed), track);

            assertEquals(6, inputs.length);

            for (double input : inputs) {
                assertTrue(input >= 0.0 && input <= 1.0, "Input out of [0, 1]: " + input);
            }
        }

        assertEquals(0.5, brain.inputs(new Car(0, -45, 0, 0), track)[5], 1e-12, "Stopped car = 0.5.");
    }

    @Test
    public void testZeroNetworkGivesNeutralControlsAndTheCarStaysStill() {
        Track track = Tracks.oval();
        CarBrain brain = new CarBrain(TestDrivers.zeroNetwork(), RaySensor.standard(), false);
        Car car = Car.atStart(track);

        CarControls controls = brain.decide(car, track);

        assertEquals(0.0, controls.steer(), 0.0);
        assertEquals(0.0, controls.throttle(), 0.0);

        for (int i = 0; i < 120; i++) {
            controls = brain.decide(car, track);
            CarPhysics.step(car, controls.steer(), controls.throttle(), 1.0 / 60.0);
        }

        assertEquals(track.getStartPosition(), car.getPosition());
    }

    @Test
    public void testZeroNetworkIsStoppedForStagnation() {
        Simulation simulation = new Simulation(Tracks.oval(), List.of(TestDrivers.zeroNetwork()), RaySensor.standard(),
                false, SimulationSettings.defaults());

        simulation.runUntilAllDead();

        assertEquals(DeathCause.STAGNATED, simulation.getAgents().getFirst().getDeathCause());
        assertEquals(0.0, simulation.getResults().getFirst().progress(), 0.0);
    }

    @Test
    public void testNetworkMustMatchTheSensors() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarBrain(new NeuralNetwork(new Rng(1), 4, 6, 2), RaySensor.standard(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new CarBrain(new NeuralNetwork(new Rng(1), 5, 6, 3), RaySensor.standard(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new CarBrain(new NeuralNetwork(new Rng(1), 5, 6, 2), RaySensor.standard(), true));
    }

    @Test
    public void testLastInputsAndOutputsAreExposed() {
        Track track = Tracks.oval();
        NeuralNetwork network = new NeuralNetwork(new Rng(2), 5, 6, 2);
        CarBrain brain = new CarBrain(network, RaySensor.standard(), false);

        CarControls controls = brain.decide(Car.atStart(track), track);

        assertArrayEquals(RaySensor.standard().sense(Car.atStart(track), track), brain.getLastInputs(), 0.0);
        assertEquals(controls.steer(), brain.getLastOutputs()[0], 0.0);
        assertEquals(controls.throttle(), brain.getLastOutputs()[1], 0.0);
    }
}
