package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * Shared fixtures: a hand-wired driving network and a simple square ring track.
 */
public final class TestDrivers {

    private TestDrivers() {
    }

    /**
     * 5-2 network (no hidden layer) that steers towards the more open side and holds a constant throttle:
     * steer = tanh(5 × (left45 - right45)), throttle = 0.6. It completes laps of the oval.
     */
    public static NeuralNetwork follower() {
        NeuralNetwork network = new NeuralNetwork(new Rng(1), 5, 2);
        double[] genome = new double[network.getParameterCount()];
        genome[1] = -5.0;
        genome[3] = 5.0;
        genome[11] = 0.5 * Math.log((1 + 0.6) / (1 - 0.6));
        network.setGenome(genome);
        return network;
    }

    /**
     * 5-6-2 network whose weights and biases are all zero.
     */
    public static NeuralNetwork zeroNetwork() {
        NeuralNetwork network = new NeuralNetwork(new Rng(1), 5, 6, 2);
        network.setGenome(new double[network.getParameterCount()]);
        return network;
    }

    /**
     * Square ring: outer square [-50, 50]², inner square [-10, 10]², start (30, 0) heading north (+y).
     */
    public static Track squareRing() {
        List<Vec2> outer = List.of(new Vec2(-50, -50), new Vec2(50, -50), new Vec2(50, 50), new Vec2(-50, 50));
        List<Vec2> inner = List.of(new Vec2(-10, -10), new Vec2(10, -10), new Vec2(10, 10), new Vec2(-10, 10));
        return new Track(outer, inner, new Vec2(30, 0), Math.PI / 2);
    }
}
