package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The car state the network can perceive: fuel, tyre temperature, tyre wear, sliding.
 */
public class CarStateInputsTest {

    private static final DynamicModel HATCHBACK = (DynamicModel) Vehicles.create("hatchback", TireCompound.MEDIUM,
            RaceRules.defaults());

    @Test
    public void testTemperatureInputFollowsTheGripWindowWithKnownBreakpoints() {
        TireCompound medium = TireCompound.MEDIUM; // window 90–110, cold span 60, hot span 40

        assertEquals(0.25, CarBrain.temperatureInput(90, medium), 1e-12, "lower edge of the window");
        assertEquals(0.50, CarBrain.temperatureInput(100, medium), 1e-12, "middle of the window");
        assertEquals(0.75, CarBrain.temperatureInput(110, medium), 1e-12, "upper edge of the window");
        assertEquals(0.125, CarBrain.temperatureInput(60, medium), 1e-12, "30 °C cold = half the cold span");
        assertEquals(0.0, CarBrain.temperatureInput(10, medium), 1e-12, "fully cold");
        assertEquals(0.875, CarBrain.temperatureInput(130, medium), 1e-12, "20 °C hot = half the hot span");
        assertEquals(1.0, CarBrain.temperatureInput(400, medium), 1e-12, "fully overheated");
        assertEquals(0.5, CarBrain.temperatureInput(90, TireCompound.SOFT), 1e-12,
                "The same reading means the same for every compound (soft window 80–100).");
    }

    @Test
    public void testCarStateReadsFuelTemperatureWearAndSliding() {
        Car car = new Car(0, 0, 0, 10);
        HATCHBACK.prepare(car);
        car.setFuel(car.getFuel() * 0.25);
        car.setTireTemperature(100);
        car.setTireWear(0.4);
        car.setSliding(true);

        assertArrayEquals(new double[]{0.25, 0.5, 0.4, 1.0}, CarBrain.carState(car, TireCompound.MEDIUM), 1e-12);
    }

    @Test
    public void testKinematicCarReadsAFullTankOfIdealTyres() {
        Car car = new Car(0, 0, 0, 10);
        KinematicModel.INSTANCE.prepare(car);

        assertArrayEquals(new double[]{1.0, 0.5, 0.0, 0.0}, CarBrain.carState(car, null));
    }

    @Test
    public void testInputsAreRaysThenSpeedThenCarStateWithMatchingLabels() {
        RaySensor sensor = RaySensor.standard();
        NeuralNetwork network = new NeuralNetwork(new Rng(3), CarBrain.inputSize(sensor, true, true), 4, 2);
        CarBrain brain = new CarBrain(network, sensor, true, true, HATCHBACK);
        Track track = Tracks.oval();
        Car car = Car.atStart(track);
        HATCHBACK.prepare(car);

        double[] inputs = brain.inputs(car, track);

        assertEquals(5 + 1 + CarBrain.CAR_STATE_INPUTS, inputs.length);
        assertArrayEquals(sensor.sense(car, track), Arrays.copyOfRange(inputs, 0, 5));
        assertEquals(0.5, inputs[5], 1e-12, "stopped = 0.5");
        assertArrayEquals(CarBrain.carState(car, TireCompound.MEDIUM), Arrays.copyOfRange(inputs, 6, 10));
        assertEquals(List.of("R 90°", "R 45°", "0°", "L 45°", "L 90°", "speed", "fuel", "tyre °C", "wear", "slide"),
                brain.inputLabels());
    }

    @Test
    public void testWrongInputCountIsRejected() {
        RaySensor sensor = RaySensor.standard();

        assertThrows(IllegalArgumentException.class, () -> new CarBrain(new NeuralNetwork(new Rng(1), 6, 2), sensor,
                true, true, HATCHBACK), "6 inputs but rays + speed + car state = 10");
        assertThrows(IllegalArgumentException.class, () -> new CarBrain(new NeuralNetwork(new Rng(1), 10, 2), sensor,
                true, true, null));
    }

    /**
     * The point of the inputs: a network can react to them. This one has no hidden layer and only a weight from
     * the fuel input to the throttle, so it brakes (throttle < 0) when the tank is empty and accelerates when full.
     */
    @Test
    public void testANetworkCanReactToItsFuelLevel() {
        RaySensor sensor = RaySensor.standard();
        int inputs = CarBrain.inputSize(sensor, false, true);
        NeuralNetwork network = new NeuralNetwork(new Rng(1), inputs, 2);
        double[] genome = new double[network.getParameterCount()];
        // genome = per neuron its weights then its bias: the throttle neuron starts after the steer neuron
        int throttle = inputs + 1;
        genome[throttle + 5] = 4.0;       // weight of the fuel input (first car state input, after the 5 rays)
        genome[throttle + inputs] = -2.0; // bias: throttle = tanh(4 × fuel - 2)
        network.setGenome(genome);

        Simulation simulation = new Simulation(Tracks.oval(), List.of(network), sensor, false, true,
                SimulationSettings.defaults(), HATCHBACK);
        CarAgent agent = simulation.getAgents().getFirst();

        simulation.step();
        assertTrue(agent.getLastControls().throttle() > 0.9, "Full tank: tanh(2) ≈ 0.96");

        agent.getCar().setFuel(0.0);
        agent.getCar().setSpeed(5.0); // still rolling, so the dry car is not stopped yet
        simulation.step();
        assertTrue(agent.getLastControls().throttle() < -0.9, "Empty tank: tanh(-2) ≈ -0.96");
    }

    @Test
    public void testCarStateMovesDuringARealDrive() {
        RaySensor sensor = RaySensor.standard();
        Simulation simulation = new Simulation(Tracks.oval(), List.of(followerWithCarState(sensor)), sensor, false,
                true, SimulationSettings.defaults(), HATCHBACK);
        CarAgent agent = simulation.getAgents().getFirst();
        double[] start = CarBrain.carState(agent.getCar(), TireCompound.MEDIUM);

        for (int i = 0; i < 10 * 60 && agent.getCar().isAlive(); i++) {
            simulation.step();
        }

        double[] later = CarBrain.carState(agent.getCar(), TireCompound.MEDIUM);
        assertTrue(later[0] < start[0], "fuel burnt");
        assertTrue(later[2] > start[2], "tyres wore");
        assertNotEquals(start[1], later[1], "tyre temperature changed");
    }

    /** {@link TestDrivers#follower()} with zero weights on the four car state inputs. */
    private static NeuralNetwork followerWithCarState(RaySensor sensor) {
        int inputs = CarBrain.inputSize(sensor, false, true);
        NeuralNetwork network = new NeuralNetwork(new Rng(1), inputs, 2);
        double[] genome = new double[network.getParameterCount()];
        genome[1] = -5.0;                                               // steer ← right 45°
        genome[3] = 5.0;                                                // steer ← left 45°
        genome[2 * inputs + 1] = 0.5 * Math.log((1 + 0.6) / (1 - 0.6)); // throttle bias: tanh = 0.6
        network.setGenome(genome);
        return network;
    }
}
