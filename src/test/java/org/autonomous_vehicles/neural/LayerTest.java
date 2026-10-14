package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LayerTest {

    @Test
    public void testForward() {
        Neuron neuron1 = new Neuron(new double[]{0.5, -0.5}, 0.1, new Sigmoid());
        Neuron neuron2 = new Neuron(new double[]{-0.3, 0.8}, -0.2, new Sigmoid());
        Layer layer = new Layer(new Neuron[]{neuron1, neuron2});

        double[] inputs = {1.0, 2.0};
        double[] outputs = layer.forward(inputs);

        assertEquals(0.401312339887548, outputs[0], 1e-9);
        assertEquals(0.7502601055951177, outputs[1], 1e-9);
    }

    @Test
    public void testRandomLayer() {
        Rng rng = new Rng(42);
        Layer layer = Layer.random(3, 2, new Sigmoid(), rng);

        assertEquals(2, layer.forward(new double[]{1.0, 1.0, 1.0}).length);
    }


    @Test
    public void testBackward() {
        Neuron neuron1 = new Neuron(new double[]{0.4, -0.4}, 0.1, new Tanh());
        Neuron neuron2 = new Neuron(new double[]{-0.2, 0.5}, -0.1, new Tanh());

        Layer layer = new Layer(new Neuron[]{neuron1, neuron2});

        double[] inputs = {0.5, -0.2};
        layer.forward(inputs);

        double[] dLossDActivations = {0.7, -0.4};
        double[] dLossDInputs = layer.backward(dLossDActivations);

        double z1 = 0.38;
        double z2 = -0.30;

        double delta1 = 0.7 * (1.0 - Math.pow(Math.tanh(z1), 2));
        double delta2 = -0.4 * (1.0 - Math.pow(Math.tanh(z2), 2));

        double[][] weightGradients = layer.getLastWeightGradients();
        double[] biasGradients = layer.getLastBiasGradients();
        double[] deltas = layer.getLastDeltas();

        Assertions.assertNotNull(deltas);
        assertEquals(delta1, deltas[0], 1e-9);
        assertEquals(delta2, deltas[1], 1e-9);

        Assertions.assertNotNull(weightGradients);
        assertEquals(delta1 * 0.5, weightGradients[0][0], 1e-9);
        assertEquals(delta1 * -0.2, weightGradients[0][1], 1e-9);
        assertEquals(delta2 * 0.5, weightGradients[1][0], 1e-9);
        assertEquals(delta2 * -0.2, weightGradients[1][1], 1e-9);

        Assertions.assertNotNull(biasGradients);
        assertEquals(delta1, biasGradients[0], 1e-9);
        assertEquals(delta2, biasGradients[1], 1e-9);

        assertEquals(delta1 * 0.4 + delta2 * -0.2, dLossDInputs[0], 1e-9);
        assertEquals(delta1 * -0.4 + delta2 * 0.5, dLossDInputs[1], 1e-9);
    }


    @Test
    public void testBackwardBeforeForwardThrows() {
        Neuron neuron = new Neuron(new double[]{0.4, -0.4}, 0.1, new Tanh());
        Layer layer = new Layer(new Neuron[]{neuron});

        assertThrows(IllegalStateException.class, () -> layer.backward(new double[]{1.0}));
    }


}
