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
