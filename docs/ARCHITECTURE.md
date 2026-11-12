# Foundation architecture and decisions

## Scope

The canonical vision is [PROJECT_BRIEF.md](../PROJECT_BRIEF.md), currently at the
repository root. A1 establishes experiment lifecycle and one manually controlled
body. The first evolution extension is documented separately in
[A2_EVOLUTION.md](A2_EVOLUTION.md). The scoped mining/collection extension is
documented in [A3_BLOCK_ACQUISITION.md](A3_BLOCK_ACQUISITION.md); generalised
acquisition and survival remain later phases.

## Toolchain

Use Minecraft **1.21.1**, **JDK 21**, Fabric Loader **0.16.14**, Fabric API
**0.116.6+1.21.1**, Yarn **1.21.1+build.3**, Loom **1.7.4** and Gradle **8.8**.
These are pinned released versions, not an assertion that they are the newest.
1.21.1 offers a documented, stable baseline without needing newer game features
for A1. Upgrades should be deliberate and pass the same integration tests.

## Boundaries

`core` is a plain Java library: experiment state, tick accounting, action and
observation records, and an environment interface. It imports no Minecraft,
Fabric or rendering classes. Future evolution code can depend on this boundary
without depending on the game. The A2 extension keeps inference, reproduction,
fitness and episode scheduling entirely inside this module.

`fabric` adapts that interface to one server-owned entity, server ticks and
operator commands. Its `client` source set contains rendering only. The mod
must be installed on both the server and observing clients. An integrated
single-player server runs the same simulation logic as a dedicated server.

All world access happens on the Minecraft server thread. No per-agent threads,
external client processes, custom networking or mixins are needed for A1.

## Agent representation investigation

| Approach | Benefits | Costs / limitations | Decision |
| --- | --- | --- | --- |
| Custom living entity with humanoid renderer | Normal entity tracking, no login, explicit actions, no navigation goals | Must define player-equivalent interactions later; mod required on observer | A1 proof of concept |
| Fabric FakePlayer | Player interaction context and permissions | Not a complete visible connected player; connection, player-list and tracking integration still need investigation | Revisit for block interaction context |
| Simulated server player (for example Carpet's fake players) | Closer to vanilla inventory and player actions | Connection emulation and player-list overhead; more version coupling or an extra dependency | Benchmark if exact player semantics become necessary |
| Separate bot clients | Full player protocol | Separate sessions/processes and unnecessary overhead for large populations | Excluded |

A custom `LivingEntity` avoids inheriting mob navigation and goal selectors.
It should accept only supplied movement, rotation and jump inputs. It will not
choose targets, steer around obstacles or search for blocks.

## Technical challenges and limits

* Entity appearance does not imply real-player mechanics. Reach, mining duration,
  tool effectiveness, inventory and block interaction permissions need a later
  explicit contract and tests.
* Pause must gate entity physics as well as experiment ticks; simply pausing a
  counter allows gravity and previous movement to continue.
* Cleanup must handle reset, stop, server shutdown, entity removal and chunk
  unloading. Runs also stop outside entity-ticking chunks to avoid counting
  inputs while body physics is suspended by simulation distance. A1 bodies are
  transient and must not reappear after loading a save.
* Minecraft ticking, collision and tracking remain costs per entity. A1 makes no
  population-size or throughput claim. Population benchmarks precede expansion.
* Pausing the body does not pause the world or other entities. Exploration reset
  does not restore terrain, time or weather. Acquisition restores only its own
  selected-log fixture; it is not a general world rollback. Evolution reset
  reseeds genetic operations, not the Minecraft world.
* Floating-point physics and uncontrolled world events are not a reproducible
  benchmark. Controlled arenas and world restoration are later work.

## Primary references

* [Fabric development versions](https://fabricmc.net/develop/)
* [Fabric entities](https://docs.fabricmc.net/1.21.1/develop/entities/first-entity)
* [Yarn 1.21.1 LivingEntity](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/entity/LivingEntity.html)
* [Fabric API source, pinned release](https://github.com/FabricMC/fabric-api/tree/0.116.6%2B1.21.1)
* [Carpet fake-player implementation](https://github.com/gnembon/fabric-carpet/blob/master/src/main/java/carpet/patches/EntityPlayerMPFake.java)
* [Gradle Wrapper](https://docs.gradle.org/8.8/userguide/gradle_wrapper.html)
