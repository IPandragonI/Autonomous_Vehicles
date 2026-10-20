package org.autonomous_vehicles.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.StatsHistory;
import org.autonomous_vehicles.sim.DeathCause;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Progress" tab: six headline numbers, what the cars of the current generation are doing, the lap records of
 * the run, then score, best lap and progress over the generations.
 */
public class StatsPanel extends VBox {

    private static final String CARD_STYLE = "-fx-background-color: #f3f4f6; -fx-background-radius: 8;";
    private static final String RECORD_STYLE = "-fx-background-color: #fde68a; -fx-background-radius: 8;"
            + " -fx-border-color: #f59e0b; -fx-border-radius: 8; -fx-border-width: 2;";
    private static final int RECORDS_SHOWN = 5;

    private final Label generation = new Label("–");
    private final Label generationNote = new Label();
    private final Label bestFitness = new Label("–");
    private final Label bestFitnessNote = new Label();
    private final Label meanFitness = new Label("–");
    private final Label meanFitnessNote = new Label();
    private final Label carsWithLap = new Label("–");
    private final Label carsWithLapNote = new Label();
    private final Label bestLap = new Label("–");
    private final Label bestLapNote = new Label();
    private final Label diversity = new Label("–");
    private final Label diversityNote = new Label();
    private final Label live = new Label();
    private final Label records = new Label();
    private final VBox bestLapCard;
    private final PauseTransition recordFlash = new PauseTransition(Duration.seconds(3));

    private final XYChart.Series<Number, Number> bestSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> lapSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> progressSeries = new XYChart.Series<>();

    public StatsPanel() {
        setSpacing(8);
        setPadding(new Insets(8));

        GridPane cards = new GridPane(8, 8);
        bestLapCard = card("Best lap (record)", bestLap, bestLapNote);
        cards.add(card("Generation", generation, generationNote), 0, 0);
        cards.add(card("Best score ever", bestFitness, bestFitnessNote), 1, 0);
        cards.add(card("Average score", meanFitness, meanFitnessNote), 2, 0);
        cards.add(card("Cars with a lap", carsWithLap, carsWithLapNote), 0, 1);
        cards.add(bestLapCard, 1, 1);
        cards.add(card("Genome diversity", diversity, diversityNote), 2, 1);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            column.setHgrow(Priority.ALWAYS);
            cards.getColumnConstraints().add(column);
        }

        recordFlash.setOnFinished(event -> bestLapCard.setStyle(CARD_STYLE));

        live.setWrapText(true);
        records.setWrapText(true);
        records.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");

        bestSeries.setName("best car");
        meanSeries.setName("average");
        lapSeries.setName("best lap (s)");
        progressSeries.setName("best car (checkpoints)");

        LineChart<Number, Number> fitnessChart = chart("Score per generation");
        fitnessChart.getData().add(bestSeries);
        fitnessChart.getData().add(meanSeries);

        LineChart<Number, Number> lapChart = chart("Best lap time per generation");
        lapChart.getData().add(lapSeries);

        LineChart<Number, Number> progressChart = chart("Furthest progress per generation");
        progressChart.getData().add(progressSeries);

        Label hint = new Label("Each generation ends when every car has stopped; the next one is bred from the best. "
                + "Diversity = mean distance of the genomes to their average: near 0, every car drives alike and "
                + "only mutations explore.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #57606a;");

