# First A3 checkpoint: one selected oak log

## Goal and implementation boundary

Make the existing neural evolution loop capable of acquiring a real block.
The operator selects one oak log in a disposable training world; every candidate
starts from the same captured pose and can move, turn, look up/down, jump and
hold a mine input. Only that selected log can be broken. No steering, target
coordinates, auto-aim, paths or automatic mining decisions are provided.

## Architecture proposed before implementation

Extend the game-independent actuator/observation contracts with pitch, mining,
target-in-view, breaking progress and collected-log count. The network becomes
11-8-6 (150 genes): the existing eight inputs plus target-in-view, mining progress
and inventory count; six outputs provide forward, strafe, yaw, pitch, jump, mine.
The A2 eight-input/four-output genomes are not compatible; no disk checkpoints
exist yet, and the release/seed behaviour will be versioned accordingly.

Keep the exploration objective available. Add a block-acquisition evaluator
inside `core`: bounded shaping for exploration/seeing/mining plus a dominant
reward for an actually collected log. Breaking alone is not success. Successful
episodes can finish early after the final physical outcome is observed; the
tick budget is otherwise a maximum. Report collection successes per generation.

The Fabric adapter owns one target transaction per episode: verify the selected
block state, handle timed mining after body movement, create real loot drops,
permit contact pickup of only its own drops into the body's single-slot
inventory, clear crack overlays, restore its own broken target and remove
leftover drops. A temporary chunk ticket keeps the selected target available
for cleanup and is released on every normal cleanup path. It does not set the
world's persistent forced-chunk flag or keep the agent ticking beyond observers.

The custom living entity remains the visible body. Full player inventory/tool
and protection-mod semantics are outside this checkpoint. Use an explicit
bare-hand model for oak logs: hardness-based progress, normal grounded rate,
airborne/submerged penalties, no tools/enchantments/status-effect mining bonuses.
Drops come from Minecraft's loot tables. No additional player clients,
connection emulation or runtime libraries are introduced.

## Lifecycle and safety of the test fixture

Selection is read-only and is allowed only while no experiment is active.
Starting acquisition authorises that one selected log to be mined and restored
for repeated evaluations. The source world must match the selected target.
Manual acquisition controls establish the actuator/drop/pickup contract before
assessing evolved behaviour. Champion replay uses the captured training target.

Reset/stop/completion/normal shutdown remove the body and owned drops, restore
the original target state when it is still air, and release the ticket. If
another actor replaces the target with a different block, preserve that edit
and abort instead of overwriting it. A missing/unexpected target also aborts.
Do not alter surrounding terrain or collect unrelated items. Abrupt crashes
are not transactional: there is no persisted restoration journal yet. Use a
disposable training world and a player-placed log, not a valuable build.

## Reward and research limits

The main success criterion is the inventory containing the acquired oak log.
Shaping is bounded below the collection reward: seeing the target earns at most
1, best mining progress at most 10, exploration at most 0.5, collection 100.
Inputs reveal local sensory/actuator state, never the selected block coordinates.
The first task uses a selected stationary log; it is not a claim of finding
arbitrary trees, reliable learned acquisition, generalisation or survival.

## Required checks

Core tests: pitch/mining bounds, backward-compatible manual constructors,
network dimensions and output mapping, exact mining progress/reset timing,
collection-dominant fitness, no success for break-only, early completion after
physical pickup, generation success counters and reset. Preserve all A1/A2
tests and the synthetic optimisation test.

Minecraft tests: reach/line-of-sight and held-input mining; no instantaneous
break; release/aim interruption and pause; real loot/contact pickup; no unrelated
item pickup or adjacent block destruction; normal target/drop/ticket cleanup;
preserve external block replacement; candidate resets, neural command ownership
and registered server tick integration. Visual progression remains a user check.
