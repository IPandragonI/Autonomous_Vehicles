package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NetworkTest {

    @Test
    public void testForward() {
        Neuron neuron1 = new Neuron(new double[]{0.5, -0.5}, 0.1, new Sigmoid());
        Neuron neuron2 = new Neuron(new double[]{-0.3, 0.8}, -0.2, new Sigmoid());
        Layer layer = new Layer(new Neuron[]{neuron1, neuron2});

        Network network = new Network(new Layer[]{layer});
        double[] inputs = {1.0, 2.0};
        double[] outputs = network.forward(inputs);

        assertEquals(0.401312339887548, outputs[0], 1e-9);
        assertEquals(0.7502601055951177, outputs[1], 1e-9);
    }

    @Test
    public void testForwardWithWrongInputLength() {
        Neuron neuron = new Neuron(new double[]{0.5, -0.5}, 0.1, new Sigmoid());

        Layer layer = new Layer(new Neuron[]{neuron});
        Network network = new Network(new Layer[]{layer});
        assertThrows(IllegalArgumentException.class, () -> network.forward(new double[]{1.0}));
    }
}
