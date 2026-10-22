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
 *     <li><b>Fuel</b> ({@link RaceRules#fuelAutonomy}): the engine burns power / (thermal efficiency × fuel energy),
 *     with power = drive force × speed (so consumption grows with both acceleration and speed; wheelspin wastes
 *     fuel) plus a small idle flow. The fuel weighs on the car. A dry car loses all drive and coasts to a stop.</li>
 *     <li><b>Tyre temperature</b> ({@link RaceRules#tyreTemperature}): the tread heats in proportion to the share of
 *     grip used × the distance driven (×{@link #SLIDING_HEAT_FACTOR} while sliding) and cools towards the air
 *     temperature, faster with speed (airflow). Grip follows {@link TireCompound#temperatureGrip}.</li>
 * </ul>
 * The car's heading stays aligned with its velocity (no drift angle): at low speed the model reduces to the
 * kinematic bicycle of {@link CarPhysics}. Stateless: the gear, rpm, wear, fuel and temperature are stored in the
 * {@link Car}. With {@link RaceRules#NONE} the arithmetic is exactly that of the model before fuel and temperature.
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

    /** Share of the fuel energy a petrol engine turns into work. */
    public static final double THERMAL_EFFICIENCY = 0.30;
    /** Lower heating value of petrol, J/kg. */
    public static final double FUEL_ENERGY = 43e6;
    /** Fuel flow at idle, as a share of the full-power flow. */
    public static final double IDLE_POWER_FRACTION = 0.02;

    /** Air (and track) temperature, °C: the temperature tyres cool towards. */
    public static final double AMBIENT_TEMPERATURE = 25.0;
    /** Tyres leave tyre blankets warm but below every compound's window: the first bends are slippery. */
    public static final double START_TYRE_TEMPERATURE = 60.0;
    /**
     * Heating at full grip use, °C per metre driven, before {@link RaceRules#tyreHeating} existed (and still for
     * {@link RaceRules#NONE} and old champion files). Steady state = 25 + heating × grip use / (0.0015 + 0.006 / v):
     * at full grip use this settles at 136–158 °C, above every compound's window, so driving at the limit overheated
     * the tyres even without sliding. See {@link RaceRules#DEFAULT_TYRE_HEATING} for the recalibrated rate.
     */
    public static final double TYRE_HEATING = 0.20;
    public static final double SLIDING_HEAT_FACTOR = 2.5;
    /** Cooling rate at a standstill, per second, times the excess over the air temperature. */
    public static final double TYRE_COOLING_STILL = 0.006;
    /** Extra cooling per metre driven (airflow), times the excess over the air temperature. */
    public static final double TYRE_COOLING_AIRFLOW = 0.0015;

    private final CarSpec spec;
    private final TireCompound tire;
    private final RaceRules rules;
    private final boolean gripSteering;
    private final double topSpeed;
    private final double fullPowerFuelFlow;

    /**
     * Model without fuel and tyre temperature ({@link RaceRules#NONE}).
     */
    public DynamicModel(CarSpec spec, TireCompound tire) {
        this(spec, tire, RaceRules.NONE);
    }

    public DynamicModel(CarSpec spec, TireCompound tire, RaceRules rules) {
        this(spec, tire, rules, false);
    }

    /**
     * @param gripSteering read the steering command relative to the grip ({@link #steeringCurvature}) instead of as a
     *                     wheel angle
     */
    public DynamicModel(CarSpec spec, TireCompound tire, RaceRules rules, boolean gripSteering) {
        if (spec == null || tire == null || rules == null) {
            throw new IllegalArgumentException("Car spec, tyre compound and race rules must not be null.");
        }

        this.spec = spec;
        this.tire = tire;
        this.rules = rules;
        this.gripSteering = gripSteering;
        this.topSpeed = computeTopSpeed();
        this.fullPowerFuelFlow = spec.peakPower() / (THERMAL_EFFICIENCY * FUEL_ENERGY);
    }

    @Override
    public void prepare(Car car) {
        car.setGear(1);
        car.setEngineRpm(spec.idleRpm());
        car.setTireWear(0.0);
        car.setSliding(false);

        if (rules.limitedFuel()) {
            car.fillFuel(startFuel());
        }

        car.setTireTemperature(rules.tyreTemperature() ? START_TYRE_TEMPERATURE : AMBIENT_TEMPERATURE);
    }

    /**
     * Fuel loaded at the start, kg: {@link RaceRules#fuelAutonomy} seconds at full power (0 with unlimited fuel).
     */
    public double startFuel() {
        return rules.fuelAutonomy() * fullPowerFuelFlow;
    }

    /**
     * Fuel burnt per second at full power, kg/s.
     */
    public double fullPowerFuelFlow() {
        return fullPowerFuelFlow;
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
        // Unlimited fuel adds 0.0, which leaves the mass (and every division by it) bit-identical.
        double mass = spec.mass() + car.getFuel();
        double normalAcceleration = GRAVITY + downforce(v) / mass;
        double mu = frictionCoefficient(car.getTireWear());

        if (rules.tyreTemperature()) {
            mu *= tire.temperatureGrip(car.getTireTemperature());
        }

        double gripLimit = mu * normalAcceleration;
        boolean sliding = false;
        boolean reversing = throttle < 0.0 && v <= REVERSE_THRESHOLD_SPEED;
        boolean dry = car.isOutOfFuel();
        double engineAcceleration = 0.0;
        double tyreAcceleration;

        if (reversing) {
            car.setGear(-1);
            car.setEngineRpm(spec.idleRpm());
            tyreAcceleration = v > -MAX_REVERSE_SPEED && !dry ? throttle * Math.min(REVERSE_ACCELERATION, gripLimit) : 0.0;
            engineAcceleration = Math.abs(tyreAcceleration);
        } else if (throttle < 0.0 || v < 0.0) {
            // Braking: against the motion, limited by the brakes and by the grip (beyond it the wheels lock).
            double requested = Math.abs(throttle) * spec.maxBrakeDeceleration();
            sliding = requested > gripLimit;
            tyreAcceleration = -Math.signum(v) * Math.min(requested, gripLimit);
            selectGear(car, v);
        } else {
            selectGear(car, v);
            double driveAcceleration = dry ? 0.0 : throttle * driveForce(car.getEngineRpm(), car.getGear()) / mass;
            double traction = mu * spec.drivenAxleLoadShare() * normalAcceleration;
            engineAcceleration = driveAcceleration; // wheelspin still burns the fuel

            if (driveAcceleration > traction) {
                driveAcceleration = traction;
                sliding = true;
            }

            tyreAcceleration = driveAcceleration;
        }

        double curvature = steeringCurvature(controls.steer(), v, gripLimit);
        double lateralAcceleration = v * v * curvature;
        double combined = Math.hypot(tyreAcceleration, lateralAcceleration);
        double gripUse = gripLimit > 0.0 ? Math.min(1.0, combined / gripLimit) : 1.0;
        double scrub = 0.0;

        if (combined > gripLimit) {
            double scale = gripLimit / combined;
            tyreAcceleration *= scale;
            lateralAcceleration *= scale;
            scrub = SCRUB_FACTOR * (1.0 - scale) * gripLimit;
            sliding = true;
        }

        double acceleration = tyreAcceleration - Math.signum(v) * (resistance(v, mass) / mass + scrub);
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

        // × 1.0 with the original rates leaves the product bit-identical.
        double wearRate = tire.wearPerKilometre() * rules.tyreWear() * (sliding ? TireCompound.SLIDING_WEAR_FACTOR : 1.0);
        car.setTireWear(car.getTireWear() + Math.abs(newSpeed) * dt / 1000.0 * wearRate);
        car.setSliding(sliding);

        if (car.hasFuelLimit()) {
            double power = engineAcceleration * mass * Math.abs(v) / spec.drivetrainEfficiency();
            double flow = Math.min(power / (THERMAL_EFFICIENCY * FUEL_ENERGY), fullPowerFuelFlow)
                    + (dry ? 0.0 : IDLE_POWER_FRACTION * fullPowerFuelFlow);
            car.setFuel(car.getFuel() - flow * dt);
        }

        if (rules.tyreTemperature()) {
            car.setTireTemperature(nextTyreTemperature(car.getTireTemperature(), gripUse, sliding, newSpeed, dt,
                    rules.tyreHeating()));
        }
    }

    /**
     * Tread temperature after {@code dt} with the original heating rate {@link #TYRE_HEATING}.
     */
    static double nextTyreTemperature(double celsius, double gripUse, boolean sliding, double speed, double dt) {
        return nextTyreTemperature(celsius, gripUse, sliding, speed, dt, TYRE_HEATING);
    }

    /**
     * Tread temperature after {@code dt}: heating ∝ grip use × distance (more while sliding), cooling ∝ the excess
     * over the air temperature, faster with speed. With the default rate ({@link RaceRules#DEFAULT_TYRE_HEATING}) a
     * car cornering near the limit settles at 92–101 °C (inside every window) whatever its speed, a car that slides
     * repeatedly overheats, and a car cruising on a straight cools down with a time constant of ~20 s at 100 km/h.
     *
     * @param heatingRate heating at full grip use, °C per metre
     */
    static double nextTyreTemperature(double celsius, double gripUse, boolean sliding, double speed, double dt,
                                      double heatingRate) {
        double distance = Math.abs(speed) * dt;
        double heating = heatingRate * gripUse * (sliding ? SLIDING_HEAT_FACTOR : 1.0) * distance;
        double cooling = (celsius - AMBIENT_TEMPERATURE) * (TYRE_COOLING_STILL * dt + TYRE_COOLING_AIRFLOW * distance);
        return celsius + heating - cooling;
    }

    /**
     * Path curvature (1/m) requested by a steering command in [-1, 1].
     * <ul>
     *     <li>Wheel angle (default): the command is the front-wheel angle, steer × max lock. At 290 km/h a formula
     *     car is at the grip limit with 4 % of its lock, so a network must hold its output within ±0.04 on a
     *     straight: every small mutation of the steering makes it slide, and evolution keeps slow cars.</li>
     *     <li>Grip ({@code gripSteering}): ±1 asks for the whole grip as lateral acceleration, μ·(g + downforce),
     *     capped by the lock at low speed. One output then means the same at every speed, like a driver who feels
     *     the grip; braking or accelerating at the same time still exceeds the friction circle and slides.</li>
     * </ul>
     *
     * @param gripLimit grip available now, m/s² (wear, temperature and downforce included)
     */
    double steeringCurvature(double steer, double speed, double gripLimit) {
        if (!gripSteering) {
            return Math.tan(steer * spec.maxSteeringAngle()) / spec.wheelbase();
        }

        double lockCurvature = Math.tan(spec.maxSteeringAngle()) / spec.wheelbase();
        double squaredSpeed = speed * speed;
        double gripCurvature = squaredSpeed > 0.0 ? gripLimit / squaredSpeed : lockCurvature;
        return steer * Math.min(lockCurvature, gripCurvature);
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
        return resistance(speed, spec.mass());
    }

    private double resistance(double speed, double mass) {
        double rolling = speed != 0.0 ? spec.rollingResistance() * mass * GRAVITY : 0.0;
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

    public RaceRules getRules() {
        return rules;
    }

    public boolean usesGripSteering() {
        return gripSteering;
    }
}
