# Generalization: champions tested on other tracks

50 generations, base configuration, seed 42. Progress is in checkpoints; a lap of the oval has 16, the chicane 30, the hairpin 36.

| Trained on | Tested on | Progress | Laps | End | Fitness |
|---|---|---|---|---|---|
| oval | oval | 47.76 | 3 | FINISHED | 85.13 |
| oval | hairpin | 0.31 | 0 | CRASHED | -0.39 |
| oval | chicane | 0.48 | 0 | CRASHED | -0.25 |
| oval + chicane | oval | 47.76 | 3 | FINISHED | 84.41 |
| oval + chicane | hairpin | 15.83 | 0 | CRASHED | 15.50 |
| oval + chicane | chicane | 57.06 | 1 | TIMEOUT | 66.99 |
| hairpin | oval | 47.77 | 3 | FINISHED | 84.25 |
| hairpin | hairpin | 39.78 | 1 | TIMEOUT | 46.93 |
| hairpin | chicane | 0.86 | 0 | CRASHED | 0.13 |
