package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.util.List;

/**
 * A hand-written driver for grip-limited cars, used as the teacher of imitation learning: it knows the whole track,
 * which a network never sees, and drives close to what the physics allows.
 * <ul>
 *     <li><b>Speed profile</b> (once per track and car): the fastest speed in each bend of the centre line,
 *     μ·(g + downforce/m) = v²·κ with a margin, then a backward pass so that every bend can be reached by braking —
 *     the quasi-steady-state method of lap-time simulators.</li>
 *     <li><b>Steering</b>: pure pursuit of the centre-line point a speed-dependent distance ahead.</li>
 *     <li><b>Throttle</b>: proportional to the gap to the profile speed slightly ahead (negative = brake).</li>
 * </ul>
 * Keeps a search hint, so one instance drives one car. Deterministic.
 */
public final class ExpertDriver {

    /** Share of the grip used in steady cornering: the rest absorbs tyre temperature, wear and steering errors. */
    public static final double CORNER_MARGIN = 0.85;
    /** Share of the braking limit used by the speed profile (braking while turning shares the grip). */
    public static final double BRAKE_MARGIN = 0.75;
    /** Spacing of the speed profile, m. */
    static final double PROFILE_STEP = 2.0;
    /** Half the span over which the centre-line curvature is measured, m. */
    static final double CURVATURE_HALF_SPAN = 8.0;
    /** Share of the driven wheels' grip the traction control lets the engine use. */
    public static final double TRACTION_MARGIN = 0.9;
    /** Throttle per m/s of speed error. */
    static final double SPEED_GAIN = 0.8;

    private final DynamicModel model;
    /** The path followed: the track's centre line, or a racing line. */
    private final TrackPreview preview;
    private final double cornerMargin;
    private final double brakeMargin;
    private final double[] profile;
    private final double profileStep;
    private int segment = -1;

    /**
     * Expert on the centre line with the default margins.
     */
    public ExpertDriver(DynamicModel model, Track track) {
        this(model, track == null ? null : track.getPreview(), CORNER_MARGIN, BRAKE_MARGIN);
    }

    /**
     * @param path         closed path to follow in driving order, e.g. {@link RacingLine#minimumCurvature}
     * @param cornerMargin share of the grip used in steady cornering, in (0, 1]
     * @param brakeMargin  share of the braking limit used by the profile, in (0, 1]
     */
    public ExpertDriver(DynamicModel model, List<Vec2> path, double cornerMargin, double brakeMargin) {
        this(model, path == null ? null : new TrackPreview(path), cornerMargin, brakeMargin);
    }

    private ExpertDriver(DynamicModel model, TrackPreview path, double cornerMargin, double brakeMargin) {
        if (model == null || path == null) {
            throw new IllegalArgumentException("Model and track must not be null.");
        }

        if (!(cornerMargin > 0.0 && cornerMargin <= 1.0) || !(brakeMargin > 0.0 && brakeMargin <= 1.0)) {
            throw new IllegalArgumentException("Margins must be in (0, 1].");
        }

        this.model = model;
        this.preview = path;
        this.cornerMargin = cornerMargin;
        this.brakeMargin = brakeMargin;
        int n = (int) Math.ceil(preview.length() / PROFILE_STEP);
        this.profileStep = preview.length() / n;
        this.profile = speedProfile(n);
    }

