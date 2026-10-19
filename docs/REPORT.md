# Final report — a car that learns to drive

All figures below come from runs in this repository (`docs/experiments/`, regenerated with
`java -cp target/classes org.autonomous_vehicles.evolution.Experiments all docs/experiments 50 10`).
Raw data: [`results.csv`](experiments/results.csv) (one line per variant × seed × generation),
[`summary.csv`](experiments/summary.csv), [`summary.md`](experiments/summary.md).

## 1. Protocol (step 36)

- **Track**: `hairpin` (36 checkpoints, two hairpins, ~51 s per lap at full speed). The oval is too easy for
  comparisons: a random generation 0 already contains cars that lap it.
- **Base configuration** (assignment §6.1): 100 cars, 5-6-2 Tanh, 5 rays, Gaussian mutation 8 % / σ = 0.25,
  tournament k = 3, 5 elites (5 %), no crossover, composite fitness with default weights, dt = 1/60 s,
  end of life = crash, 60 s, 4 s without progress or 3 laps.
- **Each variant changes one parameter**; 10 seeds (1–10) × 50 generations per variant. Means ± sample standard
  deviation over the seeds. Same seed → same result (checked by `ExperimentRunnerTest`, sequential and parallel).
- **Metrics**: first generation with a completed lap, best lap time, final best / mean fitness, final fitness standard
  deviation and genome diversity (mean distance of the genomes to their centroid), number of generations whose best
  fitness dropped ("regressions").

**Caveat on the scale.** With the 60 s limit only one hairpin lap fits in an evaluation, so the best fitness saturates
around 47 for every variant that learns. The discriminating metrics are therefore *when* the first lap happens,
the mean fitness (how good the whole population is) and the diversity. Differences smaller than about one standard
deviation should not be read as real.

| Variant | Runs with a lap | First lap (gen.) | Best lap (s) | Final best | Final mean | Final fitness std | Diversity | Regressions |
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

Curves (mean of the 10 seeds, base in every chart): `X_best_fitness.svg`, `X_mean_fitness.svg`, `X_diversity.svg`,
`X_laps.svg` for X = A…F in [`experiments/`](experiments/).

## 2. Experiments A to F (step 37)

### A — Weak mutation (2 % of the weights, σ = 0.05)
- **Hypothesis.** Children almost identical to their parents: safe but slow progress, early plateau, low diversity.
- **Observation.** Only 5 seeds out of 10 complete a lap in 50 generations, and those that do need 26.4 ± 8.6
  generations (base: 8.0). Diversity collapses to 0.10 (base 1.20) and the fitness spread inside the population to
  2.61 (base 19.80): the population is almost a set of clones. The final best is bimodal (25.75 ± 20.91): half the
  runs are stuck before the first hairpin.
- **Explanation.** With ~1 weight in 50 nudged by 0.05, a child explores a tiny neighbourhood of its parent.
  Selection quickly concentrates the population on one lineage, and that lineage cannot jump out of the local optimum
  "drive straight then crash into the first hairpin": not enough exploration.

### B — Strong mutation (30 % of the weights, σ = 0.8)
- **Hypothesis.** Children barely resemble their parents; what was learned gets destroyed; saw-toothed curves.
- **Observation.** The *best* fitness never drops (0 regressions) — the 5 elites are copied unchanged — but the
  *population* behaves as predicted: diversity 9.70 (8× base), mean fitness lower than the base (31.83 vs 34.53),
  and the mean curve is saw-toothed (seed 1: 13.9 → 11.2, 18.8 → 15.8, 27.4 → 24.4). Surprisingly, B finds the first
  lap fastest of all (2.1 ± 0.7 generations) and the best lap time (50.67 ± 0.03 s).
- **Explanation.** Most mutated children lose their parent's skill (hence the low, noisy mean), but on a 50-weight
  network the wide random search also finds good drivers quickly, and elitism acts as a ratchet that never lets them
  go. Strong mutation is only "destructive" for the champion when nothing protects it — that is what C without
  elitism shows.

