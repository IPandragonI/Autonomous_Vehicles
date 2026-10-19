package org.autonomous_vehicles.sim;

import java.util.Arrays;

/**
 * Physical description of a car type, in SI units (1 track unit = 1 metre).
 *
 * @param name                 display name
 * @param mass                 kg, driver included
 * @param maxTorque            peak engine torque, N·m
 * @param idleRpm              engine speed with the clutch slipping at a standstill
 * @param peakTorqueRpm        engine speed of the peak torque
 * @param redlineRpm           rev limiter: no torque above it
 * @param gearRatios           ratio of each forward gear, first gear first (one entry = direct drive)
 * @param finalDrive           differential ratio
 * @param drivetrainEfficiency fraction of the engine torque reaching the wheels
 * @param wheelRadius          m
 * @param dragArea             drag coefficient × frontal area (Cd·A), m²
 * @param downforceArea        lift coefficient × area (Cl·A), m²; 0 for no aerodynamic downforce
 * @param rollingResistance    rolling resistance coefficient (Crr)
 * @param wheelbase            m
 * @param maxSteeringAngle     front-wheel angle at full lock, rad
 * @param gripFactor           tyre construction grip, multiplied by the compound grip (1 = racing slick)
 * @param maxBrakeDeceleration brake system limit, m/s² (the tyres may limit it further)
 * @param drivenAxleLoadShare  fraction of the weight on the driven wheels (traction limit)
 */
public record CarSpec(String name, double mass, double maxTorque, double idleRpm, double peakTorqueRpm,
                      double redlineRpm, double[] gearRatios, double finalDrive, double drivetrainEfficiency,
                      double wheelRadius, double dragArea, double downforceArea, double rollingResistance,
                      double wheelbase, double maxSteeringAngle, double gripFactor, double maxBrakeDeceleration,
                      double drivenAxleLoadShare) {

    public CarSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A car spec needs a name.");
        }

        positive(mass, "mass");
        positive(maxTorque, "maxTorque");
        positive(idleRpm, "idleRpm");
        positive(finalDrive, "finalDrive");
        positive(wheelRadius, "wheelRadius");
        positive(wheelbase, "wheelbase");
        positive(gripFactor, "gripFactor");
        positive(maxBrakeDeceleration, "maxBrakeDeceleration");
        nonNegative(dragArea, "dragArea");
        nonNegative(downforceArea, "downforceArea");
        nonNegative(rollingResistance, "rollingResistance");

        if (!(idleRpm < peakTorqueRpm && peakTorqueRpm < redlineRpm)) {
            throw new IllegalArgumentException("Engine speeds must satisfy idle < peak torque < redline.");
        }

        if (!(drivetrainEfficiency > 0.0 && drivetrainEfficiency <= 1.0)) {
            throw new IllegalArgumentException("drivetrainEfficiency must be in (0, 1].");
        }

        if (!(drivenAxleLoadShare > 0.0 && drivenAxleLoadShare <= 1.0)) {
            throw new IllegalArgumentException("drivenAxleLoadShare must be in (0, 1].");
        }

        if (!(maxSteeringAngle > 0.0 && maxSteeringAngle < Math.PI / 2)) {
            throw new IllegalArgumentException("maxSteeringAngle must be in (0, π/2).");
        }

        if (gearRatios == null || gearRatios.length == 0) {
            throw new IllegalArgumentException("A car needs at least one gear.");
        }

        gearRatios = gearRatios.clone();

        for (int i = 0; i < gearRatios.length; i++) {
            positive(gearRatios[i], "gear ratio " + (i + 1));

            if (i > 0 && gearRatios[i] >= gearRatios[i - 1]) {
                throw new IllegalArgumentException("Gear ratios must decrease from first to top gear.");
            }
        }
    }

    private static void positive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    private static void nonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
    }

    @Override
    public double[] gearRatios() {
        return gearRatios.clone();
    }

    public int gearCount() {
        return gearRatios.length;
    }

    public double gearRatio(int gear) {
        return gearRatios[gear - 1];
    }

    /**
     * Engine torque at a given speed: rises from 55 % at idle to the peak, then falls by 60 % at the
     * redline; nothing at or above the redline (rev limiter).
     */
    public double torqueAt(double rpm) {
        if (rpm >= redlineRpm) {
            return 0.0;
        }

        if (rpm <= peakTorqueRpm) {
            double rise = Math.clamp((rpm - idleRpm) / (peakTorqueRpm - idleRpm), 0.0, 1.0);
            return maxTorque * (0.55 + 0.45 * rise);
        }

        double fall = (rpm - peakTorqueRpm) / (redlineRpm - peakTorqueRpm);
        return maxTorque * (1.0 - 0.6 * fall * fall);
    }

    /**
     * Rated power (peak of torque × angular speed), W.
     */
    public double peakPower() {
        double best = 0.0;

        for (double rpm = idleRpm; rpm < redlineRpm; rpm += 10.0) {
            best = Math.max(best, torqueAt(rpm) * rpm * 2.0 * Math.PI / 60.0);
        }

        return best;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CarSpec spec && name.equals(spec.name) && mass == spec.mass
                && maxTorque == spec.maxTorque && Arrays.equals(gearRatios, spec.gearRatios)
                && finalDrive == spec.finalDrive && gripFactor == spec.gripFactor;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + Arrays.hashCode(gearRatios);
    }

    @Override
    public String toString() {
        return name;
    }
}
