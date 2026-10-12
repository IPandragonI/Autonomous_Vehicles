package org.autonomous_vehicles.neural;

public class Trainer {
    private final double[] weights;
    private double bias;
    private final double learningRate;
    private final ActivationFunction activationFunction;

    public Trainer(int inputSize, double learningRate) {
        this.weights = new double[inputSize];
        this.bias = 0.0;
        this.learningRate = learningRate;
        this.activationFunction = new Sigmoid();
    }

    public double predict(double[] inputs) {
        if (inputs.length != weights.length) {
            throw new IllegalArgumentException("Input size does not match weights size.");
        }
        double sum = bias;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * inputs[i];
        }
        return activationFunction.activate(sum);
    }

    public void trainEpoch(LogicDataset dataset) {
        for (int i = 0; i < dataset.inputs().length; i++) {
            double[] inputs = dataset.inputs()[i];
            double target = dataset.targets()[i];

            double prediction = predict(inputs);

            double delta = 2 * (prediction - target) * prediction * (1 - prediction);
            for (int j = 0; j < weights.length; j++) {
                weights[j] -= learningRate * delta * inputs[j];
            }
            bias -= learningRate * delta;
        }
    }

    public double evaluateLoss(LogicDataset dataset) {
        double totalLoss = 0.0;
        for (int i = 0; i < dataset.inputs().length; i++) {
            double[] inputs = dataset.inputs()[i];
            double target = dataset.targets()[i];

            double prediction = predict(inputs);
            double loss = Loss.mse(prediction, target);
            totalLoss += loss;
        }
        return totalLoss / dataset.inputs().length;
    }
}
