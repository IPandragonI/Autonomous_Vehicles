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
