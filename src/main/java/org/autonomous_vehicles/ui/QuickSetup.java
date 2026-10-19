package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.evolution.SimulationConfig;
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
 *     <li>real (grip-limited) cars get the speed input — without it the network cannot know when to brake;
 *     the simple kinematic car goes back to the assignment's 5 inputs;</li>
 *     <li>the time limit is {@link SimulationSettings#recommendedMaxTime} of the longest selected track.</li>
 * </ul>
 * Everything else (population, network, evolution, fitness) is kept. Plain Java, unit-tested.
 */
public final class QuickSetup {

    private QuickSetup() {
    }

    public static SimulationConfig apply(SimulationConfig config, List<String> tracks, String carType,
                                         TireCompound tire) {
        VehicleModel vehicle = Vehicles.create(carType, tire);
        double timeLimit = tracks.stream()
                .mapToDouble(name -> SimulationSettings.recommendedMaxTime(Tracks.byName(name), vehicle))
                .max().orElse(SimulationSettings.defaults().maxTime());

        return config.toBuilder()
                .tracks(tracks)
                .carType(carType)
                .tire(tire)
                .useSpeedInput(!(vehicle instanceof KinematicModel))
                .settings(config.settings().withMaxTime(timeLimit))
                .build();
    }

    /**
     * Short explanation of what {@link #apply} chose, for the status bar.
     */
    public static String describe(SimulationConfig config) {
        return String.format(java.util.Locale.ROOT, "%s on %s%s · time limit %.0f s%s",
                Vehicles.create(config.carType(), config.tire()).name(), String.join(" + ", config.tracks()),
                config.useSpeedInput() ? " · speed input on" : "", config.settings().maxTime(),
                " · new population");
    }
}
