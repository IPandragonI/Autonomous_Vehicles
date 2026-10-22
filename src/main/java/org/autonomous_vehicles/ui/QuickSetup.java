package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.CompositeFitness;
import org.autonomous_vehicles.evolution.MutationOperator;
import org.autonomous_vehicles.evolution.SelectionType;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.KinematicModel;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.sim.VehicleModel;
import org.autonomous_vehicles.sim.Vehicles;

import java.util.List;

/**
 * One-click setup used by the toolbar: changing track, car or tyres also applies the settings that were measured
 * to work, so a beginner does not end up with cars that can never finish a lap.
 * <ul>
 *     <li>real (grip-limited) cars get the speed input — without it the network cannot know when to brake —
 *     and the car state inputs (fuel, tyre temperature, wear, sliding), since fuel and tyre temperature are on by
 *     default; the simple kinematic car goes back to the assignment's 5 inputs;</li>
 *     <li>real cars get {@link #FAST_CAR_RAYS} rays packed towards the front ({@link SimulationConfig.RayLayout#FORWARD}),
 *     the kinematic car the assignment's 5 evenly spread rays. Measured with the formula car on São Paulo,
 *     100 generations, 8 seeds: 7 seeds lap (best 3:18, two seeds 2 laps) against 6 (best 4:23) with 5 even rays.
 *     The same 9 rays with a 200 m range did worse (1 seed laps): far walls squeeze the near readings;</li>
 *     <li>the time limit is {@link SimulationSettings#recommendedMaxTime} of the longest selected track.</li>
 * </ul>
 * Everything else (population, network, ray range, evolution, fitness) is kept. The track preview, grip steering
 * and the {@code TIME_SAVED} fitness stay opt-in (control panel): same measurement, they did not make training more
 * reliable within 100 generations (preview: 4 of 8 seeds lap, but the two fastest laps of the study, 2:59 and 3:01;
 * grip steering: no seed laps). Plain Java, unit-tested.
 */
public final class QuickSetup {

    public static final int FAST_CAR_RAYS = 9;
    public static final int ASSIGNMENT_RAYS = 5;

    private QuickSetup() {
    }

    public static SimulationConfig apply(SimulationConfig config, List<String> tracks, String carType,
                                         TireCompound tire) {
        VehicleModel vehicle = Vehicles.create(carType, tire);
        double timeLimit = tracks.stream()
                .mapToDouble(name -> SimulationSettings.recommendedMaxTime(Tracks.byName(name), vehicle))
                .max().orElse(SimulationSettings.defaults().maxTime());

        boolean real = !(vehicle instanceof KinematicModel);

        return config.toBuilder()
                .tracks(tracks)
                .carType(carType)
                .tire(tire)
                .useSpeedInput(real)
                .useCarStateInputs(real)
                .rayCount(real ? FAST_CAR_RAYS : ASSIGNMENT_RAYS)
                .rayLayout(real ? SimulationConfig.RayLayout.FORWARD : SimulationConfig.RayLayout.EVEN)
                .settings(config.settings().withMaxTime(timeLimit))
                .build();
    }

    /**
     * The settings that learned fastest, measured with the formula car on São Paulo (about 10 000 car evaluations
     * per run, 8 seeds; median progress in checkpoints / runs with 2 laps / best lap): truncation 10 % with 5 elites
     * 231 / 5 of 8 / 2:50, against 189 / 2 of 8 / 3:03 for the tournament k=3 of the assignment. Stronger mutation
     * did worse (15 %/0.5: 165; 30 %/0.8: 68), weaker mutation stalled (4 %/0.15: 65), 200 cars × 50 generations was
     * slower than 100 × 100 (177), crossover did not help (197), and the default fitness weights were as good as the
     * alternatives. Truncation 10 % also lapped fastest on the hairpin with the kinematic car (experiment C).
     * <p>
     * Unlimited fuel: with the default 25 s no car finishes a São Paulo lap. Tyre temperature stays on, wear is the
     * reduced default. Track, car, tyres and seed are kept; inputs, rays and time limit come from {@link #apply}.
     */
    public static SimulationConfig recommended(SimulationConfig config) {
        SimulationConfig base = config.toBuilder()
                .populationSize(100)
                .hiddenLayers(6)
                .mutationRate(0.08)
                .mutationStrength(0.25)
                .noise(MutationOperator.Noise.GAUSSIAN)
                .selection(SelectionType.TRUNCATION)
                .truncationFraction(0.10)
                .tournamentSize(3)
                .eliteCount(5)
                .crossover(SimulationConfig.Crossover.NONE)
                .fitnessMode(SimulationConfig.FitnessMode.COMPOSITE)
                .compositeFitness(CompositeFitness.defaults())
                .rayRange(RaySensor.DEFAULT_RANGE)
                .useTrackPreview(false)
                .gripSteering(false)
                .rules(RaceRules.defaults().withFuelAutonomy(0.0))
                .build();

        return apply(base, config.tracks(), config.carType(), config.tire());
    }

    /**
     * Short explanation of what {@link #apply} chose, for the status bar.
     */
    public static String describe(SimulationConfig config) {
        return String.format(java.util.Locale.ROOT, "%s on %s%s · time limit %.0f s%s",
                Vehicles.create(config.carType(), config.tire()).name(), String.join(" + ", config.tracks()),
                (config.useSpeedInput() ? " · speed input on" : "")
                        + (config.useCarStateInputs() ? " · fuel/tyre inputs on" : ""), config.settings().maxTime(),
                " · new population");
    }
}
