# CLAUDE.md

Java 2D car simulator where neural networks learn to drive by evolution. Started as a course assignment
(*TP voiture autonome*, steps 0–38, done; see `README.md` and `docs/REPORT.md`). Long-term goal of the owner:
**import existing tracks, change the car type, and apply deep learning to driving.** Everything new should move toward
that without breaking the assignment deliverables.

## Commands

- `mvn test` — all tests (JUnit 5). Must stay green.
- `mvn javafx:run` — the UI (`ui.MainApp`); the keyboard bench is a button inside it.
- `mvn compile` then `java -cp target/classes org.autonomous_vehicles.evolution.Experiments <abcdef|generalization|analysis|all> [outDir] [generations] [seeds] [threads]`
  — headless experiments; `all` with 50 generations × 10 seeds takes ~13 min on 12 threads.
- JDK 23 (pom and IDE); code uses Java 21 APIs. Maven may not be on `PATH`; the IDE-managed JDK/Maven work.

## Architecture rules

- Packages: `util`, `geometry`, `neural`, `sim`, `evolution`, `ui`. Dependencies: `ui → evolution → {neural, sim}`,
  `sim → neural`. **Nothing outside `ui` imports JavaFX**; `SimulationController`, `SimulationFrame`,
  `NetworkSnapshot` are plain Java so the UI logic is unit-tested. Views only draw.
- **No ML library**: networks, backpropagation (`neural.Trainer`) and the GA are hand-written on purpose.
- **Determinism**: every random draw goes through one injected `util.Rng` seeded from the config. Never use
  `java.util.Random`, `Math.random()` or wall-clock time in simulation/evolution code. Cars do not interact, so a car's
  evaluation is independent of the others (elitism relies on it).
- **Car physics**: `sim.VehicleModel` implementations are stateless (per-car state — gear, rpm, tyre wear, sliding —
  lives in `Car`), so one model is shared by parallel simulations. `KinematicModel` (= `CarPhysics`, the assignment
  model) is the **default** and must keep its exact behaviour: the assignment tests and `docs/experiments/` depend on
  it. Check after touching shared code: rerun `Experiments analysis <tmpDir>` and diff `analysis.md` with
  `docs/experiments/analysis.md` (must be identical).
- `DynamicModel` = grip-limited bicycle: torque curve → gears → wheels, mass, drag, rolling resistance, downforce,
  friction circle, understeer, tyre wear. Car types are `CarSpec` presets in `sim.Vehicles`; compounds are
  `sim.TireCompound`. Units SI, 1 track unit = 1 m.
- Tracks: `.track` text files in `src/main/resources/tracks/` (centre-line control points → Catmull-Rom spline,
  constant width), loaded by `TrackLoader`, fully validated by `Track`. `WallGrid` accelerates ray/collision queries
  and is tested against brute force — keep those tests when changing it.

## Conventions

- English identifiers and comments; Javadoc explains *why*; match the existing density.
- Validate arguments with `IllegalArgumentException` and clear messages; records are immutable with defensive copies
  of arrays/lists; prefer adding an overload over changing a widely used signature.
- Tests mirror the main packages (`src/test/java/...`), `public class XxxTest`, `@Test public void testXxx()`, and
  check behaviour against known values (hand computations, theory, brute force), not just "no exception".
- Format numbers with `Locale.ROOT`; write generated files with `\n`, not `%n` (Windows would produce CRLF).
- Shell edits on Windows: GNU `sed` turns `\n` in a replacement into a real newline and source files are CRLF —
  use the editor tools or a script file instead of `sed`/heredocs for multi-line Java edits.

## Known pitfalls (measured, not guessed)

- Fitness weights: the collision penalty must not exceed the stagnation penalty, otherwise cars learn to U-turn before
  hard corners (`CompositeFitnessTest.testRetreatingDoesNotBeatTryingTheCornerWithDefaultWeights`).
