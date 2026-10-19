package org.autonomous_vehicles.evolution;

import java.util.List;

/**
 * Chooses a parent. The list is sorted by decreasing fitness and must not be modified.
 * The strategy sets the selection pressure: how much more often good individuals reproduce.
 */
public interface SelectionStrategy {

    Individual select(List<Individual> rankedPopulation);

    String name();
}
