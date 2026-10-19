package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tools to read a network and its history (step 38): distances between genomes,
 * weight histograms, and probing a driving network with typical sensor situations.
 */
public final class GenomeAnalysis {

    private GenomeAnalysis() {
    }

    public static double distance(double[] first, double[] second) {
        requireSameLength(first, second);
        double sum = 0.0;

        for (int i = 0; i < first.length; i++) {
            sum += (first[i] - second[i]) * (first[i] - second[i]);
        }

        return Math.sqrt(sum);
    }

    public static double meanAbsoluteDifference(double[] first, double[] second) {
        requireSameLength(first, second);
        double sum = 0.0;

        for (int i = 0; i < first.length; i++) {
            sum += Math.abs(first[i] - second[i]);
        }

        return first.length == 0 ? 0.0 : sum / first.length;
    }

    /**
     * Cosine of the angle between two genomes: 1 = same direction, 0 = unrelated.
     */
    public static double cosineSimilarity(double[] first, double[] second) {
        requireSameLength(first, second);
        double dot = 0.0, normFirst = 0.0, normSecond = 0.0;

        for (int i = 0; i < first.length; i++) {
            dot += first[i] * second[i];
            normFirst += first[i] * first[i];
            normSecond += second[i] * second[i];
        }

        return normFirst == 0.0 || normSecond == 0.0 ? 0.0 : dot / Math.sqrt(normFirst * normSecond);
    }

    private static void requireSameLength(double[] first, double[] second) {
        if (first == null || second == null || first.length != second.length) {
            throw new IllegalArgumentException("Genomes must have the same length.");
        }
    }

    /**
     * Counts of the values in {@code bins} equal-width bins over [min, max]; values outside go to the end bins.
     */
    public static int[] histogram(double[] values, int bins, double min, double max) {
        if (bins < 1 || !(max > min)) {
            throw new IllegalArgumentException("Need at least one bin and max > min.");
        }

        int[] counts = new int[bins];

        for (double value : values) {
            int bin = (int) Math.floor((value - min) / (max - min) * bins);
            counts[Math.clamp(bin, 0, bins - 1)]++;
        }

        return counts;
    }

    public static String asciiHistogram(double[] values, int bins, double min, double max) {
        int[] counts = histogram(values, bins, min, max);
        int highest = 1;

        for (int count : counts) {
            highest = Math.max(highest, count);
        }

        StringBuilder text = new StringBuilder();
        double width = (max - min) / bins;

        for (int i = 0; i < bins; i++) {
            text.append(String.format(Locale.ROOT, "[%6.2f, %6.2f) %3d %s\n", min + i * width, min + (i + 1) * width,
                    counts[i], "#".repeat((int) Math.round(30.0 * counts[i] / highest))));
        }

        return text.toString();
    }

    /**
     * A typical sensor reading and the network's answer.
     */
    public record Probe(String situation, double[] inputs, double steer, double throttle) {
    }

    /**
     * Feeds a 5-ray driving network with typical situations (rays ordered right → left, values in [0, 1]).
     */
    public static List<Probe> probe(NeuralNetwork network) {
        if (network.getArchitecture()[0] != 5) {
            throw new IllegalArgumentException("Probing expects the 5-ray input layer.");
        }

        Object[][] situations = {
                {"Centred in a straight corridor", new double[]{0.15, 0.2, 1.0, 0.2, 0.15}},
                {"Close to the right wall", new double[]{0.03, 0.05, 1.0, 0.4, 0.27}},
                {"Close to the left wall", new double[]{0.27, 0.4, 1.0, 0.05, 0.03}},
                {"Right-hand bend ahead", new double[]{0.15, 0.6, 0.35, 0.12, 0.1}},
                {"Left-hand bend ahead", new double[]{0.1, 0.12, 0.35, 0.6, 0.15}},
                {"Wall straight ahead", new double[]{0.15, 0.15, 0.08, 0.15, 0.15}},
        };

        List<Probe> probes = new ArrayList<>();

        for (Object[] situation : situations) {
            double[] inputs = (double[]) situation[1];
            double[] outputs = network.forward(inputs);
            probes.add(new Probe((String) situation[0], inputs, outputs[0], outputs[1]));
        }

        return probes;
    }
}