### C — Selection of the best (truncation 10 %), with and without elitism
- **Hypothesis.** Strong pressure: fast rise, loss of diversity, risk of premature convergence.
- **Observation.** Fast first lap (6.2 / 5.1 generations) and the lowest diversity of all selections
  (0.67 / 0.83). Without elitism the best fitness drops 8.1 times per run on average (0 with elites): the champion is
  lost and must be found again. The mean fitness is lower than the base (28.6 / 27.3 vs 34.5).
- **Explanation.** All 95 children descend from the 10 best cars, so the population converges on one family
  (exploitation). Without elites, every champion is replaced by its mutated children, and a mutation can break it:
  progress is not kept, only re-found — this is the role of elitism.

### D — Tournament, k = 2, 5, 10
- **Hypothesis.** k sets the pressure: larger k = faster but less diverse; weaker cars keep a chance with small k.
- **Observation.** Monotonic effect of k: first lap 7.6 → 6.8 → 6.0 generations, diversity 1.44 → 0.94 → 0.78.
  k = 10 (P(best) = k/n = 10 %) behaves like truncation 10 % (first lap 6.0 vs 6.2, diversity 0.78 vs 0.67). The final
  mean fitness decreases with k (36.75 → 32.04 → 29.74).
- **Explanation.** The probability that the best car wins a tournament is k/n (measured in `SelectionTest`), so k is
  a direct pressure knob. The lower mean under high pressure is an interpretation, not verified by a dedicated
  experiment: with a strong pressure the population piles up around the current best genome, a fast but
  wall-hugging, *fragile* driver whose mutated children crash; a weaker pressure keeps a wider set of more robust parents.

### E — Random selection (control)
- **Hypothesis.** No link between score and reproduction: only elitism keeps progress.
- **Observation.** Progress still happens (10/10 runs lap, final best 46.41), but later (first lap 13.8 ± 4.1) and
  the population stays poor: final mean 13.32 vs 34.53 for the base.
- **Explanation.** The 5 elites form a ratchet: each generation, 95 children of *random* parents are random
  variations, and whenever one beats the elites it becomes an elite. This is a hill-climber kept alive by elitism;
  selection is what turns the whole population (not only the elite) into good drivers.

### F — Architectures, 5-6-2 (50 parameters) vs 5-12-8-2 (194 parameters)
- **Hypothesis.** More parameters = more expressive but a larger space to search; smaller is not necessarily worse.
- **Observation.** No difference in learning speed (first lap 7.9 ± 3.6 vs 8.0 ± 3.9) or stability across seeds;
  marginally faster best lap (50.90 ± 0.09 vs 51.01 ± 0.09 s, about one standard deviation); diversity 3.00 because
  distances grow with the number of dimensions.
- **Explanation.** The task — steer towards the open side with 5 distances — needs very few parameters; 5-6-2 is
  already expressive enough, and the bigger network brings almost nothing on this track.

## 3. Fitness design (step 33)

The composite fitness is `Σ weight × component` with components progress, laps, lap time, speed, collision,
stagnation, centre-line deviation and steering oscillation (`FitnessComponent`). One perverse effect showed up during
development: with a **collision weight of 3** (stagnation weight 1), the hairpin population stayed at a progress of
3.6–3.8 checkpoints for 20 generations (seed 42). The cars drove to the end of the first straight, **made a U-turn and
drove back** until stopped for stagnation: turning around cost −1, trying the hairpin and crashing cost −3. In an
exploratory run (3 seeds, 40 generations), progress-only fitness and composite fitness with collision ≤ stagnation all
completed the first hairpin lap within 6–16 generations. The default collision weight is therefore 1, and
`CompositeFitnessTest.testRetreatingDoesNotBeatTryingTheCornerWithDefaultWeights` protects it.

## 4. Generalization (step 34)

Champions after 50 generations (seed 42), tested on every track ([`generalization.md`](experiments/generalization.md)):

