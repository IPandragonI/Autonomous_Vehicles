package org.autonomous_vehicles.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.autonomous_vehicles.evolution.Champion;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.neural.NetworkIO;
import org.autonomous_vehicles.sim.CarSpec;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.sim.Vehicles;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Main window. Toolbar = what you change often (playback, track, car, tyres, view); the advanced settings are
 * in a drawer; the right side has three tabs (car dashboard, progress charts, network); a one-line status bar.
 * The {@link AnimationTimer} calls the controller once per image (update), then redraws (render).
 */
public class MainApp extends Application {

    private static final String OVAL_AND_CHICANE = "oval + chicane (both)";

    private final Map<String, List<String>> trackChoices = new LinkedHashMap<>();
    private final ComboBox<String> trackBox = new ComboBox<>();
    private final ComboBox<String> carBox = new ComboBox<>();
    private final ComboBox<TireCompound> tireBox = new ComboBox<>();
    private final Label status = new Label();
    private final Label message = new Label();
    private final Label carSummary = new Label();
    private final FpsCounter fps = new FpsCounter();

    private SimulationController controller;
    private TrackView trackView;
    private StatsPanel statsPanel;
    private ControlPanel controlPanel;
    private boolean updatingToolbar;

    @Override
    public void start(Stage stage) {
        SimulationConfig config = SimulationConfig.defaults().toBuilder()
                .settings(SimulationSettings.defaults().withTrailLength(300))
                .build();
        controller = new SimulationController(config);

        trackView = new TrackView();
        trackView.setOnWorldClick(controller::selectCarAt);
        statsPanel = new StatsPanel();
        controller.addGenerationListener(statsPanel::addGeneration);

        Consumer<String> say = message::setText;
        controlPanel = new ControlPanel(controller, () -> statsPanel.reload(controller.getHistory()), say);
        ScrollPane drawer = new ScrollPane(controlPanel);
        drawer.setFitToWidth(true);
        drawer.setPrefWidth(350);

        Dashboard dashboard = new Dashboard(390, 300);
        NetworkView networkView = new NetworkView(390, 300);

        BorderPane root = new BorderPane(trackView);
        root.setTop(toolbar(stage, root, drawer));
        root.setRight(tabs(dashboard, networkView));
        root.setBottom(statusBar());

        loadToolbar();

        new AnimationTimer() {
            private long previous = -1;

            @Override
            public void handle(long now) {
                fps.frame(now);
                controller.tick(previous < 0 ? 0.0 : (now - previous) / 1e9);
                previous = now;
                trackView.draw(controller.frame(), overlay());
                statsPanel.refresh(controller);
                dashboard.draw(controller.getFocusedTelemetry());
                networkView.draw(controller.getFocusedNetwork(), controller.getFocusedInputLabels());
                status.setText(statusLine());
                Champion dropped = controller.takeDroppedSeed();

                if (dropped != null) {
                    message.setText("Random population: the champion's network (" + dropped.config().architectureLabel()
                            + ") does not fit " + controller.getConfig().architectureLabel()
                            + " (other rays or hidden layers, or fewer inputs; new inputs alone are added)");
                }
            }
        }.start();

        stage.setTitle("Autonomous Vehicles");
        Scene scene = new Scene(root, 1500, 900);
        // F11 toggles full screen (Esc also leaves it, JavaFX default); the window starts in full screen.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setFullScreenExitHint("Full screen · F11 or Esc to leave");
        stage.setFullScreen(true);
        stage.show();
    }

