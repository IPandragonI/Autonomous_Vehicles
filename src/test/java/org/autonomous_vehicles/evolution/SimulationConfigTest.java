package org.autonomous_vehicles.evolution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationConfigTest {

    private static SimulationConfig.Builder base() {
        return SimulationConfig.defaults().toBuilder();
    }

    @Test
    public void testDefaultsFollowTheAssignment() {
        SimulationConfig config = SimulationConfig.defaults();

        assertEquals(100, config.populationSize());
        assertArrayEquals(new int[]{5, 6, 2}, config.architecture());
        assertEquals("5-6-2", config.architectureLabel());
        assertEquals(5, config.createSensor().getRayCount());
        assertEquals(1.0 / 60.0, config.settings().dt(), 0.0);
    }

    @Test
    public void testOutOfRangeValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> base().populationSize(1).build());
        assertThrows(IllegalArgumentException.class, () -> base().populationSize(SimulationConfig.MAX_POPULATION + 1).build());
        assertThrows(IllegalArgumentException.class, () -> base().mutationRate(1.5).build());
        assertThrows(IllegalArgumentException.class, () -> base().mutationRate(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> base().mutationStrength(-0.1).build());
        assertThrows(IllegalArgumentException.class, () -> base().tournamentSize(101).build());
        assertThrows(IllegalArgumentException.class, () -> base().eliteCount(100).build());
        assertThrows(IllegalArgumentException.class, () -> base().hiddenLayers(0).build());
        assertThrows(IllegalArgumentException.class, () -> base().hiddenLayers(4, 4, 4, 4, 4).build());
        assertThrows(IllegalArgumentException.class, () -> base().rayCount(0).build());
        assertThrows(IllegalArgumentException.class, () -> base().truncationFraction(0).build());
        assertThrows(IllegalArgumentException.class, () -> base().tracks(List.of()).build());
    }

    @Test
    public void testConfigIsImmutable() {
        int[] layers = {12, 8};
        SimulationConfig config = base().hiddenLayers(layers).build();

        layers[0] = 99;
        config.hiddenLayers()[1] = 99;

        assertArrayEquals(new int[]{12, 8}, config.hiddenLayers());
    }

    @Test
    public void testChangingArchitectureCreatesACompatiblePopulation() {
        SimulationConfig config = base().hiddenLayers(12, 8).populationSize(10).build();

        GeneticAlgorithm algorithm = new GeneticAlgorithm(config);

        assertArrayEquals(new int[]{5, 12, 8, 2}, algorithm.getPopulation().getArchitecture());
        int expectedGenome = 5 * 12 + 12 + 12 * 8 + 8 + 8 * 2 + 2;

        for (Individual individual : algorithm.getPopulation().getIndividuals()) {
            assertEquals(expectedGenome, individual.getGenome().length);
        }

        algorithm.run(1);
    }

    @Test
    public void testSpeedInputAddsAnInput() {
        SimulationConfig config = base().useSpeedInput(true).populationSize(5).eliteCount(1).build();

        assertArrayEquals(new int[]{6, 6, 2}, config.architecture());
        new GeneticAlgorithm(config).run(1);
    }

    @Test
    public void testHotAndResetChangesAreDistinguished() {
        SimulationConfig config = SimulationConfig.defaults();

        assertFalse(config.requiresReset(base().mutationRate(0.3).build()));
        assertFalse(config.requiresReset(base().selection(SelectionType.ROULETTE).build()));
        assertFalse(config.requiresReset(base().eliteCount(0).build()));
        assertFalse(config.requiresReset(base().fitnessMode(SimulationConfig.FitnessMode.PROGRESS).build()));

        assertTrue(config.requiresReset(base().populationSize(50).build()));
        assertTrue(config.requiresReset(base().hiddenLayers(12, 8).build()));
        assertTrue(config.requiresReset(base().tracks("hairpin").build()));
        assertTrue(config.requiresReset(base().seed(1).build()));
    }

    @Test
    public void testCarTypeAndTyresAreValidatedAndNeedAReset() {
        SimulationConfig config = SimulationConfig.defaults();

        assertEquals(org.autonomous_vehicles.sim.Vehicles.KINEMATIC, config.carType(), "Assignment model by default.");
        assertThrows(IllegalArgumentException.class, () -> base().carType("bus").build());
        assertThrows(IllegalArgumentException.class, () -> base().tire(null).build());
        assertTrue(config.requiresReset(base().carType("kart").build()));
        assertTrue(config.requiresReset(base().tire(org.autonomous_vehicles.sim.TireCompound.SOFT).build()));
    }

    @Test
    public void testEvolutionRunsWithADynamicCar() {
        SimulationConfig config = base().carType("hatchback").tire(org.autonomous_vehicles.sim.TireCompound.SOFT)
                .useSpeedInput(true).populationSize(10).eliteCount(1).build();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(config);

        List<GenerationStats> stats = algorithm.run(2);

        assertEquals(2, stats.size());
        assertInstanceOf(org.autonomous_vehicles.sim.DynamicModel.class, algorithm.getEvaluator().getVehicle());
        assertEquals(algorithm.getEvaluator().getVehicle().topSpeed(),
                algorithm.getChampion().getPrimaryResult().topSpeed(), 0.0);
    }

    @Test
    public void testEqualityIncludesHotParameters() {
        assertEquals(SimulationConfig.defaults(), SimulationConfig.defaults());
        assertNotEquals(SimulationConfig.defaults(), base().mutationRate(0.3).build());
    }
}
