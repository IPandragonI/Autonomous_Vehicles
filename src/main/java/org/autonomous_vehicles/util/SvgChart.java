package org.autonomous_vehicles.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal line chart written as a standalone SVG file (no dependency), used to overlay
 * experiment curves in the report. NaN values interrupt a line.
 */
public final class SvgChart {

    private static final String[] COLORS = {
            "#1f6feb", "#d1242f", "#1a7f37", "#9a6700", "#8250df", "#bf3989", "#0a7d8c", "#57606a"
    };

    private static final int WIDTH = 760;
    private static final int HEIGHT = 380;
    private static final int LEFT = 70;
    private static final int RIGHT = 200;
    private static final int TOP = 40;
    private static final int BOTTOM = 50;

    private SvgChart() {
    }

    public static String render(String title, String xLabel, String yLabel, Map<String, double[]> series) {
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int maxLength = 1;

        for (double[] values : series.values()) {
            maxLength = Math.max(maxLength, values.length);

            for (double value : values) {
                if (Double.isFinite(value)) {
                    minY = Math.min(minY, value);
                    maxY = Math.max(maxY, value);
                }
            }
        }

        if (!Double.isFinite(minY)) {
            minY = 0.0;
            maxY = 1.0;
        }

        if (maxY - minY < 1e-9) {
            maxY = minY + 1.0;
        }

        double padding = (maxY - minY) * 0.05;
        minY -= padding;
        maxY += padding;

        int plotWidth = WIDTH - LEFT - RIGHT;
        int plotHeight = HEIGHT - TOP - BOTTOM;
        double xScale = maxLength > 1 ? (double) plotWidth / (maxLength - 1) : 0.0;
        double yScale = plotHeight / (maxY - minY);

        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" "
                        + "font-family=\"sans-serif\" font-size=\"12\">\n", WIDTH, HEIGHT, WIDTH, HEIGHT));
        svg.append(String.format("<rect width=\"%d\" height=\"%d\" fill=\"white\"/>\n", WIDTH, HEIGHT));
        svg.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"22\" font-size=\"15\" font-weight=\"bold\">%s</text>\n",
                LEFT, escape(title)));

        for (int i = 0; i <= 5; i++) {
            double value = minY + (maxY - minY) * i / 5.0;
            double y = TOP + plotHeight - (value - minY) * yScale;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#e5e7eb\"/>"
                            + "<text x=\"%d\" y=\"%.1f\" text-anchor=\"end\" fill=\"#57606a\">%s</text>\n",
                    LEFT, y, LEFT + plotWidth, y, LEFT - 6, y + 4, format(value)));
        }

        int xTicks = Math.min(10, maxLength - 1);

        for (int i = 0; i <= xTicks && xTicks > 0; i++) {
            int generation = (int) Math.round((double) (maxLength - 1) * i / xTicks);
            double x = LEFT + generation * xScale;
            svg.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%d\" text-anchor=\"middle\" fill=\"#57606a\">%d</text>\n",
                    x, TOP + plotHeight + 18, generation));
        }

        svg.append(String.format(Locale.ROOT,
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\" stroke=\"#57606a\"/>\n",
                LEFT, TOP, plotWidth, plotHeight));
        svg.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\">%s</text>\n",
                LEFT + plotWidth / 2, HEIGHT - 10, escape(xLabel)));
        svg.append(String.format(Locale.ROOT,
                "<text x=\"16\" y=\"%d\" text-anchor=\"middle\" transform=\"rotate(-90 16 %d)\">%s</text>\n",
                TOP + plotHeight / 2, TOP + plotHeight / 2, escape(yLabel)));

        int index = 0;

        for (Map.Entry<String, double[]> entry : series.entrySet()) {
            String color = COLORS[index % COLORS.length];
            StringBuilder path = new StringBuilder();
            boolean penDown = false;
            double[] values = entry.getValue();

            for (int i = 0; i < values.length; i++) {
                if (!Double.isFinite(values[i])) {
                    penDown = false;
                    continue;
                }

                double x = LEFT + i * xScale;
                double y = TOP + plotHeight - (values[i] - minY) * yScale;
                path.append(String.format(Locale.ROOT, "%s%.1f,%.1f ", penDown ? "L" : "M", x, y));
                penDown = true;
            }

            svg.append(String.format("<path d=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                    path.toString().trim(), color));

            int legendY = TOP + 10 + index * 20;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"3\"/>"
                            + "<text x=\"%d\" y=\"%d\">%s</text>\n",
                    LEFT + plotWidth + 12, legendY, LEFT + plotWidth + 32, legendY, color,
                    LEFT + plotWidth + 38, legendY + 4, escape(entry.getKey())));
            index++;
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    public static void write(Path path, String title, String xLabel, String yLabel, Map<String, double[]> series)
            throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, render(title, xLabel, yLabel, series), StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return Math.abs(value) >= 100 ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
