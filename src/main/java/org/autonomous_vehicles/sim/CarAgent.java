package org.autonomous_vehicles.sim;

import org.autonomous_vehicles.geometry.Geometry;
import org.autonomous_vehicles.geometry.Segment;
import org.autonomous_vehicles.geometry.Vec2;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * One car in a simulation, with its brain, lap tracker and the measurements used by fitness functions.
 */
public class CarAgent {

    /** A dry car has lost all drive; once it has coasted below this speed (m/s) it is out of the race. */
    public static final double OUT_OF_FUEL_STOP_SPEED = 0.1;

    private final int id;
    private final Car car;
    private final CarBrain brain;
    private final LapTracker lapTracker;
    private final Track track;
    private final SimulationSettings settings;
    private final VehicleModel vehicle;

    private final double startProgress;
    private double bestProgress;
    private double lastImprovementTime;
    private DeathCause deathCause = DeathCause.ALIVE;

    private double distance;
    private double speedSum;
    private double steerChangeSum;
    private double deviationSum;
    private long steps;
    private double lastSteer;
    private CarControls lastControls = CarControls.neutral();

    private final Deque<Vec2> trail = new ArrayDeque<>();
    private PoseBuffer currentLap = new PoseBuffer();
    private LapReplay bestLap;

    public CarAgent(int id, Track track, CarBrain brain, SimulationSettings settings) {
        this(id, track, brain, settings, KinematicModel.INSTANCE);
    }

    public CarAgent(int id, Track track, CarBrain brain, SimulationSettings settings, VehicleModel vehicle) {
        if (track == null || brain == null || settings == null || vehicle == null) {
            throw new IllegalArgumentException("Track, brain, settings and vehicle must not be null.");
        }

        this.id = id;
        this.track = track;
        this.brain = brain;
        this.settings = settings;
        this.vehicle = vehicle;
        this.car = Car.atStart(track);
        vehicle.prepare(car);
        this.lapTracker = new LapTracker(track);
        this.startProgress = lapTracker.progress(car.getPosition());
        this.bestProgress = startProgress;

        if (settings.recordLaps()) {
            currentLap.add(car);
        }
    }

    /**
     * One step: sensors → network → physics → collision → checkpoints → end-of-life rules.
     */
    public void step(double dt) {
        if (!car.isAlive()) {
            return;
        }

        CarControls controls = brain.decide(car, track);
        Vec2 from = car.getPosition();
        vehicle.step(car, controls, dt);
        Vec2 to = car.getPosition();

        steps++;
        distance += from.distanceTo(to);
        speedSum += Math.abs(car.getSpeed());
        steerChangeSum += Math.abs(controls.steer() - lastSteer);
        lastSteer = controls.steer();
        lastControls = controls;

        if (Collision.hits(car, track) || Collision.crossesWall(from, to, track)) {
            stop(DeathCause.CRASHED);
            return;
        }

        boolean lapCompleted = lapTracker.update(from, to, dt);
        deviationSum += deviation(to);
        recordPose();

        if (lapCompleted) {
            onLapCompleted();
        }

        double progress = lapTracker.progress(to);

        if (progress > bestProgress + SimulationSettings.MIN_PROGRESS) {
            bestProgress = progress;
            lastImprovementTime = lapTracker.getElapsedTime();
        }

        if (lapTracker.getLaps() >= settings.maxLaps()) {
            stop(DeathCause.FINISHED);
        } else if (car.isOutOfFuel() && Math.abs(car.getSpeed()) < OUT_OF_FUEL_STOP_SPEED) {
            stop(DeathCause.OUT_OF_FUEL);
        } else if (lapTracker.getElapsedTime() >= settings.maxTime()) {
            stop(DeathCause.TIMEOUT);
        } else if (lapTracker.getElapsedTime() - lastImprovementTime > settings.stagnationTime()) {
            stop(DeathCause.STAGNATED);
        }
    }

    private void stop(DeathCause cause) {
        deathCause = cause;
        car.crash();
    }

    /**
     * Distance to the line joining the previous and next checkpoint centres, relative to the half width.
     */
    private double deviation(Vec2 position) {
        List<Checkpoint> checkpoints = track.getCheckpoints();
        int next = lapTracker.getNextCheckpointIndex();
        Checkpoint previous = checkpoints.get(Math.floorMod(next - 1, checkpoints.size()));
        Segment centerline = new Segment(previous.midpoint(), checkpoints.get(next).midpoint());
        double halfWidth = checkpoints.get(next).gate().length() / 2.0;

        return Math.min(1.0, Geometry.distancePointToSegment(position, centerline) / halfWidth);
    }

    private void recordPose() {
        if (settings.trailLength() > 0) {
            trail.addLast(car.getPosition());

            while (trail.size() > settings.trailLength()) {
                trail.removeFirst();
            }
        }

        if (settings.recordLaps()) {
            currentLap.add(car);
        }
    }

    private void onLapCompleted() {
        if (!settings.recordLaps()) {
            return;
        }

        List<Double> lapTimes = lapTracker.getLapTimes();
        double lapTime = lapTimes.getLast();

        if (bestLap == null || lapTime < bestLap.getLapTime()) {
            bestLap = currentLap.toReplay(track.getName(), settings.dt(), lapTime);
        }

        currentLap = new PoseBuffer();
        currentLap.add(car);
    }

    public AgentResult result() {
        double timeAlive = lapTracker.getElapsedTime();

        return new AgentResult(
                Math.max(0.0, bestProgress - startProgress),
                lapTracker.getLaps(),
                lapTracker.getLapTimes(),
                distance,
                timeAlive,
                steps == 0 ? 0.0 : speedSum / steps,
                steps == 0 ? 0.0 : steerChangeSum / steps,
                steps == 0 ? 0.0 : deviationSum / steps,
                deathCause,
                settings.maxTime(),
                lapTracker.getCheckpointCount(),
                bestLap,
                vehicle.topSpeed());
    }

    public double getProgress() {
        return Math.max(0.0, bestProgress - startProgress);
    }

    public int getId() {
        return id;
    }

    public Car getCar() {
        return car;
    }

    public CarBrain getBrain() {
        return brain;
    }

    public LapTracker getLapTracker() {
        return lapTracker;
    }

    public DeathCause getDeathCause() {
        return deathCause;
    }

    public CarControls getLastControls() {
        return lastControls;
    }

    public List<Vec2> getTrail() {
        return List.copyOf(trail);
    }

    public LapReplay getBestLap() {
        return bestLap;
    }

    /**
     * Growable arrays of poses, so a lap of thousands of steps costs no boxing.
     */
    private static final class PoseBuffer {
        private double[] xs = new double[256];
        private double[] ys = new double[256];
        private double[] angles = new double[256];
        private int size;

        void add(Car car) {
            if (size == xs.length) {
                xs = Arrays.copyOf(xs, size * 2);
                ys = Arrays.copyOf(ys, size * 2);
                angles = Arrays.copyOf(angles, size * 2);
            }

            xs[size] = car.getX();
            ys[size] = car.getY();
            angles[size] = car.getAngle();
            size++;
        }

        LapReplay toReplay(String trackName, double dt, double lapTime) {
            return new LapReplay(trackName, dt, lapTime, Arrays.copyOf(xs, size), Arrays.copyOf(ys, size),
                    Arrays.copyOf(angles, size));
        }
    }
}
