# Experiments A to F: measured results

Track `hairpin`, 50 generations, 10 seeds per variant (1..10), population 100, base: SimulationConfig[5-6-2, population=100, mutation=0.08/0.25 GAUSSIAN, selection=Tournament k=3, elites=5, crossover=NONE, fitness=COMPOSITE, tracks=[hairpin], seed=42]. Mean ± sample standard deviation over the seeds; first-lap and lap-time columns only use the runs that completed a lap.

| Variant | Runs with a lap | First lap (generation) | Best lap time (s) | Final best fitness | Final mean fitness | Final fitness std | Final genome diversity | Best-fitness regressions |
|---|---|---|---|---|---|---|---|---|
| Base (tournament k=3, 8%/0.25) | 10/10 | 8.0 ± 3.9 | 51.01 ± 0.09 | 46.97 ± 0.06 | 34.53 ± 1.68 | 19.80 | 1.20 | 0.0 |
| A: weak mutation 2%/0.05 | 5/10 | 26.4 ± 8.6 | 53.87 ± 0.95 | 25.75 ± 20.91 | 24.96 ± 20.69 | 2.61 | 0.10 | 0.0 |
| B: strong mutation 30%/0.8 | 10/10 | 2.1 ± 0.7 | 50.67 ± 0.03 | 47.27 ± 0.02 | 31.83 ± 2.42 | 20.82 | 9.70 | 0.0 |
| C: truncation 10%, 5 elites | 10/10 | 6.2 ± 5.8 | 50.90 ± 0.09 | 47.08 ± 0.07 | 28.57 ± 2.47 | 21.90 | 0.67 | 0.0 |
| C: truncation 10%, no elitism | 10/10 | 5.1 ± 4.0 | 50.91 ± 0.12 | 47.07 ± 0.07 | 27.34 ± 2.39 | 22.10 | 0.83 | 8.1 |
| D: tournament k=2 | 10/10 | 7.6 ± 3.4 | 51.17 ± 0.15 | 46.86 ± 0.12 | 36.75 ± 1.52 | 18.35 | 1.44 | 0.0 |
| D: tournament k=5 | 10/10 | 6.8 ± 3.9 | 51.02 ± 0.19 | 46.99 ± 0.14 | 32.04 ± 1.99 | 21.04 | 0.94 | 0.0 |
| D: tournament k=10 | 10/10 | 6.0 ± 5.4 | 50.97 ± 0.13 | 47.05 ± 0.06 | 29.74 ± 3.05 | 21.64 | 0.78 | 0.0 |
| E: random selection | 10/10 | 13.8 ± 4.1 | 51.99 ± 0.36 | 46.41 ± 0.14 | 13.32 ± 4.31 | 19.88 | 2.55 | 0.0 |
| F: 5-12-8-2 | 10/10 | 7.9 ± 3.6 | 50.90 ± 0.09 | 47.09 ± 0.08 | 33.34 ± 1.77 | 20.43 | 3.00 | 0.0 |
