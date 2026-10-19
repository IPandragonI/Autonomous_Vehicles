package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.util.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * Builders of known results and individuals for the evolution tests.
 */
final class Results {

    private Results() {
    }

    static AgentResult result(double progress, List<Double> lapTimes, DeathCause cause) {
        return new AgentResult(progress, lapTimes.size(), lapTimes, progress * 10, 30.0, 10.0, 0.1, 0.2, cause,
                60.0, 16, null);
    }

    static AgentResult progress(double progress) {
        return result(progress, List.of(), DeathCause.STAGNATED);
    }

    static Individual individual(Rng rng, double fitness) {
        Individual individual = new Individual(new NeuralNetwork(rng, 5, 6, 2));
        individual.setFitness(fitness);
        individual.setResults(List.of(progress(fitness)));
        return individual;
    }

    /**
     * Population sorted by decreasing fitness, with the given fitness values.
     */
    static List<Individual> ranked(double... fitness) {
        Rng rng = new Rng(17);
        List<Individual> individuals = new ArrayList<>();

        for (double value : fitness) {
            individuals.add(individual(rng, value));
        }

        individuals.sort(Population.byFitnessDescending());
        return individuals;
    }
}
