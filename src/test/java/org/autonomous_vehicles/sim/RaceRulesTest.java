package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuel and tyre temperature of the {@link DynamicModel}.
 */
public class RaceRulesTest {

    private static final double DT = 1.0 / 60.0;
    private static final CarSpec HATCHBACK = Vehicles.PRESETS.get("hatchback");

    private static Car prepared(DynamicModel model, double speed) {
        Car car = new Car(0, 0, 0, speed);
        model.prepare(car);
        return car;
    }

    @Test
    public void testTemperatureGripIsFullInTheWindowAndFallsLinearlyOutside() {
        TireCompound medium = TireCompound.MEDIUM; // window 90–110 °C

        assertEquals(1.0, medium.temperatureGrip(90.0));
        assertEquals(1.0, medium.temperatureGrip(110.0));
        assertEquals(1.0 - 0.25 * 30.0 / 60.0, medium.temperatureGrip(60.0), 1e-12);
        assertEquals(0.75, medium.temperatureGrip(20.0), 1e-12, "Capped at the full cold loss.");
        assertEquals(1.0 - 0.30 * 20.0 / 40.0, medium.temperatureGrip(130.0), 1e-12);
        assertEquals(0.70, medium.temperatureGrip(300.0), 1e-12, "Capped at the full overheating loss.");
        assertTrue(TireCompound.SOFT.optimalLow() < TireCompound.MEDIUM.optimalLow()
                && TireCompound.MEDIUM.optimalLow() < TireCompound.HARD.optimalLow(), "Softer = works colder.");
    }

    @Test
    public void testTyreTemperatureStepMatchesTheHandComputation() {
        // distance 2 m; heating 0.20 × 0.5 × 2 = 0.2 °C; cooling (60 - 25) × (0.006 × 0.1 + 0.0015 × 2) = 0.126 °C
        assertEquals(60.074, DynamicModel.nextTyreTemperature(60.0, 0.5, false, 20.0, 0.1), 1e-12);
        // sliding multiplies the heating by 2.5
        assertEquals(60.0 + 0.5 - 0.126, DynamicModel.nextTyreTemperature(60.0, 0.5, true, 20.0, 0.1), 1e-12);
        // at a standstill: no heating, slow cooling
        assertEquals(60.0 - 35.0 * 0.006, DynamicModel.nextTyreTemperature(60.0, 1.0, false, 0.0, 1.0), 1e-12);
    }

    @Test
    public void testTyreTemperatureSettlesAtTheTheoreticalEquilibrium() {
        // per second at 30 m/s: heating 0.20 × 0.5 × 30 = 3 °C, cooling (T - 25) × (0.006 + 0.045) → T = 25 + 3 / 0.051
        double t = DynamicModel.START_TYRE_TEMPERATURE;

        for (int i = 0; i < 600 * 60; i++) {
            t = DynamicModel.nextTyreTemperature(t, 0.5, false, 30.0, DT);
        }

        assertEquals(25.0 + 3.0 / 0.051, t, 1e-6);
    }

    @Test
    public void testRecalibratedHeatingKeepsLimitDrivingInsideTheWindowWhereTheOriginalOverheated() {
        assertEquals(RaceRules.DEFAULT_TYRE_HEATING, RaceRules.defaults().tyreHeating(), 0.0);
        assertEquals(DynamicModel.TYRE_HEATING, new RaceRules(25, true).tyreHeating(), 0.0, "old files and tests");
        assertEquals(DynamicModel.TYRE_HEATING, RaceRules.NONE.tyreHeating(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(0, true, 1.0, -0.1));

        for (double v : new double[]{20.0, 50.0, 80.0}) {
            // full grip use: T = 25 + heating × v / (0.006 + 0.0015 v)
            double recalibrated = 25.0;
            double original = 25.0;

            for (int i = 0; i < 600 * 60; i++) {
                recalibrated = DynamicModel.nextTyreTemperature(recalibrated, 1.0, false, v, DT, 0.12);
                original = DynamicModel.nextTyreTemperature(original, 1.0, false, v, DT);
            }

            assertEquals(25.0 + 0.12 * v / (0.006 + 0.0015 * v), recalibrated, 1e-6);
            assertTrue(recalibrated > 90.0 && recalibrated < 110.0, "medium window at the limit: " + recalibrated);
            assertTrue(original > 130.0, "the original rate overheated at the limit: " + original);
        }
    }

    @Test
    public void testHardCorneringWarmsTheTyresAndCruisingCoolsThem() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(0, true));
        Car cornering = prepared(model, 20);
        Car cruising = prepared(model, 20);