- Grip-limited cars need ~65–75 s per hairpin lap: use a ~90 s time limit and the speed input, or no car laps.
- The oval is too easy to compare GA settings (random networks lap it); use `hairpin` for experiments.
- UI frame rate is bound by JavaFX rasterisation, not by Java code: antialiased fills/strokes become software masks the
  size of their bounding box. Hence `TrackView` keeps the track on a static layer drawn with a 400 px margin and only
  translated while the camera moves, and draws trails as `fillRect` dots (polylines: 13–35 FPS; dots: 55–58 FPS at ×100,
  Iris Xe, 1536×864). Measure with the FPS overlay before and after any drawing change.
- Parallelism: `Simulation.advance`/`runUntilAllDead` step each car on its own thread (bit-identical, tested). On this
  laptop the speed-up saturates at ~2.5× (6 threads); per-step timings are noisy (±20 %), so compare allocations
  (`ThreadMXBean.getThreadAllocatedBytes`, 774 B per car-step) or checksums, not single timings.
- `SimulationController.tick(elapsed)` is real time × speed within a 10 ms budget (`tick()` keeps the fixed
  speed-steps-per-call mode used by tests); the reached speed is `getEffectiveSpeed()`.

## Race mechanics (dynamic cars only)

- `sim.RaceRules(fuelAutonomy, tyreTemperature)` is a `SimulationConfig` component (`defaults()` = 25 s of fuel +
  temperature; the kinematic car ignores both). `RaceRules.NONE` keeps `DynamicModel` bit-identical to the model before
  the mechanics, and `new DynamicModel(spec, tire)` / `Vehicles.create(type, tire)` mean NONE (tests rely on it).
- Fuel: flow = drive power / (0.30 × 43 MJ/kg) + 2 % idle, the fuel weighs on the car, a dry car has no drive and is
  stopped with `DeathCause.OUT_OF_FUEL` below 0.1 m/s. Tyres: heat ∝ grip use × distance (×2.5 sliding), cool ∝
  (T − 25 °C) × (0.006/s + 0.0015/m); grip from `TireCompound.temperatureGrip` (windows soft 80–100, medium 90–110,
  hard 100–120 °C). Measured on hairpin: champions use 17–26 % of full power; the mechanics lower champion progress
  after 25 generations (4 seeds: 34.2 none, 29.5 temperature, 22.6 both) because the network does not observe them yet.

## Car state inputs and retraining

- `SimulationConfig.useCarStateInputs` adds `CarBrain.CAR_STATE_INPUTS` = 4 inputs after rays and speed: fuel
  fraction, tyre temperature relative to the compound window (0.25–0.75 inside, 0.5 = middle; same breakpoints as the
  grip loss), wear, sliding. `QuickSetup` turns it on (with speed) for dynamic cars. Random populations start with
  zero weights from those inputs (`NetworkInputs.randomPopulation`): with random weights they hurt (hairpin, 6 seeds:
  progress 24.4 without, 14.3 random, 29.8 blind start).
- Retraining: `GeneticAlgorithm(config, seed)` / `Population.seeded` = the champion + mutants. The UI's
  `SimulationController.retrainFrom` keeps the champion as seed across restarts; `NetworkInputs.adapt` widens it with
  zero weights when only inputs are added, otherwise the seed is dropped and reported once (`takeDroppedSeed`).
  Measured: hatchback champion from hairpin retrained on chicane with car state inputs, 10 generations, 4 seeds:
  progress 59.9 (1.25 laps) vs 27.4 (0.25 laps) from scratch.

## Racing observations and controls (opt-in except the rays)

- `SimulationConfig.useTrackPreview` adds `TrackPreview.INPUTS` = 6 inputs last: road heading 0/25/50/100/150/200 m
  ahead minus car heading, / π, unwrapped and clamped to ±1. `Track` keeps the centre line (`Tracks.build` passes it;
  other tracks use the checkpoint centres); the per-car search hint lives in `CarBrain`. Random populations start
  blind to it (zero weights): with random weights every seed crashed at São Paulo's turn 1 (checkpoint ~11/108).
- `rayLayout` FORWARD = `RaySensor.forwardFocused` (±u² × 90°). `gripSteering` (`DynamicModel.steeringCurvature`):
  ±1 = all the grip as lateral acceleration, capped by the lock. `FitnessComponent.TIME_SAVED` (weight 0 by default)
  = Σ(maxTime − lapTime) in seconds; the default `CENTERLINE` penalty outweighs a second saved via `LAP_TIME`.
