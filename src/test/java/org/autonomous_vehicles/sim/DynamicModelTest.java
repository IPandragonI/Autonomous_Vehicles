package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicModelTest {

    private static final double DT = 1.0 / 60.0;

    private static CarSpec hatchback() {
        return Vehicles.PRESETS.get("hatchback");
    }

    private static CarSpec withMass(CarSpec spec, double mass) {
        return new CarSpec(spec.name(), mass, spec.maxTorque(), spec.idleRpm(), spec.peakTorqueRpm(),
                spec.redlineRpm(), spec.gearRatios(), spec.finalDrive(), spec.drivetrainEfficiency(),
                spec.wheelRadius(), spec.dragArea(), spec.downforceArea(), spec.rollingResistance(), spec.wheelbase(),
                spec.maxSteeringAngle(), spec.gripFactor(), spec.maxBrakeDeceleration(), spec.drivenAxleLoadShare());
    }

    private static CarSpec withFinalDrive(CarSpec spec, double finalDrive) {
        return new CarSpec(spec.name(), spec.mass(), spec.maxTorque(), spec.idleRpm(), spec.peakTorqueRpm(),
                spec.redlineRpm(), spec.gearRatios(), finalDrive, spec.drivetrainEfficiency(),
                spec.wheelRadius(), spec.dragArea(), spec.downforceArea(), spec.rollingResistance(), spec.wheelbase(),
                spec.maxSteeringAngle(), spec.gripFactor(), spec.maxBrakeDeceleration(), spec.drivenAxleLoadShare());
    }

    private static Car prepared(DynamicModel model, double speed) {
        Car car = new Car(0, 0, 0, speed);
        model.prepare(car);
        return car;
    }

    /** Seconds of full throttle from a standstill to reach {@code target} m/s, or +∞ after two minutes. */
    private static double timeTo(DynamicModel model, double target) {
        Car car = prepared(model, 0);

        for (int step = 1; step <= 120 * 60; step++) {
            model.step(car, new CarControls(0, 1), DT);

            if (car.getSpeed() >= target) {
                return step * DT;
            }
        }

        return Double.POSITIVE_INFINITY;
    }

    private static double brakingDistance(DynamicModel model, double from) {
        Car car = prepared(model, from);

        while (car.getSpeed() > 0) {
            model.step(car, new CarControls(0, -1), DT);
        }

        return car.getX();
    }

    /** Highest lateral acceleration (speed × yaw rate) reached at full lock from 25 m/s. */
    private static double maxLateralAcceleration(DynamicModel model) {
        Car car = prepared(model, 25);
        double max = 0.0;

        for (int i = 0; i < 300; i++) {
            double before = car.getAngle();
            model.step(car, new CarControls(1, 0.3), DT);
            double yawRate = Math.atan2(Math.sin(car.getAngle() - before), Math.cos(car.getAngle() - before)) / DT;
            max = Math.max(max, Math.abs(yawRate * car.getSpeed()));
        }

        return max;
    }

    @Test
    public void testFullThrottleUsesEveryGearWithoutPassingTheRedline() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car car = prepared(model, 0);
        int previousGear = 1;

        for (int i = 0; i < 90 * 60; i++) {
            model.step(car, new CarControls(0, 1), DT);

            assertTrue(car.getEngineRpm() < hatchback().redlineRpm(), "rpm " + car.getEngineRpm());
            assertTrue(car.getGear() >= previousGear, "An automatic gearbox only shifts up while accelerating.");
            previousGear = car.getGear();
        }

        assertEquals(hatchback().gearCount(), car.getGear());
        assertEquals(model.topSpeed(), car.getSpeed(), 0.5, "Speed settles at the drag-limited top speed.");
    }

    @Test
    public void testEngineSpeedFollowsGearRatios() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        double speed = 20.0;
        double expected = speed / hatchback().wheelRadius() * hatchback().gearRatio(3) * hatchback().finalDrive()
                * 60 / (2 * Math.PI);

        assertEquals(expected, model.engineRpm(speed, 3), 1e-9);
        assertTrue(model.engineRpm(speed, 2) > model.engineRpm(speed, 3), "Lower gear = higher rpm at the same speed.");
        assertEquals(hatchback().idleRpm(), model.engineRpm(0.0, 1), 0.0, "Clutch slips at a standstill.");
        assertTrue(model.driveForce(3000, 1) > model.driveForce(3000, 2), "Lower gear = more force at the wheels.");
    }

    @Test
    public void testShorterFinalDriveTradesTopSpeedForAcceleration() {
        DynamicModel standard = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        DynamicModel shortGearing = new DynamicModel(withFinalDrive(hatchback(), 6.0), TireCompound.MEDIUM);

        assertTrue(timeTo(shortGearing, 20) < timeTo(standard, 20), "Short gearing accelerates harder.");
        assertTrue(shortGearing.topSpeed() < standard.topSpeed(), "Short gearing hits the rev limiter earlier.");
    }

    @Test
    public void testSingleGearTopSpeedIsSetByTheRedline() {
        DynamicModel kart = (DynamicModel) Vehicles.create("kart", TireCompound.MEDIUM);
        CarSpec spec = kart.getSpec();
        double redlineSpeed = spec.redlineRpm() * 2 * Math.PI / 60 / (spec.gearRatio(1) * spec.finalDrive()) * spec.wheelRadius();

        assertTrue(kart.topSpeed() <= redlineSpeed);
        assertEquals(1, spec.gearCount());
    }

    @Test
    public void testHeavierCarAcceleratesSlowerAndBrakesNoShorter() {
        DynamicModel light = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        DynamicModel heavy = new DynamicModel(withMass(hatchback(), 2400), TireCompound.MEDIUM);

        assertTrue(timeTo(heavy, 25) > 1.5 * timeTo(light, 25), "Twice the mass, same engine.");
        assertTrue(brakingDistance(heavy, 25) >= brakingDistance(light, 25) - 1e-9,
                "Grip scales with weight, so braking is grip-limited either way; drag helps the light car more.");
    }

    @Test
    public void testSofterTyresGripMoreInCornersAndUnderBraking() {
        double soft = maxLateralAcceleration(new DynamicModel(hatchback(), TireCompound.SOFT));
        double medium = maxLateralAcceleration(new DynamicModel(hatchback(), TireCompound.MEDIUM));
        double hard = maxLateralAcceleration(new DynamicModel(hatchback(), TireCompound.HARD));

        assertTrue(soft > medium && medium > hard, soft + " > " + medium + " > " + hard);

        double softBraking = brakingDistance(new DynamicModel(hatchback(), TireCompound.SOFT), 25);
        double hardBraking = brakingDistance(new DynamicModel(hatchback(), TireCompound.HARD), 25);
        assertTrue(softBraking < hardBraking, softBraking + " < " + hardBraking);
    }

    @Test
    public void testLateralAccelerationNeverExceedsTheFrictionCircle() {
        for (TireCompound tire : TireCompound.values()) {
            DynamicModel model = new DynamicModel(hatchback(), tire);
            double limit = model.frictionCoefficient(0) * DynamicModel.GRAVITY;

            assertTrue(maxLateralAcceleration(model) <= limit * 1.02, tire + ": beyond μg = " + limit);
        }
    }

    @Test
    public void testCorneringTooFastUndersteersAndSlides() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car fast = prepared(model, 30);

        model.step(fast, new CarControls(1, 0), DT);

        double commandedCurvature = Math.tan(hatchback().maxSteeringAngle()) / hatchback().wheelbase();
        double actualCurvature = Math.abs(fast.getAngle()) / (fast.getSpeed() * DT);

        assertTrue(fast.isSliding());
        assertTrue(actualCurvature < commandedCurvature / 5, "The car runs wide instead of turning on the spot.");
        assertTrue(fast.getSpeed() < 30, "Sliding scrubs speed.");
    }

    @Test
    public void testAtLowSpeedTheModelTurnsLikeTheKinematicBicycle() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car car = prepared(model, 2.0);

        model.step(car, new CarControls(0.5, 0), DT);

        double curvature = Math.tan(0.5 * hatchback().maxSteeringAngle()) / hatchback().wheelbase();
        assertEquals(car.getSpeed() * curvature * DT, car.getAngle(), 1e-9);
        assertFalse(car.isSliding());
    }

    @Test
    public void testDownforceRaisesCorneringSpeed() {
        DynamicModel formula = (DynamicModel) Vehicles.create("formula", TireCompound.MEDIUM);
        double mu = formula.frictionCoefficient(0);
        double withoutAero = Math.sqrt(mu * DynamicModel.GRAVITY * 40);

        assertTrue(formula.cornerSpeed(40) > withoutAero);
        assertTrue(maxLateralAcceleration(formula) > mu * DynamicModel.GRAVITY, "Downforce adds grip at speed.");
    }

    @Test
    public void testTyresWearAndLoseGripSoftFastest() {
        assertTrue(TireCompound.SOFT.wearPerKilometre() > TireCompound.HARD.wearPerKilometre());
        assertTrue(TireCompound.SOFT.gripAt(0.5) < TireCompound.SOFT.gripAt(0.0));
        assertEquals(TireCompound.MEDIUM.grip() * (1 - TireCompound.WEAR_GRIP_LOSS), TireCompound.MEDIUM.gripAt(1.0), 1e-12);

        DynamicModel soft = new DynamicModel(hatchback(), TireCompound.SOFT);
        DynamicModel hard = new DynamicModel(hatchback(), TireCompound.HARD);
        Car softCar = prepared(soft, 20);
        Car hardCar = prepared(hard, 20);

        for (int i = 0; i < 600; i++) {
            soft.step(softCar, new CarControls(0, 0.2), DT);
            hard.step(hardCar, new CarControls(0, 0.2), DT);
        }

        assertTrue(softCar.getTireWear() > hardCar.getTireWear() && hardCar.getTireWear() > 0);
        assertTrue(soft.frictionCoefficient(1.0) < soft.frictionCoefficient(0.0));
    }

    @Test
    public void testSlidingWearsTyresFaster() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car straight = prepared(model, 25);
        Car sliding = prepared(model, 25);

        model.step(straight, new CarControls(0, 0), DT);
        model.step(sliding, new CarControls(1, 0), DT);

        assertTrue(sliding.isSliding());
        assertTrue(sliding.getTireWear() > 3 * straight.getTireWear());
    }

    @Test
    public void testBrakingStopsWithoutReversingThenReverseEngages() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car car = prepared(model, 10);
        int stepsToStop = 0;

        while (car.getSpeed() > 0) {
            model.step(car, new CarControls(0, -1), DT);
            stepsToStop++;
        }

        assertTrue(stepsToStop < 200);

        for (int i = 0; i < 600; i++) {
            model.step(car, new CarControls(0, -1), DT);
        }

        assertEquals(-1, car.getGear());
        assertEquals(-DynamicModel.MAX_REVERSE_SPEED, car.getSpeed(), 0.1);
    }

    @Test
    public void testCoastingSlowsDownToAStop() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car car = prepared(model, 5);

        for (int i = 0; i < 60 * 60; i++) {
            model.step(car, CarControls.neutral(), DT);
        }

        assertEquals(0.0, car.getSpeed(), 0.0, "Rolling resistance and drag stop the car, never push it back.");
    }

    @Test
    public void testStoppedCarDoesNotMoveAndInvalidStepsAreRejected() {
        DynamicModel model = new DynamicModel(hatchback(), TireCompound.MEDIUM);
        Car car = prepared(model, 10);
        car.crash();

        model.step(car, new CarControls(1, 1), DT);

        assertEquals(0.0, car.getX(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> model.step(car, CarControls.neutral(), 0.2));
        assertThrows(IllegalArgumentException.class, () -> model.step(null, CarControls.neutral(), DT));
    }

    @Test
    public void testSimulationWithADynamicCarIsDeterministicAndInputsStayInRange() {
        Rng rng = new Rng(4);
        List<NeuralNetwork> networks = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            networks.add(new NeuralNetwork(rng, 6, 6, 2));
        }

        VehicleModel sports = Vehicles.create("sports", TireCompound.SOFT);
        Simulation first = new Simulation(Tracks.oval(), networks, RaySensor.standard(), true, SimulationSettings.defaults(), sports);
        Simulation second = new Simulation(Tracks.oval(), networks, RaySensor.standard(), true, SimulationSettings.defaults(), sports);

        for (int i = 0; i < 300; i++) {
            first.step();
            second.step();

            for (CarAgent agent : first.getAgents()) {
                double[] inputs = agent.getBrain().getLastInputs();

                if (inputs != null) {
                    for (double input : inputs) {
                        assertTrue(input >= 0.0 && input <= 1.0);
                    }
                }
            }
        }

        first.runUntilAllDead();
        second.runUntilAllDead();

        for (int i = 0; i < networks.size(); i++) {
            assertEquals(first.getResults().get(i).progress(), second.getResults().get(i).progress(), 0.0);
            assertEquals(sports.topSpeed(), first.getResults().get(i).topSpeed(), 0.0);
        }
    }

    @Test
    public void testKinematicModelIsTheAssignmentPhysics() {
        Car viaModel = new Car(0, 0, 0.3, 5);
        Car direct = new Car(0, 0, 0.3, 5);

        for (int i = 0; i < 100; i++) {
            KinematicModel.INSTANCE.step(viaModel, new CarControls(0.4, 0.7), DT);
            CarPhysics.step(direct, 0.4, 0.7, DT);
        }

        assertEquals(direct.getPosition(), viaModel.getPosition());
        assertEquals(CarPhysics.MAX_SPEED, KinematicModel.INSTANCE.topSpeed(), 0.0);
    }

    @Test
    public void testPresetsAndValidation() {
        for (String type : Vehicles.TYPES) {
            VehicleModel model = Vehicles.create(type, TireCompound.MEDIUM);
            assertTrue(model.topSpeed() > 15.0, type);
        }

        assertThrows(IllegalArgumentException.class, () -> Vehicles.create("bus", TireCompound.MEDIUM));
        assertThrows(IllegalArgumentException.class, () -> new DynamicModel(hatchback(), null));
        assertThrows(IllegalArgumentException.class, () -> withMass(hatchback(), 0));
        assertThrows(IllegalArgumentException.class, () -> new CarSpec("bad", 1000, 100, 1000, 900, 6000,
                new double[]{3, 2}, 4, 0.9, 0.3, 0.6, 0, 0.01, 2.5, 0.5, 1, 9, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new CarSpec("bad gears", 1000, 100, 800, 3000, 6000,
                new double[]{2, 3}, 4, 0.9, 0.3, 0.6, 0, 0.01, 2.5, 0.5, 1, 9, 0.6));
    }

    @Test
    public void testTorqueCurve() {
        CarSpec spec = hatchback();

        assertEquals(spec.maxTorque(), spec.torqueAt(spec.peakTorqueRpm()), 1e-9);
        assertEquals(0.55 * spec.maxTorque(), spec.torqueAt(spec.idleRpm()), 1e-9);
        assertEquals(0.0, spec.torqueAt(spec.redlineRpm()), 0.0, "Rev limiter.");
        assertTrue(spec.torqueAt(spec.redlineRpm() - 1) > 0.0);
    }
}
