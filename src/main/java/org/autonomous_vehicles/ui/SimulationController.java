package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.GeneticAlgorithm;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.evolution.StatsHistory;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.CarAgent;
import org.autonomous_vehicles.sim.LapReplay;
import org.autonomous_vehicles.sim.Simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the evolution for the interface, without any JavaFX dependency (so it is unit-tested).
 * <p>
 * Update and rendering are separate: each {@link #tick()} (one per displayed image) runs
 * {@code speed} simulation steps of the displayed track. When every car is stopped, the other
 * tracks (multi-track training) are simulated instantly and the next generation is bred.
 */
public class SimulationController {

    public static final int[] SPEEDS = {1, 2, 10, 100};

    private final RenderOptions renderOptions = new RenderOptions();
    private final List<Consumer<GenerationStats>> generationListeners = new ArrayList<>();

    private SimulationConfig config;
    private GeneticAlgorithm algorithm;
    private List<Simulation> simulations;
    private boolean playing;
    private int speed = 1;
    private int selectedCarId = -1;

    public SimulationController(SimulationConfig config) {
        this.config = config;
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
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * Restarts from a new random population (generation 0) with the current configuration.
     */
    public void reset() {
        algorithm = new GeneticAlgorithm(config);
        simulations = algorithm.createSimulations();
        selectedCarId = -1;
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
     * One displayed image: {@code speed} steps if playing, nothing when paused.
     */
    public void tick() {
        if (!playing) {
            return;
        }

        for (int i = 0; i < speed; i++) {
            step();
        }
    }

    /**
     * Exactly one simulation step (also usable while paused, e.g. "next step").
     */
    public void step() {
        Simulation displayed = getDisplayedSimulation();
        displayed.step();

        if (displayed.isFinished()) {
            for (Simulation other : simulations) {
                other.runUntilAllDead();
            }

            GenerationStats stats = algorithm.completeGeneration(simulations);
            simulations = algorithm.createSimulations();
            selectedCarId = -1;
            generationListeners.forEach(listener -> listener.accept(stats));
        }
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
