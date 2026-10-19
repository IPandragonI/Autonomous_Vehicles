package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.AgentResult;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Weighted sum of {@link FitnessComponent}s: fitness = Σ weight(c) × c(result).
 * Immutable; {@link #withWeight} returns a new instance (the UI edits weights this way).
 */
public final class CompositeFitness implements FitnessFunction {

    private final Map<FitnessComponent, Double> weights;

    public CompositeFitness(Map<FitnessComponent, Double> weights) {
        if (weights == null) {
            throw new IllegalArgumentException("Weights must not be null.");
        }

        EnumMap<FitnessComponent, Double> copy = new EnumMap<>(FitnessComponent.class);

        for (FitnessComponent component : FitnessComponent.values()) {
            double weight = weights.getOrDefault(component, 0.0);

            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Weight of " + component + " must be finite and non-negative.");
            }

            copy.put(component, weight);
        }

        this.weights = Collections.unmodifiableMap(copy);
    }

    public static CompositeFitness defaults() {
        EnumMap<FitnessComponent, Double> weights = new EnumMap<>(FitnessComponent.class);

        for (FitnessComponent component : FitnessComponent.values()) {
            weights.put(component, component.defaultWeight());
        }

        return new CompositeFitness(weights);
    }

    /**
     * Composite with a single component of weight 1 (used to test a component on its own).
     */
    public static CompositeFitness only(FitnessComponent component) {
        return new CompositeFitness(Map.of(component, 1.0));
    }

    public CompositeFitness withWeight(FitnessComponent component, double weight) {
        EnumMap<FitnessComponent, Double> copy = new EnumMap<>(weights);
        copy.put(component, weight);
        return new CompositeFitness(copy);
    }

    @Override
    public double evaluate(AgentResult result) {
        double total = 0.0;

        for (Map.Entry<FitnessComponent, Double> entry : weights.entrySet()) {
            if (entry.getValue() != 0.0) {
                total += entry.getValue() * entry.getKey().evaluate(result);
            }
        }

        return total;
    }

    public double weight(FitnessComponent component) {
        return weights.get(component);
    }

    public Map<FitnessComponent, Double> getWeights() {
        return weights;
    }

    @Override
    public String name() {
        return "Composite";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompositeFitness composite && weights.equals(composite.weights);
    }

    @Override
    public int hashCode() {
        return weights.hashCode();
    }
}
