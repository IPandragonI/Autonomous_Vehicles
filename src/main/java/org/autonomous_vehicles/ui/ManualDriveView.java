package org.autonomous_vehicles.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.Car;
import org.autonomous_vehicles.sim.CarControls;
import org.autonomous_vehicles.sim.Collision;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.KinematicModel;
import org.autonomous_vehicles.sim.LapTracker;
import org.autonomous_vehicles.sim.RaySensor;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.TireCompound;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.sim.VehicleModel;
import org.autonomous_vehicles.sim.Vehicles;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Keyboard test bench (step 18): drive one car with the arrow keys, with the same engine and the same fixed
 * time step (1/60 s) as the headless simulation. Walls are fatal, rays are drawn, checkpoints light up and laps
 * are timed; the dashboard shows gear, rpm, pedals and tyres.
 */
public class ManualDriveView {

    private static final double DT = 1.0 / 60.0;
    private static final Set<KeyCode> DRIVING_KEYS = EnumSet.of(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN);

    private final Set<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);
    private final RaySensor sensor = RaySensor.standard();
    private final TrackView view = new TrackView();
    private final Dashboard dashboard = new Dashboard(390, 300);
    private final Label message = new Label("Arrows: steer, accelerate, brake (hold to reverse) · R: restart · "
            + "mouse wheel: zoom");

    private Track track = Tracks.oval();
    private VehicleModel vehicle = KinematicModel.INSTANCE;
    private Car car;
    private LapTracker lapTracker;
    private CarControls controls = CarControls.neutral();
    private double accumulator;
    private long lastTime = -1;

    public void show(Stage stage) {
        ComboBox<String> trackChoice = new ComboBox<>();
        trackChoice.getItems().addAll(Tracks.BUILT_IN);
        trackChoice.getItems().addAll(Tracks.REAL);
        trackChoice.getItems().addAll(Tracks.importedNames());
        trackChoice.setValue(Tracks.OVAL);
        trackChoice.valueProperty().addListener((obs, old, name) -> {
            if (name != null) {
                track = Tracks.byName(name);
                view.resetView();
                restart();
            }
        });

        Button importTrack = new Button("Import…");
        importTrack.setOnAction(event -> {
            try {
                TrackImport.choose(stage).ifPresent(imported -> {
                    trackChoice.getItems().add(imported.getName());
                    trackChoice.setValue(imported.getName());
                });
            } catch (IllegalArgumentException e) {
                message.setText("Import failed: " + e.getMessage());
            }
        });

        ComboBox<String> carChoice = new ComboBox<>();
        carChoice.getItems().addAll(Vehicles.TYPES);
        carChoice.setValue(Vehicles.KINEMATIC);

        ComboBox<TireCompound> tireChoice = new ComboBox<>();
        tireChoice.getItems().addAll(TireCompound.values());
        tireChoice.setValue(TireCompound.MEDIUM);
        tireChoice.disableProperty().bind(carChoice.valueProperty().isEqualTo(Vehicles.KINEMATIC));

        carChoice.valueProperty().addListener((obs, old, type) -> {
            vehicle = Vehicles.create(type, tireChoice.getValue(), RaceRules.defaults());
            view.setCarType(type);
            restart();
        });
        tireChoice.valueProperty().addListener((obs, old, compound) -> {
            vehicle = Vehicles.create(carChoice.getValue(), compound, RaceRules.defaults());
            restart();
        });

        ToggleButton follow = new ToggleButton("Follow car");
        follow.setSelected(true);
        view.setFollow(true);
        follow.setOnAction(event -> view.setFollow(follow.isSelected()));

        for (Node node : List.of(trackChoice, importTrack, carChoice, tireChoice, follow)) {
            node.setFocusTraversable(false);
        }

        ToolBar toolbar = new ToolBar(new Label("Track"), trackChoice, importTrack, new Separator(), new Label("Car"),
                carChoice, new Label("Tyres"), tireChoice, new Separator(), follow);

        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #57606a;");
        VBox side = new VBox(10, dashboard, message);
        side.setPadding(new Insets(10));
        side.setPrefWidth(410);

        BorderPane root = new BorderPane(view);
        root.setTop(toolbar);
        root.setRight(side);
        Scene scene = new Scene(root, 1300, 750);

        // Filters run before the focused control: arrows drive the car instead of changing a selection.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (DRIVING_KEYS.contains(event.getCode())) {
                pressed.add(event.getCode());
                event.consume();
            } else if (event.getCode() == KeyCode.R) {
                restart();
                event.consume();
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (pressed.remove(event.getCode())) {
                event.consume();
            }
        });

        restart();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTime > 0) {
                    accumulator += Math.min(0.25, (now - lastTime) / 1e9);

                    while (accumulator >= DT) {
                        update();
                        accumulator -= DT;
                    }
                }

                lastTime = now;
                render();
            }
        };

        stage.setOnHidden(event -> timer.stop());
        stage.setTitle("Drive yourself");
        stage.setScene(scene);
        stage.show();
        timer.start();
    }

    private void restart() {
        car = Car.atStart(track);
        vehicle.prepare(car);
        lapTracker = new LapTracker(track);
        controls = CarControls.neutral();
    }

    private void update() {
        if (!car.isAlive()) {
            return;
        }

        double steer = (pressed.contains(KeyCode.LEFT) ? 1.0 : 0.0) - (pressed.contains(KeyCode.RIGHT) ? 1.0 : 0.0);
        double throttle = (pressed.contains(KeyCode.UP) ? 1.0 : 0.0) - (pressed.contains(KeyCode.DOWN) ? 1.0 : 0.0);
        controls = new CarControls(steer, throttle);

        Vec2 from = car.getPosition();
        vehicle.step(car, controls, DT);
        Vec2 to = car.getPosition();

        if (Collision.hits(car, track) || Collision.crossesWall(from, to, track)) {
            car.crash();
            return;
        }

        lapTracker.update(from, to, DT);
    }

    private void render() {
        SimulationFrame.CarSprite sprite = new SimulationFrame.CarSprite(0, car.getX(), car.getY(), car.getAngle(),
                car.isAlive(), car.isAlive() && car.isSliding());
        List<Vec2> rays = car.isAlive() ? Arrays.asList(sensor.hitPoints(car, track)) : List.of();
        view.draw(new SimulationFrame(track, List.of(sprite), 0, car.isAlive() ? car.getPosition() : null, rays,
                List.of(), lapTracker.getNextCheckpointIndex(), null));
        dashboard.draw(Telemetry.of(car, controls, lapTracker, vehicle,
                car.isAlive() ? DeathCause.ALIVE : DeathCause.CRASHED));
    }
}
