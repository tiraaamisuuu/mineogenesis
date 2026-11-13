# Mineogenesis

**Neuroevolution in Minecraft.** An experimental platform for studying whether
useful behaviour can emerge through evolutionary learning.

This branch implements **A1 foundations, A2 evolution, a first A3 acquisition checkpoint and resumable training**:
a manually controllable humanoid plus seeded neural populations evaluated through
one visible body at a time. Networks evolve through selection, mutation and
elitism, using ground-exploration or selected oak-log collection objectives. See the
[project brief](PROJECT_BRIEF.md), [foundation decisions](docs/ARCHITECTURE.md),
the [A2 design](docs/A2_EVOLUTION.md), [A3 design and limitations](docs/A3_BLOCK_ACQUISITION.md)
and [training persistence design](docs/TRAINING_WORKFLOW.md).

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
git switch phase-a4-training-workflow
./gradlew build
./gradlew :fabric:runGameTestServer
```

On Windows use `gradlew.bat`. The installable mod is
`fabric/build/libs/mineogenesis-0.4.0.jar`. Do not install the sources JAR or the
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

## Try selected oak-log acquisition

Use a disposable Creative superflat training world with cheats. The experiment
temporarily breaks **one explicitly selected log**, restoring its original state
on normal candidate reset, stop, completion and shutdown. An abrupt process crash
has no persisted recovery journal yet. Use a player-placed test log and keep
other actors from editing the fixture.

Restart Minecraft after rebuilding. On NixOS:

```sh
nix-shell --run './gradlew --no-daemon :fabric:runClient'
```

Build a two-block-high oak-log pillar on a clear, level surface. Stand on the
ground about **four blocks from it**, and look at the **upper log**. This puts
the body, which spawns two blocks ahead of you, within mining reach without
spawning inside the pillar. Then run:

```mcfunction
/mineogenesis stop
/mineogenesis acquisition target
/mineogenesis acquisition start 42 4 160 3
/mineogenesis acquisition status
```

This evaluates four candidates for up to eight seconds each over three
generations: at most **96 seconds** at 20 TPS, shorter if candidates collect the
log. Each candidate starts with a fresh body and the selected log restored.
The network controls movement, yaw, pitch, jump and a held mining input. Only
the selected log can be broken. Mining requires a clear three-block eye ray
and sustained input; breaking alone is not success. The log must drop as a
real item and enter the body's inventory through contact with its own loot.
Other world items are not collected.

Status reports target visibility, mining progress, collected logs, fitness and
generation collection successes. The main reward is 100 for actual collection;
seeing the target, partial mining and exploration provide at most 11.5 total
shaping points. An episode can finish early only after pickup is observed.
Random initial controllers may fail, and a short run is not a guarantee of
learned acquisition. The test demonstrates a task interface, not general tree
search, automatic aiming or survival.

| Command suffix after `/mineogenesis` | Behaviour |
| --- | --- |
| `acquisition target` | Select the oak log you look at, within eight blocks; no terrain edit |
| `acquisition target <x> <y> <z>` | Select an oak log within eight blocks using coordinates, including relative coordinates |
| `acquisition clear` | Clear idle target selection |
| `acquisition start [seed]` | Start acquisition defaults: 16 candidates, 200 ticks maximum, 10 generations |
| `acquisition start <seed> <population> <ticks> <generations>` | Start a bounded configured acquisition run |
| `acquisition manual` | Spawn a manually controlled body with the same selected-log actuator rules |
| `acquisition status` | Show target, current mode, progress and inventory/success counters |
| `agent mine <true/false>` | Set held mining in manual mode while preserving other manual inputs |
| `agent view <yaw> <pitch>` | Set manual view angles and clear angular velocity; positive pitch looks down |

Root `pause`, `start` (resume), `stop`, `reset`, plus `evolution status` and
`evolution replay`, also apply to neural acquisition. Target changes and manual
inputs are rejected during neural control. Reset clears results while keeping
the target selection as configuration; `acquisition clear` clears the selection.
The acquisition defaults take up to **26 minutes 40 seconds** at 20 TPS.

For a manual mechanics check with the same upper-log fixture, run:

```mcfunction
/mineogenesis stop
/mineogenesis acquisition manual
/mineogenesis agent mine true
/mineogenesis acquisition status
```

The selected log should crack and break after roughly three seconds of valid
grounded hand mining. Then use `/mineogenesis agent control 1 0 0 false` to
approach the drop, and inspect `collectedLogs=1` with `acquisition status`.
Stopping removes the carrying body and restores the target. There is one
inventory slot, no player inventory interface and no delivery of farmed logs
to the observer.

The mining profile uses `1 / (hardness * 30)` progress per valid grounded tick,
with fivefold airborne and submerged penalties. Tools, enchantments, status
effects, full player interaction permissions and arbitrary blocks are outside
this checkpoint. Pause freezes body movement and mining; the surrounding world
and item-entity physics continue. A temporary target-chunk ticket permits normal
cleanup without changing persistent forced chunks. Outside edits are preserved;
an unexpected block or missing owned drop aborts the trial.

## Try exploration evolution

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
champion in memory; completed-generation disk checkpoints remain available.

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

## Save, resume and inspect training

Every completed generation automatically queues `latest.mng` inside the current
world's `mineogenesis/` directory. This includes the next population, genetic
random state, best evaluated network, configuration, history and original fixture.
Saves do **not** contain a running entity or a partial episode. Stop/reset never
delete disk saves. `latest` belongs to autosaving and is replaced by later runs;
use a named save to retain a particular experiment.

For a quick exploration check, start `evolution start 42 4 60 5`, optionally enable
`monitor on`, and wait for at least generation one to finish. Then:

```mcfunction
/mineogenesis evolution save trial42
/mineogenesis evolution export trial42
/mineogenesis evolution storage
/mineogenesis stop
/mineogenesis evolution load trial42 5
/mineogenesis evolution continue
```

Wait for storage status to show `pending=0` and the successful write before
loading a newly queued file. The save command reports acceptance into a bounded
background queue; disk errors appear in storage status and the server log.
Normal world shutdown flushes accepted saves. Before generation one completes,
there is no checkpoint to save or export. During a generation these commands use
the **previous completed boundary**, including its champion and counters.

`load trial42 5` stages five more generations after that boundary. Loading
creates no body and changes no blocks. `continue` explicitly starts at the saved
spawn, regardless of where the command caller stands. Stay near that area so its
chunks tick. The total generation limit remains 10,000. You can quit/reopen the
world between saving and loading; training never restarts automatically.

| Command suffix after `/mineogenesis` | Behaviour |
| --- | --- |
| `evolution save <name>` | Queue the last completed boundary as `<name>.mng` |
| `evolution load <name> <additionalGenerations>` | Validate and stage a saved population while idle |
| `evolution continue` | Start the next generation from the staged boundary |
| `evolution export <name>` | Queue completed-generation statistics as `<name>.csv` |
| `evolution files` | List saved checkpoint names, including `latest` |
| `evolution storage` | Show pending writes, last result/error and the storage directory |
| `monitor on` / `monitor off` | Show/hide your training boss bar; operator player only |

Names are 1–48 letters, digits, underscores or hyphens, beginning with a letter
or digit. Archives have a version, network topology and SHA-256 checksum, bounded
sizes and validated counters. Corrupt/incompatible files are rejected without
replacing existing results. A checksum detects corruption; it is not a signature.
The `.mng` format is internal version 1, with no old-version migration yet.

CSV contains generation best/mean/worst fitness, successful candidates, population,
success rate, per-candidate tick budget, actual generation/cumulative ticks,
cumulative successes, seed and objective. It contains only completed generations;
replay and partial episodes do not change it. The opt-in vanilla boss bar updates
at most twice per second, shows generation/candidate/best/collections, freezes
progress while paused and fills on completion. Enable it again after respawning
or reconnecting. Its percentage measures evaluation slots, not learning quality.

Acquisition saves require the same dimension, loaded spawn and exact original
selected oak-log state, including axis, at load and again before continuation.
They never recreate a missing log or overwrite an outside edit. Restore the
fixture yourself if it changed. Checkpoints are **not world backups** and do not
recover terrain after an abrupt crash. Genetic continuation is checked exactly
in a deterministic core simulation; Minecraft world outcomes remain affected by
time, other actors and physics. Reliable learned collection is still unproven.

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
  genomes, reproduction, fitness, episode scheduling and validated storage. No Minecraft imports.
* `fabric/src/main/`: server simulation, entity type, lifecycle and commands.
* `fabric/src/client/`: humanoid renderer using Minecraft's bundled Steve skin.
* `fabric/src/gametest/`: isolated test mod; excluded from release JARs.
* `docs/`: decisions, limits and verification procedure.

One session belongs to each Minecraft server instance. World access and input
application stay on the server thread. Bodies are transient and removed on
stop, reset or shutdown. Removing a body or leaving entity-ticking chunks stops
its run. Exploration does not force-load chunks; acquisition temporarily leases
the selected target chunk for cleanup. Stay within simulation
distance while observing, even if the body remains visible at a longer distance.

## Current limitations

There is one invulnerable, unarmed humanoid body. It is a custom living entity,
with a single acquisition inventory slot and no player list entry, skin service,
crafting or survival mechanics. Movement uses vanilla living-entity physics,
which is not a claim of exact player-physics equivalence.

Pause affects this body only. Reset does not restore terrain, weather, time or
world randomness. Generation-boundary checkpoints preserve training state, not the world. No configured
arena, performance benchmark or support guarantee for large populations is included yet. Agents
remain visible only within normal chunk/entity tracking distances.

The seed reproduces network initialization, selection and mutation, not an
entire Minecraft world. All candidates restart at the same position and yaw,
but observers, other entities, blocks, time and weather can affect outcomes.
Use a quiet, unchanged area. Position is used only by the fitness evaluator;
networks receive local velocities, ground/collision/block-ahead flags and their
previous movement inputs, plus local target-in-view, mining progress, inventory
count and own pitch. There are no target coordinates in network inputs. The
model is now a fixed 12-8-6 tanh network (158 weights and biases); seeds do not
replay the old 0.2.0 topology. Controlled arenas, simultaneous agent populations
and generalised block acquisition remain later milestones.

## License

Project code is available under the [MIT License](LICENSE). The Gradle Wrapper
retains its upstream Apache 2.0 licensing; see [third-party notices](THIRD_PARTY_NOTICES.md).
Minecraft is required separately and is not distributed with this project.
