package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.util.Rng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Operations on the input side of a driving network, whose inputs are laid out as rays, then the speed (optional),
 * then the {@link CarBrain#CAR_STATE_INPUTS} car state inputs (optional) — see {@link SimulationConfig#inputSize()}.
 * <p>
 * A weight of zero from an input makes the network blind to it. Two uses:
 * <ul>
 *     <li>random networks start blind to the car state ({@link #silenceCarState}): with random weights those
 *     nearly constant inputs (full tank, new tyres) act as extra random biases. Measured on the hairpin with a
 *     hatchback, fuel and tyre temperature on, 25 generations, 6 seeds: champion progress 24.4 (0.33 laps) without
 *     the inputs, 14.3 with randomly weighted inputs, 29.8 (0.50 laps) with inputs that start blind;</li>
 *     <li>a champion trained without some inputs is widened to a configuration that has them ({@link #adapt}),
 *     with zero weights from the new inputs: it drives exactly as before, and retraining can learn to use them.</li>
 * </ul>
 */
public final class NetworkInputs {

    private NetworkInputs() {
    }

    /**
     * Same network with every first-layer weight from the car state inputs set to 0 (no effect without them).
     */
    public static NeuralNetwork silenceCarState(NeuralNetwork network, SimulationConfig config) {
        if (!config.useCarStateInputs()) {
            return network;
        }

        int first = config.rayCount() + (config.useSpeedInput() ? 1 : 0);
        double[][][] weights = network.getWeights();

        for (double[] neuron : weights[0]) {
            Arrays.fill(neuron, first, first + CarBrain.CAR_STATE_INPUTS, 0.0);
        }

        return build(network, network.getArchitecture(), weights, network.getBiases());
    }

    /**
     * {@code network}, built for {@code source}, rebuilt for {@code target}: same weights, plus zero weights from the
     * inputs {@code target} has and {@code source} lacks. Returns the network itself when the shapes already match,
     * and null when it cannot be adapted (other ray count or hidden layers, or {@code target} lacks one of its
     * inputs — dropping an input would change how it drives).
     */
    public static NeuralNetwork adapt(NeuralNetwork network, SimulationConfig source, SimulationConfig target) {
        if (Arrays.equals(network.getArchitecture(), target.architecture())) {
            return network;
        }

        if (!Arrays.equals(network.getArchitecture(), source.architecture())
                || source.rayCount() != target.rayCount()
                || !Arrays.equals(source.hiddenLayers(), target.hiddenLayers())
                || (source.useSpeedInput() && !target.useSpeedInput())
                || (source.useCarStateInputs() && !target.useCarStateInputs())) {
            return null;
        }

        int[] map = new int[source.inputSize()]; // source input index → target input index
        int rays = source.rayCount();

        for (int i = 0; i < rays; i++) {
            map[i] = i;
        }

        int targetSpeed = rays;
        int targetState = rays + (target.useSpeedInput() ? 1 : 0);
        int next = rays;

        if (source.useSpeedInput()) {
            map[next++] = targetSpeed;
        }

        if (source.useCarStateInputs()) {
            for (int j = 0; j < CarBrain.CAR_STATE_INPUTS; j++) {
                map[next++] = targetState + j;
            }
        }

        double[][][] weights = network.getWeights();
        double[][] firstLayer = weights[0];

        for (int n = 0; n < firstLayer.length; n++) {
            double[] widened = new double[target.inputSize()];

            for (int i = 0; i < map.length; i++) {
                widened[map[i]] = firstLayer[n][i];
            }

            firstLayer[n] = widened;
        }

        return build(network, target.architecture(), weights, network.getBiases());
    }

    /**
     * Random population blind to the car state (see the class comment); draws the same numbers as a plain random
     * population, so configurations without the car state inputs are unchanged.
     */
    static Population randomPopulation(SimulationConfig config, Rng rng) {
        Population random = new Population(config.populationSize(), config.architecture(), rng);

        if (!config.useCarStateInputs()) {
            return random;
        }

        List<Individual> blind = new ArrayList<>(random.size());

        for (Individual individual : random.getIndividuals()) {
            blind.add(new Individual(silenceCarState(individual.getNetwork(), config)));
        }

        return new Population(blind);
    }

    private static NeuralNetwork build(NeuralNetwork template, int[] architecture, double[][][] weights,
                                       double[][] biases) {
        NeuralNetwork network = new NeuralNetwork(new Rng(0), template.getActivation(), architecture);
        List<Double> genome = new ArrayList<>();

        for (int l = 0; l < weights.length; l++) {
            for (int n = 0; n < weights[l].length; n++) {
                for (double weight : weights[l][n]) {
                    genome.add(weight);
                }

                genome.add(biases[l][n]);
            }
        }

        network.setGenome(genome.stream().mapToDouble(Double::doubleValue).toArray());
        return network;
    }
}
