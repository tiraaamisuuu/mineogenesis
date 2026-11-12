# Mineogenesis

**Neuroevolution in Minecraft.** An experimental platform for studying whether
useful behaviour can emerge through evolutionary learning.

This branch implements **A1 foundations and the first A2 evolution checkpoint**:
a manually controllable humanoid plus seeded neural populations evaluated through
one visible body at a time. Networks evolve through selection, mutation and
elitism, using a small ground-exploration objective. See the
[project brief](PROJECT_BRIEF.md), [foundation decisions](docs/ARCHITECTURE.md)
and [A2 design and limitations](docs/A2_EVOLUTION.md).

## Requirements

| Component | Pinned baseline |
| --- | --- |
| Minecraft Java Edition | 1.21.1 |
| JDK | 21 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.116.6+1.21.1 |
| Yarn | 1.21.1+build.3 |
| Fabric Loom | 1.7.4 |
| Gradle | 8.8, through the included Wrapper |

Use JDK 21 for Gradle itself as well as compilation. A system Gradle installation
is unnecessary. The first build needs internet access to download the pinned
toolchain and Minecraft dependencies.

## Build and test

```sh
git clone https://github.com/tiraaamisuuu/mineogenesis.git
cd mineogenesis
git switch phase-a2-evolution
./gradlew build
./gradlew :fabric:runGameTestServer
```

On Windows use `gradlew.bat`. The installable mod is
`fabric/build/libs/mineogenesis-0.2.0.jar`. Do not install the sources JAR or the
separate core JAR; the mod includes core automatically.

On NixOS, use the included development shell:

```sh
nix-shell --run './gradlew build'
nix-shell --run './gradlew --no-daemon :fabric:runClient'
```

`shell.nix` supplies Java 21 and the native graphics, windowing and audio
libraries loaded by LWJGL. It also exposes the system GPU drivers through
`/run/opengl-driver/lib`, while preserving an existing library path. A JDK-only
shell can build the mod but may fail to launch Minecraft with
`GLFW error 65542: GLX: Failed to load GLX`. These are development-shell
dependencies; they are not bundled into the mod. The shell uses your installed
`<nixpkgs>` channel, so its system packages are not independently pinned.

The core tests can also run offline with a JDK, without Gradle or Minecraft:

```sh
./scripts/test-core.sh
```

The [test guide](docs/TESTING.md) distinguishes automated checks from the manual
rendering/observer checks. CI builds the client and server code, runs core tests
and launches the headless Minecraft integration-test server.

## Try evolution

Restart the development client after rebuilding; an already-running Minecraft
process keeps its loaded classes. On NixOS:

```sh
nix-shell --run './gradlew --no-daemon :fabric:runClient'
```

Create a Creative superflat world with cheats and a clear, level area. Stand on
the ground, stay near the experiment and run:

```mcfunction
/mineogenesis stop
/mineogenesis evolution start 42 4 60 5
/mineogenesis evolution status
```

This is a roughly **one-minute** smoke run at 20 ticks per second: seed 42,
four candidates per generation, 60 physics ticks per candidate, five generations.
Each nameplate shows its generation and candidate number. A fresh body appears
at the captured spawn point for every candidate; only one is visible at once.
At generation boundaries the best network survives unchanged and mutated
offspring fill the remaining slots. There are no programmed paths or steering
rules: every actuator input comes from the neural controller.

Fitness is one point for each new X/Z block cell visited while grounded,
excluding the starting cell. Revisits and airborne cells do not earn points.
This is an initial pipeline check; straight walking can score well on a flat
world, a small run may show no improvement, and high fitness here does not imply
mining, obstacle navigation or survival.

Use `/mineogenesis evolution status` to inspect current progress, best-ever
fitness and the last completed generation's best/mean/worst scores. Server logs
record one summary per generation. Once a run finishes its body disappears and
the best network stays available in memory. Watch that network for one episode:

```mcfunction
/mineogenesis evolution replay
```

Replay uses the original training spawn and a fresh body. It does not change
training scores. Stop an active run before replaying an already evaluated
candidate. Reset, a new evolution run or server shutdown discards the old
champion; there is no disk checkpoint yet.

| Command suffix after `/mineogenesis` | Behaviour |
| --- | --- |
| `evolution start` | Start defaults: seed 42, 16 candidates, 100 ticks, 10 generations |
| `evolution start <seed>` | Defaults with a chosen 64-bit seed |
| `evolution start <seed> <population> <ticks> <generations>` | Start a configured, bounded run |
| `evolution status` | Show training state, counters, seed and fitness summaries, including after replay |
| `evolution pause` / `evolution resume` | Freeze/resume the current training run |
| `evolution stop` | Remove the body, retain the evaluated champion and summaries |
| `evolution reset` | Remove the body, clear results and reseed the population |
| `evolution replay` | Replay the best evaluated network for the training episode budget |

