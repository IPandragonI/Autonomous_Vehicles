package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;

/**
 * Turns what a car did into one score. The genetic algorithm only ever sees this number,
 * so anything it rewards will be exploited ("reward hacking").
 */
public interface FitnessFunction {

    double evaluate(AgentResult result);

    String name();
}
