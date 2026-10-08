package org.autonomous_vehicles.neural;

import org.autonomous_vehicles.util.Rng;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class NetworkIO {

    public static void save(NeuralNetwork network, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        int[] architecture = network.getArchitecture();

        for (int i = 0; i < architecture.length; i++) {
            sb.append(architecture[i]);
            if (i < architecture.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("\n");

        double[] genome = network.getGenome();
        for (double value : genome) {
            sb.append(value).append("\n");
        }

        Files.writeString(path, sb.toString());
    }

    public static NeuralNetwork load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            throw new IOException("File is empty");
        }
        if (lines.size() < 2) {
            throw new IOException("File is missing architecture information");
        }
        String[] architectureValues = lines.getFirst().trim().split("\\s+");
        int[] architecture = new int[architectureValues.length];
        for (int i = 0; i < architectureValues.length; i++) {
            architecture[i] = Integer.parseInt(architectureValues[i]);
        }
        double[] genome = new double[lines.size() - 1];
        for (int i = 1; i < lines.size(); i++) {
            genome[i - 1] = Double.parseDouble(lines.get(i).trim());
        }
        NeuralNetwork network = new NeuralNetwork(new Rng(0), architecture);
        network.setGenome(genome);
        return network;
    }
}
