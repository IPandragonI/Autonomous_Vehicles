package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.DeathCause;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CompositeFitnessTest {

    private static AgentResult result(double progress, List<Double> laps, double meanSpeed, double steering,
                                      double deviation, DeathCause cause) {
        return new AgentResult(progress, laps.size(), laps, 100, 30, meanSpeed, steering, deviation, cause, 60.0, 16, null);
    }

    @Test
    public void testEachComponentInIsolation() {
        AgentResult result = result(18.5, List.of(15.0, 30.0), 10.0, 0.4, 0.3, DeathCause.CRASHED);

        assertEquals(18.5, CompositeFitness.only(FitnessComponent.PROGRESS).evaluate(result), 1e-12);
        assertEquals(2.0, CompositeFitness.only(FitnessComponent.LAPS).evaluate(result), 1e-12);
        assertEquals((1 - 15.0 / 60) + (1 - 30.0 / 60), CompositeFitness.only(FitnessComponent.LAP_TIME).evaluate(result), 1e-12);
        assertEquals(0.5, CompositeFitness.only(FitnessComponent.SPEED).evaluate(result), 1e-12);
        assertEquals(-1.0, CompositeFitness.only(FitnessComponent.COLLISION).evaluate(result), 1e-12);
        assertEquals(0.0, CompositeFitness.only(FitnessComponent.STAGNATION).evaluate(result), 1e-12);
        assertEquals(-0.3, CompositeFitness.only(FitnessComponent.CENTERLINE).evaluate(result), 1e-12);
        assertEquals(-0.2, CompositeFitness.only(FitnessComponent.STEERING).evaluate(result), 1e-12);
    }

    @Test
    public void testPenaltiesOnlyApplyToTheirCause() {
        AgentResult stagnated = result(3, List.of(), 5, 0, 0, DeathCause.STAGNATED);
        AgentResult finished = result(48, List.of(18.0, 18.0, 18.0), 19, 0, 0, DeathCause.FINISHED);

        assertEquals(0.0, CompositeFitness.only(FitnessComponent.COLLISION).evaluate(stagnated), 0.0);
        assertEquals(-1.0, CompositeFitness.only(FitnessComponent.STAGNATION).evaluate(stagnated), 0.0);
        assertEquals(0.0, CompositeFitness.only(FitnessComponent.STAGNATION).evaluate(finished), 0.0);
    }

    @Test
    public void testFullLapBeatsCrashHalfway() {
        CompositeFitness fitness = CompositeFitness.defaults();
        AgentResult lap = result(16.2, List.of(20.0), 15.0, 0.2, 0.3, DeathCause.TIMEOUT);
        AgentResult crashHalfway = result(8.0, List.of(), 18.0, 0.0, 0.0, DeathCause.CRASHED);

        assertTrue(fitness.evaluate(lap) > fitness.evaluate(crashHalfway));
    }

    @Test
    public void testAtEqualLapsFasterIsBetter() {
        CompositeFitness fitness = CompositeFitness.defaults();
        AgentResult fast = result(48, List.of(17.0, 16.5, 16.4), 18.0, 0.1, 0.3, DeathCause.FINISHED);
        AgentResult slow = result(48, List.of(19.0, 18.8, 18.9), 16.0, 0.1, 0.3, DeathCause.FINISHED);

        assertTrue(fitness.evaluate(fast) > fitness.evaluate(slow));
        assertTrue(CompositeFitness.only(FitnessComponent.LAP_TIME).evaluate(fast)
                > CompositeFitness.only(FitnessComponent.LAP_TIME).evaluate(slow));
    }

    @Test
    public void testRetreatingDoesNotBeatTryingTheCornerWithDefaultWeights() {
        CompositeFitness fitness = CompositeFitness.defaults();
        AgentResult retreat = result(3.6, List.of(), 12, 0.1, 0.2, DeathCause.STAGNATED);
        AgentResult tryAndCrash = result(5.0, List.of(), 14, 0.1, 0.2, DeathCause.CRASHED);

        assertTrue(fitness.evaluate(tryAndCrash) > fitness.evaluate(retreat));
    }

    @Test
    public void testWeightsAreConfigurableAndValidated() {
        CompositeFitness base = CompositeFitness.defaults();
        CompositeFitness noSpeed = base.withWeight(FitnessComponent.SPEED, 0.0);
        AgentResult result = result(10, List.of(), 20, 0, 0, DeathCause.TIMEOUT);

        assertEquals(base.evaluate(result) - FitnessComponent.SPEED.defaultWeight(), noSpeed.evaluate(result), 1e-12);
        assertEquals(1.0, base.weight(FitnessComponent.PROGRESS), 0.0);
        assertThrows(IllegalArgumentException.class, () -> base.withWeight(FitnessComponent.LAPS, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new CompositeFitness(Map.of(FitnessComponent.LAPS, Double.NaN)));
    }
}