    private ToolBar toolbar(Stage stage, BorderPane root, ScrollPane drawer) {
        Button play = new Button("▶ Play");
        play.setPrefWidth(80);
        play.setOnAction(event -> {
            if (controller.isPlaying()) {
                controller.pause();
                play.setText("▶ Play");
            } else {
                controller.play();
                play.setText("❚❚ Pause");
            }
        });

        Button step = new Button("Step");
        step.setTooltip(new Tooltip("Advance one simulation step (1/60 s)."));
        step.setOnAction(event -> controller.step());

        Button restart = new Button("Restart");
        restart.setTooltip(new Tooltip("New random population, generation 0."));
        restart.setOnAction(event -> {
            controller.reset();
            statsPanel.reload(controller.getHistory());
            message.setText("Restarted from a new random population");
        });

        ToggleGroup speeds = new ToggleGroup();
        HBox speedBox = new HBox(2);

        for (int speed : SimulationController.SPEEDS) {
            ToggleButton button = new ToggleButton("×" + speed);
            button.setToggleGroup(speeds);
            button.setSelected(speed == controller.getSpeed());
            button.setOnAction(event -> {
                controller.setSpeed(speed);
                button.setSelected(true);
            });
            speedBox.getChildren().add(button);
        }

        trackChoices.put(Tracks.OVAL, List.of(Tracks.OVAL));
        trackChoices.put("hairpin", List.of("hairpin"));
        trackChoices.put("chicane", List.of("chicane"));
        trackChoices.put(OVAL_AND_CHICANE, List.of(Tracks.OVAL, "chicane"));

        for (String circuit : Tracks.REAL) {
            trackChoices.put("🏁 " + Tracks.displayName(circuit), List.of(circuit));
        }
        trackBox.getItems().setAll(trackChoices.keySet());
        trackBox.setTooltip(new Tooltip("Track(s) the cars learn on. Changing it starts a new population."));

        Button importTrack = new Button("Import…");
        importTrack.setTooltip(new Tooltip("Load a real circuit (CSV x,y,width_right,width_left in metres, e.g. from "
                + "github.com/TUMFTM/racetrack-database) or a .track file."));
        importTrack.setOnAction(event -> importTrack(stage));

        carBox.getItems().setAll(Vehicles.TYPES);
        carBox.setTooltip(new Tooltip("kinematic = simple model of the assignment; others have an engine, gears and tyres."));
        tireBox.getItems().setAll(TireCompound.values());
        tireBox.disableProperty().bind(carBox.valueProperty().isEqualTo(Vehicles.KINEMATIC));

        trackBox.valueProperty().addListener((obs, old, value) -> applyToolbar());
        carBox.valueProperty().addListener((obs, old, value) -> applyToolbar());
        tireBox.valueProperty().addListener((obs, old, value) -> applyToolbar());

        ToggleButton follow = new ToggleButton("Follow car");
        follow.setTooltip(new Tooltip("Keep the camera on the inspected car. Wheel = zoom, drag = move, double-click = reset."));
        follow.setOnAction(event -> trackView.setFollow(follow.isSelected()));

        ToggleButton settings = new ToggleButton("⚙ Settings");
        settings.setOnAction(event -> root.setLeft(settings.isSelected() ? drawer : null));

        Button drive = new Button("Drive yourself");
        drive.setTooltip(new Tooltip("Open the keyboard test bench."));
        drive.setOnAction(event -> new ManualDriveView().show(new Stage()));

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(play, step, restart, new Separator(), new Label("Speed"), speedBox, new Separator(),
                new Label("Track"), trackBox, importTrack, new Label("Car"), carBox, new Label("Tyres"), tireBox,
                spacer, championMenu(stage), viewMenu(stage), follow, settings, drive);
    }

    private MenuButton championMenu(Stage stage) {
        MenuItem save = new MenuItem("Save champion (.json)…");
        save.setOnAction(event -> saveChampion(stage, true));
        MenuItem saveWeights = new MenuItem("Save weights only (.txt, NetworkIO)…");
        saveWeights.setOnAction(event -> saveChampion(stage, false));
        MenuItem load = new MenuItem("Load champion and replay…");
        load.setOnAction(event -> loadChampion(stage));
        MenuItem back = new MenuItem("Back to training");
        back.setOnAction(event -> {
            controller.stopReplay();
            loadToolbar();
            trackView.resetView();
            message.setText("Training resumed where it was left");
        });

        MenuItem retrainCurrent = new MenuItem("Retrain a population from the current champion");
        retrainCurrent.setOnAction(event -> retrainFromCurrentChampion());
        MenuItem retrain = new MenuItem("Retrain a population from a champion…");
        retrain.setOnAction(event -> retrainFromChampion(stage));
        MenuItem random = new MenuItem("Back to random populations");
        random.setOnAction(event -> {
            controller.clearSeed();
            afterRestart();
            message.setText("New random population (the champion is no longer the seed)");
        });

        // Which car of the run "the current champion" is: the fittest, or the one with the fastest lap.
        ToggleGroup kinds = new ToggleGroup();
        RadioMenuItem byFitness = new RadioMenuItem("Champion = best score");
        RadioMenuItem byLap = new RadioMenuItem("Champion = fastest lap (any generation)");
        byFitness.setToggleGroup(kinds);
        byLap.setToggleGroup(kinds);
        byFitness.setOnAction(event -> chooseChampionKind(SimulationController.ChampionKind.BEST_FITNESS));
        byLap.setOnAction(event -> chooseChampionKind(SimulationController.ChampionKind.FASTEST_LAP));
        MenuItem replayCurrent = new MenuItem("Replay the current champion");
        replayCurrent.setOnAction(event -> replayCurrentChampion());

        MenuButton menu = new MenuButton("Champion", null, byFitness, byLap, new SeparatorMenuItem(), save,
                saveWeights, new SeparatorMenuItem(), replayCurrent, load, back, new SeparatorMenuItem(),
                retrainCurrent, retrain, random);
        menu.setTooltip(new Tooltip("The best car of the run (best score, or fastest lap): save it with its "
                + "configuration, replay it, or breed a new population from it."));
        menu.setOnShowing(event -> {
            boolean lapCriterion = controller.getChampionKind() == SimulationController.ChampionKind.FASTEST_LAP;
            byFitness.setSelected(!lapCriterion);
            byLap.setSelected(lapCriterion);
            byLap.setText("Champion = fastest lap (any generation)" + fastestLapLabel());
            boolean none = controller.getCurrentChampion() == null;
            save.setDisable(none);
            saveWeights.setDisable(none);
            replayCurrent.setDisable(none);
            retrainCurrent.setDisable(none);
            back.setDisable(!controller.isReplaying());
            random.setDisable(controller.getSeed() == null);
        });
        return menu;
    }

