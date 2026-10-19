package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.neural.NeuralNetwork;

/**
 * Connects a neural network to a car: sensors → forward → {@link CarControls}.
 * <p>
 * Inputs: the normalised ray distances in [0, 1], optionally followed by the speed mapped to [0, 1]
 * (0 = full reverse, 0.5 = stopped, 1 = full speed). Outputs: two Tanh values in [-1, 1],
 * read as (steer, throttle). A network whose weights and biases are all zero outputs tanh(0) = 0
 * for both, i.e. neutral controls: the car stays still on the start line.
 */
public class CarBrain {

    public static final int OUTPUT_SIZE = 2;

    private final NeuralNetwork network;
    private final RaySensor sensor;
    private final boolean useSpeed;
    private final double speedScale;

    private double[] lastInputs;
    private double[] lastOutputs;

    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed) {
        this(network, sensor, useSpeed, CarPhysics.MAX_SPEED);
    }

    /**
     * @param speedScale speed read as the input 1.0 (the top speed of the car model)
     */
    public CarBrain(NeuralNetwork network, RaySensor sensor, boolean useSpeed, double speedScale) {
        if (!(speedScale > 0.0)) {
            throw new IllegalArgumentException("Speed scale must be positive.");
        }

        if (network == null || sensor == null) {
            throw new IllegalArgumentException("Network and sensor must not be null.");
        }

        int[] architecture = network.getArchitecture();

        if (architecture[0] != inputSize(sensor, useSpeed)) {
            throw new IllegalArgumentException("Network expects " + architecture[0] + " inputs but the sensors provide "
                    + inputSize(sensor, useSpeed) + ".");
        }

        if (architecture[architecture.length - 1] != OUTPUT_SIZE) {
            throw new IllegalArgumentException("Network must have exactly two outputs (steer, throttle).");
        }

        this.network = network;
        this.sensor = sensor;
        this.useSpeed = useSpeed;
        this.speedScale = speedScale;
    }

    public static int inputSize(RaySensor sensor, boolean useSpeed) {
        return sensor.getRayCount() + (useSpeed ? 1 : 0);
    }

    public double[] inputs(Car car, Track track) {
        double[] rays = sensor.sense(car, track);

        if (!useSpeed) {
            return rays;
        }

        double[] inputs = new double[rays.length + 1];
        System.arraycopy(rays, 0, inputs, 0, rays.length);
        inputs[rays.length] = Math.clamp((car.getSpeed() / speedScale + 1.0) / 2.0, 0.0, 1.0);

        return inputs;
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
