package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.Simulation;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Tracks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ImitationTest {

    /** Short: two demonstrations, two DAgger rounds, few epochs, on the 36-checkpoint hairpin. */
    private static final Imitation.Settings QUICK = new Imitation.Settings(2, 2, 8, 4, 0.05, 32, 0.15);

    private static SimulationConfig config() {
        return Imitation.recommendedConfig(SimulationConfig.defaults().toBuilder()
                .tracks("hairpin").carType("formula").tire(TireCompound.MEDIUM).useSpeedInput(true)
                .useCarStateInputs(true).rules(RaceRules.defaults().withFuelAutonomy(0))
                .settings(SimulationSettings.defaults().withMaxTime(90)).seed(5).build());
    }

    @Test
    public void testImitationIsDeterministicAndKeepsTheBestStage() {
        SimulationConfig config = config();
        Imitation.Report report = Imitation.train(config, QUICK);
        Imitation.Report again = Imitation.train(config, QUICK);

        assertArrayEquals(config.architecture(), report.network().getArchitecture(), "seeds the GA directly");
        assertArrayEquals(report.network().getGenome(), again.network().getGenome(), "same seed, same network");
        assertEquals(QUICK.daggerRounds() + 1, report.stages().size());
        assertFalse(report.expertLapTimes().isEmpty(), "the clean demonstration completes a hairpin lap");

        for (int i = 1; i < report.stages().size(); i++) {
            assertTrue(report.stages().get(i).examples() > report.stages().get(i - 1).examples(),
                    "each DAgger round adds the states the network visited");
        }

        // The kept network drives exactly the drive reported for its stage (determinism).
        Simulation replay = config.createSimulation(Tracks.byName("hairpin"), List.of(report.network().copy()));
        replay.runUntilAllDead();
        AgentResult drive = replay.getAgents().getFirst().result();
        assertEquals(report.best().result().progress(), drive.progress(), 0.0);
        assertTrue(report.stages().stream().allMatch(stage -> stage.result().laps() <= drive.laps()));
    }

    @Test
    public void testTheImitatedNetworkSeedsTheGeneticAlgorithm() {
        SimulationConfig config = config().toBuilder().populationSize(10).eliteCount(1).build();
        Imitation.Report report = Imitation.train(config, QUICK);
        GeneticAlgorithm algorithm = new GeneticAlgorithm(config, report.network());

        algorithm.run(1);

        assertTrue(algorithm.getChampion().getPrimaryResult().progress() >= report.best().result().progress(),
                "the seed itself is in generation 0, so the champion is at least as good");
    }

    @Test
    public void testTheKinematicCarCannotImitate() {
        SimulationConfig kinematic = SimulationConfig.defaults();
        assertThrows(IllegalArgumentException.class, () -> Imitation.train(kinematic, QUICK));
        assertThrows(IllegalArgumentException.class, () -> new Imitation.Settings(0, 1, 1, 1, 0.1, 1, 0.1));
    }
}
