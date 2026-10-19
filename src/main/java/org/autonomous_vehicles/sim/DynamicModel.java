package org.autonomous_vehicles.sim;

/**
 * Grip-limited bicycle model with a drivetrain.
 * <ul>
 *     <li><b>Engine and gears</b>: drive force = throttle × torque(rpm) × gear ratio × final drive × efficiency /
 *     wheel radius, where rpm follows the wheel speed through the engaged gear (idle at a standstill: the clutch
 *     slips). An automatic gearbox shifts up near the redline and down when the engine falls below its torque peak.
 *     The drive force is limited by the grip of the driven wheels (wheelspin).</li>
 *     <li><b>Weight</b>: every force is divided by the mass; aerodynamic downforce adds to the weight on the tyres.</li>
 *     <li><b>Resistances</b>: aerodynamic drag ½ρ·Cd·A·v² and rolling resistance Crr·m·g.</li>
 *     <li><b>Tyres</b>: friction circle — the combined longitudinal and lateral acceleration cannot exceed μ·g
 *     (μ from the car, the compound and its wear). Beyond it the car understeers: the turn widens, speed is scrubbed
 *     and the tyres wear four times faster. Braking is limited by the brakes and by the grip.</li>
 *     <li><b>Controls</b>: throttle > 0 accelerates; throttle < 0 brakes, or engages reverse below 0.5 m/s.</li>
 * </ul>
 * The car's heading stays aligned with its velocity (no drift angle): at low speed the model reduces to the
 * kinematic bicycle of {@link CarPhysics}. Stateless: the gear, rpm and wear are stored in the {@link Car}.
 */
public final class DynamicModel implements VehicleModel {

    public static final double GRAVITY = 9.81;
    public static final double AIR_DENSITY = 1.225;
    public static final double SHIFT_UP_FRACTION = 0.95;
    public static final double REVERSE_ACCELERATION = 3.0;
    public static final double MAX_REVERSE_SPEED = 5.0;
    public static final double REVERSE_THRESHOLD_SPEED = 0.5;
    /** Share of the excess grip demand turned into extra deceleration while sliding. */
    public static final double SCRUB_FACTOR = 0.3;

    private final CarSpec spec;
    private final TireCompound tire;
    private final double topSpeed;

    public DynamicModel(CarSpec spec, TireCompound tire) {
        if (spec == null || tire == null) {
            throw new IllegalArgumentException("Car spec and tyre compound must not be null.");
        }

        this.spec = spec;
        this.tire = tire;
        this.topSpeed = computeTopSpeed();
    }

    @Override
    public void prepare(Car car) {
        car.setGear(1);
        car.setEngineRpm(spec.idleRpm());
        car.setTireWear(0.0);
        car.setSliding(false);
    }

    @Override
    public void step(Car car, CarControls controls, double dt) {
        if (car == null || controls == null) {
            throw new IllegalArgumentException("Car and controls must not be null.");
        }

        if (!Double.isFinite(dt) || dt <= 0.0 || dt > 0.1) {
            throw new IllegalArgumentException("dt must be greater than 0 and at most 0.1 seconds.");
        }

        if (!car.isAlive()) {
            return;
        }

        double v = car.getSpeed();
        double throttle = controls.throttle();
        double normalAcceleration = GRAVITY + downforce(v) / spec.mass();
        double mu = frictionCoefficient(car.getTireWear());
        double gripLimit = mu * normalAcceleration;
        boolean sliding = false;
        boolean reversing = throttle < 0.0 && v <= REVERSE_THRESHOLD_SPEED;
        double tyreAcceleration;

        if (reversing) {
            car.setGear(-1);
            car.setEngineRpm(spec.idleRpm());
            tyreAcceleration = v > -MAX_REVERSE_SPEED ? throttle * Math.min(REVERSE_ACCELERATION, gripLimit) : 0.0;
        } else if (throttle < 0.0 || v < 0.0) {
            // Braking: against the motion, limited by the brakes and by the grip (beyond it the wheels lock).
            double requested = Math.abs(throttle) * spec.maxBrakeDeceleration();
            sliding = requested > gripLimit;
            tyreAcceleration = -Math.signum(v) * Math.min(requested, gripLimit);
            selectGear(car, v);
        } else {
            selectGear(car, v);
            double driveAcceleration = throttle * driveForce(car.getEngineRpm(), car.getGear()) / spec.mass();
            double traction = mu * spec.drivenAxleLoadShare() * normalAcceleration;

            if (driveAcceleration > traction) {
                driveAcceleration = traction;
                sliding = true;
            }

            tyreAcceleration = driveAcceleration;
        }

        double curvature = Math.tan(controls.steer() * spec.maxSteeringAngle()) / spec.wheelbase();
        double lateralAcceleration = v * v * curvature;
        double combined = Math.hypot(tyreAcceleration, lateralAcceleration);
        double scrub = 0.0;

        if (combined > gripLimit) {
            double scale = gripLimit / combined;
            tyreAcceleration *= scale;
            lateralAcceleration *= scale;
            scrub = SCRUB_FACTOR * (1.0 - scale) * gripLimit;
            sliding = true;
        }

        double acceleration = tyreAcceleration - Math.signum(v) * (resistance(v) / spec.mass() + scrub);
        double newSpeed = v + acceleration * dt;

        // Brakes and resistances stop the car, they never push it the other way.
        if (!reversing && v != 0.0 && Math.signum(newSpeed) != Math.signum(v)) {
            newSpeed = 0.0;
        }

        newSpeed = Math.max(newSpeed, -MAX_REVERSE_SPEED);

        if (car.getGear() == -1 && newSpeed >= 0.0 && !reversing) {
            car.setGear(1);
        }

        double effectiveCurvature = Math.abs(v) > 0.1 ? lateralAcceleration / (v * v) : curvature;
        double angle = normalizeAngle(car.getAngle() + newSpeed * effectiveCurvature * dt);
        car.setSpeed(newSpeed);
        car.setAngle(angle);
        car.setPosition(car.getX() + newSpeed * Math.cos(angle) * dt, car.getY() + newSpeed * Math.sin(angle) * dt);

        double wearRate = tire.wearPerKilometre() * (sliding ? TireCompound.SLIDING_WEAR_FACTOR : 1.0);
        car.setTireWear(car.getTireWear() + Math.abs(newSpeed) * dt / 1000.0 * wearRate);
        car.setSliding(sliding);
    }

