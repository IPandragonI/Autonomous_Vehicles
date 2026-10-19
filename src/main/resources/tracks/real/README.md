# Real circuits

Centre lines and track widths of 24 real circuits, from the **TUM racetrack-database**
(Technical University of Munich, Institute of Automotive Technology): https://github.com/TUMFTM/racetrack-database

Licensed under the GNU Lesser General Public License v3.0 — see `LICENSE` in this folder. Files are unmodified.

Format: one point per line, `x_m,y_m,w_tr_right_m,w_tr_left_m` (metres, driving order, closed implicitly).
Loaded by `TrackLoader.parseCsv` through `Tracks.byName("<file name without .csv>")`.

Suzuka is not included: it is a figure-of-eight whose centre line crosses itself (a bridge), which a flat 2D track
cannot represent.
