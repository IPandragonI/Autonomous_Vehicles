package org.autonomous_vehicles.neural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActivationFunctionTest {

    @Test
    public void testSigmoid() {
        ActivationFunction sigmoid = new Sigmoid();
        assertEquals(0.5, sigmoid.activate(0), 1e-9);
        assertEquals(0.9999546021312976, sigmoid.activate(10), 1e-9);
        assertEquals(4.5397868702434395e-05, sigmoid.activate(-10), 1e-9);

        //test derivative
        assertEquals(0.25, sigmoid.derivative(0), 1e-9);
        assertEquals(4.5395807735907655e-05, sigmoid.derivative(10), 1e-9);
        assertEquals(4.5395807735907655e-05, sigmoid.derivative(-10), 1e-9);
    }

    @Test
    public void testTanh() {
        ActivationFunction tanh = new Tanh();
        assertEquals(0.0, tanh.activate(0), 1e-9);
        assertEquals(0.9999999958776927, tanh.activate(10), 1e-9);
        assertEquals(-0.9999999958776927, tanh.activate(-10), 1e-9);

        //test derivative
        assertEquals(1.0, tanh.derivative(0), 1e-9);
        assertEquals(1.0 - 0.9999999958776927 * 0.9999999958776927, tanh.derivative(10), 1e-9);
        assertEquals(1.0 - (-0.9999999958776927) * (-0.9999999958776927), tanh.derivative(-10), 1e-9);
    }

    @Test
    public void testReLU() {
        ActivationFunction relu = new ReLU();
        assertEquals(0.0, relu.activate(0), 1e-9);
        assertEquals(10.0, relu.activate(10), 1e-9);
        assertEquals(0.0, relu.activate(-10), 1e-9);

        //test derivative
        assertEquals(0.0, relu.derivative(0), 1e-9);
        assertEquals(1.0, relu.derivative(10), 1e-9);
        assertEquals(0.0, relu.derivative(-10), 1e-9);
    }
}