    /**
     * Picks the gear (automatic gearbox) for the speed and stores it with the matching engine speed.
     */
    private void selectGear(Car car, double speed) {
        int gear = Math.clamp(car.getGear(), 1, spec.gearCount());
        double shiftUp = SHIFT_UP_FRACTION * spec.redlineRpm();

        while (gear < spec.gearCount() && engineRpm(speed, gear) > shiftUp) {
            gear++;
        }

        while (gear > 1 && engineRpm(speed, gear) < spec.peakTorqueRpm() && engineRpm(speed, gear - 1) < 0.9 * shiftUp) {
            gear--;
        }

        car.setGear(gear);
        car.setEngineRpm(engineRpm(speed, gear));
    }

    /**
     * Engine speed for a road speed in a gear; never below idle (the clutch slips when starting).
     */
    public double engineRpm(double speed, int gear) {
        return Math.max(spec.idleRpm(), wheelRpm(speed, gear));
    }

    private double wheelRpm(double speed, int gear) {
        return Math.abs(speed) / spec.wheelRadius() * spec.gearRatio(gear) * spec.finalDrive() * 60.0 / (2.0 * Math.PI);
    }

    /**
     * Force at the wheels at full throttle, N.
     */
    public double driveForce(double rpm, int gear) {
        return spec.torqueAt(rpm) * spec.gearRatio(gear) * spec.finalDrive() * spec.drivetrainEfficiency()
                / spec.wheelRadius();
    }

    public double frictionCoefficient(double wear) {
        return spec.gripFactor() * tire.gripAt(wear);
    }

    private double downforce(double speed) {
        return 0.5 * AIR_DENSITY * spec.downforceArea() * speed * speed;
    }

    /**
     * Drag plus rolling resistance, N (always opposing the motion).
     */
    private double resistance(double speed) {
        double rolling = speed != 0.0 ? spec.rollingResistance() * spec.mass() * GRAVITY : 0.0;
        return 0.5 * AIR_DENSITY * spec.dragArea() * speed * speed + rolling;
    }

    /**
     * Highest speed where, in some gear below the redline, the full-throttle force (traction-limited)
     * still beats the resistances. Scanned in 5 cm/s steps.
     */
    private double computeTopSpeed() {
        double best = 0.0;

        for (double v = 0.05; v < 200.0; v += 0.05) {
            boolean reachable = false;

            for (int gear = 1; gear <= spec.gearCount() && !reachable; gear++) {
                if (wheelRpm(v, gear) >= spec.redlineRpm()) {
                    continue;
                }

                double normal = spec.mass() * GRAVITY + downforce(v);
                double force = Math.min(driveForce(engineRpm(v, gear), gear),
                        frictionCoefficient(0.0) * spec.drivenAxleLoadShare() * normal);
                reachable = force > resistance(v);
            }

            if (!reachable) {
                break;
            }

            best = v;
        }

        return best;
    }

    /**
     * Highest speed at which a bend of the given radius can be taken without sliding (new tyres, no throttle).
     */
    public double cornerSpeed(double radius) {
        // v² / R = μ (g + ½ρ·Cl·A·v² / m)  →  v² = μ g / (1/R - μ ½ρ·Cl·A / m)
        double mu = frictionCoefficient(0.0);
        double aero = mu * 0.5 * AIR_DENSITY * spec.downforceArea() / spec.mass();
        double denominator = 1.0 / radius - aero;
        return denominator <= 0.0 ? Double.POSITIVE_INFINITY : Math.sqrt(mu * GRAVITY / denominator);
    }

    private static double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    @Override
    public double topSpeed() {
        return topSpeed;
    }

    @Override
    public String name() {
        return spec.name() + " (" + tire + ")";
    }

    public CarSpec getSpec() {
        return spec;
    }

    public TireCompound getTire() {
        return tire;
    }
}
