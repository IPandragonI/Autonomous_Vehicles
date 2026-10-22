
package org.autonomous_vehicles.neural;

public class Trainer {

    private final NeuralNetwork network;
    private final double learningRate;

    public Trainer(NeuralNetwork network, double learningRate) {
        if (network == null) {
            throw new IllegalArgumentException("Network must not be null.");
        }

        if (!Double.isFinite(learningRate) || learningRate <= 0.0) {
            throw new IllegalArgumentException("Learning rate must be positive and finite.");
        }

        this.network = network;
        this.learningRate = learningRate;
    }

    public double predict(double[] inputs) {
        double[] outputs = network.forward(inputs);

        if (outputs.length != 1) {
            throw new IllegalStateException("predict() requires a network with one output.");
        }

        return outputs[0];
    }

    public void trainEpoch(LogicDataset dataset) {
        validateDataset(dataset);

        for (int i = 0; i < dataset.inputs().length; i++) {
            trainExample(dataset.inputs()[i], dataset.targets()[i]);
        }
    }

    private void trainExample(double[] inputs, double target) {
        double[] predictions = network.forward(inputs);

        if (predictions.length != 1) {
            throw new IllegalArgumentException("This trainer expects one output per example.");
        }

        double[] outputGradients = {
                Loss.mseDerivative(predictions[0], target)
        };

        network.backward(outputGradients);

        double[] gradients = network.getGradientGenome();
        network.applyGradients(gradients, learningRate);
    }

    public void trainMiniBatch(LogicDataset dataset, int batchSize) {
        validateDataset(dataset);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive.");
        }

        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        for (int start = 0; start < inputs.length; start += batchSize) {
            int end = Math.min(start + batchSize, inputs.length);
            double[] accumulatedGradients = new double[network.getParameterCount()];
            int actualBatchSize = end - start;

            for (int i = start; i < end; i++) {
                double[] predictions = network.forward(inputs[i]);

                if (predictions.length != 1) {throw new IllegalArgumentException("This trainer expects one output per example.");}

                double[] outputGradients = {Loss.mseDerivative(predictions[0], targets[i])};

                network.backward(outputGradients);

                double[] gradients = network.getGradientGenome();

                for (int j = 0; j < gradients.length; j++) {accumulatedGradients[j] += gradients[j];}
            }

            for (int j = 0; j < accumulatedGradients.length; j++) {
                accumulatedGradients[j] /= actualBatchSize;
            }

            network.applyGradients(accumulatedGradients, learningRate);
        }
    }

    /**
     * One pass of mini-batch gradient descent over examples with several outputs (e.g. steer and throttle), in the
     * given order: loss = mean over outputs of the squared error, gradients averaged over each batch.
     */
    public void trainMiniBatch(double[][] inputs, double[][] targets, int batchSize) {
        validateExamples(inputs, targets);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive.");
        }

        for (int start = 0; start < inputs.length; start += batchSize) {
            int end = Math.min(start + batchSize, inputs.length);
            double[] accumulatedGradients = new double[network.getParameterCount()];

            for (int i = start; i < end; i++) {
                double[] predictions = network.forward(inputs[i]);
                double[] outputGradients = new double[predictions.length];

                for (int k = 0; k < predictions.length; k++) {
                    outputGradients[k] = Loss.mseDerivative(predictions[k], targets[i][k]) / predictions.length;
                }

                network.backward(outputGradients);
                double[] gradients = network.getGradientGenome();

                for (int j = 0; j < gradients.length; j++) {
                    accumulatedGradients[j] += gradients[j];
                }
            }

            for (int j = 0; j < accumulatedGradients.length; j++) {
                accumulatedGradients[j] /= end - start;
            }

            network.applyGradients(accumulatedGradients, learningRate);
        }
    }

    /**
     * Mean over examples and outputs of the squared error.
     */
    public double evaluateLoss(double[][] inputs, double[][] targets) {
        validateExamples(inputs, targets);
        double total = 0.0;

        for (int i = 0; i < inputs.length; i++) {
            double[] predictions = network.forward(inputs[i]);

            for (int k = 0; k < predictions.length; k++) {
                total += Loss.mse(predictions[k], targets[i][k]) / predictions.length;
            }
        }

        return total / inputs.length;
    }

    private void validateExamples(double[][] inputs, double[][] targets) {
        int[] architecture = network.getArchitecture();

        if (inputs == null || targets == null || inputs.length == 0 || inputs.length != targets.length) {
            throw new IllegalArgumentException("Need matching, non-empty inputs and targets.");
        }

        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null || inputs[i].length != architecture[0] || targets[i] == null
                    || targets[i].length != architecture[architecture.length - 1]) {
                throw new IllegalArgumentException("Example " + i + " does not match the network's inputs or outputs.");
            }
        }
    }

    public double evaluateLoss(LogicDataset dataset) {
        validateDataset(dataset);

        double totalLoss = 0.0;
        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        for (int i = 0; i < inputs.length; i++) {
            double prediction = predict(inputs[i]);
            totalLoss += Loss.mse(prediction, targets[i]);
        }

        return totalLoss / inputs.length;
    }

    private void validateDataset(LogicDataset dataset) {
        if (dataset == null
                || dataset.inputs() == null
                || dataset.targets() == null
                || dataset.inputs().length == 0
                || dataset.inputs().length != dataset.targets().length) {
            throw new IllegalArgumentException("Dataset must contain matching, non-empty inputs and targets.");
        }

        int inputSize = network.getArchitecture()[0];

        for (double[] inputs : dataset.inputs()) {
            if (inputs == null || inputs.length != inputSize) {
                throw new IllegalArgumentException("Dataset input size does not match the network.");
            }
        }
    }

    public NeuralNetwork getNetwork() {
        return network;
    }
}
