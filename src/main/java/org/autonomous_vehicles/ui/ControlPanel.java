package org.autonomous_vehicles.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.autonomous_vehicles.evolution.Champion;
import org.autonomous_vehicles.evolution.CompositeFitness;
import org.autonomous_vehicles.evolution.Imitation;
import org.autonomous_vehicles.evolution.FitnessComponent;
import org.autonomous_vehicles.evolution.MutationOperator;
import org.autonomous_vehicles.evolution.SelectionType;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.RaceRules;
import org.autonomous_vehicles.sim.TrackPreview;
import org.autonomous_vehicles.sim.Vehicles;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Advanced settings drawer (hidden by default). Track, car, tyres, playback and display live in the toolbar.
 * "Evolution" and "Fitness" apply from the next generation; "Population &amp; network" needs a new run.
 * Every change goes through {@link SimulationConfig} validation; refused values are reported, not applied.
 */
public class ControlPanel extends VBox {

    private final SimulationController controller;
    private final Runnable onReset;
    private final Consumer<String> status;

    private final Slider population = slider(10, 300, 100, 0);
    private final ComboBox<String> hiddenLayers = new ComboBox<>();
    private final Label architectureLabel = new Label();
    private final CheckBox speedInput = new CheckBox("Speed as an input");
    private final CheckBox carStateInputs = new CheckBox("Fuel, tyre temperature, wear, sliding as inputs");
    private final CheckBox trackPreview = new CheckBox("Road ahead (0–200 m) as inputs");
    private final CheckBox forwardRays = new CheckBox("Rays packed towards the front");
    private final CheckBox gripSteering = new CheckBox("Steering relative to the grip");
    private final Slider timeLimit = slider(20, 900, 60, 0);
    private final Slider rayRange = slider(20, 300, 80, 0);
    private final TextField seed = new TextField();
    private final Slider fuelAutonomy = slider(0, 300, RaceRules.DEFAULT_FUEL_AUTONOMY, 0);
    private final CheckBox tyreTemperature = new CheckBox("Tyre temperature (grip window)");
    private final Slider tyreWear = slider(0, 1, RaceRules.DEFAULT_TYRE_WEAR, 2);

    private final Slider mutationRate = slider(0, 0.5, 0.08, 2);
    private final Slider mutationStrength = slider(0, 1.5, 0.25, 2);
    private final ComboBox<MutationOperator.Noise> noise = new ComboBox<>();
    private final ComboBox<SelectionType> selection = new ComboBox<>();
    private final Spinner<Integer> tournamentSize = new Spinner<>(1, 300, 3);
    private final Slider truncation = slider(0.02, 1.0, 0.10, 2);
    private final Spinner<Integer> elites = new Spinner<>(0, 299, 5);
    private final ComboBox<SimulationConfig.Crossover> crossover = new ComboBox<>();

    private final ComboBox<SimulationConfig.FitnessMode> fitnessMode = new ComboBox<>();
    private final Map<FitnessComponent, Slider> weights = new EnumMap<>(FitnessComponent.class);

    private boolean loading;

    public ControlPanel(SimulationController controller, Runnable onReset, Consumer<String> status) {
        this.controller = controller;
        this.onReset = onReset;
        this.status = status;
        setSpacing(8);
        setPadding(new Insets(8));
        setPrefWidth(330);

        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Accordion accordion = new Accordion(evolutionPane(), fitnessPane(), runPane());
        accordion.setExpandedPane(accordion.getPanes().getFirst());

        Label champion = new Label("Save or replay the best network from the Champion menu of the toolbar.");
        champion.setWrapText(true);
        champion.setStyle("-fx-text-fill: #57606a;");

        getChildren().addAll(title, accordion, champion);
        refresh();
    }

    private TitledPane evolutionPane() {
        noise.getItems().addAll(MutationOperator.Noise.values());
        selection.getItems().addAll(SelectionType.values());
        crossover.getItems().addAll(SimulationConfig.Crossover.values());
        tournamentSize.setEditable(true);
        elites.setEditable(true);

        GridPane grid = grid();
        row(grid, 0, "Mutation rate", mutationRate, "Share of the weights changed in each child.");
        row(grid, 1, "Mutation size", mutationStrength, "How much a mutated weight moves (standard deviation).");
        row(grid, 2, "Noise", noise, null);
        row(grid, 3, "Selection", selection, "How parents are chosen.");
        row(grid, 4, "Tournament size", tournamentSize, "Higher = stronger preference for the best cars.");
        row(grid, 5, "Truncation", truncation, "Share of the population allowed to breed (truncation selection).");
        row(grid, 6, "Elites", elites, "Best cars copied unchanged to the next generation.");
        row(grid, 7, "Crossover", crossover, "Mixes the weights of two parents.");

        onChange(mutationRate, mutationStrength, truncation);
        noise.valueProperty().addListener((obs, old, value) -> applyHot());
        selection.valueProperty().addListener((obs, old, value) -> applyHot());
        crossover.valueProperty().addListener((obs, old, value) -> applyHot());
        tournamentSize.valueProperty().addListener((obs, old, value) -> applyHot());
        elites.valueProperty().addListener((obs, old, value) -> applyHot());

        return new TitledPane("Evolution · applied next generation", grid);
    }

