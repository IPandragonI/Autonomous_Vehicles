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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.CarSpec;
import org.autonomous_vehicles.sim.SimulationSettings;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.sim.Vehicles;

import java.io.File;
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

        Dashboard dashboard = new Dashboard(390, 250);
        NetworkView networkView = new NetworkView(390, 300);

        BorderPane root = new BorderPane(trackView);
        root.setTop(toolbar(stage, root, drawer));
        root.setRight(tabs(dashboard, networkView));
        root.setBottom(statusBar());

        loadToolbar();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                controller.tick();
                trackView.draw(controller.frame());
                statsPanel.refresh(controller);
                dashboard.draw(controller.getFocusedTelemetry());
                networkView.draw(controller.getFocusedNetwork());
                status.setText(statusLine());
            }
        }.start();

        stage.setTitle("Autonomous Vehicles");
        stage.setScene(new Scene(root, 1500, 900));
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
                spacer, viewMenu(stage), follow, settings, drive);
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

        Label brainHint = new Label("Left: the 5 distance sensors (and speed). Right: steering and throttle. "
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
        double bestLap = controller.getHistory().bestLapTimeEver();
        return String.format(Locale.ROOT, "Generation %d  ·  %d / %d cars on track  ·  best lap %s  ·  %s",
                controller.getGeneration(), controller.getAliveCount(), controller.getConfig().populationSize(),
                Double.isNaN(bestLap) ? "–" : Dashboard.lapTime(bestLap),
                controller.hasSelection() ? "following your selected car" : "showing the best car");
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
