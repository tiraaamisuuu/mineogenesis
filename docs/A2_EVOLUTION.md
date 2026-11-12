# A2 checkpoint: a first evolution loop

## Goal and boundary

The main A1 single-player smoke checks have passed in the user's superflat
world. This checkpoint adds the smallest observable evolution experiment:
evaluate a population of neural controllers sequentially through one visible
body, select successful genomes, mutate offspring and repeat. The A1 manual
controls remain available as a separate mode.

This is an exploration sanity check, not block acquisition or autonomous
survival. The score is the number of distinct grounded X/Z block cells visited
after the starting cell. On a flat world, walking in a straight line can solve
this simple objective. Improvement is not evidence of obstacle avoidance,
generalisation or survival, and is not guaranteed on each run.

## Architecture proposed before implementation

All network inference, immutable genomes, seeded tournament selection,
mutation, elitism, fitness bookkeeping and episode/generation lifecycle live
in the plain Java `core` module. No external numerical library is needed.

The Fabric adapter supplies a frame containing local observations and the
body's X/Z position. Only fitness bookkeeping sees position; it is never fed
to the neural controller. The existing living entity receives exactly the
same actuator records as manual control. It does not acquire navigation goals.

Use a fixed 8-input, 8-hidden, 4-output tanh network (108 weights/biases).
Inputs are body-relative forward/sideways velocity, vertical velocity,
ground contact, horizontal collision, a solid block ahead, and previous
forward/strafe inputs. Outputs control forward, strafe, yaw change and jump.

Each generation evaluates every genome from the same captured spawn point,
orientation and episode tick budget. One elite survives unchanged; the other
genomes use tournament selection and independent Gaussian weight mutation.
The population, tick budget, generation limit and seed are command arguments.
Defaults: seed 42, population 16, 100 ticks per episode, 10 generations,
mutation probability 0.10, Gaussian sigma 0.20, tournament size 3, one elite.

## Technical challenges and decisions

* Evaluate one visible body at a time initially. This reuses tested lifecycle
  and cleanup and avoids inter-agent collisions influencing scores. Simultaneous
  visible populations require a later population adapter and benchmarks.
* Count physics steps, not just calls to a controller. Supply an action before
  the world tick and evaluate its outcome at the next server tick. In particular,
  score the final submitted action before ending an episode.
* Pause freezes the body and both episode/generation progress. Resume retains
  the pending evaluation but clears prior actuator memory. Stop/reset/shutdown
  remove the body. An invalid or unavailable body aborts without assigning
  fabricated fitness or silently breeding a partial generation.
* Reseed on reset for repeatable genomes, selection and mutation. This does not
  make Minecraft bitwise deterministic: terrain, time, weather, observers and
  other entities are uncontrolled. Use a clear superflat area for the smoke test.
* Do not restore or overwrite terrain in this checkpoint. Bodies cannot mine or
  place blocks. Compare candidates in an unchanged, quiet area; proper arena
  isolation and world restoration remain future work.
* The initial fitness rewards unique grounded cells only. The starting cell is
  excluded, revisits do not count and airborne motion earns no new-cell reward.
  This discourages counting inactivity and falling as exploration.
* Keep per-tick inference buffers reusable. Population/genome allocations occur
  between episodes/generations. Fitness memory is bounded by the episode budget.
* No network persistence or checkpoint files yet. Stop retains summary statistics
  for inspection; reset/new run/server shutdown discard the previous population.

## Validation plan

Core checks must cover known network outputs, input validation, immutable
mutation, seeded repeatability, elitism, finite fitness, synthetic optimisation,
exact episode budgets including the final outcome, grounded novelty scoring,
pause/resume, reset replay and cleanup after failures. Minecraft GameTests must
cover the new commands, exclusive manual/evolution ownership, body replacement,
generation completion, pause/reset and missing-body cleanup. Existing A1 tests
must continue to pass. A graphical user run checks the visible sequence of
candidates and generation/status reporting.

## Background

Fixed-topology weight evolution is one established neuroevolution approach;
evolving topology is a separate design choice and is outside this checkpoint.
See the [UT Austin neuroevolution research overview](https://nn.cs.utexas.edu/pages/research/neuroevolution.html).
