package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TrainerTest {

    @Test
    public void testCostDecreasesAND() {
        LogicDataset dataset = LogicDataset.and();

        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 4, 1);
        Trainer trainer = new Trainer(network, 0.05);

        double initialLoss = trainer.evaluateLoss(dataset);

        for (int i = 0; i < 2000; i++) {
            trainer.trainEpoch(dataset);
        }

        double finalLoss = trainer.evaluateLoss(dataset);

        assertTrue(finalLoss < initialLoss, "Training should decrease the loss.");
    }

    @Test
    public void testMiniBatchDecreasesLoss() {
        LogicDataset dataset = LogicDataset.and();

        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 4, 1);
        Trainer trainer = new Trainer(network, 0.05);

        double initialLoss = trainer.evaluateLoss(dataset);

        for (int i = 0; i < 2000; i++) {
            trainer.trainMiniBatch(dataset, 2);
        }

        double finalLoss = trainer.evaluateLoss(dataset);

        assertTrue(finalLoss < initialLoss, "Mini-batch training should decrease the loss.");
    }

    @Test
    public void testGradientGenomeSize() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 3, 1);

        network.forward(new double[]{0.3, -0.7});
        network.backward(new double[]{0.5});

        assertEquals(network.getParameterCount(), network.getGradientGenome().length);
    }

    @Test
    public void testGradientChecking() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 3, 1);

        double[] inputs = {0.3, -0.7};
        double target = 0.8;

        double[] predictions = network.forward(inputs);
        double[] outputGradient = {Loss.mseDerivative(predictions[0], target)};

        network.backward(outputGradient);
        double[] analyticalGradients = network.getGradientGenome();

        double[] originalGenome = network.getGenome();
        double[] numericalGradients = new double[originalGenome.length];
        double epsilon = 1e-6;

        for (int i = 0; i < originalGenome.length; i++) {
            double[] plus = originalGenome.clone();
            plus[i] += epsilon;
            network.setGenome(plus);

            double lossPlus = Loss.mse(network.forward(inputs)[0], target);

            double[] minus = originalGenome.clone();
            minus[i] -= epsilon;
            network.setGenome(minus);

            double lossMinus = Loss.mse(network.forward(inputs)[0], target);

            numericalGradients[i] = (lossPlus - lossMinus) / (2.0 * epsilon);}

        network.setGenome(originalGenome);

        for (int i = 0; i < originalGenome.length; i++) {
            double analytical = analyticalGradients[i];
            double numerical = numericalGradients[i];

            double relativeError = Math.abs(analytical - numerical) / Math.max(1e-8, Math.abs(analytical) + Math.abs(numerical));

            assertTrue(relativeError < 1e-5, "Gradient mismatch at parameter " + i + ": analytical=" + analytical + ", numerical=" + numerical + ", relative error=" + relativeError);
        }
    }

    @Test
    public void testInvalidLearningRateThrows() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 3, 1);

        assertThrows(IllegalArgumentException.class, () -> new Trainer(network, 0.0));

        assertThrows(IllegalArgumentException.class, () -> new Trainer(network, -0.1));
    }

    @Test
    public void testInvalidBatchSizeThrows() {
        LogicDataset dataset = LogicDataset.and();

        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 3, 1);
        Trainer trainer = new Trainer(network, 0.05);

        assertThrows(IllegalArgumentException.class, () -> trainer.trainMiniBatch(dataset, 0));
    }

    @Test
    public void testPredict() {
        NeuralNetwork network = new NeuralNetwork(new Rng(42), 2, 3, 1);
        Trainer trainer = new Trainer(network, 0.05);

        double prediction = trainer.predict(new double[]{1.0, 0.0});

        assertTrue(Double.isFinite(prediction));
        assertTrue(prediction >= -1.0 && prediction <= 1.0);
    }

    @Test
    public void testLearnsXor() {
        LogicDataset dataset = LogicDataset.xor();

        NeuralNetwork network = new NeuralNetwork(new Rng(42), new Sigmoid(), 2, 3, 1);

        Trainer trainer = new Trainer(network, 0.5);

        for (int epoch = 0; epoch < 10000; epoch++) {
            trainer.trainEpoch(dataset);
        }

        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        for (int i = 0; i < inputs.length; i++) {
            double prediction = trainer.predict(inputs[i]);
            int predictedClass = prediction >= 0.5 ? 1 : 0;
            int expectedClass = (int) targets[i];

            assertEquals(expectedClass, predictedClass, "XOR échoue pour l'entrée " + inputs[i][0] + ", " + inputs[i][1] + " ; prédiction = " + prediction);
        }
    }
}
