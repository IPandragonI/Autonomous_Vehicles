package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.SimulationSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExperimentRunnerTest {

    private static List<ExperimentRunner.Variant> variants() {
        SimulationConfig base = SimulationConfig.defaults().toBuilder().populationSize(12).eliteCount(1)
                .settings(SimulationSettings.defaults().withMaxTime(15)).build();

        return List.of(new ExperimentRunner.Variant("X", "base", base),
                new ExperimentRunner.Variant("X", "strong mutation", base.toBuilder().mutationRate(0.3).build()));
    }

    @Test
    public void testSameSeedsGiveSameResultsWhateverTheThreads() {
        List<ExperimentRunner.RunResult> sequential = new ExperimentRunner(3, List.of(1L, 2L), 1).run(variants());
        List<ExperimentRunner.RunResult> parallel = new ExperimentRunner(3, List.of(1L, 2L), 4).run(variants());

        assertEquals(4, sequential.size());

        for (int i = 0; i < sequential.size(); i++) {
            assertEquals(sequential.get(i).seed(), parallel.get(i).seed());
            assertEquals(sequential.get(i).generations(), parallel.get(i).generations());
        }
    }

    @Test
    public void testStatisticsOnKnownData() {
        double[] values = {2, 4, 4, 4, 5, 5, 7, 9};

        assertEquals(5.0, Statistics.mean(values), 1e-12);
        assertEquals(2.0, Statistics.populationStd(values), 1e-12);
        assertEquals(Math.sqrt(32.0 / 7.0), Statistics.sampleStd(values), 1e-12);
        assertEquals(0.0, Statistics.sampleStd(new double[]{3}), 0.0);
        assertTrue(Double.isNaN(Statistics.mean(new double[0])));
        assertEquals(2.0, Statistics.min(new double[]{Double.NaN, 4, 2}), 0.0);
    }

    @Test
    public void testSummaryOnKnownRuns() {
        ExperimentRunner.Variant variant = variants().getFirst();
        ExperimentRunner.RunResult lapAt1 = new ExperimentRunner.RunResult(variant, 1, List.of(
                new GenerationStats(0, 2, 1, 0.5, 3, 30, Double.NaN, 0, 3),
                new GenerationStats(1, 8, 3, 0.5, 17, 170, 30.0, 2, 2)));
        ExperimentRunner.RunResult lapAt0 = new ExperimentRunner.RunResult(variant, 2, List.of(
                new GenerationStats(0, 6, 2, 0.5, 17, 170, 26.0, 1, 3),
                new GenerationStats(1, 4, 5, 0.5, 17, 170, 24.0, 3, 1)));

        ExperimentRunner.Summary summary = ExperimentRunner.summarize(List.of(lapAt1, lapAt0)).getFirst();

        assertEquals(2, summary.runs());
        assertEquals(2, summary.runsWithLap());
        assertEquals(0.5, summary.firstLapMean(), 1e-12);
        assertEquals(Math.sqrt(0.5), summary.firstLapStd(), 1e-12);
        assertEquals(27.0, summary.bestLapMean(), 1e-12);
        assertEquals(6.0, summary.finalBestMean(), 1e-12);
        assertEquals(4.0, summary.finalMeanMean(), 1e-12);
        assertEquals(0.5, summary.regressionsMean(), 1e-12, "Run 2 regressed once (6 → 4).");
    }

    @Test
    public void testCsvExport() throws IOException {
        List<ExperimentRunner.RunResult> results = new ExperimentRunner(2, List.of(1L), 1).run(variants());
        Path file = Files.createTempFile("results", ".csv");

        ExperimentRunner.writeCsv(results, file);
        List<String> lines = Files.readAllLines(file);

        assertTrue(lines.getFirst().startsWith("experiment,variant,seed,generation,best_fitness"));
        assertEquals(1 + 2 * 2, lines.size(), "Header + 2 variants × 2 generations.");
        assertTrue(lines.get(1).startsWith("X,base,1,0,"));
        Files.deleteIfExists(file);
    }

    @Test
    public void testExperimentVariantsChangeOneParameterEach() {
        SimulationConfig base = Experiments.base();

        for (ExperimentRunner.Variant variant : Experiments.variants()) {
            SimulationConfig config = variant.config();

            if (!variant.experiment().equals("F")) {
                assertArrayEquals(base.architecture(), config.architecture(), variant.label());
            }

            assertEquals(base.populationSize(), config.populationSize(), variant.label());
            assertEquals(base.tracks(), config.tracks(), variant.label());
            assertEquals(base.compositeFitness(), config.compositeFitness(), variant.label());
        }
    }
}
