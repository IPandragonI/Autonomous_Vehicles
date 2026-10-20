package org.autonomous_vehicles.ui;

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
import org.autonomous_vehicles.evolution.CompositeFitness;
import org.autonomous_vehicles.evolution.FitnessComponent;
import org.autonomous_vehicles.evolution.MutationOperator;
import org.autonomous_vehicles.evolution.SelectionType;
import org.autonomous_vehicles.evolution.SimulationConfig;
import org.autonomous_vehicles.sim.CarBrain;
import org.autonomous_vehicles.sim.RaceRules;

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
    private final Slider timeLimit = slider(20, 900, 60, 0);
    private final Slider rayRange = slider(20, 300, 80, 0);
    private final TextField seed = new TextField();
    private final Slider fuelAutonomy = slider(0, 300, RaceRules.DEFAULT_FUEL_AUTONOMY, 0);
    private final CheckBox tyreTemperature = new CheckBox("Tyre temperature (grip window)");

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

        Button apply = new Button("Apply & restart");
        apply.setDefaultButton(true);
        apply.setOnAction(event -> applyWithReset());

        GridPane grid = grid();
        row(grid, 0, "Cars", population, "Population size.");
        row(grid, 1, "Hidden layers", hiddenLayers, "Neurons per hidden layer, e.g. 6 or 12-8.");
        grid.add(architectureLabel, 1, 2);
        grid.add(speedInput, 1, 3);
        grid.add(carStateInputs, 1, 4);
        row(grid, 5, "Time limit (s)", timeLimit, "A car is stopped after this time.");
        row(grid, 6, "Sensor range (m)", rayRange, "How far the distance rays see.");
        row(grid, 7, "Seed", seed, "Same seed = same run.");
        row(grid, 8, "Fuel (s at full power)", fuelAutonomy, "Starting fuel of grip-limited cars, in seconds of "
                + "full power; 0 = unlimited. A dry car loses drive and is out once it stops. Ignored by kinematic.");
        grid.add(tyreTemperature, 1, 9);
        Tooltip.install(tyreTemperature, new Tooltip("Tyres heat with grip use and sliding, cool in the airflow; "
                + "grip is best inside the compound's window. Ignored by the kinematic car."));
        grid.add(apply, 1, 10);

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
        timeLimit.setValue(config.settings().maxTime());
        rayRange.setValue(config.rayRange());
        seed.setText(String.valueOf(config.seed()));
        fuelAutonomy.setValue(config.rules().fuelAutonomy());
        tyreTemperature.setSelected(config.rules().tyreTemperature());
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
                    + (speedInput.isSelected() ? 1 : 0) + (carStateInputs.isSelected() ? CarBrain.CAR_STATE_INPUTS : 0));

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

    private void applyWithReset() {
        try {
            SimulationConfig candidate = hotValues(controller.getConfig())
                    .populationSize((int) Math.round(population.getValue()))
                    .useSpeedInput(speedInput.isSelected())
                    .useCarStateInputs(carStateInputs.isSelected())
                    .hiddenLayers(parseHiddenLayers(hiddenLayers.getValue()))
                    .rayRange(Math.round(rayRange.getValue()))
                    .settings(controller.getConfig().settings().withMaxTime(Math.round(timeLimit.getValue())))
                    .seed(Long.parseLong(seed.getText().trim()))
                    .rules(new RaceRules(Math.round(fuelAutonomy.getValue()), tyreTemperature.isSelected()))
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
