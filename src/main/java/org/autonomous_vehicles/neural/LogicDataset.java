package org.autonomous_vehicles.neural;

public record LogicDataset(double[][] inputs, double[] targets) {
    public static LogicDataset and() {
        double[][] inputs = {
                {0, 0},
                {0, 1},
                {1, 0},
                {1, 1}
        };
        double[] targets = {0, 0, 0, 1};
        return new LogicDataset(inputs, targets);
    }

    public static LogicDataset or() {
        double[][] inputs = {
                {0, 0},
                {0, 1},
                {1, 0},
                {1, 1}
        };
        double[] targets = {0, 1, 1, 1};
        return new LogicDataset(inputs, targets);
    }

    public static LogicDataset xor() {
        double[][] inputs = {
                {0, 0},
                {0, 1},
                {1, 0},
                {1, 1}
        };
        double[] targets = {0, 1, 1, 0};
        return new LogicDataset(inputs, targets);
    }
}