    /** " · 0:51.23, gen 7", or " · no lap yet". */
    private String fastestLapLabel() {
        double time = controller.getAlgorithm().getFastestLapTime();
        return Double.isNaN(time) ? " · no lap yet" : String.format(Locale.ROOT, " · %s, gen %d",
                Dashboard.lapTime(time), controller.getAlgorithm().getFastestLapGeneration());
    }

    private void chooseChampionKind(SimulationController.ChampionKind kind) {
        controller.setChampionKind(kind);
        message.setText(kind == SimulationController.ChampionKind.FASTEST_LAP
                ? "Champion menu now works on the car with the fastest lap" + fastestLapLabel()
                : "Champion menu now works on the car with the best score");
    }

    private void replayCurrentChampion() {
        Champion champion = controller.getCurrentChampion();

        if (champion == null) {
            message.setText("No champion yet for this criterion.");
            return;
        }

        try {
            controller.startReplay(champion);
            trackView.setCarType(champion.config().carType());
            trackView.resetView();
            message.setText("Replaying the current champion · Champion ▸ Back to training to resume");
        } catch (IllegalArgumentException e) {
            message.setText("Cannot replay: " + e.getMessage());
        }
    }

    /**
     * Generation 0 becomes the champion plus mutants of it, with the champion's configuration; the track, car and
     * settings can then be changed as usual (the champion stays the seed while the network shape fits).
     */
    private void retrainFromChampion(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Champion to retrain a population from");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Champion", "*" + Champion.EXTENSION));
        File file = chooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        try {
            Champion champion = Champion.load(file.toPath());
            controller.retrainFrom(champion);
            afterRestart();
            message.setText(String.format(Locale.ROOT, "Retraining from %s (generation %d, fitness %.2f): car 0 is "
                    + "the champion, the others are mutants of it", file.getName(), champion.generation(),
                    champion.fitness()));
        } catch (IOException | IllegalArgumentException e) {
            message.setText("Cannot retrain: " + e.getMessage());
        }
    }

    /**
     * Same as retraining from a saved file, with the best individual of the running training (no file needed):
     * generation 0 becomes that champion plus mutants of it, with the current configuration.
     */
    private void retrainFromCurrentChampion() {
        Champion champion = controller.getCurrentChampion();

        if (champion == null) {
            message.setText("No champion yet for this criterion.");
            return;
        }

        try {
            controller.retrainFrom(champion);
            afterRestart();
            message.setText(String.format(Locale.ROOT, "Retraining from the current champion (generation %d, "
                    + "fitness %.2f): car 0 is the champion, the others are mutants of it", champion.generation(),
                    champion.fitness()));
        } catch (IllegalArgumentException e) {
            message.setText("Cannot retrain: " + e.getMessage());
        }
    }

    /** Puts every panel in line with a new run (new configuration, generation 0). */
    private void afterRestart() {
        statsPanel.reload(controller.getHistory());
        controlPanel.refresh();
        loadToolbar();
        trackView.resetView();
    }

    private void saveChampion(Stage stage, boolean json) {
        Champion champion = controller.getCurrentChampion();

        if (champion == null) {
            message.setText("No champion yet for this criterion.");
            return;
        }

        boolean fastest = controller.getChampionKind() == SimulationController.ChampionKind.FASTEST_LAP;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(json ? "Save the champion and its configuration" : "Save the champion's weights");
        chooser.setInitialFileName(String.format(Locale.ROOT, "%s_gen_%03d%s", fastest ? "fastest_lap" : "champion",
                champion.generation(), json ? Champion.EXTENSION : ".txt"));
        File file = chooser.showSaveDialog(stage);

        if (file == null) {
            return;
        }

        try {
            if (json) {
                champion.save(file.toPath());
            } else {
                NetworkIO.save(champion.network(), file.toPath());
            }

            message.setText("Champion saved to " + file.getName());
        } catch (IOException | IllegalStateException e) {
            message.setText("Cannot save: " + e.getMessage());
        }
    }

    private void loadChampion(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Champion to replay");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Champion", "*" + Champion.EXTENSION));
        File file = chooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        try {
            Champion champion = Champion.load(file.toPath());
            controller.startReplay(champion);
            trackView.setCarType(champion.config().carType());
            trackView.resetView();
            message.setText("Replaying " + file.getName() + " · Champion ▸ Back to training to resume");
        } catch (IOException | IllegalArgumentException e) {
            message.setText("Cannot load: " + e.getMessage());
        }
    }

    /**
     * Text drawn over the top-left corner of the track: FPS, and what is replayed.
     */
    private List<String> overlay() {
        Champion replay = controller.getReplay();
        double effective = controller.getEffectiveSpeed();
        String head = fps.label() + (Double.isNaN(effective) ? "" : String.format(Locale.ROOT, "  ·  sim ×%s",
                effective >= 10 ? String.format(Locale.ROOT, "%.0f", effective)
                        : String.format(Locale.ROOT, "%.1f", effective)));

        if (replay == null) {
            return List.of(head);
        }

        return List.of(head, String.format(Locale.ROOT, "REPLAY · champion of generation %d · fitness %.2f",
                replay.generation(), replay.fitness()),
                Tracks.displayName(replay.config().tracks().getFirst()) + " · " + replay.config().carType()
                        + " · " + replay.config().architectureLabel());
    }

    private MenuButton viewMenu(Stage stage) {
        RenderOptions options = controller.getRenderOptions();
        CheckMenuItem rays = check("Sensor rays", options.showRays(), options::setShowRays);
        CheckMenuItem trails = check("Trajectories", options.showTrails(), options::setShowTrails);
        CheckMenuItem checkpoints = check("Checkpoints", options.showCheckpoints(), options::setShowCheckpoints);
        CheckMenuItem dead = check("Stopped cars", options.showDeadCars(), options::setShowDeadCars);
        CheckMenuItem ghost = check("Ghost of the best lap", options.showGhost(), options::setShowGhost);

        MenuItem carImage = new MenuItem("Car image for this car type…");
        carImage.setOnAction(event -> chooseCarImage(stage));
        MenuItem clearImage = new MenuItem("Use the built-in drawing");
        clearImage.setOnAction(event -> {
            CarSprites.clear(carBox.getValue());
            trackView.refreshSprite();
            message.setText("Built-in drawing restored for " + carBox.getValue());
        });
        MenuItem resetView = new MenuItem("Fit track in window");
        resetView.setOnAction(event -> trackView.resetView());

        return new MenuButton("View", null, rays, trails, checkpoints, dead, ghost, new SeparatorMenuItem(),
                carImage, clearImage, new SeparatorMenuItem(), resetView);
    }

    private static CheckMenuItem check(String text, boolean initial, Consumer<Boolean> setter) {
        CheckMenuItem item = new CheckMenuItem(text);
        item.setSelected(initial);
        item.selectedProperty().addListener((obs, old, value) -> setter.accept(value));
        return item;
    }

    private TabPane tabs(Dashboard dashboard, NetworkView networkView) {
        Label carHint = new Label("Showing the best car on track. Click a car to follow another one.");
        carHint.setWrapText(true);
        carHint.setStyle("-fx-text-fill: #57606a;");
        carSummary.setWrapText(true);
        VBox carTab = new VBox(10, dashboard, carSummary, carHint);
        carTab.setPadding(new Insets(10));

        Label brainHint = new Label("Left: the distance sensors, then speed, fuel, tyre temperature (0.5 = middle of "
                + "the grip window), wear and sliding when enabled. Right: steering and throttle. "
                + "Lines: blue = positive weight, red = negative, thicker = stronger. Circles light up with activity.");
        brainHint.setWrapText(true);
        brainHint.setStyle("-fx-text-fill: #57606a;");
        VBox brainTab = new VBox(10, networkView, brainHint);
        brainTab.setPadding(new Insets(10));

        ScrollPane progress = new ScrollPane(statsPanel);
        progress.setFitToWidth(true);

        TabPane tabs = new TabPane(new Tab("Car", carTab), new Tab("Progress", progress), new Tab("Brain", brainTab));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefWidth(415);
        return tabs;
    }

    private HBox statusBar() {
        message.setStyle("-fx-text-fill: #57606a;");
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, status, spacer, message);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        bar.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #d0d7de; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    private String statusLine() {
        if (controller.isReplaying()) {
            double lap = controller.getFocusedAgent() == null ? Double.NaN
                    : controller.getFocusedAgent().result().bestLapTime();
            return String.format(Locale.ROOT, "Replay  ·  t = %.1f s  ·  lap %s  ·  training paused at generation %d",
                    controller.getDisplayedSimulation().getTime(), Double.isNaN(lap) ? "–" : Dashboard.lapTime(lap),
                    controller.getGeneration());
        }

        double bestLap = controller.getHistory().bestLapTimeEver();
        return String.format(Locale.ROOT, "Generation %d  ·  %d / %d cars on track  ·  best lap %s  ·  %s",
                controller.getGeneration(), controller.getAliveCount(), controller.getConfig().populationSize(),
                Double.isNaN(bestLap) ? "–" : Dashboard.lapTime(bestLap),
                (controller.hasSelection() ? "following your selected car" : "showing the best car")
                        + (controller.getSeed() == null ? "" : String.format(Locale.ROOT,
                        "  ·  bred from a champion of generation %d", controller.getSeed().generation())));
    }

    /** Puts the toolbar in line with the controller's configuration without triggering a change. */
    private void loadToolbar() {
        updatingToolbar = true;
        SimulationConfig config = controller.getConfig();
        String trackKey = trackChoices.entrySet().stream().filter(entry -> entry.getValue().equals(config.tracks()))
                .map(Map.Entry::getKey).findFirst().orElse(Tracks.OVAL);
        trackBox.setValue(trackKey);
        carBox.setValue(config.carType());
        tireBox.setValue(config.tire());
        trackView.setCarType(config.carType());
        carSummary.setText(carSummary(config));
        updatingToolbar = false;
    }

    private void applyToolbar() {
        if (updatingToolbar || trackBox.getValue() == null || carBox.getValue() == null || tireBox.getValue() == null) {
            return;
        }

        try {
            SimulationConfig config = QuickSetup.apply(controller.getConfig(), trackChoices.get(trackBox.getValue()),
                    carBox.getValue(), tireBox.getValue());
            controller.applyConfig(config);
            statsPanel.reload(controller.getHistory());
            controlPanel.refresh();
            loadToolbar();
            trackView.resetView();
            message.setText(QuickSetup.describe(config));
        } catch (IllegalArgumentException e) {
            message.setText("Not applied: " + e.getMessage());
            loadToolbar();
        }
    }

    private void importTrack(Stage stage) {
        try {
            TrackImport.choose(stage).ifPresent(track -> {
                trackChoices.put(track.getName(), List.of(track.getName()));
                trackBox.getItems().setAll(trackChoices.keySet());
                trackBox.setValue(track.getName());
                message.setText(String.format(Locale.ROOT, "Imported %s: %.1f km, %d checkpoints · zoom with the "
                        + "mouse wheel or use Follow car", track.getName(), track.lapLength() / 1000,
                        track.getCheckpoints().size()));
            });
        } catch (IllegalArgumentException e) {
            message.setText("Import failed: " + e.getMessage());
        }
    }

    private void chooseCarImage(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Top view image of the " + carBox.getValue() + " (front pointing right)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.gif"));
        File file = chooser.showOpenDialog(stage);

        if (file != null) {
            try {
                CarSprites.set(carBox.getValue(), file);
                trackView.refreshSprite();
                message.setText("Using " + file.getName() + " for " + carBox.getValue() + " (remembered)");
            } catch (IllegalArgumentException e) {
                message.setText(e.getMessage());
            }
        }
    }

    static String carSummary(SimulationConfig config) {
        if (config.carType().equals(Vehicles.KINEMATIC)) {
            return "Simple model of the assignment: constant acceleration, top speed 72 km/h, unlimited grip.";
        }

        CarSpec spec = Vehicles.PRESETS.get(config.carType());
        return String.format(Locale.ROOT, "%s · %.0f kg · %.0f kW · %d gear%s · %s tyres · top speed %.0f km/h",
                spec.name(), spec.mass(), spec.peakPower() / 1000, spec.gearCount(), spec.gearCount() > 1 ? "s" : "",
                config.tire(), config.createVehicle().topSpeed() * 3.6);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
