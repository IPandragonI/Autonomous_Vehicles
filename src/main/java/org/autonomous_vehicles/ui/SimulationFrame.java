package org.autonomous_vehicles.ui;

import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.Track;

import java.util.List;

/**
 * Everything one image of the track view needs, already filtered by the {@link RenderOptions}.
 * Built by the controller (testable without JavaFX), drawn by {@link TrackView}.
 *
 * @param cars              cars to draw (dead cars omitted when hidden)
 * @param focusId           id of the inspected car (selected, or best alive), -1 if none
 * @param rayOrigin         origin of the focused car's rays, null when rays are hidden
 * @param rayEnds           end point of each ray (empty when hidden)
 * @param trails            past positions of the cars (empty when hidden)
 * @param nextCheckpoint    next checkpoint of the focused car; earlier ones are lit, -1 to hide checkpoints
 * @param ghost             pose of the best-lap ghost, null when hidden or absent
 */
public record SimulationFrame(Track track, List<CarSprite> cars, int focusId, Vec2 rayOrigin, List<Vec2> rayEnds,
                              List<List<Vec2>> trails, int nextCheckpoint, CarSprite ghost) {

    /**
     * @param sliding the tyres are beyond their grip (dynamic car models only)
     */
    public record CarSprite(int id, double x, double y, double angle, boolean alive, boolean sliding) {
    }

    public SimulationFrame {
        cars = List.copyOf(cars);
        rayEnds = List.copyOf(rayEnds);
        trails = List.copyOf(trails);
    }
}