- Measured, formula car on SaoPaulo, QuickSetup defaults, 100 generations × 8 seeds (seeds that lap / best lap):
  baseline 6/8, 4:23 · fuel off 7/8, 3:47 · 9 forward rays 80 m **7/8, 3:18** (QuickSetup default for real cars) ·
  same at 200 m 1/8 · TIME_SAVED 7/8, 3:50 · rays + TIME_SAVED 6/8, 3:09 · + preview 4/8, **2:59** · grip steering
  0/8 (random networks lose the scrub braking that wheel-angle oversteer gives them). The default 25 s fuel load
  runs out before one São Paulo lap even at ~80 km/h. `Champion.VERSION` 2 adds the three fields; version 1 loads.
- GA settings for the formula car on SaoPaulo (QuickSetup + unlimited fuel, ~10k car evaluations, 8 seeds; median
  progress / seeds with 2 laps / best lap): ref 8 %/0.25 k=3 pop 100: 189, 2/8, 3:03 · **truncation 10 %: 231, 5/8,
  2:50** · **tournament k=5: 230, 6/8, 3:03** · 15 %/0.5: 165 · 30 %/0.8: 68 (best on hairpin, worst here) ·
  4 %/0.15: 65 but 2:59 (refining only) · pop 200 × 50 gen: 177, laps ~4:40 · pop 50 × 200 gen: 211 · uniform
  crossover: 197 · TIME_SAVED: 208 (8/8 lap) · PROGRESS only: 193. Selection pressure matters most here.
  `QuickSetup.recommended` (button "Fastest-learning defaults") applies the winner: truncation 10 %, 5 elites,
  8 %/0.25, 100 cars, 6 neurons, default weights, unlimited fuel; keeps track, car, tyres and seed.
- Tyre wear: `RaceRules.tyreWear` multiplies the compound rates. ×1 = original rates (NONE and the 2-argument
  constructor; old champion files without the field read ×1); `defaults()` = ×0.1 (a medium lasts ~15 clean laps of
  São Paulo instead of ~1.5). The measurements above were taken at ×1.
- Plateau (recommended settings, wear ×0.1, 6 seeds): best lap falls by minutes until ~gen 100, then 1–25 s per
  100 gen; at gen 200 the seeds span 2:26–3:09 (seed luck > any setting). 100 more gen from each champion (mean
  gain): continue 8.5 s · preview + TIME_SAVED 8.9 · + 4 %/0.15 8.3 · TIME_SAVED 6.2 · tournament k=3 4.3 ·
  4 %/0.15 alone 4.0. Changing GA settings after the plateau does not beat continuing; best 2:24 vs ~1:35 physical.
- Tyre heating: `RaceRules.tyreHeating` (°C/m at full grip use). The original 0.20 (`DynamicModel.TYRE_HEATING`,
  kept for NONE, 2/3-argument constructors and old files) settles at 136–158 °C at the limit, so fast driving cooked
  the tyres; `defaults()` = 0.12 settles at 92–101 °C. Measurements above predate it.

## Imitation learning (breaks the GA plateau)

