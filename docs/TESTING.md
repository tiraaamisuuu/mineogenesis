# Foundation and evolution verification

## Automated core checks

`./gradlew :core:test` or the dependency-free `./scripts/test-core.sh` runs ten
foundation contract tests covering initial state, idempotent start, input forwarding,
paused tick/physics gating, resume without a duplicate body, stale-input
clearing, stop, fresh starts, reset from every state, failed spawn/tick,
independent sessions, diagonal normalization and invalid inputs.

The suite throws on failure and exits nonzero; it does not depend on Java's
optional `assert` flag. `build` depends on these checks. Test names and a passing
count are printed to the console.

Fifteen further core tests cover neural arithmetic, finite/range validation,
immutable mutation, seeded breeding, elite preservation, controller memory,
grounded novelty scoring, physics-step budgets, the final submitted action,
pause/resume, seeded reset replay and cleanup after unavailable frames or
failed actuators/spawns. They also optimise a deterministic XOR objective and
check that selection/mutation improve the network's score while elitism keeps
the best score from regressing. This numerical check is not evidence that
Minecraft agents have learned navigation. Run just this suite with
`./gradlew :core:evolutionTest`.

## Minecraft integration checks

`./gradlew :fabric:runGameTestServer` boots a dedicated test server and executes
the separate `mineogenesis-test` mod. No graphical display or Minecraft clients
are required. The tests exercise:

1. Spawn, actual forward movement, observation availability, pause without drift,
   resume without stale input, stop/reset cleanup and disabled entity saving.
2. Explicit rotation and grounded jumping.
3. Obstructed-spawn rejection without a leftover body.
4. Command parsing, operator permissions, input gating and lifecycle commands.
5. Strafing into a wall, physical collision and the block-ahead observation.
6. Session stop after body removal, fresh restart and idempotent shutdown cleanup.

Six A2 GameTests add candidate/generation replacement with exact tick accounting,
pause/resume/reset and return to manual control, command ownership and permissions,
missing-body/shutdown cleanup, bounded champion replay without changing training
statistics, and real registered command/server-tick events driving a complete run.

Results are written to `fabric/build/test-results/gametest.xml`; logs are in
`fabric/build/gametest/logs/`. CI uploads these and the built mod as artifacts.
The test mod is excluded from the release JAR and regular client/server runs.
CI also checks that the release JAR includes the core library, client renderer
and license, and excludes integration-test code.

## Manual acceptance checks

Use a disposable Creative superflat world with cheats enabled. These checks
validate rendering and lifecycle integration beyond headless tests:

1. Start: see one named humanoid with a Steve skin. Repeated start must not add a
   second body. Fly around it and check limbs, facing and entity tracking.
2. Supply forward, backward, strafe, turn and jump inputs separately. Idle must
   not choose directions, jump or navigate around an obstacle. Verify collision
   with a solid wall and the `horizontalCollision` / raycast observations.
3. Pause during a jump: both body position and tick count must freeze. The rest
   of the world continues. Resume: gravity resumes, previous controls stay clear.
4. Stop and reset in both RUNNING and PAUSED. Confirm disappearance, retained
   ticks on stop, zero ticks on reset, and a fresh single body on the next start.
5. Leave the body's simulation distance, unload its chunk, or use `/kill` as an operator. The
   experiment must stop and allow a new start without an orphaned body.
6. Save/quit while running and reopen the world. The old body must not return;
   the new integrated-server session must be READY.
7. Use `./gradlew :fabric:runServer`, review/accept Minecraft's EULA if prompted,
   then connect with a modded client. Repeat start/control/pause/stop. A second
   observer must see the same body; a non-operator cannot run its commands.

Do not count an unperformed visual check as passing. A1 makes no throughput or
large-population claim. A later phase should measure server tick time, network
traffic and memory with increasing populations before changing tracking rates
or introducing worker threads.

## Local environment note

In the initial development session, all ten core tests passed on the local JDK.
The local Gradle launch was blocked before dependency resolution by restricted
network sockets (`java.net.SocketException: Operation not permitted`). Fabric
compilation and GameTest execution are therefore checked in GitHub Actions.
Graphical rendering and interactive observer checks require a manual client run.

## Manual evolution smoke check

Restart Minecraft with the rebuilt mod, stand in a clear superflat area and run
`/mineogenesis stop`, then `/mineogenesis evolution start 42 4 60 5`. At 20 TPS
this takes approximately a minute. Verify changing generation/candidate labels,
one body at a time, fresh spawn position each episode and generation summaries
in `/mineogenesis evolution status` and the server log. Pause/resume during an
episode, confirm counters freeze and try a manual input while neural control is
active: it must be rejected. On completion verify removal and a COMPLETED summary.
Use `evolution replay` to watch the best evaluated controller for one episode;
training counters and fitness must remain unchanged. Reset must clear the
champion, and replay without a champion must fail cleanly.

