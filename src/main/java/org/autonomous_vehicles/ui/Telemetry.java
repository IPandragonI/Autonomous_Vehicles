package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.sim.Car;
import org.autonomous_vehicles.sim.CarAgent;
import org.autonomous_vehicles.sim.CarControls;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.DynamicModel;
import org.autonomous_vehicles.sim.LapTracker;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.VehicleModel;

/**
 * Snapshot of one car for the dashboard (plain Java, so it is unit-tested).
 *
 * @param hasDrivetrain false for the kinematic model, which has no engine, gears or tyres
 * @param gear          1, 2, … or -1 for reverse (meaningful only with a drivetrain)
 * @param throttle      accelerator pedal, 0–1
 * @param brake         brake pedal, 0–1 (negative throttle)
 * @param steer         -1 (full right) … +1 (full left)
 * @param tire          compound, or null without a drivetrain
 * @param lap           lap being driven (1 = first)
 * @param bestLapTime   seconds, NaN if no lap completed yet
 * @param fuelLimited   the fuel mechanic is on for this car
 * @param fuelFraction  fuel left / starting load, 0–1 (1 with unlimited fuel)
 * @param fuelKg        fuel left, kg
 * @param tyreTemperatureOn the tyre temperature mechanic is on
 * @param tyreTemperature   tread temperature, °C
 * @param temperatureGrip   grip multiplier of that temperature, 0–1
 */
public record Telemetry(String car, boolean hasDrivetrain, double speedKmh, int gear, int gearCount, double rpm,
                        double redlineRpm, double throttle, double brake, double steer, TireCompound tire,
                        double tireWear, boolean sliding, boolean alive, DeathCause deathCause, int lap,
                        double currentLapTime, double bestLapTime, int nextCheckpoint, int checkpointCount,
                        boolean fuelLimited, double fuelFraction, double fuelKg, boolean tyreTemperatureOn,
                        double tyreTemperature, double temperatureGrip) {

    public static Telemetry of(CarAgent agent, VehicleModel vehicle) {
        return of(agent.getCar(), agent.getLastControls(), agent.getLapTracker(), vehicle, agent.getDeathCause());
    }

    public static Telemetry of(Car car, CarControls controls, LapTracker lapTracker, VehicleModel vehicle,
                               DeathCause deathCause) {
        if (car == null || controls == null || lapTracker == null || vehicle == null) {
            throw new IllegalArgumentException("Car, controls, lap tracker and vehicle must not be null.");
        }

        boolean dynamic = vehicle instanceof DynamicModel;
        DynamicModel model = dynamic ? (DynamicModel) vehicle : null;
        boolean alive = car.isAlive();
        boolean tyreTemperatureOn = dynamic && model.getRules().tyreTemperature();

        return new Telemetry(
                vehicle.name(),
                dynamic,
                Math.abs(car.getSpeed()) * 3.6,
                dynamic ? car.getGear() : 0,
                dynamic ? model.getSpec().gearCount() : 0,
                dynamic && alive ? car.getEngineRpm() : 0.0,
                dynamic ? model.getSpec().redlineRpm() : 0.0,
                alive ? Math.max(0.0, controls.throttle()) : 0.0,
                alive ? Math.max(0.0, -controls.throttle()) : 0.0,
                alive ? controls.steer() : 0.0,
                dynamic ? model.getTire() : null,
                dynamic ? car.getTireWear() : 0.0,
                alive && car.isSliding(),
                alive,
                deathCause == null ? DeathCause.ALIVE : deathCause,
                lapTracker.getLaps() + 1,
                lapTracker.getCurrentLapTime(),
                lapTracker.getBestLapTime(),
                lapTracker.getNextCheckpointIndex(),
                lapTracker.getCheckpointCount(),
                car.hasFuelLimit(),
                Math.clamp(car.getFuelFraction(), 0.0, 1.0),
                car.getFuel(),
                tyreTemperatureOn,
                car.getTireTemperature(),
                tyreTemperatureOn ? model.getTire().temperatureGrip(car.getTireTemperature()) : 1.0);
    }

    /**
     * "cold", "optimal" or "hot" relative to the compound's window ("" without the temperature mechanic).
     */
    public String temperatureState() {
        if (!tyreTemperatureOn || tire == null) {
            return "";
        }

        return tyreTemperature < tire.optimalLow() ? "cold" : tyreTemperature > tire.optimalHigh() ? "hot" : "optimal";
    }

    /**
     * Engine speed as a fraction of the redline, 0–1.
     */
    public double rpmFraction() {
        return redlineRpm > 0.0 ? Math.clamp(rpm / redlineRpm, 0.0, 1.0) : 0.0;
    }

    public String gearLabel() {
        if (!hasDrivetrain) {
            return "–";
        }

        return gear < 0 ? "R" : String.valueOf(gear);
    }
}