    private TitledPane fitnessPane() {
        fitnessMode.getItems().addAll(SimulationConfig.FitnessMode.values());
        fitnessMode.valueProperty().addListener((obs, old, value) -> applyHot());

        GridPane grid = grid();
        row(grid, 0, "Score", fitnessMode, "PROGRESS = checkpoints only; COMPOSITE = weighted criteria below.");
        int row = 1;

        for (FitnessComponent component : FitnessComponent.values()) {
            Slider slider = slider(0, 10, component.defaultWeight(), 1);
            weights.put(component, slider);
            onChange(slider);
            row(grid, row++, component.label(), slider, null);
        }

        return new TitledPane("Fitness · applied next generation", grid);
    }

    private TitledPane runPane() {
        hiddenLayers.setEditable(true);
        hiddenLayers.getItems().addAll("6", "12-8", "8", "4", "16-16");
        hiddenLayers.valueProperty().addListener((obs, old, value) -> updateArchitectureLabel());
        speedInput.selectedProperty().addListener((obs, old, value) -> updateArchitectureLabel());
        carStateInputs.selectedProperty().addListener((obs, old, value) -> updateArchitectureLabel());
        carStateInputs.setTooltip(new Tooltip("4 more inputs: fuel left, tyre temperature relative to the compound's "
                + "window, tyre wear, sliding. Constant for the kinematic car."));
        trackPreview.selectedProperty().addListener((obs, old, value) -> updateArchitectureLabel());
        trackPreview.setTooltip(new Tooltip(TrackPreview.INPUTS + " more inputs: where the road goes 0, 25, 50, 100, "
                + "150 and 200 m ahead, relative to the car's heading. Lets a fast car see a bend before the rays do."));
        forwardRays.setTooltip(new Tooltip("Same number of rays, packed around the heading (e.g. 0°, ±6°, ±23°, ±51°, "
                + "±90° for 9 rays) instead of evenly spread."));
        gripSteering.setTooltip(new Tooltip("Steering ±1 asks for all the grip as cornering force, at any speed, "
                + "instead of full steering lock. Grip-limited cars only."));

        Button apply = new Button("Apply & restart");
        apply.setDefaultButton(true);
        apply.setOnAction(event -> applyWithReset());

        GridPane grid = grid();
        row(grid, 0, "Cars", population, "Population size.");
        row(grid, 1, "Hidden layers", hiddenLayers, "Neurons per hidden layer, e.g. 6 or 12-8.");
        grid.add(architectureLabel, 1, 2);
        grid.add(speedInput, 1, 3);
        grid.add(carStateInputs, 1, 4);
        grid.add(trackPreview, 1, 5);
        grid.add(forwardRays, 1, 6);
        grid.add(gripSteering, 1, 7);
        row(grid, 8, "Time limit (s)", timeLimit, "A car is stopped after this time.");
        row(grid, 9, "Sensor range (m)", rayRange, "How far the distance rays see.");
        row(grid, 10, "Seed", seed, "Same seed = same run.");
        row(grid, 11, "Fuel (s at full power)", fuelAutonomy, "Starting fuel of grip-limited cars, in seconds of "
                + "full power; 0 = unlimited. A dry car loses drive and is out once it stops. Ignored by kinematic.");
        grid.add(tyreTemperature, 1, 12);
        Tooltip.install(tyreTemperature, new Tooltip("Tyres heat with grip use and sliding, cool in the airflow; "
                + "grip is best inside the compound's window. Ignored by the kinematic car."));
        row(grid, 13, "Tyre wear (×)", tyreWear, "Multiplier of the compounds' wear rates. 1 = original rates (a "
                + "medium is worn out after ~2 laps of São Paulo), 0.1 = ~15 laps, 0 = no wear. Ignored by kinematic.");
        grid.add(apply, 1, 14);

        Button recommended = new Button("Fastest-learning defaults");
        recommended.setOnAction(event -> applyRecommended());
        recommended.setTooltip(new Tooltip("Resets population, network, evolution, fitness and race settings to the "
                + "values that learned fastest (100 cars, 6 neurons, mutation 8 %/0.25, truncation 10 % with 5 elites, "
                + "default fitness, unlimited fuel, tyre wear ×" + RaceRules.DEFAULT_TYRE_WEAR + "). Keeps the track, "
                + "car, tyres and seed, and restarts the run."));
        grid.add(recommended, 1, 15);

        Button imitate = new Button("Learn from the expert");
        imitate.setOnAction(event -> learnFromExpert(imitate));
        imitate.setTooltip(new Tooltip("Pre-trains a network by imitating an expert driver that follows a computed "
                + "racing line (backpropagation + DAgger), then restarts the evolution from it with fine mutation "
                + "(3 %/0.1). Turns on the road-ahead inputs and a 32-32 network. Grip-limited cars only; takes "
                + "2–5 minutes, the current run goes on meanwhile. Formula car on São Paulo: 1:43.6 after 150 "
                + "generations (best evolved from scratch: 2:24)."));
        grid.add(imitate, 1, 16);

        return new TitledPane("Population & network · restarts the run", grid);
    }

