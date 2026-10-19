package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackTest {

    @Test
    public void testOvalHasTwoClosedWalls() {
        Track track = Tracks.oval();

        assertEquals(64, track.getOuterWall().size());
        assertEquals(64, track.getInnerWall().size());

        assertEquals(128, track.getWalls().size());

        List<Vec2> outer = track.getOuterWall();
        Segment lastOuterSegment = track.getWalls().get(63);

        assertEquals(outer.get(63), lastOuterSegment.start());
        assertEquals(outer.getFirst(), lastOuterSegment.end());
    }

    @Test
    public void testStartPositionIsInsideTrack() {
        Track track = Tracks.oval();

        Vec2 start = track.getStartPosition();

        assertEquals(0.0, start.x(), 1e-9);
        assertEquals(-45.0, start.y(), 1e-9);
        assertEquals(0.0, track.getStartAngle(), 1e-9);

        assertTrue(Math.pow(start.x() / 100.0, 2) + Math.pow(start.y() / 60.0, 2) < 1.0);
        assertTrue(Math.pow(start.x() / 65.0, 2) + Math.pow(start.y() / 30.0, 2) > 1.0);}

    @Test
    public void testInnerAndOuterWallsDoNotIntersect() {
        Track track = Tracks.oval();

        for (int i = 0; i < track.getOuterWall().size(); i++) {
            Segment outerSegment = track.getWalls().get(i);

            for (int j = 64; j < track.getWalls().size(); j++) {
                Segment innerSegment = track.getWalls().get(j);
                assertTrue(Geometry.intersectSegments(outerSegment, innerSegment).isEmpty(), "Inner and outer walls must not intersect.");
            }
        }
    }

    @Test
    public void testInvalidTrackWithCrossingWallsIsRejected() {
        List<Vec2> outer = List.of(new Vec2(-5, -5), new Vec2(5, -5), new Vec2(5, 5), new Vec2(-5, 5));

        List<Vec2> inner = List.of(new Vec2(0, -2), new Vec2(7, 0), new Vec2(0, 2), new Vec2(-2, 0));

        assertThrows(IllegalArgumentException.class, () -> new Track(outer, inner, new Vec2(3, 0), 0));
    }

    @Test
    public void testStartOutsideTrackIsRejected() {
        Track track = Tracks.oval();

        assertThrows(IllegalArgumentException.class, () -> new Track(track.getOuterWall(), track.getInnerWall(), new Vec2(150, 0), 0));
    }

    @Test
    public void testWallListsCannotBeModified() {
        Track track = Tracks.oval();

        assertThrows(UnsupportedOperationException.class, () -> track.getOuterWall().add(new Vec2(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> track.getInnerWall().add(new Vec2(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> track.getWalls().clear());
    }
}
