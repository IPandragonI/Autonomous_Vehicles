package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TrackPreview;
import org.autonomous_vehicles.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkInputsTest {

    private static final SimulationConfig PLAIN = SimulationConfig.defaults(); // 5-6-2
    private static final SimulationConfig FULL = PLAIN.toBuilder().useSpeedInput(true).useCarStateInputs(true).build();

    @Test
    public void testAdaptedNetworkComputesExactlyTheSameOutputsWhateverTheNewInputs() {
        NeuralNetwork plain = new NeuralNetwork(new Rng(4), PLAIN.architecture());
        NeuralNetwork widened = NetworkInputs.adapt(plain, PLAIN, FULL);
        Rng rng = new Rng(8);

        assertArrayEquals(new int[]{10, 6, 2}, widened.getArchitecture());

        for (int trial = 0; trial < 100; trial++) {
            double[] rays = new double[5];
            double[] all = new double[10];

            for (int i = 0; i < 10; i++) {
                all[i] = rng.nextDouble();
            }

            System.arraycopy(all, 0, rays, 0, 5);
            assertArrayEquals(plain.forward(rays), widened.forward(all), "bit-identical outputs");
        }
    }

    @Test
    public void testSpeedIsKeptAtItsPlaceWhenCarStateIsAdded() {
        SimulationConfig speed = PLAIN.toBuilder().useSpeedInput(true).build();
        NeuralNetwork network = new NeuralNetwork(new Rng(2), speed.architecture());

        double[][] before = network.getWeights()[0];
        double[][] after = NetworkInputs.adapt(network, speed, FULL).getWeights()[0];

        for (int n = 0; n < before.length; n++) {
            for (int i = 0; i < 6; i++) {
                assertEquals(before[n][i], after[n][i], "rays and speed keep their weights");
            }

            for (int i = 6; i < 10; i++) {
                assertEquals(0.0, after[n][i], "the car state starts ignored");
            }
        }
    }

    @Test
    public void testNetworksThatCannotBeWidenedAreRefused() {
        NeuralNetwork full = new NeuralNetwork(new Rng(2), FULL.architecture());
        NeuralNetwork plain = new NeuralNetwork(new Rng(2), PLAIN.architecture());

        assertNull(NetworkInputs.adapt(full, FULL, PLAIN), "dropping inputs would change the driving");
        assertNull(NetworkInputs.adapt(plain, PLAIN, FULL.toBuilder().hiddenLayers(8).build()));
        assertNull(NetworkInputs.adapt(plain, PLAIN, FULL.toBuilder().rayCount(7).build()));
        assertSame(plain, NetworkInputs.adapt(plain, PLAIN, PLAIN.toBuilder().tracks("hairpin").build()));
        assertNull(NetworkInputs.adapt(plain, PLAIN, PLAIN.toBuilder().gripSteering(true).build()),
                "same shape, but the steering output would mean something else");
        assertNull(NetworkInputs.adapt(plain, PLAIN, PLAIN.toBuilder().rayLayout(SimulationConfig.RayLayout.FORWARD)
                .build()), "same shape, but the rays would point elsewhere");
        SimulationConfig preview = FULL.toBuilder().useTrackPreview(true).build();
        assertNull(NetworkInputs.adapt(new NeuralNetwork(new Rng(2), preview.architecture()), preview, FULL));
    }

    @Test
    public void testTrackPreviewIsAddedAfterTheCarStateWithZeroWeights() {
        SimulationConfig preview = FULL.toBuilder().useTrackPreview(true).build();
        NeuralNetwork network = new NeuralNetwork(new Rng(2), FULL.architecture());
        double[][] before = network.getWeights()[0];
        double[][] after = NetworkInputs.adapt(network, FULL, preview).getWeights()[0];

        assertEquals(10 + TrackPreview.INPUTS, after[0].length);

        for (int n = 0; n < before.length; n++) {
            assertArrayEquals(before[n], java.util.Arrays.copyOf(after[n], 10), "rays, speed, car state unchanged");

            for (int i = 10; i < after[n].length; i++) {
                assertEquals(0.0, after[n][i], "the road ahead starts ignored");
            }
        }
    }

    @Test
    public void testWidenedKinematicChampionReplaysItsTrajectory() {
        SimulationConfig config = PLAIN.toBuilder().populationSize(20).eliteCount(2)
                .settings(SimulationSettings.defaults().withMaxTime(20)).build();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(config);
        algorithm.run(2);
        Champion champion = Champion.of(algorithm);

        SimulationConfig widenedConfig = config.toBuilder().useCarStateInputs(true).build();
        NeuralNetwork widened = NetworkInputs.adapt(champion.network(), config, widenedConfig);
        Simulation replay = new Champion(widenedConfig, widened, 0, 0.0).replay();
        replay.runUntilAllDead();

        AgentResult expected = algorithm.getChampion().getPrimaryResult();
        AgentResult actual = replay.getAgents().getFirst().result();
        assertEquals(expected.distance(), actual.distance());
        assertEquals(expected.progress(), actual.progress());
        assertEquals(expected.deathCause(), actual.deathCause());
    }

    @Test
    public void testRandomPopulationsStartBlindToTheCarStateOnly() {
        SimulationConfig dynamic = FULL.toBuilder().carType("kart").rules(RaceRules.defaults()).populationSize(10)
                .eliteCount(1).build();
        Population blind = NetworkInputs.randomPopulation(dynamic, new Rng(6));
        int first = dynamic.rayCount() + 1;

        for (Individual individual : blind.getIndividuals()) {
            for (double[] neuron : individual.getNetwork().getWeights()[0]) {
                for (int i = 0; i < first; i++) {
                    assertNotEquals(0.0, neuron[i], "rays and speed stay random");
                }

                for (int i = first; i < first + CarBrain.CAR_STATE_INPUTS; i++) {
                    assertEquals(0.0, neuron[i]);
                }
            }
        }

        SimulationConfig plain = PLAIN.toBuilder().populationSize(10).eliteCount(1).build();
        Population a = NetworkInputs.randomPopulation(plain, new Rng(6));
        Population b = new Population(10, plain.architecture(), new Rng(6));

        for (int i = 0; i < 10; i++) {
            assertArrayEquals(b.get(i).getGenome(), a.get(i).getGenome(), "unchanged without car state inputs");
        }
    }

    @Test
    public void testRandomPopulationsStartBlindToTheRoadAheadToo() {
        SimulationConfig preview = FULL.toBuilder().useTrackPreview(true).populationSize(10).eliteCount(1).build();
        Population blind = NetworkInputs.randomPopulation(preview, new Rng(6));
        Population random = new Population(10, preview.architecture(), new Rng(6));
        int first = preview.inputSize() - TrackPreview.INPUTS;

        for (int k = 0; k < 10; k++) {
            double[][] blindLayer = blind.get(k).getNetwork().getWeights()[0];
            double[][] randomLayer = random.get(k).getNetwork().getWeights()[0];

            for (int n = 0; n < blindLayer.length; n++) {
                assertArrayEquals(java.util.Arrays.copyOf(randomLayer[n], 6), java.util.Arrays.copyOf(blindLayer[n], 6),
                        "same draws for the rays and speed");

                for (int i = first; i < preview.inputSize(); i++) {
                    assertEquals(0.0, blindLayer[n][i], "the road ahead starts ignored");
                }
            }
        }
    }
}