    /**
     * Reloads every control from the controller's configuration (e.g. after a toolbar change).
     */
    public void refresh() {
        SimulationConfig config = controller.getConfig();
        loading = true;
        population.setValue(config.populationSize());
        hiddenLayers.setValue(Arrays.stream(config.hiddenLayers()).mapToObj(String::valueOf).collect(Collectors.joining("-")));
        speedInput.setSelected(config.useSpeedInput());
        carStateInputs.setSelected(config.useCarStateInputs());
        trackPreview.setSelected(config.useTrackPreview());
        forwardRays.setSelected(config.rayLayout() == SimulationConfig.RayLayout.FORWARD);
        gripSteering.setSelected(config.gripSteering());
        timeLimit.setValue(config.settings().maxTime());
        rayRange.setValue(config.rayRange());
        seed.setText(String.valueOf(config.seed()));
        fuelAutonomy.setValue(config.rules().fuelAutonomy());
        tyreTemperature.setSelected(config.rules().tyreTemperature());
        tyreWear.setValue(config.rules().tyreWear());
        mutationRate.setValue(config.mutationRate());
        mutationStrength.setValue(config.mutationStrength());
        noise.setValue(config.noise());
        selection.setValue(config.selection());
        tournamentSize.getValueFactory().setValue(config.tournamentSize());
        truncation.setValue(config.truncationFraction());
        elites.getValueFactory().setValue(config.eliteCount());
        crossover.setValue(config.crossover());
        fitnessMode.setValue(config.fitnessMode());
        weights.forEach((component, slider) -> slider.setValue(config.compositeFitness().weight(component)));
        loading = false;
        updateArchitectureLabel();
    }

    private void updateArchitectureLabel() {
        try {
            int[] hidden = parseHiddenLayers(hiddenLayers.getValue());
            StringBuilder label = new StringBuilder("Network: ").append(controller.getConfig().rayCount()
                    + (speedInput.isSelected() ? 1 : 0) + (carStateInputs.isSelected() ? CarBrain.CAR_STATE_INPUTS : 0)
                    + (trackPreview.isSelected() ? TrackPreview.INPUTS : 0));

            for (int size : hidden) {
                label.append('-').append(size);
            }

            architectureLabel.setText(label.append("-2").toString());
            architectureLabel.setStyle("-fx-text-fill: #57606a;");
        } catch (IllegalArgumentException e) {
            architectureLabel.setText(e.getMessage());
            architectureLabel.setStyle("-fx-text-fill: #b42318;");
        }
    }

    private SimulationConfig.Builder hotValues(SimulationConfig base) {
        Map<FitnessComponent, Double> weightValues = new EnumMap<>(FitnessComponent.class);
        weights.forEach((component, slider) -> weightValues.put(component, round(slider.getValue(), 1)));

        return base.toBuilder()
                .mutationRate(round(mutationRate.getValue(), 2))
                .mutationStrength(round(mutationStrength.getValue(), 2))
                .noise(noise.getValue())
                .selection(selection.getValue())
                .tournamentSize(tournamentSize.getValue())
                .truncationFraction(round(truncation.getValue(), 2))
                .eliteCount(elites.getValue())
                .crossover(crossover.getValue())
                .fitnessMode(fitnessMode.getValue())
                .compositeFitness(new CompositeFitness(weightValues));
    }

    private void applyHot() {
        if (loading) {
            return;
        }

        try {
            controller.applyConfig(hotValues(controller.getConfig()).build());
            status.accept("Evolution settings updated · used from the next generation");
        } catch (IllegalArgumentException e) {
            status.accept("Not applied: " + e.getMessage());
        }
    }