Population range is 2..256, episode budget 1..12000 ticks, generations 1..10000.
The default run takes about **13 minutes 20 seconds** at 20 TPS. Lower TPS
extends wall-clock duration. Mutation probability is 0.10, Gaussian sigma 0.20,
tournament size 3, and one elite is preserved. These settings are documented in
[A2_EVOLUTION.md](docs/A2_EVOLUTION.md).

Root `pause`, `stop`, `reset` and `status` apply to the current mode. Root `start`
resumes a paused training run or replay; when there is no active/paused run it
starts manual mode. Manual `agent control` and `agent idle` are rejected during
training and replay. Reset clears the whole session, including saved-in-memory
training results. Missing/dead bodies or non-ticking chunks abort evaluation
without breeding a partial generation.

## Try manual control

Run `./gradlew :fabric:runClient`, create a Creative superflat world with cheats
enabled, and stand on a clear, flat surface. Alternatively, install the mod and
the pinned Fabric API in a Fabric 1.21.1 launcher profile. Both an observing
client and a dedicated server need the mod. No extra clients are required for
the simulated body.

Use the following commands in order:

```mcfunction
/mineogenesis start
/mineogenesis agent observe
/mineogenesis agent control 1 0 0 false
/mineogenesis pause
/mineogenesis status
/mineogenesis start
/mineogenesis agent control 0 0 5 false
/mineogenesis agent idle
/mineogenesis agent control 0 0 0 true
/mineogenesis stop
/mineogenesis reset
```

`start` spawns **Agent 1** two blocks ahead, facing your direction. If that space
is obstructed, move to a clear area and try again. A paused run resumes in place
when you use `start`; the previous input is cleared. Fly in Creative or switch
to Spectator to observe. Observer mode is not changed automatically.

| Command | Behaviour |
| --- | --- |
| `start` | Create a fresh body from READY/STOPPED; resume from PAUSED; no-op while RUNNING |
| `pause` | Freeze agent physics and tick accounting; clear controls |
| `stop` | Remove the body; retain the elapsed tick count |
| `reset` | Remove the body; clear ticks; return to READY |
| `status` | Show lifecycle state and elapsed simulation ticks |
| `agent control <forward> <strafe> <yawDelta> <jump>` | Hold explicit inputs until replaced, paused or stopped |
| `agent idle` | Clear actuator inputs; normal momentum and gravity still apply |
| `agent observe` | Show velocity, view angles, grounded/collision flags and the block in a three-block raycast |

All commands require operator permission level 2 (or single-player cheats).
Manual control is accepted only while a manual run is RUNNING.
Forward and strafe inputs range from -1 to 1; positive strafe moves left.
Diagonal inputs are normalized. Yaw delta is degrees per server tick, from -180
to 180; positive turns right. A held jump input repeats when grounded under
vanilla living-entity jump timing. No input means no intentional movement.

## Development layout

* `core/`: plain Java lifecycle, actuator/observation records, neural inference,
  genomes, reproduction, fitness and episode scheduling. No Minecraft imports.
* `fabric/src/main/`: server simulation, entity type, lifecycle and commands.
* `fabric/src/client/`: humanoid renderer using Minecraft's bundled Steve skin.
* `fabric/src/gametest/`: isolated test mod; excluded from release JARs.
* `docs/`: decisions, limits and verification procedure.

One session belongs to each Minecraft server instance. World access and input
application stay on the server thread. Bodies are transient and removed on
stop, reset or shutdown. Removing a body or leaving entity-ticking chunks stops
its run; the experiment does not force-load chunks. Stay within simulation
distance while observing, even if the body remains visible at a longer distance.

## Current limitations

There is one invulnerable, unarmed humanoid body. It is a custom living entity,
not a connected player: no player list entry, inventory, skin service, mining,
crafting or survival mechanics. Movement uses vanilla living-entity physics,
which is not a claim of exact player-physics equivalence.

Pause affects this body only. Reset does not restore terrain, weather, time or
world randomness. No experiment persistence, configured arena, performance
benchmark or support guarantee for large populations is included yet. Agents
remain visible only within normal chunk/entity tracking distances.

The seed reproduces network initialization, selection and mutation, not an
entire Minecraft world. All candidates restart at the same position and yaw,
but observers, other entities, blocks, time and weather can affect outcomes.
Use a quiet, unchanged area. Position is used only by the fitness evaluator;
networks receive local velocities, ground/collision/block-ahead flags and their
previous movement inputs, with no target coordinates. The model is a fixed
8-8-4 tanh network (108 weights and biases). Checkpoints, controlled arenas,
simultaneous agent populations and block acquisition are later milestones.

## License

Project code is available under the [MIT License](LICENSE). The Gradle Wrapper
retains its upstream Apache 2.0 licensing; see [third-party notices](THIRD_PARTY_NOTICES.md).
Minecraft is required separately and is not distributed with this project.
