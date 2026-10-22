package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RacingLineTest {

    /** Σ (P[i-1] - 2 P[i] + P[i+1])²: what the smoothing minimises (∝ the squared curvature). */
    private static double bending(List<Vec2> path) {
        int n = path.size();
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            Vec2 a = path.get((i + n - 1) % n);
            Vec2 b = path.get(i);
            Vec2 c = path.get((i + 1) % n);
            double x = a.x() - 2 * b.x() + c.x();
            double y = a.y() - 2 * b.y() + c.y();
            sum += x * x + y * y;
        }

        return sum;
    }

    @Test
    public void testTheLineStaysBetweenTheWallsWithItsMarginAndBendsLessThanTheCentreLine() {
        Track track = Tracks.byName("SaoPaulo");
        List<Vec2> centre = RacingLine.minimumCurvature(track, RacingLine.DEFAULT_WALL_MARGIN, 0);
        List<Vec2> line = RacingLine.minimumCurvature(track, RacingLine.DEFAULT_WALL_MARGIN, 20_000);

        assertEquals(centre.size(), line.size());

        for (Vec2 point : line) {
            assertTrue(track.isOnTrack(point), point.toString());
            // The margin is measured along the station's normal; a wall met obliquely may be a little closer.
            assertFalse(track.circleTouchesWall(point, Car.RADIUS), "a car on the line clears the walls: " + point);
        }

        // measured: 9.6 against 15.9 after 20 000 passes (−39 %)
        assertTrue(bending(line) < 0.7 * bending(centre), bending(line) + " vs " + bending(centre));
        assertEquals(new TrackPreview(centre).length(), track.getPreview().length(), 0.01 * track.getPreview().length(),
                "0 passes = the centre line");
        assertTrue(new TrackPreview(line).length() < new TrackPreview(centre).length(), "the line cuts the bends");
    }

    @Test
    public void testTheExpertIsFasterOnTheRacingLine() {
        Track track = Tracks.byName("SaoPaulo");
        DynamicModel model = (DynamicModel) Vehicles.create("formula", TireCompound.MEDIUM,
                RaceRules.defaults().withFuelAutonomy(0));
        double centre = new ExpertDriver(model, track).profileLapTime();
        double racing = new ExpertDriver(model, RacingLine.of(track), ExpertDriver.CORNER_MARGIN,
                ExpertDriver.BRAKE_MARGIN).profileLapTime();

        assertTrue(racing < centre - 5.0, "profile " + racing + " s on the racing line, " + centre + " s on the centre");
        assertThrows(IllegalArgumentException.class, () -> RacingLine.minimumCurvature(track, 0.5, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpertDriver(model, RacingLine.of(track), 1.5, 0.75), "margins are shares of the grip");
    }
}
