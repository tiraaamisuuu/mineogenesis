# Mineogenesis

**Neuroevolution in Minecraft.** An experimental platform for studying whether
useful behaviour can emerge through evolutionary learning.

This branch implements **Phase A1 — Foundations**: one visible, manually
controllable agent and an experiment lifecycle. The body does not make decisions.
Neural networks, genetic algorithms, fitness, mining and population experiments
are future milestones. See the [project brief](PROJECT_BRIEF.md) and
[architecture decisions](docs/ARCHITECTURE.md).

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
git switch phase-a1-foundations
./gradlew build
./gradlew :fabric:runGameTestServer
```

On Windows use `gradlew.bat`. The installable mod is
`fabric/build/libs/mineogenesis-0.1.0.jar`. Do not install the sources JAR or the
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

## Try the proof of concept

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

All commands have the `/mineogenesis` prefix and require operator permission
level 2 (or single-player cheats). Control is accepted only while RUNNING.
Forward and strafe inputs range from -1 to 1; positive strafe moves left.
Diagonal inputs are normalized. Yaw delta is degrees per server tick, from -180
to 180; positive turns right. A held jump input repeats when grounded under
vanilla living-entity jump timing. No input means no intentional movement.

## Development layout

* `core/`: plain Java state machine, environment port, action/observation records,
  dependency-free tests. No Minecraft imports.
* `fabric/src/main/`: server simulation, entity type, lifecycle and commands.
* `fabric/src/client/`: humanoid renderer using Minecraft's bundled Steve skin.
* `fabric/src/gametest/`: isolated test mod; excluded from release JARs.
* `docs/`: decisions, limits and verification procedure.

One session belongs to each Minecraft server instance. World access and input
application stay on the server thread. Bodies are transient and removed on
stop, reset or shutdown. Removing a body or leaving entity-ticking chunks stops
its run; the experiment does not force-load chunks. Stay within simulation
distance while observing, even if the body remains visible at a longer distance.

## A1 limitations

There is one invulnerable, unarmed humanoid body. It is a custom living entity,
not a connected player: no player list entry, inventory, skin service, mining,
crafting or survival mechanics. Movement uses vanilla living-entity physics,
which is not a claim of exact player-physics equivalence.

Pause affects this body only. Reset does not restore terrain, weather, time or
world randomness. No experiment persistence, configured arena, performance
benchmark or support guarantee for large populations is included yet. Agents
remain visible only within normal chunk/entity tracking distances.

## License

Project code is available under the [MIT License](LICENSE). The Gradle Wrapper
retains its upstream Apache 2.0 licensing; see [third-party notices](THIRD_PARTY_NOTICES.md).
Minecraft is required separately and is not distributed with this project.
