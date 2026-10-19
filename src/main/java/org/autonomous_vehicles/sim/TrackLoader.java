package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads a track from a small text format (no JSON dependency):
 * <pre>
 * # comment
 * name hairpin
 * width 16            # track width
 * samples 12          # spline samples between two control points
 * checkpoints 24      # number of gates, the last one is the finish line
 * point -100 -60      # centre-line control points, in driving order (at least 3)
 * point  100 -60
 * ...
 * </pre>
 * The track is closed automatically and fully validated by {@link Track}
 * (closed walls, no crossing, checkpoints ordered, start between the walls).
 */
public final class TrackLoader {

    public static final String RESOURCE_DIRECTORY = "/tracks/";
    public static final String EXTENSION = ".track";
    public static final String CSV_EXTENSION = ".csv";
    public static final double DEFAULT_CSV_WIDTH = 12.0;
    public static final double CSV_CHECKPOINT_SPACING = 40.0;

    private TrackLoader() {
    }

    /**
     * Loads a {@code .track} file, or a {@code .csv} centre line with widths ({@link #parseCsv}).
     */
    public static Track load(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String content = Files.readString(path, StandardCharsets.UTF_8);

        return fileName.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION) ? parseCsv(content, baseName)
                : parse(content, baseName);
    }

    /**
     * Reads a real circuit as published by the TUM racetrack database
     * (https://github.com/TUMFTM/racetrack-database, folder {@code tracks/}): one point per line,
     * {@code x_m,y_m,w_tr_right_m,w_tr_left_m} in metres, in driving order, closed implicitly; lines starting with
     * {@code #} are comments. Two columns (x, y) are also accepted with a constant {@value #DEFAULT_CSV_WIDTH} m width.
     */
    public static Track parseCsv(String content, String name) {
        if (content == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("CSV content and track name are required.");
        }

        List<Vec2> points = new ArrayList<>();
        List<double[]> widths = new ArrayList<>();
        String[] lines = content.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] cells = line.split("\\s*[,;]\\s*|\\s+");
            int lineNumber = i + 1;

            if (cells.length != 2 && cells.length != 4) {
                throw new IllegalArgumentException("Line " + lineNumber + ": expected x,y or x,y,width_right,width_left.");
            }

            points.add(new Vec2(parseDouble(cells[0], lineNumber), parseDouble(cells[1], lineNumber)));
            widths.add(cells.length == 4
                    ? new double[]{parseDouble(cells[2], lineNumber), parseDouble(cells[3], lineNumber)}
                    : new double[]{DEFAULT_CSV_WIDTH / 2, DEFAULT_CSV_WIDTH / 2});
        }

        double[] right = widths.stream().mapToDouble(pair -> pair[0]).toArray();
        double[] left = widths.stream().mapToDouble(pair -> pair[1]).toArray();

        return Tracks.fromCenterlineWithWidths(name, points, left, right, CSV_CHECKPOINT_SPACING);
    }

    public static Track loadResource(String name) {
        try (InputStream stream = TrackLoader.class.getResourceAsStream(RESOURCE_DIRECTORY + name + EXTENSION)) {
            if (stream == null) {
                throw new IllegalArgumentException("Unknown track: " + name);
            }

            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), name);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read track " + name, e);
        }
    }

    public static Track parse(String content, String defaultName) {
        if (content == null) {
            throw new IllegalArgumentException("Track content must not be null.");
        }

        String name = defaultName;
        double width = Double.NaN;
        int samples = 10;
        int checkpoints = 20;
        List<Vec2> points = new ArrayList<>();

        String[] lines = content.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]);

            if (line.isEmpty()) {
                continue;
            }

            String[] tokens = line.split("\\s+");
            int lineNumber = i + 1;

            switch (tokens[0].toLowerCase(Locale.ROOT)) {
                case "name" -> name = requireArity(tokens, 2, lineNumber)[1];
                case "width" -> width = parseDouble(requireArity(tokens, 2, lineNumber)[1], lineNumber);
                case "samples" -> samples = parseInt(requireArity(tokens, 2, lineNumber)[1], lineNumber);
                case "checkpoints" -> checkpoints = parseInt(requireArity(tokens, 2, lineNumber)[1], lineNumber);
                case "point" -> {
                    requireArity(tokens, 3, lineNumber);
                    points.add(new Vec2(parseDouble(tokens[1], lineNumber), parseDouble(tokens[2], lineNumber)));
                }
                default -> throw new IllegalArgumentException("Line " + lineNumber + ": unknown keyword '" + tokens[0] + "'.");
            }
        }

        if (Double.isNaN(width)) {
            throw new IllegalArgumentException("Track '" + name + "' must declare a width.");
        }

        return Tracks.fromCenterline(name, points, width, samples, checkpoints);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return (hash >= 0 ? line.substring(0, hash) : line).trim();
    }

    private static String[] requireArity(String[] tokens, int expected, int lineNumber) {
        if (tokens.length != expected) {
            throw new IllegalArgumentException("Line " + lineNumber + ": '" + tokens[0] + "' expects "
                    + (expected - 1) + " value(s).");
        }

        return tokens;
    }

    private static double parseDouble(String token, int lineNumber) {
        try {
            double value = Double.parseDouble(token);

            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }

            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + lineNumber + ": invalid number '" + token + "'.");
        }
    }

    private static int parseInt(String token, int lineNumber) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + lineNumber + ": invalid integer '" + token + "'.");
        }
    }
}
