package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.sim.DynamicModel;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.TrackPreview;
import org.autonomous_vehicles.sim.VehicleModel;
import org.autonomous_vehicles.sim.Vehicles;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Complete, validated and immutable configuration of an evolution run.
 * Some parameters can change between two generations ({@link #requiresReset} is false for them);
 * the others change the population or the world and need a new run.
 */
public record SimulationConfig(
        int populationSize,
        int[] hiddenLayers,
        boolean useSpeedInput,
        boolean useCarStateInputs,
        boolean useTrackPreview,
        int rayCount,
        double rayRange,
        RayLayout rayLayout,
        double mutationRate,
        double mutationStrength,
        MutationOperator.Noise noise,
        SelectionType selection,
        int tournamentSize,
        double truncationFraction,
        int eliteCount,
        Crossover crossover,
        FitnessMode fitnessMode,
        CompositeFitness compositeFitness,
        List<String> tracks,
        SimulationSettings settings,
        String carType,
        TireCompound tire,
        RaceRules rules,
        boolean gripSteering,
        long seed) {

    public enum Crossover { NONE, UNIFORM, ONE_POINT }

    public enum FitnessMode { PROGRESS, COMPOSITE }

    /**
     * How the rays are spread over the 180° in front of the car: evenly (the assignment), or packed towards the front
     * ({@link RaySensor#forwardFocused}) for fast cars, which need to see far ahead more than to the side.
     */
    public enum RayLayout { EVEN, FORWARD }

    public static final int MAX_POPULATION = 1000;
    public static final int MAX_LAYER_SIZE = 64;
    public static final int MAX_HIDDEN_LAYERS = 4;
    public static final int MAX_RAYS = 15;

    public SimulationConfig {
        require(populationSize >= 2 && populationSize <= MAX_POPULATION,
                "Population size must be between 2 and " + MAX_POPULATION + ".");
        require(hiddenLayers != null && hiddenLayers.length <= MAX_HIDDEN_LAYERS,
                "At most " + MAX_HIDDEN_LAYERS + " hidden layers.");
        hiddenLayers = hiddenLayers.clone();

        for (int size : hiddenLayers) {
            require(size >= 1 && size <= MAX_LAYER_SIZE, "Hidden layer sizes must be between 1 and " + MAX_LAYER_SIZE + ".");
        }

        require(rayCount >= 1 && rayCount <= MAX_RAYS, "Ray count must be between 1 and " + MAX_RAYS + ".");
        require(Double.isFinite(rayRange) && rayRange > 0.0 && rayRange <= 500.0, "Ray range must be in (0, 500].");
        require(Double.isFinite(mutationRate) && mutationRate >= 0.0 && mutationRate <= 1.0, "Mutation rate must be in [0, 1].");
        require(Double.isFinite(mutationStrength) && mutationStrength >= 0.0 && mutationStrength <= 5.0,
                "Mutation strength must be in [0, 5].");
        require(noise != null && selection != null && crossover != null && fitnessMode != null && compositeFitness != null
                && settings != null && rayLayout != null, "Configuration values must not be null.");
        require(tournamentSize >= 1 && tournamentSize <= populationSize, "Tournament size must be between 1 and the population size.");
        require(Double.isFinite(truncationFraction) && truncationFraction > 0.0 && truncationFraction <= 1.0,
                "Truncation fraction must be in (0, 1].");
        require(eliteCount >= 0 && eliteCount < populationSize, "Elite count must be between 0 and population size - 1.");
        require(tracks != null && !tracks.isEmpty() && tracks.stream().allMatch(name -> name != null && !name.isBlank()),
                "At least one track is required.");
        tracks = List.copyOf(tracks);
        require(Vehicles.TYPES.contains(carType), "Car type must be one of " + Vehicles.TYPES + ".");
        require(tire != null, "Tyre compound must not be null.");
        require(rules != null, "Race rules must not be null.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Starting values of the assignment (section 6.1): 100 cars, 5-6-2 Tanh, 5 rays,
     * mutation 8 % with σ = 0.25, 5 % elitism, dt = 1/60 s. Fuel and tyre temperature are on, but the kinematic
     * car of the assignment ignores them: they act once a grip-limited car type is chosen.
     */
    public static SimulationConfig defaults() {
        return new SimulationConfig(100, new int[]{6}, false, false, false, 5, RaySensor.DEFAULT_RANGE,
                RayLayout.EVEN, 0.08, 0.25, MutationOperator.Noise.GAUSSIAN, SelectionType.TOURNAMENT, 3,
                TruncationSelection.DEFAULT_FRACTION, 5, Crossover.NONE, FitnessMode.COMPOSITE,
                CompositeFitness.defaults(), List.of("oval"), SimulationSettings.defaults(), Vehicles.KINEMATIC,
                TireCompound.MEDIUM, RaceRules.defaults(), false, 42L);
    }

    @Override
    public int[] hiddenLayers() {
        return hiddenLayers.clone();
    }

    /**
     * Rays, then the speed (optional), then the {@link CarBrain#CAR_STATE_INPUTS} car state inputs (optional), then
     * the {@link TrackPreview#INPUTS} road-ahead readings (optional).
     */
    public int inputSize() {
        return rayCount + (useSpeedInput ? 1 : 0) + (useCarStateInputs ? CarBrain.CAR_STATE_INPUTS : 0)
                + (useTrackPreview ? TrackPreview.INPUTS : 0);
    }

    /**
     * Full architecture: inputs, hidden layers, 2 outputs.
     */
    public int[] architecture() {
        int[] architecture = new int[hiddenLayers.length + 2];
        architecture[0] = inputSize();
        System.arraycopy(hiddenLayers, 0, architecture, 1, hiddenLayers.length);
        architecture[architecture.length - 1] = CarBrain.OUTPUT_SIZE;
        return architecture;
    }

    public String architectureLabel() {
        return Arrays.stream(architecture()).mapToObj(String::valueOf).collect(Collectors.joining("-"));
    }

    public RaySensor createSensor() {
        return rayLayout == RayLayout.FORWARD ? RaySensor.forwardFocused(rayCount, Math.PI, rayRange)
                : RaySensor.evenlySpread(rayCount, Math.PI, rayRange);
    }

    public VehicleModel createVehicle() {
        return Vehicles.create(carType, tire, rules, gripSteering);
    }

    /**
     * One simulation per network on {@code track}, observing and driving as this configuration says.
     */
    public Simulation createSimulation(Track track, List<NeuralNetwork> networks) {
        return new Simulation(track, networks, createSensor(), useSpeedInput, useCarStateInputs, useTrackPreview,
                settings, createVehicle());
    }

    public Evaluator createEvaluator(List<Track> tracks) {
        return new Evaluator(tracks, createSensor(), useSpeedInput, useCarStateInputs, useTrackPreview, settings,
                createFitness(), createVehicle());
    }

    public FitnessFunction createFitness() {
        return fitnessMode == FitnessMode.PROGRESS ? new ProgressFitness() : compositeFitness;
    }

    /**
     * True when going from this configuration to {@code other} needs a new population or a new world.
     * Mutation, selection, elitism, crossover and fitness can change between two generations.
     */
    public boolean requiresReset(SimulationConfig other) {
        return populationSize != other.populationSize
                || !Arrays.equals(hiddenLayers, other.hiddenLayers)
                || useSpeedInput != other.useSpeedInput
                || useCarStateInputs != other.useCarStateInputs
                || useTrackPreview != other.useTrackPreview
                || rayCount != other.rayCount
                || rayRange != other.rayRange
                || rayLayout != other.rayLayout
                || !tracks.equals(other.tracks)
                || !settings.equals(other.settings)
                || !carType.equals(other.carType)
                || tire != other.tire
                || !rules.equals(other.rules)
                || gripSteering != other.gripSteering
                || seed != other.seed;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SimulationConfig config
                && !requiresReset(config)
                && mutationRate == config.mutationRate
                && mutationStrength == config.mutationStrength
                && noise == config.noise
                && selection == config.selection
                && tournamentSize == config.tournamentSize
                && truncationFraction == config.truncationFraction
                && eliteCount == config.eliteCount
                && crossover == config.crossover
                && fitnessMode == config.fitnessMode
                && compositeFitness.equals(config.compositeFitness);
    }

    @Override
    public int hashCode() {
        return Objects.hash(populationSize, Arrays.hashCode(hiddenLayers), mutationRate, selection, tracks, seed);
    }

    @Override
    public String toString() {
        return "SimulationConfig[" + architectureLabel() + ", population=" + populationSize
                + ", mutation=" + mutationRate + "/" + mutationStrength + " " + noise
                + ", selection=" + selection + (selection == SelectionType.TOURNAMENT ? " k=" + tournamentSize : "")
                + (selection == SelectionType.TRUNCATION ? " " + truncationFraction : "")
                + ", elites=" + eliteCount + ", crossover=" + crossover + ", fitness=" + fitnessMode
                + ", tracks=" + tracks + ", car=" + carType
                + (carType.equals(Vehicles.KINEMATIC) ? "" : "/" + tire + rulesLabel())
                + (rayLayout == RayLayout.EVEN ? "" : ", rays " + rayLayout)
                + (useTrackPreview ? ", track preview" : "") + ", seed=" + seed + "]";
    }

    private String rulesLabel() {
        return (rules.limitedFuel() ? String.format(java.util.Locale.ROOT, " fuel %.0fs", rules.fuelAutonomy()) : "")
                + (rules.tyreTemperature() ? " tyre-temp" : "")
                + (rules.tyreWear() == 1.0 ? "" : String.format(java.util.Locale.ROOT, " wear x%.2f", rules.tyreWear()))
                + (rules.tyreHeating() == DynamicModel.TYRE_HEATING ? ""
                        : String.format(java.util.Locale.ROOT, " heat %.2f", rules.tyreHeating()))
                + (gripSteering ? " grip-steering" : "");
    }

    /**
     * Copy-and-modify helper; {@link #build()} validates the result.
     */
    public static final class Builder {
        private int populationSize;
        private int[] hiddenLayers;
        private boolean useSpeedInput;
        private boolean useCarStateInputs;
        private boolean useTrackPreview;
        private int rayCount;
        private double rayRange;
        private RayLayout rayLayout;
        private double mutationRate;
        private double mutationStrength;
        private MutationOperator.Noise noise;
        private SelectionType selection;
        private int tournamentSize;
        private double truncationFraction;
        private int eliteCount;
        private Crossover crossover;
        private FitnessMode fitnessMode;
        private CompositeFitness compositeFitness;
        private List<String> tracks;
        private SimulationSettings settings;
        private String carType;
        private TireCompound tire;
        private RaceRules rules;
        private boolean gripSteering;
        private long seed;

        private Builder(SimulationConfig config) {
            populationSize = config.populationSize;
            hiddenLayers = config.hiddenLayers();
            useSpeedInput = config.useSpeedInput;
            useCarStateInputs = config.useCarStateInputs;
            useTrackPreview = config.useTrackPreview;
            rayCount = config.rayCount;
            rayRange = config.rayRange;
            rayLayout = config.rayLayout;
            mutationRate = config.mutationRate;
            mutationStrength = config.mutationStrength;
            noise = config.noise;
            selection = config.selection;
            tournamentSize = config.tournamentSize;
            truncationFraction = config.truncationFraction;
            eliteCount = config.eliteCount;
            crossover = config.crossover;
            fitnessMode = config.fitnessMode;
            compositeFitness = config.compositeFitness;
            tracks = config.tracks;
            settings = config.settings;
            carType = config.carType;
            tire = config.tire;
            rules = config.rules;
            gripSteering = config.gripSteering;
            seed = config.seed;
        }

        public Builder populationSize(int value) { populationSize = value; return this; }
        public Builder hiddenLayers(int... value) { hiddenLayers = value == null ? null : value.clone(); return this; }
        public Builder useSpeedInput(boolean value) { useSpeedInput = value; return this; }
        public Builder useCarStateInputs(boolean value) { useCarStateInputs = value; return this; }
        public Builder useTrackPreview(boolean value) { useTrackPreview = value; return this; }
        public Builder rayCount(int value) { rayCount = value; return this; }
        public Builder rayRange(double value) { rayRange = value; return this; }
        public Builder rayLayout(RayLayout value) { rayLayout = value; return this; }
        public Builder mutationRate(double value) { mutationRate = value; return this; }
        public Builder mutationStrength(double value) { mutationStrength = value; return this; }
        public Builder noise(MutationOperator.Noise value) { noise = value; return this; }
        public Builder selection(SelectionType value) { selection = value; return this; }
        public Builder tournamentSize(int value) { tournamentSize = value; return this; }
        public Builder truncationFraction(double value) { truncationFraction = value; return this; }
        public Builder eliteCount(int value) { eliteCount = value; return this; }
        public Builder crossover(Crossover value) { crossover = value; return this; }
        public Builder fitnessMode(FitnessMode value) { fitnessMode = value; return this; }
        public Builder compositeFitness(CompositeFitness value) { compositeFitness = value; return this; }
        public Builder tracks(List<String> value) { tracks = value; return this; }
        public Builder tracks(String... value) { tracks = List.of(value); return this; }
        public Builder settings(SimulationSettings value) { settings = value; return this; }
        public Builder carType(String value) { carType = value; return this; }
        public Builder tire(TireCompound value) { tire = value; return this; }
        public Builder rules(RaceRules value) { rules = value; return this; }
        public Builder gripSteering(boolean value) { gripSteering = value; return this; }
        public Builder seed(long value) { seed = value; return this; }

        public SimulationConfig build() {
            return new SimulationConfig(populationSize, hiddenLayers, useSpeedInput, useCarStateInputs, useTrackPreview,
                    rayCount, rayRange, rayLayout, mutationRate, mutationStrength, noise, selection, tournamentSize,
                    truncationFraction, eliteCount, crossover, fitnessMode, compositeFitness, tracks, settings, carType,
                    tire, rules, gripSteering, seed);
        }
    }
}
