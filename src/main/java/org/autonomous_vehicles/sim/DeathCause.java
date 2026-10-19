package org.autonomous_vehicles.sim;

/**
 * Why a car left the simulation (ALIVE while it is still driving).
 */
public enum DeathCause {
    ALIVE,
    CRASHED,
    STAGNATED,
    TIMEOUT,
    FINISHED
}
