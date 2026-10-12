package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TrainerTest {

    @Test
    public void testCostDecreasesAND() {
        LogicDataset dataset = LogicDataset.and();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        double initialCost = trainer.evaluateLoss(dataset);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double finalCost = trainer.evaluateLoss(dataset);

        assertTrue(finalCost < initialCost);
    }

    @Test
    public void testLearnsAND() {
        LogicDataset dataset = LogicDataset.and();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        for (int i = 0; i < inputs.length; i++) {
            int predicted = trainer.predict(inputs[i]) >= 0.5 ? 1 : 0;
            assertEquals((int) targets[i], predicted);
        }
    }

    @Test
    public void testCostDecreasesOR() {
        LogicDataset dataset = LogicDataset.or();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        double initialCost = trainer.evaluateLoss(dataset);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double finalCost = trainer.evaluateLoss(dataset);

        assertTrue(finalCost < initialCost);
    }

    @Test
    public void testLearnsOR() {
        LogicDataset dataset = LogicDataset.or();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        for (int i = 0; i < inputs.length; i++) {
            int predicted = trainer.predict(inputs[i]) >= 0.5 ? 1 : 0;
            assertEquals((int) targets[i], predicted);
        }
    }

    @Test
    public void testCostDecreasesXOR() {
        LogicDataset dataset = LogicDataset.xor();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double finalCost = trainer.evaluateLoss(dataset);

        assertTrue(finalCost > 0.1);
    }

    @Test
    public void testLearnsXOR() {
        LogicDataset dataset = LogicDataset.xor();
        Trainer trainer = new Trainer(dataset.inputs()[0].length, 0.5);

        for (int i = 0; i < 5000; i++) {
            trainer.trainEpoch(dataset);
        }

        double[][] inputs = dataset.inputs();
        double[] targets = dataset.targets();

        int correctPredictions = 0;

        for (int i = 0; i < inputs.length; i++) {
            int predicted = trainer.predict(inputs[i]) >= 0.5 ? 1 : 0;

            if (predicted == (int) targets[i]) {
                correctPredictions++;
            }
        }

        assertTrue(correctPredictions < 4);
    }
}
