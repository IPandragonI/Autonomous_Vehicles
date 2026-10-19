package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LapReplayTest {

    private static AgentResult runFollower() {
        Simulation simulation = new Simulation(Tracks.oval(), List.of(TestDrivers.follower()), RaySensor.standard(),
                false, SimulationSettings.defaults());
        simulation.runUntilAllDead();
        return simulation.getResults().getFirst();
    }

    @Test
    public void testBestLapIsRecorded() {
        AgentResult result = runFollower();
        LapReplay replay = result.bestLap();

        assertNotNull(replay);
        assertEquals(result.bestLapTime(), replay.getLapTime(), 0.0);
        assertEquals("oval", replay.getTrackName());
        assertEquals(Math.round(replay.getLapTime() / replay.getDt()) + 1, replay.getFrameCount());
    }

    @Test
    public void testReplayReproducesTheRecordedLapExactly() {
        LapReplay first = runFollower().bestLap();
        LapReplay second = runFollower().bestLap();

        assertTrue(first.sameTrajectory(second));
        assertEquals(0.0, first.meanDistanceTo(second), 0.0);
        assertEquals(first.getLapTime(), second.getLapTime(), 0.0);
    }

    @Test
    public void testReplayPassesThroughEveryCheckpointOfTheLap() {
        LapReplay replay = runFollower().bestLap();
        LapTracker tracker = new LapTracker(Tracks.oval());

        for (int i = 1; i < replay.getFrameCount(); i++) {
            tracker.update(replay.positionAt(i - 1), replay.positionAt(i), replay.getDt());
        }

        assertEquals(Tracks.oval().getCheckpoints().size(), tracker.getCheckpointsPassed());
    }

    @Test
    public void testFrameAtTimeIsClampedToTheLap() {
        LapReplay replay = new LapReplay("t", 0.5, 1.0, new double[]{0, 1, 2}, new double[]{0, 0, 0}, new double[]{0, 0, 0});

        assertEquals(0, replay.frameAtTime(-3));
        assertEquals(1, replay.frameAtTime(0.6));
        assertEquals(2, replay.frameAtTime(99));
    }

    @Test
    public void testComparingTwoTrajectories() {
        LapReplay straight = new LapReplay("t", 1, 2, new double[]{0, 1, 2}, new double[]{0, 0, 0}, new double[]{0, 0, 0});
        LapReplay shifted = new LapReplay("t", 1, 2, new double[]{0, 1, 2}, new double[]{3, 3, 3}, new double[]{0, 0, 0});

        assertEquals(3.0, straight.meanDistanceTo(shifted), 1e-12);
        assertEquals(new Vec2(1, 3), shifted.positionAt(1));
        assertFalse(straight.sameTrajectory(shifted));
    }

    @Test
    public void testInvalidReplayIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LapReplay("t", 1, 2, new double[]{0}, new double[]{0, 1}, new double[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new LapReplay("t", 0, 2, new double[]{0}, new double[]{0}, new double[]{0}));
    }
}
