package org.autonomous_vehicles.ui;

/**
 * Optional render layers, toggled from the control panel (no JavaFX dependency).
 */
public class RenderOptions {

    private boolean showRays = true;
    private boolean showTrails = true;
    private boolean showCheckpoints = true;
    private boolean showDeadCars = true;
    private boolean showGhost = true;

    public boolean showRays() {
        return showRays;
    }

    public void setShowRays(boolean showRays) {
        this.showRays = showRays;
    }

    public boolean showTrails() {
        return showTrails;
    }

    public void setShowTrails(boolean showTrails) {
        this.showTrails = showTrails;
    }

    public boolean showCheckpoints() {
        return showCheckpoints;
    }

    public void setShowCheckpoints(boolean showCheckpoints) {
        this.showCheckpoints = showCheckpoints;
    }

    public boolean showDeadCars() {
        return showDeadCars;
    }

    public void setShowDeadCars(boolean showDeadCars) {
        this.showDeadCars = showDeadCars;
    }

    public boolean showGhost() {
        return showGhost;
    }

    public void setShowGhost(boolean showGhost) {
        this.showGhost = showGhost;
    }
}
