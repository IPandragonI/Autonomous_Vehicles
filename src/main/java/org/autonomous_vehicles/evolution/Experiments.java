package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.neural.NetworkIO;
import org.autonomous_vehicles.neural.NeuralNetwork;
import org.autonomous_vehicles.sim.AgentResult;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Tracks;
import org.autonomous_vehicles.util.SvgChart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.LongStream;

/**
 * Headless experiments of phases 7 and 8 (command line, no JavaFX):
 * <ul>
 *     <li>{@code abcdef}: experiments A to F of step 37 against a common base configuration;</li>
 *     <li>{@code generalization}: champions trained on one or several tracks, tested on the others (step 34);</li>
 *     <li>{@code analysis}: one champion's genome at generations 0, 10 and 50 (step 38).</li>
 * </ul>
 * Usage: {@code Experiments <abcdef|generalization|analysis|all> [outputDir] [generations] [seeds] [threads]}
 */
public final class Experiments {

    public static final String EXPERIMENT_TRACK = "hairpin";

    private Experiments() {
    }

    /**
     * Base configuration shared by every experiment; each variant changes exactly one parameter.
     */
    public static SimulationConfig base() {
        return SimulationConfig.defaults().toBuilder().tracks(EXPERIMENT_TRACK).build();
    }

    public static List<ExperimentRunner.Variant> variants() {
        SimulationConfig base = base();
        List<ExperimentRunner.Variant> variants = new ArrayList<>();

        variants.add(new ExperimentRunner.Variant("Base", "Base (tournament k=3, 8%/0.25)", base));
        variants.add(new ExperimentRunner.Variant("A", "A: weak mutation 2%/0.05",
                base.toBuilder().mutationRate(0.02).mutationStrength(0.05).build()));
        variants.add(new ExperimentRunner.Variant("B", "B: strong mutation 30%/0.8",
                base.toBuilder().mutationRate(0.30).mutationStrength(0.8).build()));
        variants.add(new ExperimentRunner.Variant("C", "C: truncation 10%, 5 elites",
                base.toBuilder().selection(SelectionType.TRUNCATION).truncationFraction(0.10).build()));
        variants.add(new ExperimentRunner.Variant("C", "C: truncation 10%, no elitism",
                base.toBuilder().selection(SelectionType.TRUNCATION).truncationFraction(0.10).eliteCount(0).build()));

        for (int k : new int[]{2, 5, 10}) {
            variants.add(new ExperimentRunner.Variant("D", "D: tournament k=" + k, base.toBuilder().tournamentSize(k).build()));
        }

        variants.add(new ExperimentRunner.Variant("E", "E: random selection",
                base.toBuilder().selection(SelectionType.RANDOM).build()));
        variants.add(new ExperimentRunner.Variant("F", "F: 5-12-8-2", base.toBuilder().hiddenLayers(12, 8).build()));

        return variants;
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "all";
        Path output = Path.of(args.length > 1 ? args[1] : "docs/experiments");
        int generations = args.length > 2 ? Integer.parseInt(args[2]) : 50;
        int seeds = args.length > 3 ? Integer.parseInt(args[3]) : 10;
        int threads = args.length > 4 ? Integer.parseInt(args[4]) : Runtime.getRuntime().availableProcessors();

        switch (mode) {
            case "abcdef" -> runAbcdef(output, generations, seeds, threads);
            case "generalization" -> runGeneralization(output, generations);
            case "analysis" -> runAnalysis(output);
            case "all" -> {
                runAbcdef(output, generations, seeds, threads);
                runGeneralization(output, generations);
                runAnalysis(output);
            }
            default -> throw new IllegalArgumentException("Unknown mode " + mode);
        }
    }

