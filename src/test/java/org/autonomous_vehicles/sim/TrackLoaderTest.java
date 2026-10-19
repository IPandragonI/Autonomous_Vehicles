package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackLoaderTest {

    private static final String SQUARE = """
            # rounded square
            name square
            width 20
            samples 12
            checkpoints 12
            point -60 -60
            point  60 -60
            point  60  60
            point -60  60
            """;

    @Test
    public void testParseBuildsAValidClosedTrack() {
        Track track = TrackLoader.parse(SQUARE, "unused");

        assertEquals("square", track.getName());
        assertEquals(12, track.getCheckpoints().size());
        assertEquals(48, track.getOuterWall().size());
        assertTrue(track.isOnTrack(track.getStartPosition()));
    }

    @Test
    public void testLoadFromFile() throws IOException {
        Path file = Files.createTempFile("square", ".track");
        Files.writeString(file, SQUARE.replace("name square\n", ""));

        Track track = TrackLoader.load(file);

        assertEquals(file.getFileName().toString().replace(".track", ""), track.getName());
        Files.deleteIfExists(file);
    }

    @Test
    public void testBuiltInTracksAreValid() {
        for (String name : Tracks.BUILT_IN) {
            Track track = Tracks.byName(name);

            assertEquals(name, track.getName());
            assertTrue(track.getCheckpoints().size() >= 16, name);
            assertTrue(track.isOnTrack(track.getStartPosition()), name);
        }
    }

    @Test
    public void testCheckpointsAreOrderedInTheDrivingDirection() {
        for (String name : Tracks.BUILT_IN) {
            List<Checkpoint> checkpoints = Tracks.byName(name).getCheckpoints();

            for (int i = 0; i < checkpoints.size(); i++) {
                Checkpoint current = checkpoints.get(i);
                Checkpoint next = checkpoints.get((i + 1) % checkpoints.size());

                assertTrue(next.midpoint().subtract(current.midpoint()).dot(current.forward()) > 0, name + " checkpoint " + i);
            }
        }
    }

    @Test
    public void testErrorsGiveTheLineNumber() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> TrackLoader.parse("width 10\ncolour red\n", "x"));
        assertTrue(unknown.getMessage().contains("Line 2"), unknown.getMessage());

        IllegalArgumentException number = assertThrows(IllegalArgumentException.class,
                () -> TrackLoader.parse("width 10\npoint 1 abc\n", "x"));
        assertTrue(number.getMessage().contains("Line 2"), number.getMessage());
    }

    @Test
    public void testMissingWidthOrPointsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TrackLoader.parse("point 0 0\npoint 10 0\npoint 0 10\n", "x"));
        assertThrows(IllegalArgumentException.class, () -> TrackLoader.parse("width 10\npoint 0 0\npoint 100 0\n", "x"));
    }

    @Test
    public void testTooTightCornerIsRejected() {
        String tight = "width 40\nsamples 8\ncheckpoints 6\npoint 0 0\npoint 30 0\npoint 30 30\npoint 0 30\n";

        assertThrows(IllegalArgumentException.class, () -> TrackLoader.parse(tight, "tight"));
    }

    @Test
    public void testUnknownResourceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Tracks.byName("does-not-exist"));
    }

    @Test
    public void testCheckpointsOutOfOrderAreRejected() {
        Track oval = Tracks.oval();
        List<Checkpoint> swapped = new java.util.ArrayList<>(oval.getCheckpoints());
        Checkpoint moved = swapped.remove(3);
        swapped.add(5, moved);

        assertThrows(IllegalArgumentException.class, () -> new Track("bad", oval.getOuterWall(), oval.getInnerWall(),
                oval.getStartPosition(), oval.getStartAngle(), swapped));
    }

    @Test
    public void testSelfIntersectingWallIsRejected() {
        List<Vec2> bowTie = List.of(new Vec2(-50, -50), new Vec2(50, 50), new Vec2(50, -50), new Vec2(-50, 50));
        List<Vec2> inner = List.of(new Vec2(40, -2), new Vec2(44, -2), new Vec2(44, 2), new Vec2(40, 2));

        assertThrows(IllegalArgumentException.class, () -> new Track(bowTie, inner, new Vec2(30, 0), 0));
    }
}
