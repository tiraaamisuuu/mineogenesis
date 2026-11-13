# Training workflow checkpoint (0.4.0)

## Goal

Preserve useful progress and make runs inspectable before expanding the learning
task. Add completed-generation checkpoints, continuation of saved populations,
CSV history and a vanilla server boss bar. Keep the existing exploration and
selected-log mechanics independently testable. This is workflow infrastructure,
not evidence of reliable learned collection or a full A4 research benchmark.

## Architecture proposed before implementation

`core` owns immutable population/run snapshots, a bounded versioned binary
archive, checksum/validation, atomic file replacement and CSV formatting. The
archive carries an opaque bounded string metadata map; it imports no game
classes. No Java object deserialization or external storage dependency is used.

The Fabric adapter translates the captured world/spawn/selected-log fixture into
metadata and stores archives under the current save's `mineogenesis` directory.
It validates placement and target state again before continuation. Loading is
read-only with respect to terrain and creates no entity. Only an explicit
continue command starts the next saved generation. Existing active or paused
experiments cannot be replaced by a load.

Capture snapshots after a complete generation, with next-generation genomes
already bred, genetic RNG state (including a cached Gaussian draw), best evaluated
genome, history, cumulative counters, objective and configuration. No partial
episode/world state is serialized. Saving during a generation uses its previous
completed boundary. Before the first boundary, no resumable checkpoint exists.

Use a small explicit RNG state implementation compatible with Java Random's
48-bit generator and polar Gaussian stream; compare it directly with Java Random
in tests so 0.3.0 seeded training behaviour is preserved. Store only primitive
state, never a serialized Random object. Continuing a boundary must match an
uninterrupted deterministic core simulation, including future mutation draws.

Automatic saves are written by a bounded, single background writer using immutable
archives with no Minecraft references. Coalesce pending automatic saves to the
latest boundary. Explicit saves must either be queued or report busy; never
silently discard a named save. Surface write errors and flush pending work on
normal shutdown. Explicit loading is bounded and permitted only while idle.

Export completed-generation fitness, collection counts and tick budgets as CSV.
Display current generation/candidate, best fitness and collection successes in a
vanilla server boss bar, updated at a limited cadence. Monitoring is opt-in for
each observer and uses existing Minecraft packets, not custom client networking.

## Invariants and limits

* Snapshots, genomes, metadata and history must not expose mutable arrays/lists.
* Validate topology, format version, sizes, finite values, generation/history
  order, counters and checksum before creating a runnable population.
* Names are bounded alphanumeric/underscore/hyphen tokens, not file paths.
  Reject path traversal, unsupported files and malformed placement metadata.
* Write temporary files beside their destination and atomically replace a
  completed archive. Reject unsupported atomic replacement rather than writing
  an incomplete checkpoint over the previous good one.
* The save stores controller/training state and a fixture description. It is
  not a world backup or a restoration journal. After a crash, a changed/missing
  target must be restored by the operator or a different run selected.
* Genetic continuation can be exact in the pure deterministic test environment;
  Minecraft world events, pause effects and observers remain uncontrolled.
* Body/world access stays on the server thread. Background writers never touch
  entities, worlds or command sources. Persistence queues are bounded.

## Required checks

RNG compatibility and cached-Gaussian round trips; immutable population snapshots;
uninterrupted versus restored evolution; generation counters/history and progress;
archive round trips and corruption/version/topology/limit rejection; atomic
replacement, safe names and CSV columns; writer queue/error/shutdown behaviour.
Minecraft checks cover placement restoration without spawning on load, explicit
continuation, refusal to replace active runs, selected-target validation and
preservation of outside edits, exports/autosaves, and progress through real
registered server events. Existing A1/A2/A3 checks must remain green.


## Implemented format and lifecycle

Release 0.4.0 stores internal archive version 1 (`MNGC` magic), topology 12/8/6,
RNG version 1, fitness version 1, all configuration fields, objective, completed generation, best
fitness/totals, next-population RNG state, 158 double genes per network, champion,
ordered completed-generation records and a bounded sorted metadata map. A final
SHA-256 digest covers the whole payload. Maximum file size is 2 MiB. This is an
integrity check, not authentication. No 0.2.0/0.3.0 disk format existed to migrate.

The adapter's fixture version 1 records dimension identifier, finite bounded
spawn coordinates/yaw/pitch and, for acquisition, target coordinates/log axis.
Unknown or missing fields, missing dimensions and changed/unloaded targets fail
before replacing session state. Continuation rechecks the fixture and the entity
adapter checks ticking chunks, collision and bounds before spawning.

The writer has one daemon thread, four pending explicit requests and one pending
automatic request. Automatic requests coalesce; accepted explicit saves/exports
are retained. A single writer prevents file replacement races within a session.
`latest` is reserved for autosaves. Status records the last successful write and
last write error, with errors also logged by the server. Normal shutdown waits up
to five seconds to drain; an error/timeout is logged and never called a successful
flush. Atomic replacement requires filesystem support. This is not an fsync-based
power-loss durability guarantee. Listing/loading are bounded idle administrative
operations on the server thread; serialization/writing run in the worker.

The monitor uses vanilla server boss bars, no custom payloads or client source
changes. It updates every ten server ticks, sends only changed bar fields, prunes
removed players, and clears subscriptions on shutdown. Observation is opt-in for
operator players. Reconnect/respawn requires opting in again. Progress is the
fraction of candidate evaluation slots (including bounded within-episode progress)
over the configured total; it does not measure controller competence.

The existing seed stream is preserved. Preparing the next population even at a
configured final generation allows extension without changing any already
evaluated actions. Checkpoint creation copies immutable references/history on the
server thread; maximum population and generation bounds limit memory. Disk work
runs off-thread and never reads Minecraft state. High-generation autosave cost
and server tick performance still need a measured benchmark.


## CSV comparison contract

Export version 1 uses twenty columns, in this order:

`generation,best_fitness,mean_fitness,worst_fitness,successful_candidates,population,success_rate,episode_tick_budget,generation_ticks,total_ticks,total_successes,seed,objective,mutation_probability,mutation_sigma,network_topology,fitness_version,fixture_id,export_version,configured_generations`

`fixture_id` is SHA-256 over lexicographically sorted UTF-8 metadata key/value
pairs, with zero-byte separators. Metadata rejects control characters, so these
separators are unambiguous. It fingerprints the saved description, not terrain,
world identity or dynamic state. Preserve the matching archive/world/run notes.
`configured_generations` distinguishes an in-progress boundary from a completed
configured run. A continued run exports its entire historical sequence under the
new configured generation limit.

The analysis tool validates all rows, cumulative tick/success counters, finite
fitness/rates, configuration consistency and supported versions. It groups only
matching objective/population/tick/mutation/topology/fitness/fixture settings and
matching configured/completed generation counts. Duplicate seed/base-configuration
pairs are rejected even when exported at different boundaries, preventing a run
and continuation from being counted as independent seeds. Separate world datasets
must still be analyzed separately. The tool emits CSV and never modifies inputs.
