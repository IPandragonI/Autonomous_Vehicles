# Autonomous Vehicles — neural networks and a genetic algorithm, hand-written in Java

A population of cars, each driven by its own neural network (no ML library), learns to drive around
2D tracks by evolution. Course: *TP — une voiture qui apprend à conduire* (steps 0 to 38).
The final report (experiments A–F, champion analysis) is in [`docs/REPORT.md`](docs/REPORT.md).

## Running

Requirements: JDK 23 (the project's `pom.xml` and IDE target 23; the code only uses Java 21 APIs), Maven.

| What | Command |
|---|---|
| All tests (260) | `mvn test` |
| Evolution UI (steps 28–35) | `mvn javafx:run` |
| Keyboard test bench (step 18) | button **Drive yourself** in the UI |
| Experiments A–F, generalization, champion analysis (steps 34–38) | `mvn compile` then `java -cp target/classes org.autonomous_vehicles.evolution.Experiments all docs/experiments 50 10` |

`Experiments` arguments: `<abcdef|generalization|analysis|all> [outputDir] [generations=50] [seeds=10] [threads=cores]`.
The full run (100 evolutions + generalization + analysis) took about 13 minutes on 12 threads.

**UI.**
- **Toolbar** (what you change often): Play/Pause, Step, Restart, speed (×1 ×2 ×10 ×100 simulation steps per frame),
  **Track** (+ **Import…**), **Car**, **Tyres**. Changing track/car/tyres starts a new population with the measured
  recommendations (speed input on for real cars, time limit sized to the track); the status bar says what was applied.
  **View** menu: rays, trajectories, checkpoints, stopped cars, ghost, car image. **Follow car**, **⚙ Settings**,
  **Drive yourself**.
- **Track view**: mouse wheel = zoom around the cursor, drag = move, double-click = fit; *Follow car* keeps the camera
  on the inspected car (needed on real circuits). Click a car to inspect it; otherwise the best car is shown.
- **Right tabs**: **Car** (dashboard: rev counter with redline, engaged gear, speed, throttle/brake/steering, tyre
  compound and wear, lap and checkpoint, SLIDING / CRASHED badges), **Progress** (best score, cars with a lap, best
  lap, two charts), **Brain** (the inspected network).
- **⚙ Settings** drawer (hidden by default), one section open at a time: *Evolution* and *Fitness* apply from the next
  generation; *Population & network* (cars, hidden layers, speed input, time limit, sensor range, seed) restarts the
  run. Every value goes through `SimulationConfig` validation; refused values are reported in the status bar.

**Keyboard bench.** Arrows: steer / accelerate / brake (hold to reverse); R: restart; same track/car/tyre selectors
and dashboard; the camera follows the car.

## Real circuits

**24 real circuits are built in** (marked 🏁 in the Track menu): Austin, Brands Hatch, Budapest, Catalunya, Hockenheim,
IMS, Melbourne, Mexico City, Montreal, Monza, Moscow Raceway, Norisring, Nürburgring, Oschersleben, Sakhir, São Paulo,
Sepang, Shanghai, Silverstone, Sochi, Spa, Spielberg, Yas Marina, Zandvoort. They come unmodified from TU Munich's
[TUM racetrack-database](https://github.com/TUMFTM/racetrack-database) (LGPL-3.0; licence and attribution in
`src/main/resources/tracks/real/`) and are built once on first use, then cached.

Other circuits can be imported with **Import…**: format `x_m,y_m,w_tr_right_m,w_tr_left_m` per line, metres,
driving order, `#` comments; a 2-column `x,y` file gets a 12 m width. `.track` files work too.

**Track look**: grass, tarmac, white track limits, red-and-white kerbs (rumble strips) and a chequered start/finish
line. Kerbs are computed from the wall geometry (`TrackDecor`): on the inside of every bend tighter than a 120 m radius,
and on the outside of tight bends (under 50 m); they appear once you zoom in enough for a 1.5 m stripe to be visible.
They are decoration only — the simulation has no kerb physics.

Import builds the walls from the per-point widths, cuts the small wall loops of very tight bends, places a checkpoint
every ~40 m and registers the track for the session. Checked on the 25 database files: 24 import (load time 30–380 ms,
e.g. Spa 6.97 km / 175 checkpoints); **Suzuka is refused** because its figure-of-eight crosses itself (bridge),
which a flat 2D track cannot represent. Real circuits are long and narrow compared with the built-in ones: in a first
run, a kart population on Norisring (2.3 km) reached checkpoint 12 of 57 in 9 generations, then stalled at the same
corner for 15 more — expect to need many more generations (or more cars) than on the toy tracks.

## Car images

Cars are drawn from above at their real size, in a style per type (kart frame, single-seater with wings, hatchback,
sports car). To use your own picture: **View → Car image for this car type…** and pick a PNG seen **from above with the
front pointing right** (transparent background recommended); it is stretched to the car's length × width and
remembered between sessions (**Use the built-in drawing** reverts). Images can also be shipped as
`src/main/resources/cars/<type>.png`. Drawings are visual only: every type still collides as a 1.2 m circle.
3D models (OBJ/glTF) are not supported: the simulator is 2D top-down, and a 3D view would need a separate renderer.

## Car models

The car type is chosen in the UI (*Run* → **Car**, **Tyres**, then *Apply & reset*), in the keyboard bench, or with
`SimulationConfig.toBuilder().carType("sports").tire(TireCompound.SOFT)`. Units are SI; 1 track unit = 1 m.

- **`kinematic`** (default) — the deliberately simple model of the assignment (`CarPhysics`): unlimited grip, constant
  acceleration, 20 m/s. The assignment tests and the experiments in `docs/` use it.
- **`kart`, `hatchback`, `sports`, `formula`** — `DynamicModel` driven by a `CarSpec`:
  - engine torque curve with idle, peak torque and rev limiter; gear ratios × final drive × wheel radius give the
    force at the wheels and the engine speed; automatic gearbox (up near the redline, down below the torque peak);
  - mass (acceleration = force / mass), aerodynamic drag ½ρ·Cd·A·v², rolling resistance, downforce ½ρ·Cl·A·v²
    adding load on the tyres;
  - tyres: friction circle μ·g shared by braking/acceleration and cornering, traction limit of the driven axle,
    brakes limited by the brake system and by grip; a bend taken too fast makes the car understeer (it runs wide,
    scrubs speed) and marks it *sliding* (red outline);
  - compounds `SOFT` / `MEDIUM` / `HARD` (μ × 1.25 / 1.15 / 1.05 of the car's grip factor) that **wear** with distance
    (0.30 / 0.15 / 0.07 per km, ×4 while sliding) and lose up to 35 % grip when worn;
  - throttle < 0 brakes, then engages reverse below 0.5 m/s (max 5 m/s).

Measured with the presets (medium tyres unless stated, `DynamicModelTest` checks the trends):

| Car | Power | Top speed | 0–100 km/h | Gears | Lateral grip soft / medium / hard | Braking 90→0 km/h, soft / hard |
|---|---|---|---|---|---|---|
| kart | 22 kW | 120 km/h | 9.7 s | 1 | 1.24 / 1.14 / 1.04 g | 24.2 / 27.9 m |
| hatchback | 71 kW | 184 km/h | 10.4 s | 5 | 1.00 / 0.92 / 0.84 g | 32.0 / 36.8 m |
| sports | 243 kW | 278 km/h | 5.0 s | 6 | 1.19 / 1.10 / 1.00 g | 27.7 / 31.0 m |
| formula | 470 kW | 292 km/h | 3.7 s | 8 | 1.58 / 1.45 / 1.33 g (downforce at 25 m/s) | 20.9 / 24.6 m |

**Learning with real cars** (exploratory runs, 2 seeds, speed input on). On the oval every car learns within a few
generations (best laps: formula 14.9 s, kart 17.0 s, hatchback 19.4 s). On the hairpin a grip-limited car needs
65–75 s per lap, so **raise the time limit to ~90 s** (Run → *Time limit*): with 90 s the kart lapped by generation
8–10, the hatchback by 20–23, the formula car on one seed of two (generation 34; the other stayed stuck crashing at full
speed into the first hairpin). Turn on *Speed as extra input* for these cars: without it the network has to infer its
speed from how the ray distances change.

To add a car type, add a `CarSpec` to `Vehicles.PRESETS`; a new compound is a new `TireCompound` constant.

## Architecture

```
org.autonomous_vehicles
├── util       Rng (seeded LCG: nextDouble, nextInt, nextGaussian), SvgChart
├── geometry   Vec2, Segment, Geometry (ray/segment, segment/segment, point/segment, polygons)
├── neural     Neuron, Layer, NeuralNetwork, activations, Loss, Trainer, NetworkIO      (steps 1–11)
├── sim        Car, CarPhysics, CarControls, Track, Tracks, TrackLoader, WallGrid, Collision,
│              Checkpoint, LapTracker, RaySensor, CarBrain, CarAgent, Simulation,
│              SimulationSettings, AgentResult, DeathCause, LapReplay                     (steps 12–20, 34–35)
│              VehicleModel, KinematicModel, DynamicModel, CarSpec, TireCompound, Vehicles  (car models)
├── evolution  Individual, Population, FitnessFunction, ProgressFitness, FitnessComponent, CompositeFitness,
│              Evaluator, MutationOperator, CrossoverOperator, SelectionStrategy (+ Truncation, Roulette,
│              Tournament, Random), SelectionType, SimulationConfig, GeneticAlgorithm, GenerationStats,
│              StatsHistory, Statistics, ExperimentRunner, Experiments, GenomeAnalysis    (steps 21–27, 31, 33, 36–38)
└── ui         MainApp, SimulationController, SimulationFrame, RenderOptions, NetworkSnapshot,
               TrackView, NetworkView, StatsPanel, ControlPanel, ManualDriveView          (steps 18, 28–32)
```

Dependency rule (assignment §1.3): `ui → evolution → {neural, sim}`; `sim → neural` (a car's brain is a network);
`neural` and `sim` know nothing of evolution or the UI, and nothing outside `ui` imports JavaFX, so everything runs
headless. `SimulationController`, `SimulationFrame` and `NetworkSnapshot` are plain Java and unit-tested; the JavaFX
views only draw what they are given.

### Data flow

1. **Track** — `Tracks.byName(name)`: the built-in oval, or a `.track` resource read by `TrackLoader`
   (centre-line control points → Catmull-Rom spline → constant-width walls + ordered checkpoint gates). `Track`
   validates: closed walls, no wall crossing itself or the other wall, start between the walls, checkpoints on the
   track and ordered in the driving direction, start just after the finish line. A `WallGrid` (uniform grid) speeds up
   ray and collision queries; tests check it against brute force.
2. **Sensing** — `RaySensor`: 5 rays (−90°, −45°, 0°, +45°, +90°, positive = left), distance to the nearest wall
   / range (80) → `double[]` in [0, 1] (1 = nothing in range); optional 6th input: speed mapped to [0, 1].
3. **Deciding** — `CarBrain`: inputs → `NeuralNetwork.forward` (Tanh) → (steer, throttle) in [−1, 1].
4. **Acting** — `VehicleModel.step` (kinematic or dynamic, Euler, dt = 1/60 s) → `Collision` (circle of radius 1.2 against the walls, plus the
   swept move to prevent tunnelling) → `LapTracker` (ordered, direction-checked checkpoints, laps, lap times) →
   end of life (crash, 60 s timeout, 4 s without progress, 3 laps).
5. **Scoring** — each car yields an `AgentResult` (progress, laps, lap times, distance, mean speed, steering
   oscillation, centre-line deviation, death cause, best-lap recording); a `FitnessFunction` turns it into one number;
   with several tracks the fitness is the mean.
6. **Breeding** — `Population.nextGeneration`: elites copied unchanged; other slots = selected parent
   (→ optional crossover) → mutation. Every random draw goes through one `Rng` seeded by the configuration:
   same seed, same run.

### Track file format (`src/main/resources/tracks/*.track`)

```
# comment
name hairpin
width 16          # track width (> car diameter)
samples 8         # spline points between two control points
checkpoints 36    # gates; the last one is the finish line
point -100 -62    # centre-line control points in driving order (≥ 3), closed automatically
point  -50 -62
...
```

Errors are reported with their line number; an invalid geometry (e.g. a bend tighter than half the width) is refused.

## Progress checklist

Steps 0–14 were done before this work; steps 15–38 were completed here. ✅ = implemented and verified by the listed
tests or runs.

| Step | Requirement | Main files | Validation | Status |
|---|---|---|---|---|
| 0–11 | Project, neuron → backpropagation, XOR | `neural/*`, `util/Rng` | existing tests (all green) | ✅ |
| 12 | 2D geometry | `geometry/*` | `GeometryTest` (+ collinear-overlap regression) | ✅ (bug fixed) |
| 13 | Car physics | `Car`, `CarPhysics`, `CarControls` | `CarTest` | ✅ |
| 14 | Track | `Track`, `Tracks.oval()` | `TrackTest`, `TrackLoaderTest` | ✅ |
| 15 | Collisions | `Collision`, `Car.RADIUS`, `DeathCause` | `CollisionTest`: wall → dead, centre → alive, grazing ±1 cm, tunnelling, grid = brute force | ✅ |
| 16 | Checkpoints and laps | `Checkpoint`, `LapTracker` | `LapTrackerTest`: scripted lap = 1 lap, skipping / wrong way / back-and-forth count nothing, lap time | ✅ |
| 17 | Ray sensors | `RaySensor`, `WallGrid` | `RaySensorTest`: known corridor, empty ray = 1.0, rays turn with the car, grid = brute force | ✅ |
| 18 | Keyboard test bench | `ManualDriveView` | run with synthetic key events: acceleration = theory (12.7 after 1.58 s), arrows never change the track list, R restarts; screenshots checked | ✅ |
| 19 | Network inputs/outputs | `CarBrain` | `CarBrainTest`: outputs bounded, inputs in [0, 1], zero network = still car | ✅ |
| 20 | Headless simulation loop | `Simulation`, `CarAgent`, `SimulationSettings` | `SimulationTest`: 100 random cars, most die < 10 s, same seed = same result, cars independent | ✅ |
| 21 | Individual, population | `Individual`, `Population` | `PopulationTest` | ✅ |
| 22 | Fitness v1, evaluation | `FitnessFunction`, `ProgressFitness`, `Evaluator` | `FitnessTest`: still car = 0, sorted decreasing | ✅ |
| 23 | Mutation | `MutationOperator` | `MutationOperatorTest`: rate 0, proportion ≈ rate (4σ), original untouched | ✅ |
| 24 | Four selections | `TruncationSelection`, `RouletteSelection`, `TournamentSelection`, `RandomSelection` | `SelectionTest`: frequencies ∝ fitness, k=1 uniform, k=n best | ✅ |
| 25 | Elitism, next generation | `Population.nextGeneration` | `PopulationTest`: size kept, elites identical, best never regresses | ✅ |
| 26 | Crossover | `CrossoverOperator`, `SimulationConfig.Crossover` | `CrossoverOperatorTest` | ✅ |
| 27 | Evolution loop | `GeneticAlgorithm` | `GeneticAlgorithmTest`: mean gen 30 > gen 0, reloaded champion = same score, determinism | ✅ |
| 28 | Real-time controller | `SimulationController`, `TrackView`, `MainApp` | `SimulationControllerTest`: pause freezes, ×10 = 10 steps, reset = generation 0 | ✅ |
| 29 | Detailed display | `RenderOptions`, `SimulationFrame`, trails | toggles hide rays/trails/checkpoints/dead cars; trail bounded | ✅ |
| 30 | Statistics and charts | `GenerationStats`, `StatsHistory`, `StatsPanel` | `GenerationStatsTest` on known data | ✅ |
| 31 | Control panel | `SimulationConfig`, `ControlPanel` | `SimulationConfigTest`: out-of-range refused, new architecture → compatible population | ✅ |
| 32 | Network view | `NetworkView`, `NetworkSnapshot` | snapshot = hand-computed activations; focused car's inputs/outputs | ✅ |
| 33 | Composite fitness | `FitnessComponent`, `CompositeFitness`, weights in the UI | `CompositeFitnessTest`: each component alone, lap > mid-lap crash, faster > slower | ✅ |
| 34 | Hard tracks, loading, generalization | `TrackLoader`, `hairpin.track`, `chicane.track`, multi-track | `TrackLoaderTest`; champion of track 1 tested on track 2 (`GeneticAlgorithmTest`, `docs/experiments/generalization.md`) | ✅ |
| 35 | Best lap, ghost | `LapReplay`, ghost in `TrackView` | `LapReplayTest`: replay identical on re-run, passes every checkpoint | ✅ |
| 36 | Experiment protocol | `ExperimentRunner`, `Statistics` | `ExperimentRunnerTest`: same seeds = same results for 1 or 4 threads, mean/std on known data, CSV | ✅ |
| 37 | Experiments A–F | `Experiments`, `docs/experiments/*` | 10 variants × 10 seeds × 50 generations; write-up in `docs/REPORT.md` | ✅ |
| 38 | Reading a network | `GenomeAnalysis`, `docs/experiments/analysis.md` | four questions answered in `docs/REPORT.md`; the oral explanation is yours | ✅ (written part) |

### Corrections to earlier steps

- **Build was broken** at step 15: `Collision` called a non-existent `Segment.toRectangle()` and used an axis-aligned
  AWT box (ignored the heading and diagonal walls). Replaced by a circle/segment distance test.
- `Geometry.intersectSegments`, collinear case: `Math.clamp(t2, 0, t1)` threw `IllegalArgumentException` when the
  second segment lay behind the first (`t1 < 0`); the overlap is now computed as `[max(0, min), min(1, max)]`.
- `NeuralNetwork.copy()` silently rebuilt the network with Tanh, whatever its activation; the activation is now kept.
- `Rng` returned negative numbers for negative seeds (seed now reduced modulo 2³²); added `nextInt` and
  `nextGaussian` needed by selection and mutation.
- `Track` now also rejects self-intersecting walls and an inner wall outside the outer one.

### Known limitations

- `NetworkIO` stores architecture and genome but not the activation; loaded networks are Tanh (true for every car network).
- The default (assignment) car model has no grip limit, so its evolved drivers keep full throttle; choose a dynamic
  car type for braking and tyre management.
- The dynamic model keeps the heading aligned with the velocity (understeer only, no oversteer or drift angle), shifts
  gears instantly, has no tyre temperature, and every car type uses the same collision circle (radius 1.2 m).
- With the 60 s limit a hairpin lap (~51 s) can be completed only once per evaluation, which caps the fitness
  (see the report).
- JavaFX rendering was checked by running the application and inspecting window snapshots, not by automated UI tests.
