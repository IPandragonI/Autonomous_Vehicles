package org.autonomous_vehicles.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.Locale;

/**
 * Draws a {@link NetworkSnapshot}: neurons in columns, links whose thickness and opacity follow |weight|
 * (blue = positive, red = negative), neurons filled by their activation (same colour code).
 */
public class NetworkView extends Canvas {

    private static final String[] INPUT_LABELS = {"R 90°", "R 45°", "0°", "L 45°", "L 90°", "speed"};
    private static final String[] OUTPUT_LABELS = {"steer", "throttle"};

    public NetworkView(double width, double height) {
        super(width, height);
    }

    public void draw(NetworkSnapshot snapshot) {
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(Color.web("#111418"));
        g.fillRect(0, 0, getWidth(), getHeight());

        if (snapshot == null) {
            g.setFill(Color.GRAY);
            g.fillText("No car selected yet", 10, 20);
            return;
        }

        int[] architecture = snapshot.architecture();
        int layers = architecture.length;
        double left = 85, right = getWidth() - 100, top = 20, bottom = getHeight() - 20;
        double[][] xs = new double[layers][];
        double[][] ys = new double[layers][];

        for (int l = 0; l < layers; l++) {
            xs[l] = new double[architecture[l]];
            ys[l] = new double[architecture[l]];

            for (int n = 0; n < architecture[l]; n++) {
                xs[l][n] = left + (right - left) * l / Math.max(1, layers - 1);
                ys[l][n] = top + (bottom - top) * (n + 0.5) / architecture[l];
            }
        }

        double maxWeight = 1e-9;

        for (double[][] layer : snapshot.weights()) {
            for (double[] neuron : layer) {
                for (double weight : neuron) {
                    maxWeight = Math.max(maxWeight, Math.abs(weight));
                }
            }
        }

        for (int l = 1; l < layers; l++) {
            for (int n = 0; n < architecture[l]; n++) {
                for (int i = 0; i < architecture[l - 1]; i++) {
                    double weight = snapshot.weight(l - 1, n, i);
                    double strength = Math.abs(weight) / maxWeight;
                    g.setStroke(signColor(weight, 0.15 + 0.85 * strength));
                    g.setLineWidth(0.5 + 3.5 * strength);
                    g.strokeLine(xs[l - 1][i], ys[l - 1][i], xs[l][n], ys[l][n]);
                }
            }
        }

        double radius = Math.min(12.0, (bottom - top) / (2.5 * maxNeurons(architecture)));
        g.setTextBaseline(VPos.CENTER);

        for (int l = 0; l < layers; l++) {
            for (int n = 0; n < architecture[l]; n++) {
                double activation = snapshot.activation(l, n);
                g.setFill(signColor(activation, Math.min(1.0, Math.abs(activation))));
                g.fillOval(xs[l][n] - radius, ys[l][n] - radius, 2 * radius, 2 * radius);
                g.setStroke(Color.web("#d0d7de"));
                g.setLineWidth(1.0);
                g.strokeOval(xs[l][n] - radius, ys[l][n] - radius, 2 * radius, 2 * radius);

                g.setFill(Color.web("#d0d7de"));
                String value = String.format(Locale.ROOT, "%.2f", activation);

                if (l == 0) {
                    g.setTextAlign(TextAlignment.RIGHT);
                    g.fillText((n < INPUT_LABELS.length ? INPUT_LABELS[n] : "in" + n) + " " + value, xs[l][n] - radius - 4, ys[l][n]);
                } else if (l == layers - 1) {
                    g.setTextAlign(TextAlignment.LEFT);
                    g.fillText((n < OUTPUT_LABELS.length ? OUTPUT_LABELS[n] : "out" + n) + " " + value, xs[l][n] + radius + 4, ys[l][n]);
                }
            }
        }
    }

    private static int maxNeurons(int[] architecture) {
        int max = 1;

        for (int size : architecture) {
            max = Math.max(max, size);
        }

        return max;
    }

    private static Color signColor(double value, double opacity) {
        return value >= 0 ? Color.web("#4ea1ff", opacity) : Color.web("#ff5c5c", opacity);
    }
}
