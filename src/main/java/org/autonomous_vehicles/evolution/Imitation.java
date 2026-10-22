package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.neural.Trainer;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.Car;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.sim.CarControls;
import org.autonomous_vehicles.sim.Collision;
import org.autonomous_vehicles.sim.DynamicModel;
import org.autonomous_vehicles.sim.ExpertDriver;
import org.autonomous_vehicles.sim.LapTracker;
import org.autonomous_vehicles.sim.RacingLine;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.Rng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Supervised pre-training of a driving network by imitating {@link ExpertDriver} (behaviour cloning + DAgger), so
 * that evolution starts from a car that already brakes for the bends instead of discovering it by mutation.
 * <ol>
 *     <li>Demonstrations: the expert drives laps with some steering and throttle noise (so that it also shows how
 *     to recover from errors it would never make); every step records what the network would see
 *     ({@link CarBrain#inputs}) and the expert's controls without the noise.</li>
 *     <li>The network learns them by mini-batch backpropagation ({@link Trainer}).</li>
 *     <li>DAgger rounds: the network drives, the expert labels every state it visits (including its mistakes),
 *     the examples are added and the network is trained again. Plain cloning drifts away from the expert's states
 *     and then has no example of how to come back.</li>
 * </ol>
 * The result has the configuration's architecture, so it seeds {@link GeneticAlgorithm#GeneticAlgorithm(
 * SimulationConfig, NeuralNetwork)} directly. Deterministic for a given configuration and seed.
 */
public final class Imitation {

    /**
     * @param demonstrations noisy expert laps recorded first
     * @param daggerRounds   rounds where the network drives and the expert labels
     * @param epochs         training passes after the demonstrations
     * @param roundEpochs    training passes after each DAgger round
     * @param learningRate   gradient descent step
     * @param batchSize      examples per gradient step
     * @param noise          standard deviation of the noise added to the expert's steering and throttle
     * @param racingLine     the expert follows {@link RacingLine#of} instead of the centre line
     * @param cornerMargin   the expert's share of the grip in bends ({@link ExpertDriver})
     * @param brakeMargin    the expert's share of the braking limit
     */
    public record Settings(int demonstrations, int daggerRounds, int epochs, int roundEpochs, double learningRate,
                           int batchSize, double noise, boolean racingLine, double cornerMargin, double brakeMargin) {

        public Settings {
            if (demonstrations < 1 || daggerRounds < 0 || epochs < 1 || roundEpochs < 1 || batchSize < 1
                    || !(learningRate > 0.0) || !(noise >= 0.0)) {
                throw new IllegalArgumentException("Invalid imitation settings.");
            }
        }

        /**
         * Expert on the racing line with the default margins.
         */
        public Settings(int demonstrations, int daggerRounds, int epochs, int roundEpochs, double learningRate,
                        int batchSize, double noise) {
            this(demonstrations, daggerRounds, epochs, roundEpochs, learningRate, batchSize, noise, true,
                    ExpertDriver.CORNER_MARGIN, ExpertDriver.BRAKE_MARGIN);
        }

        /**
         * Racing-line expert, 3 demonstrations, 8 DAgger rounds. Stages fluctuate (a round can regress), hence the
         * best stage is kept. 2–5 minutes on São Paulo with a 32-32 network.
         */
        public static Settings defaults() {
            return new Settings(3, 8, 60, 20, 0.05, 32, 0.15);
        }
    }

    /**
     * How the network did after one training stage, driving alone with the configuration's settings.
     *
     * @param stage    0 = after the demonstrations, then one per DAgger round
     * @param examples examples learned so far
     * @param loss     mean squared error on them
     * @param result   the network's drive on the first track
     */
    public record Stage(int stage, int examples, double loss, AgentResult result) {
    }

    /**
     * @param network        the best stage's network (most laps, then fastest lap, then most progress)
     * @param best           that stage
     * @param expertLapTimes laps of the clean expert demonstration, s
     * @param stages         one entry per training stage
     */
    public record Report(NeuralNetwork network, Stage best, List<Double> expertLapTimes, List<Stage> stages) {

        public Report {
            expertLapTimes = List.copyOf(expertLapTimes);
            stages = List.copyOf(stages);
        }
    }

    private Imitation() {
    }

