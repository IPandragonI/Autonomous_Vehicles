package org.autonomous_vehicles.evolution;

/**
 * Descriptive statistics used by the generation statistics and the experiments.
 */
public final class Statistics {

    private Statistics() {
    }

    public static double mean(double[] values) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }

        double sum = 0.0;

        for (double value : values) {
            sum += value;
        }

        return sum / values.length;
    }

    /**
     * Population standard deviation (divides by n): spread of the values themselves.
     */
    public static double populationStd(double[] values) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }

        return Math.sqrt(sumOfSquaredDeviations(values) / values.length);
    }

    /**
     * Sample standard deviation (divides by n - 1): estimate across repeated runs (seeds). 0 for a single value.
     */
    public static double sampleStd(double[] values) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }

        if (values.length == 1) {
            return 0.0;
        }

        return Math.sqrt(sumOfSquaredDeviations(values) / (values.length - 1));
    }

    private static double sumOfSquaredDeviations(double[] values) {
        double mean = mean(values);
        double sum = 0.0;

        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }

        return sum;
    }

    public static double min(double[] values) {
        double min = Double.NaN;

        for (double value : values) {
            if (!Double.isNaN(value) && (Double.isNaN(min) || value < min)) {
                min = value;
            }
        }

        return min;
    }

    public static double max(double[] values) {
        double max = Double.NaN;

        for (double value : values) {
            if (!Double.isNaN(value) && (Double.isNaN(max) || value > max)) {
                max = value;
            }
        }

        return max;
    }
}
