package org.autonomous_vehicles.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.autonomous_vehicles.evolution.GenerationStats;
import org.autonomous_vehicles.evolution.StatsHistory;

import java.util.Locale;

/**
 * "Progress" tab: three headline numbers, then score and best lap time over the generations.
 */
public class StatsPanel extends VBox {

    private final Label bestFitness = new Label("–");
    private final Label carsWithLap = new Label("–");
    private final Label bestLap = new Label("–");

    private final XYChart.Series<Number, Number> bestSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> lapSeries = new XYChart.Series<>();

    public StatsPanel() {
        setSpacing(8);
        setPadding(new Insets(8));

        HBox cards = new HBox(8, card("Best score", bestFitness), card("Cars with a lap", carsWithLap),
                card("Best lap", bestLap));

        bestSeries.setName("best car");
        meanSeries.setName("average");
        lapSeries.setName("best lap (s)");

        LineChart<Number, Number> fitnessChart = chart("Score per generation");
        fitnessChart.getData().add(bestSeries);
        fitnessChart.getData().add(meanSeries);

        LineChart<Number, Number> lapChart = chart("Best lap time per generation");
        lapChart.getData().add(lapSeries);

        Label hint = new Label("Each generation ends when every car has stopped; the next one is bred from the best.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #57606a;");

        getChildren().addAll(cards, fitnessChart, lapChart, hint);
    }

    private static VBox card(String title, Label value) {
        Label name = new Label(title);
        name.setStyle("-fx-text-fill: #57606a; -fx-font-size: 11px;");
        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        VBox card = new VBox(2, name, value);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(6, 10, 6, 10));
        card.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 8;");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
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
        chart.setPrefHeight(220);
        return chart;
    }

    /**
     * Refreshes the headline numbers (called every image).
     */
    public void refresh(SimulationController controller) {
        GenerationStats last = controller.getHistory().last();

        if (last == null) {
            bestFitness.setText("–");
            carsWithLap.setText("–");
            bestLap.setText("–");
            return;
        }

        bestFitness.setText(String.format(Locale.ROOT, "%.1f", last.bestFitness()));
        carsWithLap.setText(last.lapsCompleted() + " / " + controller.getConfig().populationSize());
        double best = controller.getHistory().bestLapTimeEver();
        bestLap.setText(Double.isNaN(best) ? "–" : Dashboard.lapTime(best));
    }

    public void addGeneration(GenerationStats stats) {
        bestSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.bestFitness()));
        meanSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.meanFitness()));

        if (!Double.isNaN(stats.bestLapTime())) {
            lapSeries.getData().add(new XYChart.Data<>(stats.generation(), stats.bestLapTime()));
        }
    }

    public void reload(StatsHistory history) {
        bestSeries.getData().clear();
        meanSeries.getData().clear();
        lapSeries.getData().clear();
        history.all().forEach(this::addGeneration);
    }
}