    static void runAbcdef(Path output, int generations, int seedCount, int threads) throws IOException {
        List<Long> seeds = LongStream.rangeClosed(1, seedCount).boxed().toList();
        ExperimentRunner runner = new ExperimentRunner(generations, seeds, threads);
        long start = System.nanoTime();
        List<ExperimentRunner.RunResult> results = runner.run(variants());
        System.out.printf(Locale.ROOT, "%d runs in %.1f s\n", results.size(), (System.nanoTime() - start) / 1e9);

        List<ExperimentRunner.Summary> summaries = ExperimentRunner.summarize(results);
        ExperimentRunner.writeCsv(results, output.resolve("results.csv"));
        ExperimentRunner.writeSummaryCsv(summaries, output.resolve("summary.csv"));

        Map<String, List<ExperimentRunner.RunResult>> byLabel = new LinkedHashMap<>();

        for (ExperimentRunner.RunResult result : results) {
            byLabel.computeIfAbsent(result.variant().label(), key -> new ArrayList<>()).add(result);
        }

        for (String experiment : List.of("A", "B", "C", "D", "E", "F")) {
            Map<String, List<ExperimentRunner.RunResult>> selected = new LinkedHashMap<>();

            byLabel.forEach((label, runs) -> {
                String group = runs.getFirst().variant().experiment();

                if (group.equals("Base") || group.equals(experiment)) {
                    selected.put(label, runs);
                }
            });

            chart(output.resolve(experiment + "_best_fitness.svg"), "Experiment " + experiment + ": best fitness (mean of "
                    + seedCount + " seeds)", "Best fitness", selected, GenerationStats::bestFitness);
            chart(output.resolve(experiment + "_mean_fitness.svg"), "Experiment " + experiment + ": mean fitness",
                    "Mean fitness", selected, GenerationStats::meanFitness);
            chart(output.resolve(experiment + "_diversity.svg"), "Experiment " + experiment + ": genome diversity",
                    "Mean distance to centroid", selected, GenerationStats::genomeDiversity);
            chart(output.resolve(experiment + "_laps.svg"), "Experiment " + experiment + ": cars completing a lap",
                    "Cars with >= 1 lap", selected, stats -> stats.lapsCompleted());
        }

        Map<String, double[]> lapTimes = new LinkedHashMap<>();

        for (ExperimentRunner.RunResult run : byLabel.values().iterator().next()) {
            double[] curve = new double[run.generations().size()];
            double best = Double.NaN;

            for (int g = 0; g < curve.length; g++) {
                double lap = run.generations().get(g).bestLapTime();
                best = Double.isNaN(best) || lap < best ? (Double.isNaN(lap) ? best : lap) : best;
                curve[g] = best;
            }

            if (lapTimes.size() < 8) {
                lapTimes.put("seed " + run.seed(), curve);
            }
        }

        SvgChart.write(output.resolve("base_best_lap_time.svg"), "Base: best lap time so far (one line per seed)",
                "Generation", "Lap time (s)", lapTimes);

        writeSummaryMarkdown(output.resolve("summary.md"), summaries, generations, seedCount);
    }

    private static void chart(Path path, String title, String yLabel, Map<String, List<ExperimentRunner.RunResult>> runs,
                              ToDoubleFunction<GenerationStats> metric) throws IOException {
        Map<String, double[]> series = new LinkedHashMap<>();
        runs.forEach((label, results) -> series.put(label, ExperimentRunner.meanCurve(results, metric)));
        SvgChart.write(path, title, "Generation", yLabel, series);
    }

    private static void writeSummaryMarkdown(Path path, List<ExperimentRunner.Summary> summaries, int generations,
                                             int seeds) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Experiments A to F: measured results\n\n");
        md.append(String.format(Locale.ROOT, "Track `%s`, %d generations, %d seeds per variant (1..%d), population %d, "
                        + "base: %s. Mean ± sample standard deviation over the seeds; first-lap and lap-time columns "
                        + "only use the runs that completed a lap.\n\n", EXPERIMENT_TRACK, generations, seeds, seeds,
                base().populationSize(), base()));
        md.append("| Variant | Runs with a lap | First lap (generation) | Best lap time (s) | Final best fitness "
                + "| Final mean fitness | Final fitness std | Final genome diversity | Best-fitness regressions |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");

