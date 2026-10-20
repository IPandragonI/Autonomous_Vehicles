package org.autonomous_vehicles.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * What the "Brain" tab says about a {@link NetworkSnapshot} in words: size, the decision just taken and which
 * inputs drive it (plain Java, unit-tested).
 *
 * @param architecture   "8-6-2"
 * @param parameterCount weights + biases (the genome length)
 * @param steer          first output, -1 (full right) … +1 (full left)
 * @param throttle       second output, negative = braking
 * @param influences     every input with its share of the first layer's drive, largest first
 * @param saturated      fraction of hidden neurons stuck near ±1 (they no longer react to small input changes)
 */
public record BrainSummary(String architecture, int parameterCount, double steer, double throttle,
                           List<Influence> influences, double saturated) {

    /** A hidden neuron whose |activation| exceeds this is saturated (tanh: the slope is below 10 %). */
    static final double SATURATION = 0.95;

    /**
     * @param share fraction of the total influence, 0–1: |input| × Σ |weights leaving it|, normalised
     */
    public record Influence(String label, double value, double share) {
    }

    public BrainSummary {
        influences = List.copyOf(influences);
    }

    /**
     * @param inputLabels name of each input; missing names become "in&lt;n&gt;"
     */
    public static BrainSummary of(NetworkSnapshot snapshot, List<String> inputLabels) {
        if (snapshot == null || inputLabels == null) {
            throw new IllegalArgumentException("Snapshot and labels must not be null.");
        }

        int[] architecture = snapshot.architecture();
        int parameters = 0;

        for (int l = 1; l < architecture.length; l++) {
            parameters += architecture[l] * (architecture[l - 1] + 1);
        }

        double[] raw = new double[architecture[0]];
        double total = 0.0;

        for (int i = 0; i < raw.length; i++) {
            double outgoing = 0.0;

            for (int n = 0; n < architecture[1]; n++) {
                outgoing += Math.abs(snapshot.weight(0, n, i));
            }

            raw[i] = Math.abs(snapshot.activation(0, i)) * outgoing;
            total += raw[i];
        }

        List<Influence> influences = new ArrayList<>();

        for (int i = 0; i < raw.length; i++) {
            influences.add(new Influence(i < inputLabels.size() ? inputLabels.get(i) : "in" + i,
                    snapshot.activation(0, i), total > 0.0 ? raw[i] / total : 0.0));
        }

        influences.sort(Comparator.comparingDouble(Influence::share).reversed());

        int hidden = 0, saturated = 0;

        for (int l = 1; l < architecture.length - 1; l++) {
            for (int n = 0; n < architecture[l]; n++) {
                hidden++;

                if (Math.abs(snapshot.activation(l, n)) > SATURATION) {
                    saturated++;
                }
            }
        }

        // Clamped like CarBrain does before driving, so the words match what the car actually does.
        double[] outputs = snapshot.outputs();
        return new BrainSummary(Arrays.stream(architecture).mapToObj(String::valueOf).collect(Collectors.joining("-")),
                parameters, Math.clamp(outputs[0], -1.0, 1.0),
                outputs.length > 1 ? Math.clamp(outputs[1], -1.0, 1.0) : 0.0, influences,
                hidden == 0 ? 0.0 : (double) saturated / hidden);
    }

    /**
     * "turning left 35 % · accelerating 80 %", in the dashboard's convention (positive steer = left).
     */
    public String decision() {
        String turn = Math.abs(steer) < 0.05 ? "straight ahead"
                : String.format(Locale.ROOT, "turning %s %.0f %%", steer > 0 ? "left" : "right", 100 * Math.abs(steer));
        String pedal = Math.abs(throttle) < 0.05 ? "coasting"
                : String.format(Locale.ROOT, "%s %.0f %%", throttle > 0 ? "accelerating" : "braking",
                100 * Math.abs(throttle));
        return turn + " · " + pedal;
    }
}
