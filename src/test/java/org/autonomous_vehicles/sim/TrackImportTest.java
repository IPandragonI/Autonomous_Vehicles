package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class TrackImportTest {

    /** CSV in the TUM racetrack-database format: a 100 m circle, counter-clockwise, width varying on the right. */
    private static String circleCsv() {
        StringBuilder csv = new StringBuilder("# x_m,y_m,w_tr_right_m,w_tr_left_m\n");

        for (int i = 0; i < 200; i++) {
            double theta = 2 * Math.PI * i / 200;
            csv.append(String.format(Locale.ROOT, "%.6f,%.6f,%.3f,%.3f\n", 100 * Math.cos(theta), 100 * Math.sin(theta),
                    5 + Math.sin(3 * theta), 6.0));
        }

        return csv.toString();
    }

    @Test
    public void testTumCsvBecomesAValidTrack() {
        Track track = TrackLoader.parseCsv(circleCsv(), "circle");

        assertEquals("circle", track.getName());
        assertEquals(2 * Math.PI * 100, track.lapLength(), 10.0, "Lap through the checkpoints ≈ the circumference.");
        assertEquals(16, track.getCheckpoints().size(), "One checkpoint every ~40 m.");
        assertTrue(track.isOnTrack(track.getStartPosition()));
    }

    @Test
    public void testWidthsAreAppliedOnTheRightSide() {
        Track track = TrackLoader.parseCsv(circleCsv(), "circle");

        // Counter-clockwise: the left of the driving direction is the inside (6 m), the right the outside (5 + sin).
        assertEquals(100 - 6.0, track.getInnerWall().getFirst().length(), 0.05);
        assertEquals(100 + 5.0, track.getOuterWall().getFirst().length(), 0.05);
    }

    @Test
    public void testCheckpointsAreEvenlySpacedForImportedTracks() {
        Track track = TrackLoader.parseCsv(circleCsv(), "circle");
        List<Checkpoint> checkpoints = track.getCheckpoints();
        double expected = track.lapLength() / checkpoints.size();

        for (int i = 0; i < checkpoints.size(); i++) {
            double gap = checkpoints.get(i).midpoint().distanceTo(checkpoints.get((i + 1) % checkpoints.size()).midpoint());
            assertEquals(expected, gap, 0.15 * expected, "Gap after checkpoint " + i);
        }
    }

    @Test
    public void testTwoColumnCsvUsesTheDefaultWidth() {
        StringBuilder csv = new StringBuilder();

        for (int i = 0; i < 200; i++) {
            double theta = 2 * Math.PI * i / 200;
            csv.append(String.format(Locale.ROOT, "%.6f %.6f\n", 100 * Math.cos(theta), 100 * Math.sin(theta)));
        }

        Track track = TrackLoader.parseCsv(csv.toString(), "plain");

        assertEquals(100 - TrackLoader.DEFAULT_CSV_WIDTH / 2, track.getInnerWall().getFirst().length(), 0.05);
    }

    @Test
    public void testLoadPicksTheFormatFromTheExtension() throws IOException {
        Path file = Files.createTempFile("circuit", ".csv");
        Files.writeString(file, circleCsv());

        Track track = TrackLoader.load(file);

        assertEquals(file.getFileName().toString().replace(".csv", ""), track.getName());
        Files.deleteIfExists(file);
    }

    @Test
    public void testFigureOfEightIsRejectedWithAClearReason() {
        StringBuilder csv = new StringBuilder();

        for (int i = 0; i < 400; i++) {
            double t = 2 * Math.PI * i / 400;
            double d = 1 + Math.sin(t) * Math.sin(t);
            csv.append(String.format(Locale.ROOT, "%.4f,%.4f,5,5\n", 300 * Math.cos(t) / d, 300 * Math.sin(t) * Math.cos(t) / d));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TrackLoader.parseCsv(csv.toString(), "eight"));
        assertTrue(error.getMessage().contains("crosses itself"), error.getMessage());
    }

    @Test
    public void testBadLinesReportTheirNumber() {
        IllegalArgumentException columns = assertThrows(IllegalArgumentException.class,
                () -> TrackLoader.parseCsv("# header\n1,2,3\n", "bad"));
        assertTrue(columns.getMessage().contains("Line 2"), columns.getMessage());

        IllegalArgumentException number = assertThrows(IllegalArgumentException.class,
                () -> TrackLoader.parseCsv("1,2,3,x\n", "bad"));
        assertTrue(number.getMessage().contains("Line 1"), number.getMessage());

        assertThrows(IllegalArgumentException.class, () -> TrackLoader.parseCsv("0,0,5,5\n10,0,5,5\n", "tiny"));
        assertThrows(IllegalArgumentException.class, () -> TrackLoader.parseCsv("0,0,0,5\n", "zero width"));
    }

    @Test
    public void testWallLoopsInTightCornersAreRepaired() {
        // Rounded square, 200 m sides, 5 m corner radius but 8 m to each side: the inner offset of every corner
        // folds over itself and must be cut out.
        List<Vec2> points = new ArrayList<>();
        double half = 100, radius = 5;
        double[][] centres = {{half - radius, -half + radius}, {half - radius, half - radius},
                {-half + radius, half - radius}, {-half + radius, -half + radius}};

        for (int c = 0; c < 4; c++) {
            double startAngle = -Math.PI / 2 + c * Math.PI / 2;
            double[] previous = centres[(c + 3) % 4];
            Vec2 from = new Vec2(previous[0] + radius * Math.cos(startAngle), previous[1] + radius * Math.sin(startAngle));
            Vec2 to = new Vec2(centres[c][0] + radius * Math.cos(startAngle), centres[c][1] + radius * Math.sin(startAngle));

            for (int s = 0; s < 38; s++) {
                points.add(from.add(to.subtract(from).scale(s / 38.0)));
            }

            for (int a = 0; a < 6; a++) {
                double angle = startAngle + Math.PI / 2 * a / 6;
                points.add(new Vec2(centres[c][0] + radius * Math.cos(angle), centres[c][1] + radius * Math.sin(angle)));
            }
        }

        double[] widths = new double[points.size()];
        Arrays.fill(widths, 8.0);

        Track track = Tracks.fromCenterlineWithWidths("tight", points, widths, widths, 40.0);

        assertTrue(track.getInnerWall().size() < points.size(), "Inner wall points inside the folds were removed.");
        assertTrue(track.isOnTrack(track.getStartPosition()));
    }

    @Test
    public void testImportedTracksAreFoundByName() {
        Track track = TrackLoader.parseCsv(circleCsv(), "imported-circle");

        Tracks.register(track);

        assertSame(track, Tracks.byName("imported-circle"));
        assertTrue(Tracks.importedNames().contains("imported-circle"));
        assertThrows(IllegalArgumentException.class, () -> Tracks.register(Tracks.oval()));
    }

    @Test
    public void testRecommendedTimeLimitLeavesRoomForALap() {
        Track hairpin = Tracks.byName("hairpin");
        VehicleModel hatchback = Vehicles.create("hatchback", TireCompound.MEDIUM);

        assertEquals(60.0, SimulationSettings.recommendedMaxTime(Tracks.oval(), KinematicModel.INSTANCE), 0.0);
        assertTrue(SimulationSettings.recommendedMaxTime(hairpin, hatchback) >= 90.0,
                "Grip-limited cars measured at 65-75 s per hairpin lap.");
        assertEquals(0.0, SimulationSettings.recommendedMaxTime(hairpin, hatchback) % 10.0, 0.0);
    }
}
