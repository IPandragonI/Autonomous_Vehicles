package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TrackPreviewTest {

    private static final double RADIUS = 200.0;
    private static final int POINTS = 2000;

    /** Counter-clockwise circle starting on the +x axis. */
    private static List<Vec2> circle(double radius, int points) {
        List<Vec2> centerline = new ArrayList<>();

        for (int i = 0; i < points; i++) {
            double a = 2.0 * Math.PI * i / points;
            centerline.add(new Vec2(radius * Math.cos(a), radius * Math.sin(a)));
        }

        return centerline;
    }

    /** Readings of a car on the circle at polar angle {@code a}, heading {@code offset} left of the tangent. */
    private static double[] onCircle(TrackPreview preview, double a, double offset) {
        return preview.read(RADIUS * Math.cos(a), RADIUS * Math.sin(a), a + Math.PI / 2.0 + offset);
    }

    @Test
    public void testOnACircleEachReadingIsTheArcAheadOverTheRadius() {
        TrackPreview preview = new TrackPreview(circle(RADIUS, POINTS));

        for (double a : new double[]{0.3, 2.0, 2.0 * Math.PI - 0.01}) { // the last one reads across the start line
            double[] readings = onCircle(preview, a, 0.0);

            for (int i = 0; i < TrackPreview.INPUTS; i++) {
                double turn = TrackPreview.DISTANCES[i] / RADIUS;
                assertEquals(turn / Math.PI, readings[i], 1e-3, "road turns d/R radians in d metres");
            }
        }
    }

    @Test
    public void testHeadingErrorShiftsEveryReading() {
        TrackPreview preview = new TrackPreview(circle(RADIUS, POINTS));
        double[] aligned = onCircle(preview, 1.0, 0.0);
        double[] pointingLeft = onCircle(preview, 1.0, 0.2);

        for (int i = 0; i < TrackPreview.INPUTS; i++) {
            assertEquals(aligned[i] - 0.2 / Math.PI, pointingLeft[i], 1e-9, "the road is 0.2 rad more to the right");
        }
    }

    @Test
    public void testClockwiseRoadReadsNegativeAndTightBendsSaturateInsteadOfWrapping() {
        List<Vec2> clockwise = new ArrayList<>(circle(30.0, 400));
        Collections.reverse(clockwise);
        TrackPreview preview = new TrackPreview(clockwise);
        // On the reversed circle the car at polar angle a drives clockwise: heading a - π/2.
        double a = 1.0;
        double[] readings = preview.read(30.0 * Math.cos(a), 30.0 * Math.sin(a), a - Math.PI / 2.0);

        assertEquals(-2.0 * Math.PI, preview.lapTurn(), 1e-9);
        assertEquals(-25.0 / 30.0 / Math.PI, readings[1], 1e-3);
        assertEquals(-1.0, readings[TrackPreview.INPUTS - 1], 0.0, "200 m round a 30 m circle is beyond ±π");

        for (int i = 1; i < TrackPreview.INPUTS; i++) {
            assertTrue(readings[i] <= readings[i - 1], "unwrapped: the readings never jump back");
        }
    }

    @Test
    public void testLocatingFromTheHintMatchesTheBruteForceSearchAlongARealCircuit() {
        Track track = Tracks.byName("SaoPaulo");
        TrackPreview preview = track.getPreview();
        Rng rng = new Rng(3);
        int hint = -1;

        assertEquals(track.lapLength(), preview.length(), 0.02 * preview.length(), "same circuit as the checkpoints");
        assertEquals(2.0 * Math.PI, Math.abs(preview.lapTurn()), 1e-9);

        // Drive the checkpoint loop twice in 1.5 m steps with some lateral noise, as a car would.
        List<Checkpoint> checkpoints = track.getCheckpoints();

        for (int lap = 0; lap < 2; lap++) {
            for (int k = 0; k < checkpoints.size(); k++) {
                Vec2 from = checkpoints.get(k).midpoint();
                Vec2 to = checkpoints.get((k + 1) % checkpoints.size()).midpoint();
                int steps = (int) Math.ceil(from.distanceTo(to) / 1.5);

                for (int s = 0; s < steps; s++) {
                    Vec2 p = from.add(to.subtract(from).scale((double) s / steps));
                    double x = p.x() + (rng.nextDouble() - 0.5) * 4.0;
                    double y = p.y() + (rng.nextDouble() - 0.5) * 4.0;
                    hint = preview.locate(x, y, hint);
                    int global = preview.locate(x, y, -1);
                    double[] fromHint = new double[TrackPreview.INPUTS];
                    double[] fromGlobal = new double[TrackPreview.INPUTS];
                    preview.read(x, y, 0.3, hint, fromHint, 0);
                    preview.read(x, y, 0.3, global, fromGlobal, 0);
                    assertArrayEquals(fromGlobal, fromHint, 1e-9, "lap " + lap + " checkpoint " + k);
                }
            }
        }
    }

    @Test
    public void testBrainAppendsThePreviewAfterTheOtherInputs() {
        Track track = Tracks.byName("hairpin");
        RaySensor sensor = RaySensor.standard();
        VehicleModel vehicle = Vehicles.create("formula", TireCompound.MEDIUM);
        int size = CarBrain.inputSize(sensor, true, true, true);
        CarBrain brain = new CarBrain(new NeuralNetwork(new Rng(1), size, 4, 2), sensor, true, true, true, vehicle);
        Car car = Car.atStart(track);

        double[] inputs = brain.inputs(car, track);
        double[] expected = track.getPreview().read(car.getX(), car.getY(), car.getAngle());

        assertEquals(5 + 1 + CarBrain.CAR_STATE_INPUTS + TrackPreview.INPUTS, inputs.length);
        assertArrayEquals(expected, Arrays.copyOfRange(inputs, inputs.length - TrackPreview.INPUTS, inputs.length), 0.0);
        assertEquals(0.0, inputs[inputs.length - TrackPreview.INPUTS], 0.02, "the start faces along the road");
        assertEquals("road 200 m", brain.inputLabels().getLast());
        assertThrows(IllegalArgumentException.class,
                () -> new CarBrain(new NeuralNetwork(new Rng(1), size - 1, 4, 2), sensor, true, true, true, vehicle));
    }

    @Test
    public void testEveryTrackHasACentreLineButAWallsOnlyTrackCannotBePreviewed() {
        assertNotNull(Tracks.oval().getPreview(), "built from the checkpoint centres");
        assertNotNull(Tracks.byName("hairpin").getPreview());
        assertThrows(IllegalStateException.class, () -> TestDrivers.squareRing().getPreview());
        assertThrows(IllegalArgumentException.class, () -> new TrackPreview(List.of(new Vec2(0, 0), new Vec2(1, 0))));
    }
}