    /**
     * The configuration to imitate with, then to evolve from: the road ahead as inputs, a 32-32 network and fine
     * mutation (3 %/0.1). Measured with the formula car on São Paulo:
     * <ul>
     *     <li>rays only never lap (6 neurons: crash at the first bend; 16-16: 35/108): rays do not show where the
     *     expert brakes 150 m ahead;</li>
     *     <li>centre-line expert (1:51.5 flying), 16-16: the imitated network laps in 1:55.7 on its own, and
     *     8 %/0.25 evolution does not improve it in 50 generations; 32-32 then 3 %/0.1: 1:50.2;</li>
     *     <li>racing-line expert (1:38.3): imitation alone does not lap (the line passes close to the walls), but
     *     evolution from it does — fastest lap after 150 generations at 3 %/0.1: 16-16 1:52.2 (60 gen), 32-32
     *     <b>1:43.6</b> (best evolved from scratch: 2:24).</li>
     * </ul>
     */
    public static SimulationConfig recommendedConfig(SimulationConfig config) {
        return config.toBuilder().useTrackPreview(true).hiddenLayers(32, 32).mutationRate(0.03).mutationStrength(0.1)
                .build();
    }

    /**
     * @throws IllegalArgumentException if the configuration's car is the kinematic one (the expert needs the grip
     *                                  model) or a track has no centre line
     */
    public static Report train(SimulationConfig config, Settings settings) {
        if (!(config.createVehicle() instanceof DynamicModel)) {
            throw new IllegalArgumentException("Imitation needs a grip-limited car: the expert drives with its grip.");
        }

        Rng rng = new Rng(config.seed());
        List<Track> tracks = config.tracks().stream().map(Tracks::byName).toList();
        NeuralNetwork network = new NeuralNetwork(rng, config.architecture());
        Trainer trainer = new Trainer(network, settings.learningRate());
        List<double[]> inputs = new ArrayList<>();
        List<double[]> targets = new ArrayList<>();
        List<Double> expertLaps = new ArrayList<>();

        Map<Track, List<Vec2>> paths = new HashMap<>();

        for (Track track : tracks) {
            paths.put(track, settings.racingLine() ? RacingLine.of(track)
                    : RacingLine.minimumCurvature(track, Car.RADIUS, 0)); // 0 passes: the centre line
        }

        Function<Track, ExpertDriver> experts = track -> new ExpertDriver((DynamicModel) config.createVehicle(),
                paths.get(track), settings.cornerMargin(), settings.brakeMargin());

        for (Track track : tracks) {
            for (int lap = 0; lap < settings.demonstrations(); lap++) {
                // The first demonstration is clean: it gives the expert's lap time and its exact line.
                double noise = lap == 0 ? 0.0 : settings.noise();
                List<Double> laps = drive(config, track, experts.apply(track), network, noise, 1.0, rng, inputs,
                        targets);

                if (lap == 0 && track == tracks.getFirst()) {
                    expertLaps.addAll(laps);
                }
            }
        }

        List<Stage> stages = new ArrayList<>();
        fit(trainer, inputs, targets, settings.epochs(), settings.batchSize(), rng);
        stages.add(evaluate(0, config, tracks.getFirst(), network, trainer, inputs, targets));
        NeuralNetwork best = network.copy();
        Stage bestStage = stages.getFirst();

        for (int round = 1; round <= settings.daggerRounds(); round++) {
            // The expert's share of the driving falls from 50 % to ~4 % over the rounds (the β of DAgger): early
            // networks would crash at the first bend and only ever show that bend.
            double expertShare = 0.5 * Math.pow(0.7, round - 1);

            for (Track track : tracks) {
                drive(config, track, experts.apply(track), network, 0.0, expertShare, rng, inputs, targets);
            }

            fit(trainer, inputs, targets, settings.roundEpochs(), settings.batchSize(), rng);
            Stage stage = evaluate(round, config, tracks.getFirst(), network, trainer, inputs, targets);
            stages.add(stage);

            if (better(stage.result(), bestStage.result())) {
                best = network.copy();
                bestStage = stage;
            }
        }

        return new Report(best, bestStage, expertLaps, stages);
    }

    /** Steps between two draws of who drives (0.5 s at 60 Hz): long enough for the network's errors to show. */
    private static final int CONTROL_BLOCK = 30;

