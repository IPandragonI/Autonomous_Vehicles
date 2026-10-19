package org.autonomous_vehicles.evolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs configurations headlessly, several seeds each, in parallel. Each run owns its
 * {@link GeneticAlgorithm} (and therefore its random generator), so the same seed always gives
 * the same result whatever the number of threads; results come back in submission order.
 */
public class ExperimentRunner {

    public record Variant(String experiment, String label, SimulationConfig config) {
    }

    public record RunResult(Variant variant, long seed, List<GenerationStats> generations) {

        public RunResult {
            generations = List.copyOf(generations);
        }

        /**
         * First generation with a completed lap, or -1.
         */
        public int firstLapGeneration() {
            return generations.stream().filter(stats -> stats.lapsCompleted() > 0)
                    .mapToInt(GenerationStats::generation).findFirst().orElse(-1);
        }

        public double bestLapTime() {
            return Statistics.min(generations.stream().mapToDouble(GenerationStats::bestLapTime).toArray());
        }

        public GenerationStats last() {
            return generations.getLast();
        }

        /**
         * Number of generations whose best fitness is lower than the previous generation's.
         */
        public int bestFitnessRegressions() {
            int regressions = 0;

            for (int i = 1; i < generations.size(); i++) {
                if (generations.get(i).bestFitness() < generations.get(i - 1).bestFitness() - 1e-9) {
                    regressions++;
                }
            }

            return regressions;
        }
    }

    /**
     * Mean and sample standard deviation over the seeds of one variant.
     * The first-lap and lap-time figures only use the runs that completed a lap.
     */
    public record Summary(String experiment, String variant, int runs, int runsWithLap,
                          double firstLapMean, double firstLapStd, double bestLapMean, double bestLapStd,
                          double finalBestMean, double finalBestStd, double finalMeanMean, double finalMeanStd,
                          double finalDiversityMean, double finalFitnessStdMean, double regressionsMean) {
    }

    private final int generations;
    private final List<Long> seeds;
    private final int threads;

    public ExperimentRunner(int generations, List<Long> seeds, int threads) {
        if (generations < 1 || seeds == null || seeds.isEmpty() || threads < 1) {
            throw new IllegalArgumentException("Need at least one generation, one seed and one thread.");
        }

        this.generations = generations;
        this.seeds = List.copyOf(seeds);
        this.threads = threads;
    }

    public List<RunResult> run(List<Variant> variants) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            List<Future<RunResult>> futures = new ArrayList<>();

            for (Variant variant : variants) {
                for (long seed : seeds) {
                    futures.add(executor.submit(() -> runOne(variant, seed)));
                }
            }

            List<RunResult> results = new ArrayList<>();

            for (Future<RunResult> future : futures) {
                results.add(future.get());
            }

            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Experiment interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A run failed: " + e.getCause().getMessage(), e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    public RunResult runOne(Variant variant, long seed) {
        GeneticAlgorithm algorithm = new GeneticAlgorithm(variant.config().toBuilder().seed(seed).build());
        return new RunResult(variant, seed, algorithm.run(generations));
    }

    public static List<Summary> summarize(List<RunResult> results) {
        Map<String, List<RunResult>> byVariant = new LinkedHashMap<>();

        for (RunResult result : results) {
            byVariant.computeIfAbsent(result.variant().experiment() + "\u0000" + result.variant().label(),
                    key -> new ArrayList<>()).add(result);
        }

        List<Summary> summaries = new ArrayList<>();

        for (List<RunResult> runs : byVariant.values()) {
            List<RunResult> withLap = runs.stream().filter(run -> run.firstLapGeneration() >= 0).toList();
            double[] firstLaps = withLap.stream().mapToDouble(RunResult::firstLapGeneration).toArray();
            double[] lapTimes = withLap.stream().mapToDouble(RunResult::bestLapTime).toArray();
            double[] finalBest = runs.stream().mapToDouble(run -> run.last().bestFitness()).toArray();
            double[] finalMean = runs.stream().mapToDouble(run -> run.last().meanFitness()).toArray();
            Variant variant = runs.getFirst().variant();

            summaries.add(new Summary(variant.experiment(), variant.label(), runs.size(), withLap.size(),
                    Statistics.mean(firstLaps), Statistics.sampleStd(firstLaps),
                    Statistics.mean(lapTimes), Statistics.sampleStd(lapTimes),
                    Statistics.mean(finalBest), Statistics.sampleStd(finalBest),
                    Statistics.mean(finalMean), Statistics.sampleStd(finalMean),
                    Statistics.mean(runs.stream().mapToDouble(run -> run.last().genomeDiversity()).toArray()),
                    Statistics.mean(runs.stream().mapToDouble(run -> run.last().fitnessStd()).toArray()),
                    Statistics.mean(runs.stream().mapToDouble(RunResult::bestFitnessRegressions).toArray())));
        }

        return summaries;
    }

    /**
     * Mean over the seeds of a per-generation metric, for one variant.
     */
    public static double[] meanCurve(List<RunResult> runs, java.util.function.ToDoubleFunction<GenerationStats> metric) {
        int length = runs.stream().mapToInt(run -> run.generations().size()).min().orElse(0);
        double[] curve = new double[length];

        for (int g = 0; g < length; g++) {
            final int generation = g;
            curve[g] = Statistics.mean(runs.stream().mapToDouble(run -> metric.applyAsDouble(run.generations().get(generation))).toArray());
        }

        return curve;
    }

    /**
     * One line per (variant, seed, generation).
     */
    public static void writeCsv(List<RunResult> results, Path path) throws IOException {
        StringBuilder csv = new StringBuilder("experiment,variant,seed,generation,best_fitness,mean_fitness,fitness_std,"
                + "max_progress,max_distance,best_lap_time,laps_completed,genome_diversity\n");

        for (RunResult result : results) {
            for (GenerationStats stats : result.generations()) {
                csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%.6f,%.6f,%.6f,%.4f,%.4f,%s,%d,%.6f\n",
                        quote(result.variant().experiment()), quote(result.variant().label()), result.seed(),
                        stats.generation(), stats.bestFitness(), stats.meanFitness(), stats.fitnessStd(),
                        stats.maxProgress(), stats.maxDistance(),
                        Double.isNaN(stats.bestLapTime()) ? "" : String.format(Locale.ROOT, "%.4f", stats.bestLapTime()),
                        stats.lapsCompleted(), stats.genomeDiversity()));
            }
        }

        write(path, csv.toString());
    }

    public static void writeSummaryCsv(List<Summary> summaries, Path path) throws IOException {
        StringBuilder csv = new StringBuilder("experiment,variant,runs,runs_with_lap,first_lap_mean,first_lap_std,"
                + "best_lap_mean,best_lap_std,final_best_mean,final_best_std,final_mean_mean,final_mean_std,"
                + "final_diversity_mean,final_fitness_std_mean,best_regressions_mean\n");

        for (Summary s : summaries) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f\n",
                    quote(s.experiment()), quote(s.variant()), s.runs(), s.runsWithLap(),
                    number(s.firstLapMean()), number(s.firstLapStd()), number(s.bestLapMean()), number(s.bestLapStd()),
                    s.finalBestMean(), s.finalBestStd(), s.finalMeanMean(), s.finalMeanStd(),
                    s.finalDiversityMean(), s.finalFitnessStdMean(), s.regressionsMean()));
        }

        write(path, csv.toString());
    }

    private static String number(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String quote(String text) {
        return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static void write(Path path, String content) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
