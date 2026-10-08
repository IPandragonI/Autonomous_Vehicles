package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class NetworkIOTest {

    @Test
    public void testSaveAndLoad() throws IOException {
        NeuralNetwork original = new NeuralNetwork(new Rng(42), 5, 6, 2);

        Path path = Files.createTempFile("network", ".txt");

        NetworkIO.save(original, path);
        NeuralNetwork loaded = NetworkIO.load(path);

        double[] inputs = {1.0, 2.0, 3.0, 4.0, 5.0};

        double[] originalOutput = original.forward(inputs);
        double[] loadedOutput = loaded.forward(inputs);

        assertArrayEquals(originalOutput, loadedOutput);
        assertArrayEquals(original.getArchitecture(), loaded.getArchitecture());
        assertArrayEquals(original.getGenome(), loaded.getGenome());

        Files.deleteIfExists(path);
    }
}
