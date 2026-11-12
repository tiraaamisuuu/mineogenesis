# A1 verification

## Automated core checks

`./gradlew :core:test` or the dependency-free `./scripts/test-core.sh` runs ten
contract tests covering initial state, idempotent start, input forwarding,
paused tick/physics gating, resume without a duplicate body, stale-input
clearing, stop, fresh starts, reset from every state, failed spawn/tick,
independent sessions, diagonal normalization and invalid inputs.

The suite throws on failure and exits nonzero; it does not depend on Java's
optional `assert` flag. `build` depends on these checks. Test names and a passing
count are printed to the console.

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
5. Leave the body's chunk until it unloads, or use `/kill` as an operator. The
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