        getChildren().addAll(cards, section("This generation"), live, section("Lap records of the run"), records,
                fitnessChart, lapChart, progressChart, hint);
    }

    private static VBox card(String title, Label value, Label note) {
        Label name = new Label(title);
        name.setStyle("-fx-text-fill: #57606a; -fx-font-size: 11px;");
        value.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        note.setStyle("-fx-text-fill: #57606a; -fx-font-size: 10px;");
        VBox card = new VBox(1, name, value, note);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(6, 8, 6, 8));
        card.setStyle(CARD_STYLE);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label section(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 4 0 0 0;");
        return label;
    }

    private static LineChart<Number, Number> chart(String title) {
        NumberAxis x = new NumberAxis();
        x.setLabel("generation");
        x.setForceZeroInRange(false);
        x.setMinorTickVisible(false);
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(200);
        return chart;
    }

    /**
     * Refreshes the numbers (called every image; labels only redraw when their text changes).
     */
    public void refresh(SimulationController controller) {
        StatsHistory history = controller.getHistory();
        GenerationStats last = history.last();
        int population = controller.getConfig().populationSize();

        generation.setText(String.valueOf(controller.getGeneration()));
        generationNote.setText(controller.isReplaying() ? "training paused (replay)"
                : history.size() + " evaluated · " + population + " cars");
        live.setText(liveLine(controller.getDeathCounts()));

        double record = controller.getLapRecord();
        List<LapRecord> all = controller.getLapRecords();
        bestLap.setText(Double.isNaN(record) ? "–" : Dashboard.lapTime(record));
        bestLapNote.setText(all.isEmpty() ? "no lap yet" : "gen " + all.getLast().generation()
                + " · " + all.size() + " record" + (all.size() > 1 ? "s" : ""));
        records.setText(recordLines(all));

        if (last == null) {
            for (Label label : List.of(bestFitness, meanFitness, carsWithLap, diversity)) {
                label.setText("–");
            }

            bestFitnessNote.setText("after generation 0");
            meanFitnessNote.setText("");
            carsWithLapNote.setText("");
            diversityNote.setText("");
            return;
        }

        bestFitness.setText(String.format(Locale.ROOT, "%.1f", history.bestFitnessEver()));
        bestFitnessNote.setText(String.format(Locale.ROOT, "last generation %.1f", last.bestFitness()));
        meanFitness.setText(String.format(Locale.ROOT, "%.1f", last.meanFitness()));
        meanFitnessNote.setText(String.format(Locale.ROOT, "± %.1f (spread)", last.fitnessStd()));
        carsWithLap.setText(last.lapsCompleted() + " / " + population);
        int firstLap = history.firstLapGeneration();
        carsWithLapNote.setText(firstLap < 0 ? "no lap yet" : "first lap: generation " + firstLap);
        diversity.setText(String.format(Locale.ROOT, "%.2f", last.genomeDiversity()));
        diversityNote.setText(String.format(Locale.ROOT, "reach %.1f checkpoints", last.maxProgress()));
    }

    /** "12 driving · 30 crashed · 5 stuck · …", leaving out the causes nobody died of. */
    static String liveLine(Map<DeathCause, Integer> counts) {
        StringBuilder line = new StringBuilder(counts.getOrDefault(DeathCause.ALIVE, 0) + " driving");

        for (DeathCause cause : DeathCause.values()) {
            int count = counts.getOrDefault(cause, 0);

            if (cause != DeathCause.ALIVE && count > 0) {
                line.append(" · ").append(count).append(' ').append(switch (cause) {
                    case CRASHED -> "crashed";
                    case STAGNATED -> "stuck";
                    case TIMEOUT -> "out of time";
                    case FINISHED -> "finished";
                    case OUT_OF_FUEL -> "out of fuel";
                    case ALIVE -> "driving";
                });
            }
        }

        return line.toString();
    }

    /** The latest records first, one per line. */
    static String recordLines(List<LapRecord> all) {
        if (all.isEmpty()) {
            return "No lap completed yet.";
        }

        StringBuilder lines = new StringBuilder();
        int oldestShown = Math.max(0, all.size() - RECORDS_SHOWN);

        for (int i = all.size() - 1; i >= oldestShown; i--) {
            LapRecord record = all.get(i);
            lines.append(String.format(Locale.ROOT, "%-8s gen %-4d %s", Dashboard.lapTime(record.lapTime()),
                    record.generation(), record.isFirstLap() ? "first lap"
                            : String.format(Locale.ROOT, "−%.2f s", record.gain())));

            if (i > oldestShown) {
                lines.append('\n');
            }
        }

        return lines.toString();
    }

    /**
     * Highlights the record card for a few seconds.
     */
    public void flashRecord() {
        bestLapCard.setStyle(RECORD_STYLE);
        recordFlash.playFromStart();
    }

    public void addGeneration(GenerationStats stats) {
        bestSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.bestFitness()));
        meanSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.meanFitness()));
        progressSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.maxProgress()));

        if (!Double.isNaN(stats.bestLapTime())) {
            lapSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.bestLapTime()));
        }
    }

    public void reload(StatsHistory history) {
        bestSeries.getData().clear();
        meanSeries.getData().clear();
        lapSeries.getData().clear();
        progressSeries.getData().clear();
        history.all().forEach(this::addGeneration);
    }
}
