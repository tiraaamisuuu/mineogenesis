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