- `sim.ExpertDriver`: QSS speed profile on the centre line (corner margin 0.85, braking 0.75), pure pursuit,
  traction control and ABS (full throttle spins a formula car's wheels below ~150 km/h; spinning overheats tyres).
  Formula car laps SaoPaulo 1:55.6 / 1:50.9 (flying), Monza 2:00, Spa 2:40, Silverstone 2:27, hairpin 0:39.
- `evolution.Imitation`: noisy expert demonstrations, then DAgger rounds where control alternates between network
  and expert in 0.5 s blocks (expert share 0.5 × 0.7^(round−1)); without the mixing the network crashes at the
  first bend and DAgger only ever sees that bend. Keeps the best stage. `Trainer.trainMiniBatch(double[][],
  double[][], int)` is the multi-output backprop. UI: "Learn from the expert" seeds `retrainFrom`.
- Measured, formula/SaoPaulo: preview + 16-16 imitates to **1:55.7 with no GA** (best GA ever: 2:24); rays only
  never lap (6: first bend; 16-16: 35/108). 50 GA generations from the imitated network did not improve it.
- `sim.RacingLine`: minimum-curvature line (4th-order smoothing, clamped to walls − 2.5 m; 80 000 passes ≈ 2 s;
  midpoint smoothing gives the shortest path, which measured slower than the centre line). The expert tracks its
  path with curvature feed-forward + lateral/heading feedback (pure pursuit cut the apexes into the wall).
  Expert flying lap: centre 1:51.5 → racing line 1:38.3 (corner 0.85, brake 0.75; higher margins crash in lap 3).
- Imitating the racing-line expert never laps alone, but evolution from it at 3 %/0.1 does: 32-32, fastest lap
  1:47.2 (50 gen), 1:43.8 (100), **1:43.6** (150); safer expert (0.80/0.70) 1:45.7; centre-line expert 1:50.2.
  `Imitation.recommendedConfig` = preview + 32-32 + 3 %/0.1.

## Champion files

- `evolution.Champion` = network + full `SimulationConfig` + generation + fitness, saved as JSON through the
  dependency-free `util.Json` (doubles round-trip bit-exactly). `Champion.replay()` re-drives exactly the evaluated
  trajectory (determinism); `SimulationController.startReplay/stopReplay` shows it without touching the training run.
  `NetworkIO` (.txt, weights only) stays for the assignment deliverables. Bump `Champion.VERSION` on format changes.
- Lap records: `SimulationController` watches the displayed cars once per image (plus the algorithm's fastest lap)
  and reports each new record once (`takeNewLapRecord`, banner over the track); the same lap can come back ~1e-15 s
  apart, hence `RECORD_TOLERANCE`. The tabs' figures come from plain-Java `CarDetails`, `BrainSummary` and
  `getDeathCounts`; `MainApp` refreshes only the visible tab.

## Roadmap toward the long-term goal

- **Real circuits** (done): 24 TUM CSVs ship in `src/main/resources/tracks/real/` (LGPL-3.0, keep `LICENSE` and
  `README.md` there); `Tracks.REAL` lists them, `Tracks.byName` builds each once (cache). Suzuka is excluded
  (figure-of-eight). `ui.TrackDecor` derives kerbs and the chequered finish line from the wall geometry (visual only).
- **Track import** (done): `TrackLoader.parseCsv` reads TUM racetrack-database CSVs (`x,y,w_right,w_left`, metres)
  through `Tracks.fromCenterlineWithWidths` (wall-loop repair, evenly spaced gates, figure-of-eight rejected) and
  `Tracks.register` makes them available by name. Built-in tracks deliberately keep the old gate placement (the
  `importedData` flag) so `docs/experiments/` stays reproducible. Learning on real circuits is the open problem
  (kart on Norisring stalls at checkpoint 12/57 after 25 generations).
- **UI**: plain-Java logic (`Telemetry`, `QuickSetup`, `CarAppearance`, `SimulationController`) is unit-tested;
  JavaFX classes (`MainApp`, `TrackView`, `Dashboard`, `CarPainter`, `ControlPanel`) only draw and wire. Verify
  visual changes by running the app and snapshotting the scene (compile harnesses with `-encoding UTF-8`: labels use ▶ ×).
- **Car types**: add `CarSpec` presets (or load them from files); per-type collision size is not modelled yet
  (all cars use `Car.RADIUS`). Possible physics upgrades: dynamic bicycle model with slip angles (oversteer/drift),
  tyre temperature, gear-shift time.
- **Deep learning**: `CarBrain` is the observation → action seam (inputs: rays + optional speed; outputs: steer,
  throttle). Supervised learning can reuse `neural.Trainer` (backprop) on recorded driving; reinforcement learning
  needs an environment wrapper around `Simulation`/`CarAgent` (reset, step(action) → observation, reward, done) with
  the reward built from `AgentResult`/`FitnessComponent`. Richer observations (gear, rpm, tyre wear, sliding) are
  already available on `Car`.