        assertEquals(DynamicModel.START_TYRE_TEMPERATURE, cornering.getTireTemperature());

        for (int i = 0; i < 20 * 60; i++) {
            model.step(cornering, new CarControls(0.5, 0.4), DT);
            model.step(cruising, new CarControls(0, 0.15), DT);
        }

        assertTrue(cornering.getTireTemperature() > DynamicModel.START_TYRE_TEMPERATURE + 10,
                "Cornering heats: " + cornering.getTireTemperature());
        assertTrue(cruising.getTireTemperature() < DynamicModel.START_TYRE_TEMPERATURE,
                "Cruising cools: " + cruising.getTireTemperature());
    }

    @Test
    public void testColdTyresGripLess() {
        double cold = lateralAccelerationAt(25.0);
        double warm = lateralAccelerationAt(100.0);

        // temperatureGrip(25 °C) = 1 - 0.25 × min(1, 65 / 60) = 0.75 for the medium compound
        assertEquals(0.75, cold / warm, 0.01);
    }

    /** Lateral acceleration of one step at full lock from 25 m/s, far beyond the grip: equals the grip limit. */
    private static double lateralAccelerationAt(double celsius) {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(0, true));
        Car car = prepared(model, 25);
        car.setTireTemperature(celsius);

        model.step(car, new CarControls(1, 0), DT);

        return Math.abs(car.getAngle()) / DT * car.getSpeed();
    }

    @Test
    public void testStartingFuelIsTheAutonomyAtFullPower() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(25, false));
        double fullPowerFlow = HATCHBACK.peakPower() / (0.30 * 43e6);
        Car car = prepared(model, 0);

        assertEquals(fullPowerFlow, model.fullPowerFuelFlow(), 1e-15);
        assertEquals(25 * fullPowerFlow, car.getFuel(), 1e-12);
        assertTrue(car.hasFuelLimit());
        assertEquals(1.0, car.getFuelFraction());
    }

    @Test
    public void testIdlingBurnsTheIdleFlowOnly() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(25, false));
        Car car = prepared(model, 0);
        double start = car.getFuel();

        for (int i = 0; i < 60; i++) {
            model.step(car, CarControls.neutral(), DT);
        }

        assertEquals(0.02 * model.fullPowerFuelFlow() * 60 * DT, start - car.getFuel(), 1e-12);
    }

    @Test
    public void testConsumptionGrowsWithThrottleAndNeverExceedsFullPower() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(25, false));
        double full = burntIn(model, 1.0, 10);
        double half = burntIn(model, 0.5, 10);

        assertTrue(full > 1.5 * half, full + " vs " + half);
        assertTrue(full <= 1.02 * 10 * model.fullPowerFuelFlow(), "At most full power plus idle.");
        assertTrue(full > 0.3 * 10 * model.fullPowerFuelFlow(), "Accelerating hard burns a lot: " + full);
    }

    private static double burntIn(DynamicModel model, double throttle, double seconds) {
        Car car = prepared(model, 0);
        double start = car.getFuel();

        for (int i = 0; i < seconds * 60; i++) {
            model.step(car, new CarControls(0, throttle), DT);
        }

        return start - car.getFuel();
    }

    @Test
    public void testDryCarLosesAllDriveButCanStillBrake() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(25, false));
        Car car = prepared(model, 20);
        car.setFuel(0.0);

        assertTrue(car.isOutOfFuel());

        for (int i = 0; i < 60; i++) {
            double before = car.getSpeed();
            model.step(car, new CarControls(0, 1), DT);
            assertTrue(car.getSpeed() < before, "Full throttle without fuel only slows down.");
        }

        double coasting = car.getSpeed();
        model.step(car, new CarControls(0, -1), DT);
        assertTrue(car.getSpeed() < coasting - 0.1, "Brakes still work.");
        assertEquals(0.0, car.getFuel());
    }

    @Test
    public void testFuelWeighsOnTheCar() {
        DynamicModel heavy = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(3600, false));
        DynamicModel light = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, RaceRules.NONE);
        Car loaded = prepared(heavy, 0);
        Car empty = prepared(light, 0);

        for (int i = 0; i < 300; i++) {
            heavy.step(loaded, new CarControls(0, 1), DT);
            light.step(empty, new CarControls(0, 1), DT);
        }

        assertTrue(loaded.getFuel() > 10, "An hour of fuel weighs tens of kg: " + loaded.getFuel());
        assertTrue(loaded.getSpeed() < empty.getSpeed());
    }

    @Test
    public void testDryCarThatStopsIsOutOfTheRace() {
        DynamicModel model = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(25, false));
        CarAgent dry = agent(model);
        CarAgent fuelled = agent(model);
        dry.getCar().setFuel(0.0);

        dry.step(DT);
        fuelled.step(DT);

        assertEquals(DeathCause.OUT_OF_FUEL, dry.getDeathCause());
        assertFalse(dry.getCar().isAlive());
        assertEquals(DeathCause.ALIVE, fuelled.getDeathCause());
    }

    private static CarAgent agent(VehicleModel model) {
        NeuralNetwork network = TestDrivers.follower();
        CarBrain brain = new CarBrain(network, RaySensor.standard(), false, model.topSpeed());
        return new CarAgent(0, Tracks.oval(), brain, SimulationSettings.defaults(), model);
    }

    @Test
    public void testWithoutRulesNothingIsTrackedAndTheKinematicCarIgnoresThem() {
        Car car = prepared(new DynamicModel(HATCHBACK, TireCompound.MEDIUM), 10);
        assertFalse(car.hasFuelLimit());
        assertEquals(1.0, car.getFuelFraction());
        assertEquals(DynamicModel.AMBIENT_TEMPERATURE, car.getTireTemperature());

        Simulation kinematic = new Simulation(Tracks.oval(), List.of(TestDrivers.follower()), RaySensor.standard(),
                false, SimulationSettings.defaults(), Vehicles.create(Vehicles.KINEMATIC, TireCompound.SOFT,
                RaceRules.defaults()));
        kinematic.step();
        assertFalse(kinematic.getAgents().getFirst().getCar().hasFuelLimit());
    }

    @Test
    public void testInvalidRulesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(-1, true));
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(RaceRules.MAX_FUEL_AUTONOMY + 1, false));
        assertThrows(IllegalArgumentException.class, () -> new Car(0, 0, 0, 0).fillFuel(0));
        assertFalse(RaceRules.NONE.limitedFuel());
        assertTrue(RaceRules.defaults().limitedFuel() && RaceRules.defaults().tyreTemperature());
    }

    @Test
    public void testWearMultiplierScalesTheCompoundRateAndOneKeepsTheOriginalRates() {
        assertEquals(1.0, RaceRules.NONE.tyreWear(), 0.0, "NONE = the model before the mechanics");
        assertEquals(1.0, new RaceRules(25, true).tyreWear(), 0.0);
        assertEquals(RaceRules.DEFAULT_TYRE_WEAR, RaceRules.defaults().tyreWear(), 0.0);
        assertEquals(0.3, RaceRules.defaults().withFuelAutonomy(0).withTyreWear(0.3).tyreWear(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(0, false, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new RaceRules(0, false, RaceRules.MAX_TYRE_WEAR + 1));

        DynamicModel reduced = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(0, false, 0.1));
        Car car = prepared(reduced, 20);
        reduced.step(car, new CarControls(0, 0), DT);
        // 0.15 per km × 0.1, over the distance of the step
        assertEquals(car.getSpeed() * DT / 1000.0 * 0.15 * 0.1, car.getTireWear(), 1e-15);

        DynamicModel original = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(0, false, 1.0));
        DynamicModel legacy = new DynamicModel(HATCHBACK, TireCompound.MEDIUM);
        Car a = prepared(original, 20);
        Car b = prepared(legacy, 20);

        for (int i = 0; i < 600; i++) {
            original.step(a, new CarControls(0.3, 0.4), DT);
            legacy.step(b, new CarControls(0.3, 0.4), DT);
        }

        assertEquals(b.getTireWear(), a.getTireWear(), 0.0, "×1 is bit-identical to the original rates");
        assertEquals(b.getX(), a.getX(), 0.0);

        DynamicModel noWear = new DynamicModel(HATCHBACK, TireCompound.MEDIUM, new RaceRules(0, false, 0.0));
        Car none = prepared(noWear, 20);
        noWear.step(none, new CarControls(0, 1), DT);
        assertEquals(0.0, none.getTireWear(), 0.0, "×0: no wear");
    }
}
