package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;

/**
 * Fitness v1: progress along the ordered checkpoints (checkpoints passed + fraction to the next one).
 * Using checkpoints instead of raw distance prevents scoring by driving in circles.
 * A car that never moves forward scores exactly 0.
 */
public class ProgressFitness implements FitnessFunction {

    @Override
    public double evaluate(AgentResult result) {
        return result.progress();
    }

    @Override
    public String name() {
        return "Progress";
    }
}
