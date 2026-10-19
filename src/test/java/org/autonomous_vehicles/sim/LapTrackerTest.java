package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LapTrackerTest {

    private static final double DT = 1.0 / 60.0;

    /**
     * Points on the oval's centre line (ellipse 82.5 × 45), from the start (angle -90°) over {@code turns} turns.
     */
    private static List<Vec2> centreLine(double turns, boolean counterClockwise, int steps) {
        List<Vec2> points = new ArrayList<>();

        for (int i = 0; i <= steps; i++) {
            double theta = -Math.PI / 2 + (counterClockwise ? 1 : -1) * 2 * Math.PI * turns * i / steps;
            points.add(new Vec2(82.5 * Math.cos(theta), 45.0 * Math.sin(theta)));
        }

        return points;
    }

    private static LapTracker drive(List<Vec2> path) {
        LapTracker tracker = new LapTracker(Tracks.oval());

        for (int i = 1; i < path.size(); i++) {
            tracker.update(path.get(i - 1), path.get(i), DT);
        }

        return tracker;
    }

    @Test
    public void testScriptedLapCountsOneLap() {
        LapTracker tracker = drive(centreLine(1.02, true, 1200));

        assertEquals(1, tracker.getLaps());
        assertEquals(16, tracker.getCheckpointsPassed());
        assertEquals(0, tracker.getNextCheckpointIndex());
    }

    @Test
    public void testLapTimeIsMeasured() {
        List<Vec2> path = centreLine(1.02, true, 1200);
        LapTracker tracker = drive(path);

        double lapTime = tracker.getLapTimes().getFirst();

        assertTrue(lapTime > 1100 * DT && lapTime < 1200 * DT, "Lap time " + lapTime);
        assertEquals(lapTime, tracker.getBestLapTime(), 1e-12);
    }

    @Test
    public void testTwoLaps() {
        LapTracker tracker = drive(centreLine(2.02, true, 2400));

        assertEquals(2, tracker.getLaps());
        assertEquals(32, tracker.getCheckpointsPassed());
    }

    @Test
    public void testDrivingTheWrongWayCountsNothing() {
        LapTracker tracker = drive(centreLine(1.1, false, 1200));

        assertEquals(0, tracker.getLaps());
        assertEquals(0, tracker.getCheckpointsPassed());
    }

    @Test
    public void testSkippingACheckpointDoesNotCount() {
        Track track = Tracks.oval();
        LapTracker tracker = new LapTracker(track);
        Checkpoint second = track.getCheckpoints().get(1);
        Vec2 before = second.midpoint().subtract(second.forward().scale(2.0));
        Vec2 after = second.midpoint().add(second.forward().scale(2.0));

        tracker.update(before, after, DT);

        assertEquals(0, tracker.getCheckpointsPassed(), "Checkpoint 1 cannot be validated before checkpoint 0.");
        assertEquals(0, tracker.getNextCheckpointIndex());
    }

    @Test
    public void testGoingBackAndForthThroughACheckpointCountsOnce() {
        Track track = Tracks.oval();
        LapTracker tracker = new LapTracker(track);
        Checkpoint first = track.getCheckpoints().getFirst();
        Vec2 before = first.midpoint().subtract(first.forward().scale(2.0));
        Vec2 after = first.midpoint().add(first.forward().scale(2.0));

        tracker.update(before, after, DT);
        tracker.update(after, before, DT);
        tracker.update(before, after, DT);

        assertEquals(1, tracker.getCheckpointsPassed());
    }

    @Test
    public void testReversingOverTheFinishLineDoesNotCompleteALap() {
        Track track = Tracks.oval();
        LapTracker tracker = new LapTracker(track);
        Vec2 start = track.getStartPosition();
        Vec2 behindFinish = track.getCheckpoints().getLast().midpoint().subtract(new Vec2(5, 0));

        tracker.update(start, behindFinish, DT);
        tracker.update(behindFinish, start, DT);

        assertEquals(0, tracker.getLaps());
    }

    @Test
    public void testCheckpointOnlyCountsForwardCrossing() {
        Checkpoint gate = new Checkpoint(new Segment(new Vec2(0, -5), new Vec2(0, 5)), new Vec2(1, 0));

        assertTrue(gate.isCrossedForward(new Vec2(-1, 0), new Vec2(1, 0)));
        assertFalse(gate.isCrossedForward(new Vec2(1, 0), new Vec2(-1, 0)));
        assertFalse(gate.isCrossedForward(new Vec2(-3, 0), new Vec2(-1, 0)));
        assertFalse(gate.isCrossedForward(new Vec2(-1, 6), new Vec2(1, 6)));
    }

    @Test
    public void testProgressGrowsAlongTheTrack() {
        List<Vec2> path = centreLine(0.5, true, 600);
        LapTracker tracker = new LapTracker(Tracks.oval());
        double previous = tracker.progress(path.getFirst());

        for (int i = 1; i < path.size(); i++) {
            tracker.update(path.get(i - 1), path.get(i), DT);

            if (i % 60 == 0) {
                double progress = tracker.progress(path.get(i));
                assertTrue(progress > previous, "Progress must increase along the track.");
                previous = progress;
            }
        }

        assertEquals(8, tracker.getCheckpointsPassed());
    }
}