| Trained on | Oval (16/lap) | Hairpin (36/lap) | Chicane (30/lap) |
|---|---|---|---|
| oval | 3 laps | crash after 0.31 | crash after 0.48 |
| oval + chicane | 3 laps | 15.83, crash | 1 lap + 27 checkpoints (timeout) |
| hairpin | 3 laps | 1 lap + 3.78 (timeout) | crash after 0.86 |

The oval champion has learned the oval "by heart" and crashes at once elsewhere (over-fitting). Training on two
tracks gives a driver that also goes 15.8 checkpoints on a hairpin it has never seen. The hairpin champion fails on the
chicane: all three tracks are driven counter-clockwise, but only the chicane has quick right-left S bends, and the
hairpin champion is biased to the left (section 6).

## 5. Lap time (step 35)

Mean over the 10 base seeds of each generation's best hairpin lap: 54.25 s (generation 10, 9 seeds with a lap),
52.77 s (20), 51.82 s (30), 51.22 s (40), 51.02 s (49) — the lap time keeps decreasing long after the first lap
([`base_best_lap_time.svg`](experiments/base_best_lap_time.svg)). Because the car model has no grip limit, the gain
comes from the line (cutting the inside of the bends), not from braking. The best lap is recorded (`LapReplay`),
replays identically on a re-run (`LapReplayTest`) and is drawn as a ghost in the UI.

## 6. Reading a champion (step 38)

Run: base configuration on the hairpin, seed 42 ([`analysis.md`](experiments/analysis.md); genomes in
`experiments/champions/`).

**Why is this car better?** The generation-0 champion stops after 3.26 checkpoints; the generation-50 champion
completes a lap in 51.05 s, and 63 cars of its generation do. Its answers to typical sensor readings
(steer > 0 = left):

| Situation | Gen 0 steer / throttle | Gen 10 | Gen 50 |
|---|---|---|---|
| Centred in a straight corridor | −0.01 / +0.84 | −0.07 / +0.83 | +0.49 / +0.99 |
| Close to the right wall | −0.00 / +0.82 | +0.07 / +0.86 | +0.91 / +0.99 |
| Close to the left wall | −0.05 / +0.84 | −0.23 / +0.77 | −0.50 / +0.99 |
| Right-hand bend ahead | −0.78 / +0.64 | −0.32 / +0.65 | −0.68 / +0.97 |
| Left-hand bend ahead | −0.78 / +0.63 | +0.06 / +0.80 | +0.96 / +0.98 |
| Wall straight ahead | −0.90 / +0.59 | +0.12 / +0.69 | +0.61 / +0.96 |

Generation 0 steers right whatever the bend; generation 50 steers away from the nearer wall and into each bend.

**What has its network learned?** A wall-following rule — steer towards the side whose rays are longer — with full
throttle everywhere (no grip limit makes braking useless), and a **left bias** (+0.49 when centred, turns left when a
wall is straight ahead) that fits a counter-clockwise track. The same bias explains why it fails on the chicane.

**How have its weights evolved?** Distance 0 → 10: 4.80 with cosine similarity 0.03 (the generation-10 champion is
from another lineage); 10 → 50: 3.65, cosine 0.63 (the same lineage, refined). The distance between consecutive
champions shows the history: large jumps (~4.7) in the first generations when a new lineage takes over, many exact
zeros (an elite stays champion), then small steps (0.1–2) as mutations refine it. The weight histogram widens:
generation 0 is uniform in [−0.76, 0.76] (Xavier limit √(6 / (5 + 6)) ≈ 0.74 for the first layer), generation 50 has
tails at −2.53 and 1.61: a few strong weights carry the decision.

**Why is generation n+1 better than generation n?** Because evaluation is deterministic and the elites are copied
unchanged, the best car of n is still in n+1, so the best fitness cannot drop (0 regressions in every run with
elitism; 8.1 per run without, experiment C). Selection gives better cars more children (tournament: P(best) = k/n),
and mutation explores around them; whenever a child beats the elites, it becomes the new reference. Without
selection (experiment E) the elites still climb, but the population as a whole stays poor.
