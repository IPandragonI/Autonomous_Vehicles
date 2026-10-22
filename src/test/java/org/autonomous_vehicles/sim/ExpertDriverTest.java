package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExpertDriverTest {

    private static final double DT = 1.0 / 60.0;

    private static DynamicModel formula() {
        return (DynamicModel) Vehicles.create("formula", TireCompound.MEDIUM, RaceRules.defaults().withFuelAutonomy(0));
    }

    /** Drives the expert until one lap, a crash or 300 s; returns the lap times. */
    private static List<Double> lap(DynamicModel model, Track track) {
        ExpertDriver expert = new ExpertDriver(model, track);
        Car car = Car.atStart(track);
        model.prepare(car);
        LapTracker laps = new LapTracker(track);

        while (laps.getLaps() == 0 && laps.getElapsedTime() < 300) {
            Vec2 from = car.getPosition();
            model.step(car, expert.decide(car), DT);
            Vec2 to = car.getPosition();

            if (Collision.hits(car, track) || Collision.crossesWall(from, to, track)) {
                fail("The expert crashed on " + track.getName() + " at checkpoint " + laps.getCheckpointsPassed());
            }

            laps.update(from, to, DT);
        }

        return laps.getLapTimes();
    }

    @Test
    public void testTheExpertLapsWithinAFewSecondsOfItsSpeedProfile() {
        for (String name : List.of("hairpin", "SaoPaulo")) {
            Track track = Tracks.byName(name);
            DynamicModel model = formula();
            double profile = new ExpertDriver(model, track).profileLapTime();
            List<Double> laps = lap(model, track);

            assertEquals(1, laps.size(), name);
            // Standing start and pure-pursuit corner cutting: slower than the profile, but not by much.
            assertTrue(laps.getFirst() > profile && laps.getFirst() < 1.35 * profile,
                    name + ": lap " + laps.getFirst() + " s, profile " + profile + " s");
        }
    }

    @Test
    public void testEveryBendOfTheProfileCanBeReachedByBraking() {
        Track track = Tracks.byName("SaoPaulo");
        DynamicModel model = formula();
        ExpertDriver expert = new ExpertDriver(model, track);
        double length = track.getPreview().length();
        double maxBraking = ExpertDriver.BRAKE_MARGIN * model.getSpec().maxBrakeDeceleration();
        double top = 0.0;

        for (double s = 0.0; s < length; s += 1.0) {
            double here = expert.profileSpeed(s);
            double next = expert.profileSpeed(s + 1.0);
            top = Math.max(top, here);
            // 2 % tolerance: the speed is interpolated linearly between profile points, not its square.
            assertTrue(here * here - next * next <= 1.02 * 2.0 * maxBraking * 1.0, "at " + s + " m: " + here + " → " + next);
            assertTrue(here <= model.topSpeed() + 1e-9);
        }

        assertTrue(top > 70.0, "the profile reaches > 250 km/h on the straights: " + top * 3.6);
    }

    @Test
    public void testTractionControlKeepsTheDrivenWheelsFromSpinning() {
        Track track = Tracks.byName("SaoPaulo");
        DynamicModel model = formula();
        ExpertDriver expert = new ExpertDriver(model, track);
        Car car = Car.atStart(track);
        model.prepare(car);
        car.setSpeed(15.0); // first gear, far below the profile: it wants full throttle

        CarControls controls = expert.decide(car);
        model.step(car, controls, DT);

        assertTrue(controls.throttle() < 1.0, "full throttle would spin the wheels at 15 m/s: " + controls.throttle());
        assertFalse(car.isSliding());
        assertThrows(IllegalArgumentException.class, () -> new ExpertDriver(null, track));
    }
}
