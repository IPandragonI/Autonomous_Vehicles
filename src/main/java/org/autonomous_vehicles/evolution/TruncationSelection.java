package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

import java.util.List;

/**
 * "The best": a parent is drawn uniformly among the top {@code fraction} of the population (10 % by default).
 * Strong pressure: fast progress, quick loss of diversity.
 */
public class TruncationSelection implements SelectionStrategy {

    public static final double DEFAULT_FRACTION = 0.10;

    private final double fraction;
    private final Rng rng;

    public TruncationSelection(double fraction, Rng rng) {
        if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("Fraction must be in (0, 1].");
        }

        if (rng == null) {
            throw new IllegalArgumentException("Rng must not be null.");
        }

        this.fraction = fraction;
        this.rng = rng;
    }

    @Override
    public Individual select(List<Individual> rankedPopulation) {
        int topCount = Math.max(1, (int) Math.round(rankedPopulation.size() * fraction));
        return rankedPopulation.get(rng.nextInt(Math.min(topCount, rankedPopulation.size())));
    }

    @Override
    public String name() {
        return "Truncation " + Math.round(fraction * 100) + "%";
    }
}
