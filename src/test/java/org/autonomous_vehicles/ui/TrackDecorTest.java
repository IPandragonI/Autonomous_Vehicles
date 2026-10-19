package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackDecorTest {

    @Test
    public void testKerbsLieOnTheTrackAndAlternate() {
        for (String name : List.of("oval", "hairpin", "chicane", "Monza")) {
            Track track = Tracks.byName(name);
            TrackDecor decor = TrackDecor.of(track);

            assertFalse(decor.kerbs().isEmpty(), name + " has bends, so it has kerbs.");

            for (TrackDecor.Quad kerb : decor.kerbs()) {
                assertTrue(track.isOnTrack(kerb.centroid()), name + ": kerb painted off the track at " + kerb.centroid());
            }

            long red = decor.kerbs().stream().filter(q -> q.paint() == TrackDecor.Paint.KERB_RED).count();
            long white = decor.kerbs().size() - red;
            assertTrue(red > 0 && white > 0, name + ": stripes alternate red and white.");
        }
    }

    @Test
    public void testOvalInsideKerbsOnlyWhereTheBendIsTighterThanTheThreshold() {
        // Inner ellipse 65 × 30 m: radius of curvature ρ(t) = (a² sin²t + b² cos²t)^{3/2} / (ab),
        // 14 m at the ends, 141 m in the middle of the sides. Inside kerbs need ρ < 120 m (a little slack for the
        // ±2-point smoothing).
        double a = 65, b = 30;
        TrackDecor decor = TrackDecor.of(Tracks.oval());
        int insideKerbs = 0;

        for (TrackDecor.Quad kerb : decor.kerbs()) {
            Vec2 centre = kerb.centroid();

            if (Math.pow(centre.x() / a, 2) + Math.pow(centre.y() / b, 2) < 1.3) {
                double t = Math.atan2(centre.y() / b, centre.x() / a);
                double radius = Math.pow(a * a * Math.sin(t) * Math.sin(t) + b * b * Math.cos(t) * Math.cos(t), 1.5) / (a * b);
                assertTrue(radius < TrackDecor.INSIDE_RADIUS + 10, "Inside kerb where the radius is " + radius + " m");
                insideKerbs++;
            }
        }

        assertTrue(insideKerbs > 0);
    }

    @Test
    public void testChequeredFinishLine() {
        Track oval = Tracks.oval();
        TrackDecor decor = TrackDecor.of(oval);
        double gate = oval.getCheckpoints().getLast().gate().length();
        long squaresPerRow = Math.round(gate / TrackDecor.CHEQUER_SIZE);

        assertEquals(2 * squaresPerRow, decor.finishLine().size());
        assertEquals(TrackDecor.Paint.CHEQUER_BLACK, decor.finishLine().get(0).paint());
        assertEquals(TrackDecor.Paint.CHEQUER_WHITE, decor.finishLine().get(1).paint());
    }

    @Test
    public void testEveryShippedCircuitLoads() {
        for (String circuit : Tracks.REAL) {
            Track track = Tracks.byName(circuit);

            assertEquals(circuit, track.getName());
            assertTrue(track.lapLength() > 2000, circuit + " is a full-size circuit.");
            assertSame(track, Tracks.byName(circuit), "Real circuits are built once and cached.");
        }

        assertEquals("Brands Hatch", Tracks.displayName("BrandsHatch"));
        assertEquals("IMS", Tracks.displayName("IMS"));
    }
}
