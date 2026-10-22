package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Connects a neural network to a car: sensors → forward → {@link CarControls}.
 * <p>
 * Inputs: the normalised ray distances in [0, 1], optionally followed by the speed mapped to [0, 1]
 * (0 = full reverse, 0.5 = stopped, 1 = full speed), optionally followed by the {@link #CAR_STATE_INPUTS} car state
 * inputs of {@link #carState} (fuel, tyre temperature, tyre wear, sliding), optionally followed by the
 * {@link TrackPreview#INPUTS} road-ahead readings of {@link TrackPreview} in [-1, 1]. Outputs: two Tanh values in [-1, 1],
 * read as (steer, throttle). A network whose weights and biases are all zero outputs tanh(0) = 0
 * for both, i.e. neutral controls: the car stays still on the start line.
 */
public class CarBrain {

    public static final int OUTPUT_SIZE = 2;

    /** Fuel left, tyre temperature, tyre wear, sliding: see {@link #carState}. */
    public static final int CAR_STATE_INPUTS = 4;
    public static final List<String> CAR_STATE_LABELS = List.of("fuel", "tyre °C", "wear", "slide");

    private final NeuralNetwork network;
    private final RaySensor sensor;
    private final boolean useSpeed;
    private final double speedScale;
    private final boolean useCarState;
    /** Compound whose window the temperature input is read against; null without tyre temperature. */
    private final TireCompound temperatureCompound;
    private final boolean useTrackPreview;

    /** Centre-line segment the car was nearest to at the last reading (search hint), -1 before the first one. */
    private int previewSegment = -1;
    private double[] lastInputs;
    private double[] lastOutputs;

    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed) {
        this(network, sensor, useSpeed, CarPhysics.MAX_SPEED);
    }

    /**
     * @param speedScale speed read as the input 1.0 (the top speed of the car model)
     */
    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, double speedScale) {
        this(network, sensor, useSpeed, speedScale, false, null);
    }

    /**
     * @param useCarState also feed the car state ({@link #carState}) to the network
     * @param vehicle     gives the speed scale (its top speed) and, for a dynamic model with tyre temperature, the
     *                    compound window the temperature input is relative to
     */
    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, boolean useCarState,
                    VehicleModel vehicle) {
        this(network, sensor, useSpeed, useCarState, false, vehicle);
    }

    /**
     * @param useTrackPreview also feed the road ahead ({@link TrackPreview}) to the network
     */
    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, boolean useCarState,
                    boolean useTrackPreview, VehicleModel vehicle) {
        this(network, sensor, useSpeed, vehicle == null ? CarPhysics.MAX_SPEED : vehicle.topSpeed(), useCarState,
                vehicle instanceof DynamicModel model && model.getRules().tyreTemperature() ? model.getTire() : null,
                useTrackPreview);

        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle must not be null.");
        }
    }

    private CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, double speedScale,
                     boolean useCarState, TireCompound temperatureCompound) {
        this(network, sensor, useSpeed, speedScale, useCarState, temperatureCompound, false);
    }

    private CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, double speedScale,
                     boolean useCarState, TireCompound temperatureCompound, boolean useTrackPreview) {
        if (!(speedScale > 0.0)) {
            throw new IllegalArgumentException("Speed scale must be positive.");
        }

        if (network == null || sensor == null) {
            throw new IllegalArgumentException("Network and sensor must not be null.");
        }

        int[] architecture = network.getArchitecture();
        int expected = inputSize(sensor, useSpeed, useCarState, useTrackPreview);

        if (architecture[0] != expected) {
            throw new IllegalArgumentException("Network expects " + architecture[0] + " inputs but the sensors provide "
                    + expected + ".");
        }

        if (architecture[architecture.length - 1] != OUTPUT_SIZE) {
            throw new IllegalArgumentException("Network must have exactly two outputs (steer, throttle).");
        }

        this.network = network;
        this.sensor = sensor;
        this.useSpeed = useSpeed;
        this.speedScale = speedScale;
        this.useCarState = useCarState;
        this.temperatureCompound = temperatureCompound;
        this.useTrackPreview = useTrackPreview;
    }

    public static int inputSize(RaySensor sensor, boolean useSpeed) {
        return inputSize(sensor, useSpeed, false);
    }

    public static int inputSize(RaySensor sensor, boolean useSpeed, boolean useCarState) {
        return inputSize(sensor, useSpeed, useCarState, false);
    }

    public static int inputSize(RaySensor sensor, boolean useSpeed, boolean useCarState, boolean useTrackPreview) {
        return sensor.getRayCount() + (useSpeed ? 1 : 0) + (useCarState ? CAR_STATE_INPUTS : 0)
                + (useTrackPreview ? TrackPreview.INPUTS : 0);
    }

    public double[] inputs(Car car, Track track) {
        double[] rays = sensor.sense(car, track);

        if (!useSpeed && !useCarState && !useTrackPreview) {
            return rays;
        }

        double[] inputs = new double[inputSize(sensor, useSpeed, useCarState, useTrackPreview)];
        System.arraycopy(rays, 0, inputs, 0, rays.length);
        int next = rays.length;

        if (useSpeed) {
            inputs[next++] = Math.clamp((car.getSpeed() / speedScale + 1.0) / 2.0, 0.0, 1.0);
        }

        if (useCarState) {
            System.arraycopy(carState(car, temperatureCompound), 0, inputs, next, CAR_STATE_INPUTS);
            next += CAR_STATE_INPUTS;
        }

        if (useTrackPreview) {
            TrackPreview preview = track.getPreview();
            previewSegment = preview.locate(car.getX(), car.getY(), previewSegment);
            preview.read(car.getX(), car.getY(), car.getAngle(), previewSegment, inputs, next);
        }

        return inputs;
    }

    /**
     * Car state as four inputs in [0, 1]:
     * <ol>
     *     <li>fuel left / starting load (1 with unlimited fuel);</li>
     *     <li>tyre temperature relative to the compound's window, so one reading means the same for every compound:
     *     0.25–0.75 inside the window (0.5 = its middle), down to 0 when {@link TireCompound#COLD_SPAN} below it
     *     (fully cold), up to 1 when {@link TireCompound#HOT_SPAN} above it (fully overheated) — the same breakpoints
     *     as the grip loss, so the input tracks the grip; 0.5 without tyre temperature;</li>
     *     <li>tyre wear, 0 = new, 1 = worn out;</li>
     *     <li>1 if the tyres slid during the last step, else 0.</li>
     * </ol>
     * The kinematic car reads a constant (1, 0.5, 0, 0): a full tank of ideal tyres that never slide.
     *
     * @param compound compound whose window the temperature is read against, null without tyre temperature
     */
    public static double[] carState(Car car, TireCompound compound) {
        return new double[]{
                Math.clamp(car.getFuelFraction(), 0.0, 1.0),
                compound == null ? 0.5 : temperatureInput(car.getTireTemperature(), compound),
                Math.clamp(car.getTireWear(), 0.0, 1.0),
                car.isSliding() ? 1.0 : 0.0};
    }

    static double temperatureInput(double celsius, TireCompound compound) {
        if (celsius < compound.optimalLow()) {
            return 0.25 * Math.max(0.0, 1.0 - (compound.optimalLow() - celsius) / TireCompound.COLD_SPAN);
        }

        if (celsius > compound.optimalHigh()) {
            return 0.75 + 0.25 * Math.min(1.0, (celsius - compound.optimalHigh()) / TireCompound.HOT_SPAN);
        }

        return 0.25 + 0.5 * (celsius - compound.optimalLow()) / (compound.optimalHigh() - compound.optimalLow());
    }

    /**
     * Name of every input, in order ("R 90°" … "L 90°" for the rays, then "speed" and the car state), for display.
     */
    public List<String> inputLabels() {
        List<String> labels = new ArrayList<>();

        for (double angle : sensor.getAngles()) {
            long degrees = Math.round(Math.toDegrees(angle));
            labels.add(degrees == 0 ? "0°" : (degrees < 0 ? "R " : "L ") + Math.abs(degrees) + "°");
        }

        if (useSpeed) {
            labels.add("speed");
        }

        if (useCarState) {
            labels.addAll(CAR_STATE_LABELS);
        }

        if (useTrackPreview) {
            for (double distance : TrackPreview.DISTANCES) {
                labels.add(String.format(java.util.Locale.ROOT, "road %.0f m", distance));
            }
        }

        return labels;
    }

    public boolean usesCarState() {
        return useCarState;
    }

    public boolean usesTrackPreview() {
        return useTrackPreview;
    }

    public CarControls decide(Car car, Track track) {
        lastInputs = inputs(car, track);
        lastOutputs = network.forward(lastInputs);

        return new CarControls(bound(lastOutputs[0]), bound(lastOutputs[1]));
    }

    private static double bound(double value) {
        return Double.isFinite(value) ? Math.clamp(value, -1.0, 1.0) : 0.0;
    }

    public NeuralNetwork getNetwork() {
        return network;
    }

    public RaySensor getSensor() {
        return sensor;
    }

    public double[] getLastInputs() {
        return lastInputs == null ? null : lastInputs.clone();
    }

    public double[] getLastOutputs() {
        return lastOutputs == null ? null : lastOutputs.clone();
    }
}