The user has confirmed the main A1 single-player spawn/movement and lifecycle
checks. A2 visual/generation checks and the multi-observer acceptance check
remain manual. Numerical or headless passes do not establish generalisation,
full-world repeatability, mining ability or population throughput.

## Acquisition checks

Seven additional core tests cover legacy input/observation compatibility,
pitch/mining output mapping, local target/pitch feedback, sixty valid hand-mining
ticks and interruption, collection-dominant bounded shaping, early termination
after actual pickup outcomes and collection success counters. The full core
suite now has 32 tests; `:core:acquisitionTest` runs the seven new checks.

Eight additional Minecraft GameTests check timed breaking and real inventory
pickup, preservation of unrelated drops, exact original-state restoration,
release/aim/pause/occlusion, the selected-block allowlist, preserving external
replacement while cleaning owned resources, candidate resets/replay, pitch
limits and three-block reach, and invalid selection/obstructed spawn cleanup.
A fixed-weight neural conformance controller also drives forward/mine inputs
through the real world to collect a log. This proves the actuator path can
complete the task, not that evolution learned that controller. The full server
suite has 20 tests including the A1/A2 cases.

For manual acceptance, use the upper-log fixture and commands in the README in
a disposable world. Verify crack animation, selection, contact pickup,
`collectedLogs=1`, target restoration and generation success reporting. Try
pause/resume, then stop/reset during both breaking and uncollected-drop phases.
The new visual acquisition checks remain manual until reported by the user.
Normal cleanup is not a persisted recovery mechanism for abrupt crashes.


## Training storage and monitoring checks (0.4.0)

Twelve workflow core tests bring the full suite to **44 tests**. They compare the
checkpoint RNG with Java Random over uniform, bounded and cached-Gaussian draws;
verify immutable population/history/metadata snapshots; compare every controller
action and final results between uninterrupted and resumed training; exercise
partial-generation boundaries/reset/progress; round-trip the binary archive;
reject corruption, truncation, unsupported versions/topologies/fitness versions and excessive
sizes; exercise 1,000 deterministic payload mutations with recomputed checksums; reject inconsistent counters and unsafe names/symlinks; check atomic
replacement and temporary-file cleanup; validate CSV columns; and exercise
coalesced background saves, explicit exports, shutdown flushing and write errors.
Run this subset with `./gradlew :core:workflowTest`.

Five additional Minecraft GameTests bring the server suite to **25 tests**:

* Complete a run, queue named save/CSV/autosave, close its session, load into a new
  session without a body, continue at the original pose, reject active/paused
  replacement, retain history/counters, and preserve results after corrupt load.
* Restore acquisition selection, refuse an outside replacement without overwriting
  it, check log-axis changes again immediately before continuation, and finish
  resumed acquisition with the original fixture intact.
* Reject malformed world/coordinate/pitch/version/unknown metadata and verify
  monitor progress at a loaded boundary, pause and reset; console subscription
  is rejected.
* Create two mock server players with embedded connections, opt each in/out
  independently through commands, confirm idempotence, prune removed players and
  clear subscriptions on shutdown. Existing registered-server-event coverage also
  queues a unique named archive, waits for the background write and decodes the
  real server-owned session's saved counters after reset.
* Configure mutation probability/sigma through both exploration/acquisition start
  grammars, round-trip them through a named archive and continuation, and reject
  out-of-range/nonfinite command parameters without spawning.

Manual acceptance still required: in a client, enable `monitor on`, pause/resume,
finish training and hide the bar. Check a second observer has no bar until they
opt in, and re-enable after respawn/reconnect. Save a named checkpoint, confirm
storage `pending=0`, save/quit/reopen, `load <name> 2` and `continue`. Verify the
original spawn, generation counter and selected-log rules. Open the exported CSV
and compare its completed-generation rows with the command/log summaries.
The headless suite does not claim client rendering or cross-restart world
repeatability. A normal session close/flush is tested; process/power loss recovery
and large-population throughput are not established.


## Offline CSV analysis tests

`python3 -m unittest discover -s scripts/tests -v` runs ten independent parser,
summary and CLI tests plus a Java-export interoperability test when its fixture
is supplied. These checks keep fitness separate from actual collection, verify
sample standard deviations/singletons, reject duplicate exports/continuations,
separate configurations/fixtures and partial budgets, reject nonfinite values,
invalid versions, inconsistent counters, malformed/oversized files and exploration
collection claims, and check parseable CLI output/nonzero failure codes.

CI creates a CSV with the production Java exporter and validates it with the
Python reader (all eleven tests, no skip). To run that contract locally after
`./scripts/test-core.sh`:

```sh
java -cp core/build/manual-tests io.github.tiraaamisuuu.mineogenesis.core.evolution.WorkflowTests core/build/manual-tests/reference.csv
MINEOGENESIS_TEST_EXPORT=core/build/manual-tests/reference.csv python3 -m unittest discover -s scripts/tests -v
```

The reference file is a deterministic core fixture, not Minecraft learning data.
No pilot dataset or learned-collection success is claimed by these tests.
