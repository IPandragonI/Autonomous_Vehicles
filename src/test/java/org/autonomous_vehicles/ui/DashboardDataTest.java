package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.CompositeFitness;
import org.autonomous_vehicles.evolution.SelectionType;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.Car;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.CarControls;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.DynamicModel;
import org.autonomous_vehicles.sim.KinematicModel;
import org.autonomous_vehicles.sim.LapTracker;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.sim.Vehicles;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardDataTest {

    @Test
    public void testTelemetryOfADynamicCar() {
        DynamicModel model = (DynamicModel) Vehicles.create("hatchback", TireCompound.SOFT);
        Car car = Car.atStart(Tracks.oval());
        model.prepare(car);
        LapTracker tracker = new LapTracker(Tracks.oval());

        for (int i = 0; i < 180; i++) {
            model.step(car, new CarControls(0, 1), 1.0 / 60.0);
        }

        Telemetry telemetry = Telemetry.of(car, new CarControls(-0.4, 1), tracker, model, DeathCause.ALIVE);

        assertTrue(telemetry.hasDrivetrain());
        assertEquals(car.getSpeed() * 3.6, telemetry.speedKmh(), 1e-9);
        assertEquals(car.getGear(), telemetry.gear());
        assertEquals(5, telemetry.gearCount());
        assertEquals(car.getEngineRpm(), telemetry.rpm(), 1e-9);
        assertEquals(car.getEngineRpm() / 6500, telemetry.rpmFraction(), 1e-9);
        assertEquals(1.0, telemetry.throttle(), 0.0);
        assertEquals(0.0, telemetry.brake(), 0.0);
        assertEquals(-0.4, telemetry.steer(), 0.0);
        assertEquals(TireCompound.SOFT, telemetry.tire());
        assertEquals(1, telemetry.lap());
        assertEquals(String.valueOf(car.getGear()), telemetry.gearLabel());
    }

    @Test
    public void testTelemetryOfTheKinematicCarAndOfAStoppedCar() {
        Car car = Car.atStart(Tracks.oval());
        LapTracker tracker = new LapTracker(Tracks.oval());

        Telemetry simple = Telemetry.of(car, new CarControls(0, -0.5), tracker, KinematicModel.INSTANCE, DeathCause.ALIVE);

        assertFalse(simple.hasDrivetrain());
        assertEquals("–", simple.gearLabel());
        assertNull(simple.tire());
        assertEquals(0.5, simple.brake(), 0.0);

        DynamicModel model = (DynamicModel) Vehicles.create("kart", TireCompound.MEDIUM);
        Car crashed = Car.atStart(Tracks.oval());
        model.prepare(crashed);
        crashed.setEngineRpm(9000);
        crashed.crash();

        Telemetry stopped = Telemetry.of(crashed, new CarControls(1, 1), tracker, model, DeathCause.CRASHED);

        assertEquals(0.0, stopped.rpm(), 0.0, "A stopped car shows no engine speed.");
        assertEquals(0.0, stopped.throttle(), 0.0);
        assertFalse(stopped.alive());
        assertEquals(DeathCause.CRASHED, stopped.deathCause());
    }

    @Test
    public void testControllerExposesTheFocusedCarTelemetry() {
        SimulationConfig config = SimulationConfig.defaults().toBuilder().populationSize(10).eliteCount(1)
                .carType("sports").useSpeedInput(true).build();
        SimulationController controller = new SimulationController(config);
        controller.step();

        Telemetry telemetry = controller.getFocusedTelemetry();

        assertNotNull(telemetry);
        assertTrue(telemetry.hasDrivetrain());
        assertEquals(controller.getFocusedAgent().getCar().getGear(), telemetry.gear());
        assertFalse(controller.hasSelection());
    }

    @Test
    public void testQuickSetupAppliesTheMeasuredRecommendations() {
        SimulationConfig base = SimulationConfig.defaults();

        SimulationConfig hatchback = QuickSetup.apply(base, List.of("hairpin"), "hatchback", TireCompound.HARD);

        assertEquals("hatchback", hatchback.carType());
        assertEquals(TireCompound.HARD, hatchback.tire());
        assertTrue(hatchback.useSpeedInput(), "Real cars need to know their speed to brake.");
        assertEquals(SimulationSettings.recommendedMaxTime(Tracks.byName("hairpin"), hatchback.createVehicle()),
                hatchback.settings().maxTime(), 0.0);
        assertArrayEquals(base.hiddenLayers(), hatchback.hiddenLayers(), "The network size is kept.");
        assertEquals(QuickSetup.FAST_CAR_RAYS, hatchback.rayCount());
        assertEquals(SimulationConfig.RayLayout.FORWARD, hatchback.rayLayout());
        assertEquals(base.rayRange(), hatchback.rayRange(), 0.0, "200 m rays measured worse: the range is kept");
        assertFalse(hatchback.useTrackPreview() || hatchback.gripSteering(), "opt-in");

        SimulationConfig back = QuickSetup.apply(hatchback, List.of("oval"), Vehicles.KINEMATIC, TireCompound.HARD);

        assertFalse(back.useSpeedInput());
        assertEquals(5, back.rayCount(), "the assignment's sensors");
        assertEquals(SimulationConfig.RayLayout.EVEN, back.rayLayout());
        assertEquals(60.0, back.settings().maxTime(), 0.0);
        assertTrue(QuickSetup.describe(hatchback).contains("hairpin"));
    }

    @Test
    public void testRecommendedSettingsAreTheFastestLearningOnesAndKeepTrackCarAndSeed() {
        SimulationConfig tweaked = QuickSetup.apply(SimulationConfig.defaults(), List.of("SaoPaulo"), "formula",
                TireCompound.SOFT).toBuilder().populationSize(37).hiddenLayers(12, 8).mutationRate(0.3)
                .selection(SelectionType.RANDOM).eliteCount(0).crossover(SimulationConfig.Crossover.UNIFORM)
                .useTrackPreview(true).gripSteering(true).rayRange(200).seed(99).build();

        SimulationConfig recommended = QuickSetup.recommended(tweaked);

        assertEquals(List.of("SaoPaulo"), recommended.tracks());
        assertEquals("formula", recommended.carType());
        assertEquals(TireCompound.SOFT, recommended.tire());
        assertEquals(99, recommended.seed());
        assertEquals(100, recommended.populationSize());
        assertArrayEquals(new int[]{6}, recommended.hiddenLayers());
        assertEquals(0.08, recommended.mutationRate(), 0.0);
        assertEquals(0.25, recommended.mutationStrength(), 0.0);
        assertEquals(SelectionType.TRUNCATION, recommended.selection());
        assertEquals(0.10, recommended.truncationFraction(), 0.0);
        assertEquals(5, recommended.eliteCount());
        assertEquals(SimulationConfig.Crossover.NONE, recommended.crossover());
        assertEquals(CompositeFitness.defaults(), recommended.compositeFitness());
        assertFalse(recommended.rules().limitedFuel(), "with 25 s no car finishes a São Paulo lap");
        assertEquals(RaceRules.DEFAULT_TYRE_WEAR, recommended.rules().tyreWear(), 0.0);
        assertFalse(recommended.useTrackPreview() || recommended.gripSteering());
        assertEquals(QuickSetup.FAST_CAR_RAYS, recommended.rayCount());
        assertEquals(80.0, recommended.rayRange(), 0.0);
        assertEquals(tweaked.settings().maxTime(), recommended.settings().maxTime(), 0.0);
        assertEquals(recommended, QuickSetup.recommended(recommended), "applying it twice changes nothing");
    }

    @Test
    public void testEveryCarTypeHasAnAppearance() {
        for (String type : Vehicles.TYPES) {
            CarAppearance appearance = CarAppearance.forType(type);

            assertTrue(appearance.length() > appearance.width(), type);
            assertTrue(appearance.length() >= 1.5 && appearance.length() <= 6.0, type);
        }

        assertEquals(CarAppearance.Style.OPEN_WHEEL, CarAppearance.forType("formula").style());
        assertEquals(CarAppearance.forType(Vehicles.KINEMATIC), CarAppearance.forType("unknown"));
    }

    @Test
    public void testHiddenLayerParsing() {
        assertArrayEquals(new int[]{12, 8}, ControlPanel.parseHiddenLayers("12-8"));
        assertArrayEquals(new int[]{6}, ControlPanel.parseHiddenLayers(" 6 "));
        assertArrayEquals(new int[0], ControlPanel.parseHiddenLayers(""));
        assertThrows(IllegalArgumentException.class, () -> ControlPanel.parseHiddenLayers("12-x"));
    }

    @Test
    public void testLapTimeFormat() {
        assertEquals("1:05.25", Dashboard.lapTime(65.25));
        assertEquals("0:09.50", Dashboard.lapTime(9.5));
    }
}