    private double[] speedProfile(int n) {
        CarSpec spec = model.getSpec();
        double[] speeds = new double[n];

        for (int i = 0; i < n; i++) {
            double curvature = Math.abs(preview.curvatureAt(i * profileStep, CURVATURE_HALF_SPAN));
            double corner = curvature < 1e-6 ? Double.POSITIVE_INFINITY
                    : cornerSpeed(1.0 / curvature, cornerMargin * model.frictionCoefficient(0.0));
            speeds[i] = Math.min(model.topSpeed(), corner);
        }

        // Backward pass, twice round the loop so the braking zone before the finish line also counts.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = n - 1; i >= 0; i--) {
                double next = speeds[(i + 1) % n];
                double mu = model.frictionCoefficient(0.0);
                double grip = mu * (DynamicModel.GRAVITY + downforce(next) / spec.mass());
                double braking = brakeMargin * Math.min(spec.maxBrakeDeceleration(), grip);
                speeds[i] = Math.min(speeds[i], Math.sqrt(next * next + 2.0 * braking * profileStep));
            }
        }

        return speeds;
    }

    /** v² / R = μ (g + ½ρ·Cl·A·v² / m), solved for v; +∞ when downforce outgrows the bend. */
    private double cornerSpeed(double radius, double mu) {
        double aero = mu * 0.5 * DynamicModel.AIR_DENSITY * model.getSpec().downforceArea() / model.getSpec().mass();
        double denominator = 1.0 / radius - aero;
        return denominator <= 0.0 ? Double.POSITIVE_INFINITY : Math.sqrt(mu * DynamicModel.GRAVITY / denominator);
    }

    private double downforce(double speed) {
        return 0.5 * DynamicModel.AIR_DENSITY * model.getSpec().downforceArea() * speed * speed;
    }

    /**
     * Profile speed at an arc length (any value: wrapped), interpolated.
     */
    public double profileSpeed(double arc) {
        double position = (arc - Math.floor(arc / preview.length()) * preview.length()) / profileStep;
        int i = Math.min((int) position, profile.length - 1);
        double t = position - i;
        return profile[i] + t * (profile[(i + 1) % profile.length] - profile[i]);
    }

    /**
     * Lap time of the speed profile itself, s: what the expert aims at on the centre line.
     */
    public double profileLapTime() {
        double time = 0.0;

        for (int i = 0; i < profile.length; i++) {
            time += 2.0 * profileStep / (profile[i] + profile[(i + 1) % profile.length]);
        }

        return time;
    }

    public CarControls decide(Car car) {
        double x = car.getX();
        double y = car.getY();
        double v = car.getSpeed();
        segment = preview.locate(x, y, segment);
        double here = preview.arcLength(segment, x, y);

        // Path tracking: the path's own curvature (feed-forward, read slightly ahead for the step's delay) plus a
        // correction of the lateral and heading errors with critically damped gains over a speed-dependent
        // distance. Pure pursuit of a point ahead cuts every bend by ~L²κ/8, which puts a racing line that touches
        // the apexes into the inner wall.
        double settle = Math.clamp(6.0 + 0.25 * Math.abs(v), 6.0, 30.0);
        double[] onPath = preview.pointAt(here);
        double[] left = preview.leftNormalAt(here);
        double lateral = (x - onPath[0]) * left[0] + (y - onPath[1]) * left[1];
        double headingGap = preview.headingAt(here) - car.getAngle();
        double headingError = Math.atan2(Math.sin(headingGap), Math.cos(headingGap));
        double curvature = preview.curvatureAt(here + 0.1 * Math.abs(v), CURVATURE_HALF_SPAN)
                - lateral / (settle * settle) + 2.0 * headingError / settle;

        double steer = model.usesGripSteering()
                ? curvature * v * v / Math.max(1e-6, gripLimit(car))
                : Math.atan(curvature * model.getSpec().wheelbase()) / model.getSpec().maxSteeringAngle();

        double targetSpeed = profileSpeed(here + 0.2 * Math.abs(v));
        double throttle = Math.clamp(SPEED_GAIN * (targetSpeed - v), -1.0, 1.0);

        if (v < 1.0) {
            throttle = Math.max(throttle, 0.0); // never select reverse
        }

        // Anti-lock: full brake pedal asks for the brakes' limit, more than the grip at low speed (12 vs 15 m/s²
        // for the formula car): the wheels would lock, slide and overheat.
        double brakeLimit = -TRACTION_MARGIN * gripLimit(car) / model.getSpec().maxBrakeDeceleration();
        return new CarControls(Math.clamp(steer, -1.0, 1.0),
                Math.clamp(throttle, Math.max(-1.0, brakeLimit), tractionThrottle(car)));
    }

    /**
     * Largest throttle that does not spin the driven wheels (traction control). Full throttle in the low gears of a
     * powerful car asks for more than μ × the driven axle load: the wheels spin, and sliding heats the tyres 2.5×
     * faster — measured on São Paulo, a formula car at full throttle reached 168 °C (window 90–110 °C), lost 30 % of
     * its grip and left the track at the 17th checkpoint.
     */
    private double tractionThrottle(Car car) {
        double mass = model.getSpec().mass() + car.getFuel();
        // The rpm from the road speed, not the one stored in the car, which may be stale (idle before the first step).
        int gear = Math.max(1, car.getGear());
        double fullDrive = model.driveForce(model.engineRpm(car.getSpeed(), gear), gear) / mass;
        double traction = TRACTION_MARGIN * model.getSpec().drivenAxleLoadShare() * gripLimit(car);
        return fullDrive <= traction ? 1.0 : traction / fullDrive;
    }

    /** Grip available now, m/s², as {@link DynamicModel#step} computes it. */
    private double gripLimit(Car car) {
        double mass = model.getSpec().mass() + car.getFuel();
        double mu = model.frictionCoefficient(car.getTireWear());

        if (model.getRules().tyreTemperature()) {
            mu *= model.getTire().temperatureGrip(car.getTireTemperature());
        }

        return mu * (DynamicModel.GRAVITY + downforce(car.getSpeed()) / mass);
    }
}
