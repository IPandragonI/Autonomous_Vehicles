package org.autonomous_vehicles.evolution;

import org.autonomous_vehicles.sim.TestDrivers;
import org.autonomous_vehicles.util.SvgChart;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GenomeAnalysisTest {

    @Test
    public void testDistances() {
        double[] a = {0, 0, 0};
        double[] b = {1, 2, 2};

        assertEquals(3.0, GenomeAnalysis.distance(a, b), 1e-12);
        assertEquals(5.0 / 3.0, GenomeAnalysis.meanAbsoluteDifference(a, b), 1e-12);
        assertEquals(1.0, GenomeAnalysis.cosineSimilarity(b, new double[]{2, 4, 4}), 1e-12);
        assertEquals(0.0, GenomeAnalysis.cosineSimilarity(new double[]{1, 0}, new double[]{0, 1}), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> GenomeAnalysis.distance(a, new double[2]));
    }

    @Test
    public void testHistogram() {
        int[] counts = GenomeAnalysis.histogram(new double[]{-5, -0.9, -0.1, 0.1, 0.2, 0.9, 7}, 4, -1, 1);

        assertArrayEquals(new int[]{2, 1, 2, 2}, counts);
        assertTrue(GenomeAnalysis.asciiHistogram(new double[]{0.1, 0.2}, 2, 0, 1).contains("#"));
    }

    @Test
    public void testProbeShowsWhatTheFollowerLearned() {
        List<GenomeAnalysis.Probe> probes = GenomeAnalysis.probe(TestDrivers.follower());

        assertEquals(6, probes.size());

        for (GenomeAnalysis.Probe probe : probes) {
            assertTrue(Math.abs(probe.steer()) <= 1.0 && Math.abs(probe.throttle()) <= 1.0);
        }

        assertTrue(probes.get(1).steer() > 0, "Close to the right wall: steer left.");
        assertTrue(probes.get(2).steer() < 0, "Close to the left wall: steer right.");
        assertEquals(0.0, probes.get(0).steer(), 1e-12, "Centred: go straight.");
    }

    @Test
    public void testSvgChartDrawsOneLinePerSeriesAndBreaksOnNaN() {
        Map<String, double[]> series = new LinkedHashMap<>();
        series.put("a", new double[]{1, 2, 3});
        series.put("b < c", new double[]{1, Double.NaN, 2, 4});

        String svg = SvgChart.render("Title", "Generation", "Fitness", series);

        assertTrue(svg.startsWith("<svg"));
        assertEquals(2, svg.split("<path ").length - 1);
        assertTrue(svg.contains("b &lt; c"));
        String secondPath = svg.split("<path d=\"")[2];
        assertEquals(2, secondPath.substring(0, secondPath.indexOf('"')).chars().filter(c -> c == 'M').count());
    }
}
