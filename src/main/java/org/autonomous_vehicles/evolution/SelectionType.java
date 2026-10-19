package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.util.Rng;

/**
 * The four selection methods, as offered in the configuration and the control panel.
 */
public enum SelectionType {
    TRUNCATION("Best (truncation)"),
    ROULETTE("Roulette"),
    TOURNAMENT("Tournament"),
    RANDOM("Random (control)");

    private final String label;

    SelectionType(String label) {
        this.label = label;
    }

    public SelectionStrategy create(SimulationConfig config, Rng rng) {
        return switch (this) {
            case TRUNCATION -> new TruncationSelection(config.truncationFraction(), rng);
            case ROULETTE -> new RouletteSelection(rng);
            case TOURNAMENT -> new TournamentSelection(config.tournamentSize(), rng);
            case RANDOM -> new RandomSelection(rng);
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
