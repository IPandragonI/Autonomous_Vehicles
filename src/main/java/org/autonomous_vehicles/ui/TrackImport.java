package org.autonomous_vehicles.ui;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.TrackLoader;
import org.autonomous_vehicles.sim.Tracks;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * "Import track…": a real circuit as CSV ({@code x_m,y_m,w_tr_right_m,w_tr_left_m}, e.g. from the TUM racetrack
 * database) or a {@code .track} file. The track is registered under its file name for the rest of the session.
 */
final class TrackImport {

    private TrackImport() {
    }

    /**
     * @return the imported track, or empty if the dialog was cancelled
     * @throws IllegalArgumentException with a readable reason if the file is not a valid track
     */
    static Optional<Track> choose(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import a track");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tracks (*.csv, *.track)", "*.csv", "*.track"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(owner);

        if (file == null) {
            return Optional.empty();
        }

        try {
            Track track = TrackLoader.load(file.toPath());
            Tracks.register(track);
            return Optional.of(track);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read " + file.getName() + ": " + e.getMessage());
        }
    }
}
