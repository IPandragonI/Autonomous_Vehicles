package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GripSteeringTest {

    private static final double DT = 1.0 / 60.0;
    private static final CarSpec FORMULA = Vehicles.PRESETS.get("formula");

    private static DynamicModel model(boolean gripSteering) {
        return new DynamicModel(FORMULA, TireCompound.MEDIUM, RaceRules.NONE, gripSteering);
    }

    private static Car moving(DynamicModel model, double speed) {
        Car car = new Car(0, 0, 0, speed);
        model.prepare(car);
        return car;
    }

    /** μ (g + ½ρ·Cl·A·v² / m): the grip of the formula car on new medium tyres at speed v. */
    private static double grip(double v) {
        double mu = FORMULA.gripFactor() * TireCompound.MEDIUM.grip();
        return mu * (DynamicModel.GRAVITY + 0.5 * DynamicModel.AIR_DENSITY * FORMULA.downforceArea() * v * v
                / FORMULA.mass());
    }

    @Test
    public void testWheelSteeringStaysTheDefaultBitForBit() {
        DynamicModel explicit = model(false);
        DynamicModel legacy = new DynamicModel(FORMULA, TireCompound.MEDIUM);
        Car a = moving(explicit, 30);
        Car b = moving(legacy, 30);
        Rng rng = new Rng(5);

        for (int i = 0; i < 600; i++) {
            CarControls controls = new CarControls(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
            explicit.step(a, controls, DT);
            legacy.step(b, controls, DT);
            assertEquals(b.getX(), a.getX(), 0.0);
            assertEquals(b.getAngle(), a.getAngle(), 0.0);
            assertEquals(b.getSpeed(), a.getSpeed(), 0.0);
        }

        assertSame(Vehicles.create("formula", TireCompound.MEDIUM, RaceRules.NONE).getClass(), DynamicModel.class);
        assertFalse(((DynamicModel) Vehicles.create("formula", TireCompound.MEDIUM, RaceRules.NONE)).usesGripSteering());
        assertTrue(((DynamicModel) Vehicles.create("formula", TireCompound.MEDIUM, RaceRules.NONE, true))
                .usesGripSteering());
    }

    @Test
    public void testFullSteerAsksForExactlyTheGripAtEverySpeed() {
        DynamicModel model = model(true);

        for (double v : new double[]{20.0, 50.0, 80.0}) {
            for (double steer : new double[]{1.0, 0.5, -0.25}) {
                assertEquals(steer * grip(v), v * v * model.steeringCurvature(steer, v, grip(v)), 1e-9 * grip(v),
                        "lateral acceleration = steer × grip at " + v + " m/s");
            }

            Car car = moving(model, v);
            model.step(car, new CarControls(0.99, 0.0), DT);
            assertFalse(car.isSliding(), "just inside the limit, at any speed");
            assertEquals(car.getSpeed() * 0.99 * grip(v) / (v * v) * DT, car.getAngle(), 1e-12);
        }
    }

    @Test
    public void testSameSmallCommandSlidesWithWheelSteeringButNotWithGripSteeringAt290KmH() {
        double v = 80.0;
        // Wheel steering: 5 % of the lock asks for v² tan(0.05 × 0.40) / 3.0 ≈ 43 m/s², above the ~32 m/s² of grip.
        assertTrue(v * v * Math.tan(0.05 * FORMULA.maxSteeringAngle()) / FORMULA.wheelbase() > grip(v));

        Car wheel = moving(model(false), v);
        Car gripCar = moving(model(true), v);
        model(false).step(wheel, new CarControls(0.05, 0.0), DT);
        model(true).step(gripCar, new CarControls(0.05, 0.0), DT);

        assertTrue(wheel.isSliding(), "wheel angle: a 0.05 output already overdrives the tyres");
        assertFalse(gripCar.isSliding(), "grip: 0.05 is 5 % of the grip");
        assertTrue(wheel.getSpeed() < gripCar.getSpeed(), "sliding scrubs speed");
    }

    @Test
    public void testLowSpeedIsLimitedByTheSteeringLock() {
        DynamicModel model = model(true);
        double lock = Math.tan(FORMULA.maxSteeringAngle()) / FORMULA.wheelbase();

        assertEquals(lock, model.steeringCurvature(1.0, 1.0, grip(1.0)), 1e-12, "grip/v² = 12/m, far beyond the lock");
        assertEquals(-0.5 * lock, model.steeringCurvature(-0.5, 0.0, grip(0.0)), 1e-12, "standstill: no division by 0");
    }
}