    private void learnFromExpert(Button button) {
        SimulationConfig base = controller.getConfig();

        if (Vehicles.KINEMATIC.equals(base.carType())) {
            status.accept("Imitation needs a grip-limited car: choose a kart, hatchback, sports or formula car.");
            return;
        }

        SimulationConfig config = Imitation.recommendedConfig(base);
        button.setDisable(true);
        status.accept("Learning from the expert on " + String.join(" + ", config.tracks()) + "… (2–5 min)");

        Thread worker = new Thread(() -> {
            try {
                Imitation.Report report = Imitation.train(config, Imitation.Settings.defaults());
                AgentResult drive = report.best().result();
                String expert = report.expertLapTimes().isEmpty() ? "no lap"
                        : String.format(Locale.ROOT, "%.1f s", report.expertLapTimes().getFirst());
                String learned = drive.laps() > 0 ? String.format(Locale.ROOT, "laps in %.1f s", drive.bestLapTime())
                        : String.format(Locale.ROOT, "reaches checkpoint %.0f/%d", drive.progress(),
                        drive.checkpointCount());

                Platform.runLater(() -> {
                    controller.retrainFrom(new Champion(config, report.network(), 0, 0.0));
                    refresh();
                    onReset.run();
                    button.setDisable(false);
                    status.accept("Imitation done: the network " + learned + " (expert " + expert
                            + ") · evolution restarted from it");
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    status.accept("Imitation failed: " + e.getMessage());
                });
            }
        }, "imitation");
        worker.setDaemon(true);
        worker.start();
    }

    private void applyRecommended() {
        SimulationConfig candidate = QuickSetup.recommended(controller.getConfig());

        if (!controller.applyConfig(candidate)) {
            controller.reset();
        }

        refresh();
        onReset.run();
        status.accept("Fastest-learning defaults applied · restarted (" + candidate.architectureLabel() + ", "
                + "truncation 10 %, unlimited fuel)");
    }

    private void applyWithReset() {
        try {
            SimulationConfig candidate = hotValues(controller.getConfig())
                    .populationSize((int) Math.round(population.getValue()))
                    .useSpeedInput(speedInput.isSelected())
                    .useCarStateInputs(carStateInputs.isSelected())
                    .useTrackPreview(trackPreview.isSelected())
                    .rayLayout(forwardRays.isSelected() ? SimulationConfig.RayLayout.FORWARD
                            : SimulationConfig.RayLayout.EVEN)
                    .gripSteering(gripSteering.isSelected())
                    .hiddenLayers(parseHiddenLayers(hiddenLayers.getValue()))
                    .rayRange(Math.round(rayRange.getValue()))
                    .settings(controller.getConfig().settings().withMaxTime(Math.round(timeLimit.getValue())))
                    .seed(Long.parseLong(seed.getText().trim()))
                    .rules(new RaceRules(Math.round(fuelAutonomy.getValue()), tyreTemperature.isSelected(),
                            round(tyreWear.getValue(), 2), controller.getConfig().rules().tyreHeating()))
                    .build();

            if (!controller.applyConfig(candidate)) {
                controller.reset();
            }

            onReset.run();
            status.accept("Restarted with a new population (" + candidate.architectureLabel() + ")");
        } catch (NumberFormatException e) {
            status.accept("Not applied: the seed must be a whole number.");
        } catch (IllegalArgumentException e) {
            status.accept("Not applied: " + e.getMessage());
        }
    }

    /**
     * "12-8" → {12, 8}; empty → no hidden layer.
     */
    static int[] parseHiddenLayers(String text) {
        if (text == null || text.isBlank()) {
            return new int[0];
        }

        try {
            return Arrays.stream(text.trim().split("\\s*[-,]\\s*")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Hidden layers: whole numbers separated by '-', e.g. 12-8.");
        }
    }

    private void onChange(Slider... sliders) {
        for (Slider slider : sliders) {
            slider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
                if (!changing) {
                    applyHot();
                }
            });
            slider.valueProperty().addListener((obs, old, value) -> {
                if (!slider.isValueChanging()) {
                    applyHot();
                }
            });
        }
    }

    private static Slider slider(double min, double max, double value, int decimals) {
        Slider slider = new Slider(min, max, value);
        slider.setPrefWidth(140);

        if (decimals == 0) {
            slider.valueProperty().addListener((obs, old, v) -> slider.setValue(Math.round(v.doubleValue())));
        }

        return slider;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        return grid;
    }

    private static void row(GridPane grid, int row, String label, Node node, String help) {
        Label name = new Label(label);

        if (help != null) {
            Tooltip tooltip = new Tooltip(help);
            name.setTooltip(tooltip);
            Tooltip.install(node, tooltip);
        }

        grid.add(name, 0, row);

        if (node instanceof Slider slider) {
            Label value = new Label();
            value.setMinWidth(40);
            value.textProperty().bind(slider.valueProperty().asString(Locale.ROOT,
                    slider.getMax() > 10 ? "%.0f" : "%.2f"));
            grid.add(new HBox(4, slider, value), 1, row);
        } else {
            grid.add(node, 1, row);
        }
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
