package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.neural.NeuralNetwork;

/**
 * What the network view displays: weights, biases and the activations of the last forward pass,
 * with the inputs as layer 0. Copied from the network so the view never reads changing state.
 *
 * @param activations [layer][neuron], layer 0 = inputs, last layer = outputs (steer, throttle)
 * @param weights     [layer][neuron][input], as returned by {@link NeuralNetwork#getWeights()}
 */
public record NetworkSnapshot(int[] architecture, double[][] activations, double[][][] weights, double[][] biases) {

    /**
     * @return null if the network has not run a forward pass yet
     */
    public static NetworkSnapshot of(NeuralNetwork network, double[] inputs) {
        double[][] layerOutputs = network.getLastActivations();

        if (layerOutputs == null || inputs == null) {
            return null;
        }

        double[][] activations = new double[layerOutputs.length + 1][];
        activations[0] = inputs.clone();
        System.arraycopy(layerOutputs, 0, activations, 1, layerOutputs.length);

        return new NetworkSnapshot(network.getArchitecture(), activations, network.getWeights(), network.getBiases());
    }

    public double activation(int layer, int neuron) {
        return activations[layer][neuron];
    }

    public double weight(int layer, int neuron, int input) {
        return weights[layer][neuron][input];
    }

    public double[] outputs() {
        return activations[activations.length - 1].clone();
    }
}
