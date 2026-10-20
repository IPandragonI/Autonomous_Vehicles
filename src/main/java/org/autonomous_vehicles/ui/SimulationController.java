package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.Champion;
import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.GeneticAlgorithm;
import org.autonomous_vehicles.evolution.NetworkInputs;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.evolution.StatsHistory;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.CarAgent;
import org.autonomous_vehicles.sim.LapReplay;
import org.autonomous_vehicles.sim.Simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Drives the evolution for the interface, without any JavaFX dependency (so it is unit-tested).
 * <p>
 * Update and rendering are separate. {@link #tick(double)} (once per displayed image) runs as many steps as the
 * real time elapsed × the speed requires — ×1 is real time whatever the frame rate — in parallel batches, and
 * stops at {@link #FRAME_BUDGET_NANOS} so the interface stays at 60 images per second; time that could not be
 * simulated is dropped rather than caught up (no spiral of death), and {@link #getEffectiveSpeed()} reports the
 * speed actually reached. When every car is stopped, the other tracks (multi-track training) are simulated
 * instantly and the next generation is bred. The results never depend on how steps are batched.
 */
public class SimulationController {

    public static final int[] SPEEDS = {1, 2, 10, 100, 1000};

    /** Simulation work allowed per displayed image: leaves ~6 ms of a 60 Hz frame for drawing. */
    public static final long FRAME_BUDGET_NANOS = 10_000_000L;
    /** Steps per parallel batch: large enough to amortise the thread hand-off, small enough to meet the budget. */
    static final int BATCH_STEPS = 20;
    /** A longer pause between two images (window dragged, debugger) is not caught up. */
    static final double MAX_FRAME_GAP = 0.25;

    private final RenderOptions renderOptions = new RenderOptions();
    private final List<Consumer<GenerationStats>> generationListeners = new ArrayList<>();

    private SimulationConfig config;
    private GeneticAlgorithm algorithm;
    private List<Simulation> simulations;
    private boolean playing;
    private int speed = 1;
    private int selectedCarId = -1;
    private Champion replay;
    private List<Simulation> pausedTraining;
    private Champion seed;
    private Champion seedDropped;
    private ChampionKind championKind = ChampionKind.BEST_FITNESS;

    private final LongSupplier clock;
    private double owedSteps;
    private double effectiveSpeed = Double.NaN;

    public SimulationController(SimulationConfig config) {
        this(config, System::nanoTime);
    }

    /**
     * @param clock monotonic nanoseconds, used only for the per-image time budget (injected so tests are exact)
     */
    public SimulationController(SimulationConfig config, LongSupplier clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null.");
        }

        this.config = config;
        this.clock = clock;
        reset();
    }

    public void play() {
        playing = true;
    }

    public void pause() {
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    /**
     * Number of simulation steps per displayed image.
     */
    public void setSpeed(int speed) {
        if (speed < 1 || speed > 1000) {
            throw new IllegalArgumentException("Speed must be between 1 and 1000 steps per tick.");
        }

        this.speed = speed;
        owedSteps = 0.0;
        effectiveSpeed = Double.NaN; // the smoothing restarts, so the new speed shows at once
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * Restarts from a new random population (generation 0) with the current configuration.
     */
    public void reset() {
        NeuralNetwork seedNetwork = null;

        if (seed != null) {
            // A configuration with more inputs (speed, car state) widens the champion with zero weights from them.
            seedNetwork = NetworkInputs.adapt(seed.network(), seed.config(), config);

            if (seedNetwork == null) {
                seedDropped = seed; // other rays or hidden layers, or fewer inputs: the network does not fit
                seed = null;
            }
        }

        algorithm = new GeneticAlgorithm(config, seedNetwork);
        simulations = algorithm.createSimulations();
        selectedCarId = -1;
        replay = null;
        pausedTraining = null;
    }

    /**
     * Restarts the training from a saved champion with its configuration: generation 0 is the champion plus mutants
     * of it ({@code Population.seeded}). The champion stays the seed of every later restart (Restart button,
     * track or car change) until {@link #clearSeed()}, or until a change needs another network shape.
     */
    public void retrainFrom(Champion champion) {
        if (champion == null) {
            throw new IllegalArgumentException("Champion must not be null.");
        }

        Champion previousSeed = seed;
        SimulationConfig previousConfig = config;
        seed = champion;
        seedDropped = null;
        config = champion.config();

        try {
            reset();
        } catch (IllegalArgumentException e) {
            // e.g. the champion's track was imported and is not available: keep the current run untouched
            seed = previousSeed;
            config = previousConfig;
            throw e;
        }
    }

    /**
     * Back to random populations (from the next restart, which happens now).
     */
    public void clearSeed() {
        seed = null;
        seedDropped = null;
        reset();
    }

    /**
     * Which car of the run the Champion menu works on (save, replay, retrain).
     */
    public enum ChampionKind {
        /** Highest fitness ever (the algorithm's champion). */
        BEST_FITNESS,
        /** Fastest lap ever, whatever its generation (often a faster but riskier driver). */
        FASTEST_LAP
    }

    public void setChampionKind(ChampionKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("Champion kind must not be null.");
        }

        championKind = kind;
    }

    public ChampionKind getChampionKind() {
        return championKind;
    }

    /**
     * The champion of the running training by the chosen {@link ChampionKind}, or null when there is none yet (no
     * generation evaluated, or no lap completed for {@link ChampionKind#FASTEST_LAP}). It lives as long as the run:
     * a restart starts a new memory.
     */
    public Champion getCurrentChampion() {
        return switch (championKind) {
            case BEST_FITNESS -> algorithm.getChampion() == null ? null : Champion.of(algorithm);
            case FASTEST_LAP -> algorithm.getFastestLap() == null ? null : Champion.fastestLap(algorithm);
        };
    }

    /**
     * The champion the populations are bred from, or null for random populations.
     */
    public Champion getSeed() {
        return seed;
    }

    /**
     * The seed the last restart had to give up because the configuration changed the network shape (inputs or
     * hidden layers), or null; reading it clears it, so the interface reports it once.
     */
    public Champion takeDroppedSeed() {
        Champion dropped = seedDropped;
        seedDropped = null;
        return dropped;
    }

    /**
     * Shows a saved champion driving alone on its first track, in a loop, with its own configuration (track, car,
     * sensors). The training run is set aside untouched and resumes with {@link #stopReplay()}.
     *
     * @throws IllegalArgumentException if the champion's track is not available; nothing changes then
     */
    public void startReplay(Champion champion) {
        if (champion == null) {
            throw new IllegalArgumentException("Champion must not be null.");
        }

        Simulation simulation = champion.replay();

        if (replay == null) {
            pausedTraining = simulations;
        }

        replay = champion;
        simulations = List.of(simulation);
        selectedCarId = -1;
    }

    /**
     * Back to the training run, exactly where it was left.
     */
    public void stopReplay() {
        if (replay == null) {
            return;
        }

        simulations = pausedTraining;
        pausedTraining = null;
        replay = null;
        selectedCarId = -1;
    }

    public boolean isReplaying() {
        return replay != null;
    }

    /**
     * The replayed champion, or null when training.
     */
    public Champion getReplay() {
        return replay;
    }

    /**
     * Applies a configuration: hot changes (mutation, selection, elitism, crossover, fitness) take effect at the
     * next breeding; others (population, architecture, sensors, tracks, seed) restart the run.
     *
     * @return true if the run was reset
     */
    public boolean applyConfig(SimulationConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("Configuration must not be null.");
        }

        boolean needsReset = config.requiresReset(newConfig);
        config = newConfig;

        if (needsReset) {
            reset();
        } else {
            algorithm.updateConfig(newConfig);
        }

        return needsReset;
    }

    /**
     * One displayed image in fixed-step mode: {@code speed} steps if playing, nothing when paused.
     */
    public void tick() {
        if (!playing) {
            return;
        }

        advance(speed);
    }

    /**
     * One displayed image, {@code elapsedSeconds} after the previous one: simulates elapsed × speed seconds,
     * within the {@link #FRAME_BUDGET_NANOS} budget. Nothing happens when paused.
     */
    public void tick(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0.0) {
            throw new IllegalArgumentException("Elapsed time must be finite and non-negative.");
        }

        if (!playing) {
            effectiveSpeed = Double.NaN;
            return;
        }

        double dt = getDisplayedSimulation().getSettings().dt(); // a replay keeps its own time step
        double elapsed = Math.min(elapsedSeconds, MAX_FRAME_GAP);
        owedSteps += elapsed * speed / dt;
        long start = clock.getAsLong();
        int simulated = 0;

        while (owedSteps >= 1.0) {
            int batch = (int) Math.min(owedSteps, BATCH_STEPS);
            advance(batch);
            owedSteps -= batch;
            simulated += batch;

            if (clock.getAsLong() - start >= FRAME_BUDGET_NANOS) {
                owedSteps = 0.0;
                break;
            }
        }

        if (elapsed > 0.0) {
            double speedNow = simulated * dt / elapsed;
            // Smoothed over ~10 images so the displayed value is readable.
            effectiveSpeed = Double.isNaN(effectiveSpeed) ? speedNow : 0.9 * effectiveSpeed + 0.1 * speedNow;
        }
    }

    /**
     * Simulated seconds per real second over the last images (≈ the speed unless the budget limits it),
     * NaN when paused or in fixed-step mode.
     */
    public double getEffectiveSpeed() {
        return effectiveSpeed;
    }

    /**
     * Exactly one simulation step (also usable while paused, e.g. "next step").
     */
    public void step() {
        advance(1);
    }

    /**
     * {@code steps} steps of the displayed track, in parallel; a generation (or replay) that ends part-way is
     * followed by the next one for the remaining steps.
     */
    private void advance(int steps) {
        int remaining = steps;

        while (remaining > 0) {
            Simulation displayed = getDisplayedSimulation();
            remaining -= displayed.advance(remaining);

            if (displayed.isFinished()) {
                endOfRun();
            }
        }
    }

    private void endOfRun() {
        if (replay != null) {
            simulations = List.of(replay.replay());
            return;
        }

        for (Simulation other : simulations) {
            other.runUntilAllDead();
        }

        GenerationStats stats = algorithm.completeGeneration(simulations);
        simulations = algorithm.createSimulations();
        selectedCarId = -1;
        generationListeners.forEach(listener -> listener.accept(stats));
    }

    public void addGenerationListener(Consumer<GenerationStats> listener) {
        generationListeners.add(listener);
    }

    /**
     * Selects the car closest to a world point (within 8 units), or clears the selection.
     */
    public void selectCarAt(Vec2 point) {
        selectedCarId = getDisplayedSimulation().getAgents().stream()
                .filter(agent -> agent.getCar().getPosition().distanceTo(point) <= 8.0)
                .min(Comparator.comparingDouble(agent -> agent.getCar().getPosition().distanceTo(point)))
                .map(CarAgent::getId)
                .orElse(-1);
    }

    /**
     * The selected car, or else the living car with the most progress, or else the car with the most progress.
     */
    public CarAgent getFocusedAgent() {
        List<CarAgent> agents = getDisplayedSimulation().getAgents();

        if (selectedCarId >= 0 && selectedCarId < agents.size()) {
            return agents.get(selectedCarId);
        }

        Comparator<CarAgent> byProgress = Comparator.comparingDouble(CarAgent::getProgress);
        return agents.stream().filter(agent -> agent.getCar().isAlive()).max(byProgress)
                .orElseGet(() -> agents.stream().max(byProgress).orElse(null));
    }

    /**
     * Dashboard data of the inspected car, or null if there is none.
     */
    public Telemetry getFocusedTelemetry() {
        CarAgent focused = getFocusedAgent();
        return focused == null ? null : Telemetry.of(focused, getDisplayedSimulation().getVehicle());
    }

    /**
     * True when the inspected car was chosen by a click (otherwise the best car is followed automatically).
     */
    public boolean hasSelection() {
        return selectedCarId >= 0;
    }

    /**
     * Names of the inspected car's network inputs (rays, speed, car state), empty if there is no car.
     */
    public List<String> getFocusedInputLabels() {
        CarAgent focused = getFocusedAgent();
        return focused == null ? List.of() : focused.getBrain().inputLabels();
    }

    public NetworkSnapshot getFocusedNetwork() {
        CarAgent focused = getFocusedAgent();
        return focused == null ? null
                : NetworkSnapshot.of(focused.getBrain().getNetwork(), focused.getBrain().getLastInputs());
    }

    public SimulationFrame frame() {
        Simulation displayed = getDisplayedSimulation();
        CarAgent focused = getFocusedAgent();
        List<SimulationFrame.CarSprite> cars = new ArrayList<>();
        List<List<Vec2>> trails = new ArrayList<>();

        for (CarAgent agent : displayed.getAgents()) {
            boolean alive = agent.getCar().isAlive();

            if (alive || renderOptions.showDeadCars()) {
                cars.add(new SimulationFrame.CarSprite(agent.getId(), agent.getCar().getX(), agent.getCar().getY(),
                        agent.getCar().getAngle(), alive, alive && agent.getCar().isSliding()));

                if (renderOptions.showTrails()) {
                    trails.add(agent.getTrail());
                }
            }
        }

        Vec2 rayOrigin = null;
        List<Vec2> rayEnds = List.of();

        if (renderOptions.showRays() && focused != null && focused.getCar().isAlive()) {
            rayOrigin = focused.getCar().getPosition();
            rayEnds = Arrays.asList(focused.getBrain().getSensor().hitPoints(focused.getCar(), displayed.getTrack()));
        }

        SimulationFrame.CarSprite ghost = null;
        LapReplay bestLap = algorithm.getBestLap();

        if (renderOptions.showGhost() && bestLap != null && bestLap.getTrackName().equals(displayed.getTrack().getName())) {
            int frame = bestLap.frameAtTime(displayed.getTime() % bestLap.getLapTime());
            Vec2 position = bestLap.positionAt(frame);
            ghost = new SimulationFrame.CarSprite(-1, position.x(), position.y(), bestLap.angleAt(frame), true, false);
        }

        int nextCheckpoint = !renderOptions.showCheckpoints() ? -1
                : focused == null ? 0 : focused.getLapTracker().getNextCheckpointIndex();

        return new SimulationFrame(displayed.getTrack(), cars, focused == null ? -1 : focused.getId(), rayOrigin,
                rayEnds, trails, nextCheckpoint, ghost);
    }

    public Simulation getDisplayedSimulation() {
        return simulations.getFirst();
    }

    /**
     * Generation being simulated, 0 for the random initial population.
     */
    public int getGeneration() {
        return algorithm.getGeneration();
    }

    public int getAliveCount() {
        return getDisplayedSimulation().getAliveCount();
    }

    public StatsHistory getHistory() {
        return algorithm.getHistory();
    }

    public GeneticAlgorithm getAlgorithm() {
        return algorithm;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public RenderOptions getRenderOptions() {
        return renderOptions;
    }
}
