package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.ActivationFunction;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.neural.ReLU;
import org.autonomous_vehicles.neural.Sigmoid;
import org.autonomous_vehicles.neural.Tanh;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.Json;
import org.autonomous_vehicles.util.Rng;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A champion saved to disk: its network (weights and biases per layer), the full configuration it was evolved
 * with, and the generation and fitness it reached.
 * <p>
 * Stored as JSON: readable, diffable, and loadable by other tools (a web port, a Python notebook). Unlike the
 * {@code NetworkIO} text format of the assignment, the file carries everything needed to replay the champion:
 * the simulation is deterministic and cars do not interact, so {@link #replay()} drives exactly the trajectory
 * the champion drove during its evaluation.
 *
 * @param generation generation in which the champion was evaluated
 * @param fitness    fitness of that evaluation (mean over the training tracks)
 */
public record Champion(SimulationConfig config, NeuralNetwork network, int generation, double fitness) {

    public static final String FORMAT = "autonomous-vehicles/champion";
    public static final int VERSION = 1;
    public static final String EXTENSION = ".json";

    public Champion {
        if (config == null || network == null) {
            throw new IllegalArgumentException("A champion needs a configuration and a network.");
        }

        if (!Arrays.equals(network.getArchitecture(), config.architecture())) {
            throw new IllegalArgumentException("Network architecture " + label(network.getArchitecture())
                    + " does not match the configuration (" + config.architectureLabel() + ").");
        }

        if (generation < 0 || !Double.isFinite(fitness)) {
            throw new IllegalArgumentException("Generation must not be negative and fitness must be finite.");
        }

        activationName(network.getActivation());
        network = network.copy();
    }

    /**
     * Best individual ever evaluated by the algorithm, with the algorithm's current configuration.
     *
     * @throws IllegalStateException if no generation has been evaluated yet
     */
    public static Champion of(GeneticAlgorithm algorithm) {
        Individual champion = algorithm.getChampion();

        if (champion == null) {
            throw new IllegalStateException("No generation has been evaluated yet.");
        }

        return new Champion(algorithm.getConfig(), champion.getNetwork(), algorithm.getChampionGeneration(),
                champion.getFitness());
    }

    /**
     * The car with the fastest lap of the run ({@link GeneticAlgorithm#getFastestLap()}), as a champion: it can be
     * saved, replayed and retrained like the fittest one.
     *
     * @throws IllegalStateException while no car has completed a lap
     */
    public static Champion fastestLap(GeneticAlgorithm algorithm) {
        Individual fastest = algorithm.getFastestLap();

        if (fastest == null) {
            throw new IllegalStateException("No car has completed a lap yet.");
        }

        return new Champion(algorithm.getConfig(), fastest.getNetwork(), algorithm.getFastestLapGeneration(),
                fastest.getFitness());
    }

    /**
     * Copy of the network (the record keeps its own, so a caller cannot change a saved champion).
     */
    @Override
    public NeuralNetwork network() {
        return network.copy();
    }

    /**
     * A fresh simulation of this champion alone on its first training track (the one the UI displays).
     *
     * @throws IllegalArgumentException if the track is not available (an imported track must be imported again)
     */
    public Simulation replay() {
        String name = config.tracks().getFirst();
        Track track;

        try {
            track = Tracks.byName(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Track '" + name + "' is not available: import it first.", e);
        }

        return new Simulation(track, List.of(network.copy()), config.createSensor(), config.useSpeedInput(),
                config.useCarStateInputs(), config.settings(), config.createVehicle());
    }

    public void save(Path path) throws IOException {
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    /**
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if it is not a valid champion file
     */
    public static Champion load(Path path) throws IOException {
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public String toJson() {
        Map<String, Object> net = new LinkedHashMap<>();
        net.put("architecture", ints(network.getArchitecture()));
        net.put("activation", activationName(network.getActivation()));
        List<Object> weights = new ArrayList<>();

        for (double[][] layer : network.getWeights()) {
            List<Object> rows = new ArrayList<>();

            for (double[] neuron : layer) {
                rows.add(doubles(neuron));
            }

            weights.add(rows);
        }

        net.put("weights", weights);
        List<Object> biases = new ArrayList<>();

        for (double[] layer : network.getBiases()) {
            biases.add(doubles(layer));
        }

        net.put("biases", biases);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("generation", generation);
        root.put("fitness", fitness);
        root.put("network", net);
        root.put("config", configToJson(config));
        return Json.write(root);
    }

    public static Champion fromJson(String text) {
        Map<String, Object> root = object(Json.parse(text), "file");

        if (!FORMAT.equals(root.get("format"))) {
            throw new IllegalArgumentException("Not a champion file (expected \"format\": \"" + FORMAT + "\").");
        }

        if (integer(root, "version") != VERSION) {
            throw new IllegalArgumentException("Unsupported champion file version " + root.get("version") + ".");
        }

        SimulationConfig config = configFromJson(object(root.get("config"), "config"));
        Map<String, Object> net = object(root.get("network"), "network");
        int[] architecture = intArray(net, "architecture");
        NeuralNetwork network;

        try {
            network = new NeuralNetwork(new Rng(0), activation(string(net, "activation")), architecture);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid network: " + e.getMessage(), e);
        }

        network.setGenome(genome(net, architecture));
        return new Champion(config, network, integer(root, "generation"), number(root, "fitness"));
    }

    /**
     * Rebuilds the genome in {@link NeuralNetwork#getGenome()} order (per neuron: its weights, then its bias),
     * checking every layer has the size the architecture announces.
     */
    private static double[] genome(Map<String, Object> net, int[] architecture) {
        List<Object> weights = list(net.get("weights"), "weights");
        List<Object> biases = list(net.get("biases"), "biases");
        int layers = architecture.length - 1;

        if (weights.size() != layers || biases.size() != layers) {
            throw new IllegalArgumentException("Expected weights and biases for " + layers + " layers.");
        }

        List<Double> genome = new ArrayList<>();

        for (int l = 0; l < layers; l++) {
            List<Object> neurons = list(weights.get(l), "weights[" + l + "]");
            double[] layerBiases = doubleArray(biases.get(l), "biases[" + l + "]");

            if (neurons.size() != architecture[l + 1] || layerBiases.length != architecture[l + 1]) {
                throw new IllegalArgumentException("Layer " + l + " must have " + architecture[l + 1] + " neurons.");
            }

            for (int n = 0; n < neurons.size(); n++) {
                double[] row = doubleArray(neurons.get(n), "weights[" + l + "][" + n + "]");

                if (row.length != architecture[l]) {
                    throw new IllegalArgumentException("Neuron " + n + " of layer " + l + " must have "
                            + architecture[l] + " weights.");
                }

                for (double weight : row) {
                    genome.add(weight);
                }

                genome.add(layerBiases[n]);
            }
        }

        return genome.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static Map<String, Object> configToJson(SimulationConfig config) {
        Map<String, Object> weights = new LinkedHashMap<>();

        for (Map.Entry<FitnessComponent, Double> entry : config.compositeFitness().getWeights().entrySet()) {
            weights.put(entry.getKey().name(), entry.getValue());
        }

        SimulationSettings settings = config.settings();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("dt", settings.dt());
        run.put("maxTime", settings.maxTime());
        run.put("stagnationTime", settings.stagnationTime());
        run.put("maxLaps", settings.maxLaps());
        run.put("trailLength", settings.trailLength());
        run.put("recordLaps", settings.recordLaps());

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("populationSize", config.populationSize());
        json.put("hiddenLayers", ints(config.hiddenLayers()));
        json.put("useSpeedInput", config.useSpeedInput());
        json.put("useCarStateInputs", config.useCarStateInputs());
        json.put("rayCount", config.rayCount());
        json.put("rayRange", config.rayRange());
        json.put("mutationRate", config.mutationRate());
        json.put("mutationStrength", config.mutationStrength());
        json.put("noise", config.noise().name());
        json.put("selection", config.selection().name());
        json.put("tournamentSize", config.tournamentSize());
        json.put("truncationFraction", config.truncationFraction());
        json.put("eliteCount", config.eliteCount());
        json.put("crossover", config.crossover().name());
        json.put("fitnessMode", config.fitnessMode().name());
        json.put("fitnessWeights", weights);
        json.put("tracks", List.copyOf(config.tracks()));
        json.put("settings", run);
        json.put("carType", config.carType());
        json.put("tire", config.tire().name());
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("fuelAutonomy", config.rules().fuelAutonomy());
        rules.put("tyreTemperature", config.rules().tyreTemperature());
        json.put("rules", rules);
        json.put("seed", config.seed());
        return json;
    }

    private static SimulationConfig configFromJson(Map<String, Object> json) {
        Map<String, Object> run = object(field(json, "settings"), "settings");
        Map<String, Object> weightsJson = object(field(json, "fitnessWeights"), "fitnessWeights");
        EnumMap<FitnessComponent, Double> weights = new EnumMap<>(FitnessComponent.class);

        for (String key : weightsJson.keySet()) {
            weights.put(constant(FitnessComponent.class, key, "fitness component"), number(weightsJson, key));
        }

        List<String> tracks = new ArrayList<>();

        for (Object track : list(field(json, "tracks"), "tracks")) {
            if (!(track instanceof String name)) {
                throw new IllegalArgumentException("Track names must be strings.");
            }

            tracks.add(name);
        }

        SimulationSettings settings = new SimulationSettings(number(run, "dt"), number(run, "maxTime"),
                number(run, "stagnationTime"), integer(run, "maxLaps"), integer(run, "trailLength"),
                bool(run, "recordLaps"));

        return new SimulationConfig(integer(json, "populationSize"), intArray(json, "hiddenLayers"),
                bool(json, "useSpeedInput"),
                // absent from files written before the car state inputs existed: those networks never read it
                json.containsKey("useCarStateInputs") && bool(json, "useCarStateInputs"),
                integer(json, "rayCount"), number(json, "rayRange"),
                number(json, "mutationRate"), number(json, "mutationStrength"),
                constant(MutationOperator.Noise.class, string(json, "noise"), "noise"),
                constant(SelectionType.class, string(json, "selection"), "selection"),
                integer(json, "tournamentSize"), number(json, "truncationFraction"), integer(json, "eliteCount"),
                constant(SimulationConfig.Crossover.class, string(json, "crossover"), "crossover"),
                constant(SimulationConfig.FitnessMode.class, string(json, "fitnessMode"), "fitness mode"),
                new CompositeFitness(weights), tracks, settings, string(json, "carType"),
                constant(TireCompound.class, string(json, "tire"), "tyre compound"), rules(json),
                longValue(json, "seed"));
    }

    /**
     * Files written before fuel and tyre temperature existed have no "rules": their champion was evaluated without
     * them, so {@link RaceRules#NONE} replays it exactly.
     */
    private static RaceRules rules(Map<String, Object> json) {
        if (!json.containsKey("rules")) {
            return RaceRules.NONE;
        }

        Map<String, Object> rules = object(json.get("rules"), "rules");
        return new RaceRules(number(rules, "fuelAutonomy"), bool(rules, "tyreTemperature"));
    }

    private static String activationName(ActivationFunction activation) {
        return switch (activation) {
            case Tanh ignored -> "tanh";
            case Sigmoid ignored -> "sigmoid";
            case ReLU ignored -> "relu";
            default -> throw new IllegalArgumentException("Cannot save activation "
                    + activation.getClass().getSimpleName() + ".");
        };
    }

    private static ActivationFunction activation(String name) {
        return switch (name) {
            case "tanh" -> new Tanh();
            case "sigmoid" -> new Sigmoid();
            case "relu" -> new ReLU();
            default -> throw new IllegalArgumentException("Unknown activation '" + name + "'.");
        };
    }

    private static String label(int[] architecture) {
        return String.join("-", Arrays.stream(architecture).mapToObj(String::valueOf).toList());
    }

    private static List<Object> ints(int[] values) {
        return Arrays.stream(values).<Object>mapToObj(value -> value).toList();
    }

    private static List<Object> doubles(double[] values) {
        return Arrays.stream(values).<Object>mapToObj(value -> value).toList();
    }

    // Typed reads: a missing or mistyped field is reported by name instead of a ClassCastException.

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("'" + name + "' must be a JSON object.");
        }

        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String name) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("'" + name + "' must be a JSON array.");
        }

        return (List<Object>) list;
    }

    private static Object field(Map<String, Object> json, String key) {
        if (!json.containsKey(key)) {
            throw new IllegalArgumentException("Missing field '" + key + "'.");
        }

        return json.get(key);
    }

    private static double number(Map<String, Object> json, String key) {
        if (!(field(json, key) instanceof Number number)) {
            throw new IllegalArgumentException("'" + key + "' must be a number.");
        }

        return number.doubleValue();
    }

    private static long longValue(Map<String, Object> json, String key) {
        if (!(field(json, key) instanceof Long number)) {
            throw new IllegalArgumentException("'" + key + "' must be a whole number.");
        }

        return number;
    }

    private static int integer(Map<String, Object> json, String key) {
        long value = longValue(json, key);

        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("'" + key + "' is out of range.");
        }

        return (int) value;
    }

    private static boolean bool(Map<String, Object> json, String key) {
        if (!(field(json, key) instanceof Boolean value)) {
            throw new IllegalArgumentException("'" + key + "' must be true or false.");
        }

        return value;
    }

    private static String string(Map<String, Object> json, String key) {
        if (!(field(json, key) instanceof String value)) {
            throw new IllegalArgumentException("'" + key + "' must be a string.");
        }

        return value;
    }

    private static int[] intArray(Map<String, Object> json, String key) {
        List<Object> values = list(field(json, key), key);
        int[] result = new int[values.size()];

        for (int i = 0; i < result.length; i++) {
            if (!(values.get(i) instanceof Long value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("'" + key + "' must contain whole numbers.");
            }

            result[i] = (int) (long) value;
        }

        return result;
    }

    private static double[] doubleArray(Object value, String name) {
        List<Object> values = list(value, name);
        double[] result = new double[values.size()];

        for (int i = 0; i < result.length; i++) {
            if (!(values.get(i) instanceof Number number)) {
                throw new IllegalArgumentException("'" + name + "' must contain numbers.");
            }

            result[i] = number.doubleValue();
        }

        return result;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, String name, String what) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + what + " '" + name + "'.", e);
        }
    }
}
