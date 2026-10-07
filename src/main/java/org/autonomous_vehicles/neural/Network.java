package org.autonomous_vehicles.neural;

public class Network {

    private final Layer[] layers;

    public Network(Layer[] layers) {
        this.layers = layers;
    }

    public double[] forward(double[] inputs) {
        double[] outputs = inputs;
        for (Layer layer : layers) {
            outputs = layer.forward(outputs);
        }
        return outputs;
    }

}