    /**
     * One run of at most two laps (or the time limit, a crash or the stagnation limit), recording the network's
     * inputs and the expert's controls at every step. Who drives is drawn every {@link #CONTROL_BLOCK} steps: the
     * expert (with noise) with probability {@code expertShare}, else the network.
     *
     * @return completed lap times
     */
    private static List<Double> drive(SimulationConfig config, Track track, ExpertDriver expert, NeuralNetwork network,
                                      double noise, double expertShare, Rng rng, List<double[]> inputs,
                                      List<double[]> targets) {
        DynamicModel model = (DynamicModel) config.createVehicle();
        CarBrain brain = new CarBrain(network, config.createSensor(), config.useSpeedInput(),
                config.useCarStateInputs(), config.useTrackPreview(), model);
        Car car = Car.atStart(track);
        model.prepare(car);
        LapTracker laps = new LapTracker(track);
        double dt = config.settings().dt();
        double steerNoise = 0.0;
        double throttleNoise = 0.0;
        double bestProgress = 0.0;
        double lastImprovement = 0.0;
        boolean networkDrives = false;
        long step = 0;

        while (laps.getElapsedTime() < config.settings().maxTime() && laps.getLaps() < 2) {
            double[] observation = brain.inputs(car, track);
            CarControls label = expert.decide(car);
            inputs.add(observation);
            targets.add(new double[]{label.steer(), label.throttle()});

            if (step++ % CONTROL_BLOCK == 0) {
                networkDrives = expertShare < 1.0 && rng.nextDouble() >= expertShare;
            }

            CarControls applied;

            if (networkDrives) {
                double[] outputs = network.forward(observation);
                applied = new CarControls(Math.clamp(outputs[0], -1.0, 1.0), Math.clamp(outputs[1], -1.0, 1.0));
            } else {
                // Correlated noise (time constant ~0.3 s), so the car really drifts off the expert's line.
                steerNoise = 0.95 * steerNoise + noise * 0.31 * rng.nextGaussian();
                throttleNoise = 0.95 * throttleNoise + noise * 0.31 * rng.nextGaussian();
                applied = new CarControls(Math.clamp(label.steer() + steerNoise, -1.0, 1.0),
                        Math.clamp(label.throttle() + throttleNoise, -1.0, 1.0));
            }

            Vec2 from = car.getPosition();
            model.step(car, applied, dt);
            Vec2 to = car.getPosition();

            if (Collision.hits(car, track) || Collision.crossesWall(from, to, track)) {
                break;
            }

            laps.update(from, to, dt);
            double progress = laps.progress(to);

            if (progress > bestProgress + 0.05) {
                bestProgress = progress;
                lastImprovement = laps.getElapsedTime();
            } else if (laps.getElapsedTime() - lastImprovement > config.settings().stagnationTime()) {
                break;
            }
        }

        return laps.getLapTimes();
    }

    private static void fit(Trainer trainer, List<double[]> inputs, List<double[]> targets, int epochs, int batchSize,
                            Rng rng) {
        int n = inputs.size();
        double[][] x = new double[n][];
        double[][] y = new double[n][];
        int[] order = new int[n];

        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        for (int epoch = 0; epoch < epochs; epoch++) {
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int swap = order[i];
                order[i] = order[j];
                order[j] = swap;
            }

            for (int i = 0; i < n; i++) {
                x[i] = inputs.get(order[i]);
                y[i] = targets.get(order[i]);
            }

            trainer.trainMiniBatch(x, y, batchSize);
        }
    }

    private static Stage evaluate(int stage, SimulationConfig config, Track track, NeuralNetwork network,
                                  Trainer trainer, List<double[]> inputs, List<double[]> targets) {
        double loss = trainer.evaluateLoss(inputs.toArray(double[][]::new), targets.toArray(double[][]::new));
        Simulation simulation = config.createSimulation(track, List.of(network.copy()));
        simulation.runUntilAllDead();
        return new Stage(stage, inputs.size(), loss, simulation.getAgents().getFirst().result());
    }

    /** More laps win; at equal laps, the faster best lap, or the further progress without a lap. */
    private static boolean better(AgentResult candidate, AgentResult best) {
        if (candidate.laps() != best.laps()) {
            return candidate.laps() > best.laps();
        }

        if (candidate.laps() > 0) {
            return candidate.bestLapTime() < best.bestLapTime();
        }

        return candidate.progress() > best.progress();
    }
}
