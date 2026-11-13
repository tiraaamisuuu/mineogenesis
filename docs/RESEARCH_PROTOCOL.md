# Pilot comparisons before expanding the task

The selected-log experiment is an actuator/learning-loop checkpoint. A successful
mechanics test or a high shaped fitness score does not demonstrate learned
collection. Measure actual collected episodes separately from shaping, preserve
every run (including failures), and compare multiple seeds before changing the
task or expanding the controller.

## Comparison unit and metadata

Use a completed run as the independent comparison unit. Candidate episodes and
generations within one evolving population are dependent observations. Do not
count two exports of the same seeded run, or a saved run and its continuation,
as independent replicates. An analysis command should reject duplicate seed and
configuration pairs by default. Keep different objectives, populations, episode
budgets, generation budgets, mutation settings and fixture descriptions separate.

CSV exports must retain mutation settings, topology/fitness versions and a stable
fingerprint of the adapter's fixture metadata in addition to existing counters.
The fingerprint describes dimension, spawn pose and target; it is not a terrain
checksum or a world identifier. Save the world and named archive alongside the
CSV for a repeatable protocol. Two different worlds can have the same fixture
description but different terrain/events; the operator must keep those datasets
in separate analysis invocations.

## Initial protocol

1. Use one disposable superflat world, a clear fixed floor, and a player-placed
   upper oak log. Record Minecraft/mod versions, fixture coordinates, spawn pose,
   world seed, weather/time rules, other mods and observer setup in a run note.
2. Verify mining/pickup manually first. Restore the exact fixture and fixed
   observer pose, stop existing runs, then select the target.
3. Use preselected seeds (for example 11, 22, 33, 44, 55). Keep population, episode
   budget, generations and mutation settings unchanged within a comparison.
   Both start commands accept an optional mutation probability/sigma pair after
   the generation count (for example `acquisition start 11 16 200 10 0.15 0.3`).
   Change one factor at a time and preserve the same preselected seed list.
   A tiny pilot may expose bugs but cannot support a reliable learning claim.
4. Keep the area loaded and quiet. Export at completion and retain a matching
   named checkpoint. Record aborted runs and their reasons separately; never
   quietly omit them from reported outcomes.
5. Summarize per-run best-so-far fitness change, first/final generation collection rate,
   aggregate collected episodes and actual simulation ticks. Summaries across
   seeds report means and sample standard deviations when at least two seeds
   exist. A one-seed result has no between-seed variance estimate.
6. Inspect champion replay separately. Replays are demonstrations, not held-out
   evaluations. Generalisation claims require a later frozen-controller protocol
   on unseen fixtures with separate evaluation records and multiple trials.

`scripts/analyze-runs.py` uses Python's standard library, validates CSV
invariants, emits per-run and grouped summary CSVs and preserves incompatible groups.
No external analysis dependency or implicit claim of statistical significance is
needed. This checkpoint does not automate Minecraft benchmark orchestration,
control all world randomness, measure large populations or prove generalisation.