        for (ExperimentRunner.Summary s : summaries) {
            md.append(String.format(Locale.ROOT, "| %s | %d/%d | %s | %s | %.2f ± %.2f | %.2f ± %.2f | %.2f | %.2f | %.1f |\n",
                    s.variant(), s.runsWithLap(), s.runs(), pm(s.firstLapMean(), s.firstLapStd(), 1),
                    pm(s.bestLapMean(), s.bestLapStd(), 2), s.finalBestMean(), s.finalBestStd(), s.finalMeanMean(),
                    s.finalMeanStd(), s.finalFitnessStdMean(), s.finalDiversityMean(), s.regressionsMean()));
        }

        Files.createDirectories(path.getParent());
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
    }

    private static String pm(double mean, double std, int decimals) {
        if (Double.isNaN(mean)) {
            return "-";
        }

        return String.format(Locale.ROOT, "%." + decimals + "f ± %." + decimals + "f", mean, std);
    }

    /**
     * Trains on the oval only and on oval + chicane, then tests both champions on every built-in track.
     */
    static void runGeneralization(Path output, int generations) throws IOException {
        Map<String, List<String>> trainings = new LinkedHashMap<>();
        trainings.put("oval", List.of("oval"));
        trainings.put("oval + chicane", List.of("oval", "chicane"));
        trainings.put("hairpin", List.of("hairpin"));

        StringBuilder md = new StringBuilder("# Generalization: champions tested on other tracks\n\n");
        md.append(String.format(Locale.ROOT, "%d generations, base configuration, seed 42. Progress is in checkpoints; "
                + "a lap of the oval has 16, the chicane 30, the hairpin 36.\n\n", generations));
        md.append("| Trained on | Tested on | Progress | Laps | End | Fitness |\n|---|---|---|---|---|---|\n");

        for (Map.Entry<String, List<String>> training : trainings.entrySet()) {
            SimulationConfig config = base().toBuilder().tracks(training.getValue()).seed(42).build();
            GeneticAlgorithm algorithm = new GeneticAlgorithm(config);
            algorithm.run(generations);
            NeuralNetwork champion = algorithm.getChampion().getNetwork();

            for (String testName : Tracks.BUILT_IN) {
                Track test = Tracks.byName(testName);
                Evaluator evaluator = new Evaluator(List.of(test), config.createSensor(), config.useSpeedInput(),
                        config.settings(), config.createFitness());
                Population single = new Population(List.of(new Individual(champion.copy())));
                evaluator.evaluate(single);
                AgentResult result = single.get(0).getPrimaryResult();
                md.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %d | %s | %.2f |\n", training.getKey(), testName,
                        result.progress(), result.laps(), result.deathCause(), single.get(0).getFitness()));
            }
        }

        Files.createDirectories(output);
        Files.writeString(output.resolve("generalization.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.print(md);
    }

    /**
     * Follows the champion of one run on the experiment track: genomes at generations 0, 10 and 50,
     * distances, weight histograms and responses to typical sensor situations.
     */
    static void runAnalysis(Path output) throws IOException {
        int[] milestones = {0, 10, 50};
        GeneticAlgorithm algorithm = new GeneticAlgorithm(base().toBuilder().seed(42).build());
        algorithm.run(milestones[milestones.length - 1] + 1);

        Path championDirectory = output.resolve("champions");
        Files.createDirectories(championDirectory);
        Map<Integer, double[]> genomes = algorithm.getChampionGenomes();
        Map<Integer, NeuralNetwork> networks = new LinkedHashMap<>();

        for (int generation : milestones) {
            NeuralNetwork network = algorithm.getPopulation().get(0).getNetwork().copy();
            network.setGenome(genomes.get(generation));
            networks.put(generation, network);
            NetworkIO.save(network, championDirectory.resolve(String.format("champion_gen_%03d.txt", generation)));
        }

        StringBuilder md = new StringBuilder("# Reading a champion and its history\n\n");
        md.append("Run: base configuration on `" + EXPERIMENT_TRACK + "`, seed 42. The champion of a generation is its "
                + "best individual. Genomes are saved in `champions/` (NetworkIO format).\n\n");
        md.append("## Fitness of the champions\n\n| Generation | Best fitness | Mean fitness | Max progress | Best lap (s) | Cars with a lap |\n|---|---|---|---|---|---|\n");

        for (int generation : milestones) {
            GenerationStats stats = algorithm.getHistory().all().get(generation);
            md.append(String.format(Locale.ROOT, "| %d | %.2f | %.2f | %.2f | %s | %d |\n", generation, stats.bestFitness(),
                    stats.meanFitness(), stats.maxProgress(),
                    Double.isNaN(stats.bestLapTime()) ? "-" : String.format(Locale.ROOT, "%.2f", stats.bestLapTime()),
                    stats.lapsCompleted()));
        }

        md.append("\n## Distances between champion genomes\n\n| Pair | Euclidean distance | Mean abs. change per weight | Cosine similarity |\n|---|---|---|---|\n");

        for (int[] pair : new int[][]{{0, 10}, {10, 50}, {0, 50}}) {
            double[] a = genomes.get(pair[0]);
            double[] b = genomes.get(pair[1]);
            md.append(String.format(Locale.ROOT, "| %d → %d | %.3f | %.4f | %.3f |\n", pair[0], pair[1],
                    GenomeAnalysis.distance(a, b), GenomeAnalysis.meanAbsoluteDifference(a, b),
                    GenomeAnalysis.cosineSimilarity(a, b)));
        }

        md.append("\nDistance between consecutive champions (generation g-1 → g):\n\n```\n");

        for (int g = 1; g <= milestones[milestones.length - 1]; g++) {
            md.append(String.format(Locale.ROOT, "%2d: %.3f\n", g, GenomeAnalysis.distance(genomes.get(g - 1), genomes.get(g))));
        }

        md.append("```\n\n## Weight histograms\n\n");

        for (int generation : milestones) {
            double[] genome = genomes.get(generation);
            md.append(String.format(Locale.ROOT, "Generation %d (min %.2f, max %.2f, mean |w| %.3f):\n\n```\n%s```\n\n",
                    generation, Statistics.min(genome), Statistics.max(genome), meanAbs(genome),
                    GenomeAnalysis.asciiHistogram(genome, 12, -3.0, 3.0)));
        }

        md.append("## What the networks answer to typical situations\n\nRays are ordered right (-90°) → left (+90°). "
                + "steer > 0 turns left, throttle > 0 accelerates.\n\n| Situation | Inputs |");

        for (int generation : milestones) {
            md.append(" Gen ").append(generation).append(" steer / throttle |");
        }

        md.append("\n|---|---|").append("---|".repeat(milestones.length)).append("\n");
        List<List<GenomeAnalysis.Probe>> probes = new ArrayList<>();

        for (int generation : milestones) {
            probes.add(GenomeAnalysis.probe(networks.get(generation)));
        }

        for (int i = 0; i < probes.getFirst().size(); i++) {
            GenomeAnalysis.Probe first = probes.getFirst().get(i);
            md.append("| ").append(first.situation()).append(" | ").append(Arrays.toString(first.inputs())).append(" |");

            for (List<GenomeAnalysis.Probe> generationProbes : probes) {
                GenomeAnalysis.Probe probe = generationProbes.get(i);
                md.append(String.format(Locale.ROOT, " %+.2f / %+.2f |", probe.steer(), probe.throttle()));
            }

            md.append("\n");
        }

        Files.writeString(output.resolve("analysis.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.print(md);
    }

    private static double meanAbs(double[] values) {
        double sum = 0.0;

        for (double value : values) {
            sum += Math.abs(value);
        }

        return sum / values.length;
    }
}
