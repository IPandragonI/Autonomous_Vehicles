package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.DynamicModel;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.TrackPreview;
import org.autonomous_vehicles.sim.Vehicles;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ChampionTest {

    private static SimulationConfig config() {
        return SimulationConfig.defaults().toBuilder()
                .populationSize(20)
                .eliteCount(2)
                .hiddenLayers(4, 3)
                .useSpeedInput(true)
                .settings(SimulationSettings.defaults().withMaxTime(20))
                .seed(7)
                .build();
    }

    private static GeneticAlgorithm evolved(SimulationConfig config, int generations) {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(config);
        algorithm.run(generations);
        return algorithm;
    }

    private static void assertSameDrive(AgentResult expected, AgentResult actual) {
        assertEquals(expected.progress(), actual.progress());
        assertEquals(expected.laps(), actual.laps());
        assertEquals(expected.lapTimes(), actual.lapTimes());
        assertEquals(expected.distance(), actual.distance());
        assertEquals(expected.timeAlive(), actual.timeAlive());
        assertEquals(expected.meanSteeringChange(), actual.meanSteeringChange());
        assertEquals(expected.deathCause(), actual.deathCause());
    }

    @Test
    public void testRoundTripKeepsTheGenomeBitForBitAndTheConfiguration() {
        GeneticAlgorithm algorithm = evolved(config(), 3);
        Champion saved = Champion.of(algorithm);

        Champion loaded = Champion.fromJson(saved.toJson());

        assertArrayEquals(algorithm.getChampion().getGenome(), loaded.network().getGenome());
        assertEquals(saved.config(), loaded.config());
        assertFalse(saved.config().requiresReset(loaded.config()));
        assertEquals(algorithm.getChampionGeneration(), loaded.generation());
        assertEquals(algorithm.getChampion().getFitness(), loaded.fitness());
        assertEquals(saved.toJson(), loaded.toJson());
    }

    @Test
    public void testReplayDrivesExactlyTheEvaluatedTrajectory() {
        GeneticAlgorithm algorithm = evolved(config(), 3);
        Champion loaded = Champion.fromJson(Champion.of(algorithm).toJson());

        Simulation replay = loaded.replay();
        replay.runUntilAllDead();

        assertEquals(1, replay.getAgents().size());
        assertSameDrive(algorithm.getChampion().getPrimaryResult(), replay.getAgents().getFirst().result());
    }

    @Test
    public void testDynamicCarAndOtherTrackAreReplayedWithTheirOwnConfiguration() {
        SimulationConfig config = config().toBuilder().tracks("hairpin").carType(Vehicles.TYPES.getLast())
                .tire(TireCompound.SOFT).settings(SimulationSettings.defaults().withMaxTime(15)).build();
        GeneticAlgorithm algorithm = evolved(config, 2);

        Champion loaded = Champion.fromJson(Champion.of(algorithm).toJson());
        Simulation replay = loaded.replay();
        replay.runUntilAllDead();

        assertEquals("hairpin", replay.getTrack().getName());
        assertEquals(TireCompound.SOFT, loaded.config().tire());
        assertSameDrive(algorithm.getChampion().getPrimaryResult(), replay.getAgents().getFirst().result());
    }

    @Test
    public void testRaceRulesAreSavedAndOldFilesReplayWithoutThem() {
        SimulationConfig config = config().toBuilder().carType("kart").rules(new RaceRules(12, true)).build();
        String json = Champion.of(evolved(config, 1)).toJson();

        assertEquals(new RaceRules(12, true), Champion.fromJson(json).config().rules());

        SimulationConfig reduced = config.toBuilder().rules(new RaceRules(12, true, 0.25)).build();
        String withWear = Champion.of(evolved(reduced, 1)).toJson();
        assertEquals(0.25, Champion.fromJson(withWear).config().rules().tyreWear(), 0.0);
        String beforeWear = withWear.replaceFirst(",\n\\s*\"tyreWear\": [0-9.]+", "");
        assertFalse(beforeWear.contains("tyreWear"), beforeWear);
        assertEquals(1.0, Champion.fromJson(beforeWear).config().rules().tyreWear(), 0.0,
                "Files from before the wear setting were evaluated with the original rates.");

        SimulationConfig recalibrated = config.toBuilder().rules(RaceRules.defaults()).build();
        String withHeating = Champion.of(evolved(recalibrated, 1)).toJson();
        assertEquals(RaceRules.defaults(), Champion.fromJson(withHeating).config().rules());
        String beforeHeating = withHeating.replaceFirst(",\n\\s*\"tyreHeating\": [0-9.]+", "");
        assertFalse(beforeHeating.contains("tyreHeating"), beforeHeating);
        assertEquals(DynamicModel.TYRE_HEATING, Champion.fromJson(beforeHeating).config().rules().tyreHeating(), 0.0);

        String legacy = json.replaceFirst("\"rules\": \\{[^}]*},\n\\s*", "");
        assertFalse(legacy.contains("\"rules\""), legacy);
        assertEquals(RaceRules.NONE, Champion.fromJson(legacy).config().rules(),
                "Files from before the mechanics were evaluated without them.");
    }

    @Test
    public void testCarStateInputsAreSavedAndReplayedAndOldFilesHaveNone() {
        SimulationConfig config = config().toBuilder().carType("kart").useCarStateInputs(true).build();
        GeneticAlgorithm algorithm = evolved(config, 2);
        Champion loaded = Champion.fromJson(Champion.of(algorithm).toJson());

        assertTrue(loaded.config().useCarStateInputs());
        assertEquals(5 + 1 + 4, loaded.network().getArchitecture()[0], "rays + speed + car state");

        Simulation replay = loaded.replay();
        replay.runUntilAllDead();
        assertSameDrive(algorithm.getChampion().getPrimaryResult(), replay.getAgents().getFirst().result());

        String legacy = Champion.of(evolved(config(), 1)).toJson().replace("  \"useCarStateInputs\": false,\n", "");
        assertFalse(legacy.contains("useCarStateInputs"), legacy);
        assertFalse(Champion.fromJson(legacy).config().useCarStateInputs());
    }

    @Test
    public void testRacingOptionsAreSavedAndReplayedAndVersion1FilesHaveNone() {
        SimulationConfig config = config().toBuilder().tracks("hairpin").carType("formula").useSpeedInput(true)
                .useTrackPreview(true).rayCount(7).rayRange(150).rayLayout(SimulationConfig.RayLayout.FORWARD)
                .gripSteering(true).compositeFitness(CompositeFitness.defaults()
                        .withWeight(FitnessComponent.TIME_SAVED, 1.0)).build();
        GeneticAlgorithm algorithm = evolved(config, 2);
        Champion loaded = Champion.fromJson(Champion.of(algorithm).toJson());

        assertEquals(config.toBuilder().seed(loaded.config().seed()).build(), loaded.config());
        assertTrue(loaded.config().useTrackPreview() && loaded.config().gripSteering());
        assertEquals(SimulationConfig.RayLayout.FORWARD, loaded.config().rayLayout());
        assertEquals(7 + 1 + TrackPreview.INPUTS, loaded.network().getArchitecture()[0], "rays + speed + preview");

        Simulation replay = loaded.replay();
        replay.runUntilAllDead();
        assertSameDrive(algorithm.getChampion().getPrimaryResult(), replay.getAgents().getFirst().result());

        String version1 = Champion.of(evolved(config(), 1)).toJson().replace("\"version\": 2", "\"version\": 1")
                .replace("  \"useTrackPreview\": false,\n", "").replace("  \"rayLayout\": \"EVEN\",\n", "")
                .replace("  \"gripSteering\": false,\n", "");
        assertFalse(version1.contains("gripSteering") || version1.contains("rayLayout"), version1);
        SimulationConfig legacy = Champion.fromJson(version1).config();
        assertFalse(legacy.useTrackPreview() || legacy.gripSteering());
        assertEquals(SimulationConfig.RayLayout.EVEN, legacy.rayLayout());
    }

    @Test
    public void testWeightsAndBiasesAreStoredPerLayerAndNeuron() {
        SimulationConfig config = SimulationConfig.defaults().toBuilder().rayCount(1).hiddenLayers(1).build();
        NeuralNetwork network = new NeuralNetwork(new Rng(0), config.architecture());
        // genome order: per neuron, its weights then its bias
        network.setGenome(new double[]{0.5, -1.0, 2.0, 0.25, -3.0, 4.0});

        String json = new Champion(config, network, 4, 1.5).toJson();

        assertTrue(json.contains("\"architecture\": [1, 1, 2]"), json);
        assertTrue(json.contains("\"weights\": [\n      [\n        [0.5]\n      ],\n      [\n        [2.0],\n        [-3.0]\n      ]\n    ]"), json);
        assertTrue(json.contains("\"biases\": [\n      [-1.0],\n      [0.25, 4.0]\n    ]"), json);
        assertArrayEquals(network.getGenome(), Champion.fromJson(json).network().getGenome());
    }

    @Test
    public void testSaveAndLoadThroughAFile(@TempDir Path directory) throws IOException {
        GeneticAlgorithm algorithm = evolved(config(), 1);
        Path file = directory.resolve("champion.json");

        Champion.of(algorithm).save(file);
        Champion loaded = Champion.load(file);

        assertFalse(Files.readString(file).contains("\r"), "Generated files use \\n line endings.");
        assertArrayEquals(algorithm.getChampion().getGenome(), loaded.network().getGenome());
    }

    @Test
    public void testInvalidFilesAreRejectedWithAClearMessage() {
        String json = Champion.of(evolved(config(), 1)).toJson();

        assertMessage("Not a champion file", json.replace(Champion.FORMAT, "something-else"));
        String version = "\"version\": " + Champion.VERSION;
        assertMessage("Unsupported champion file version",
                json.replace(version, "\"version\": " + (Champion.VERSION + 1)));
        assertMessage("Unsupported champion file version", json.replace(version, "\"version\": 0"));
        assertMessage("Missing field 'seed'", json.replace("\"seed\"", "\"sed\""));
        assertMessage("Unknown activation", json.replace("\"tanh\"", "\"swish\""));
        assertMessage("Unknown tyre compound", json.replace("\"MEDIUM\"", "\"WET\""));
        assertMessage("does not match the configuration", json.replace("\"rayCount\": 5", "\"rayCount\": 4"));
        assertThrows(IllegalArgumentException.class, () -> Champion.fromJson("[]"));
    }

    @Test
    public void testLayerOfTheWrongSizeIsRejected() {
        SimulationConfig config = SimulationConfig.defaults().toBuilder().rayCount(1).hiddenLayers(1).build();
        String json = new Champion(config, new NeuralNetwork(new Rng(0), config.architecture()), 0, 0.0).toJson()
                .replaceFirst("\"biases\": \\[\n      \\[[^\\]]*\\]", "\"biases\": [\n      [1.0, 2.0]");

        assertMessage("Layer 0 must have 1 neurons", json);
    }

    private static void assertMessage(String expected, String json) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Champion.fromJson(json));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    @Test
    public void testChampionCannotBeTakenBeforeTheFirstGeneration() {
        assertThrows(IllegalStateException.class, () -> Champion.of(new GeneticAlgorithm(config())));
    }

    @Test
    public void testChampionKeepsItsOwnNetwork() {
        GeneticAlgorithm algorithm = evolved(config(), 1);
        Champion champion = Champion.of(algorithm);
        double[] before = champion.network().getGenome();

        champion.network().setGenome(new double[before.length]);

        assertArrayEquals(before, champion.network().getGenome());
    }
}
