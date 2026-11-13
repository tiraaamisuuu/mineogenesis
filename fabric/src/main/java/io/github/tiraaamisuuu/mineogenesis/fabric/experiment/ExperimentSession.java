package io.github.tiraaamisuuu.mineogenesis.fabric.experiment;

import io.github.tiraaamisuuu.mineogenesis.core.ExperimentManager;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionRun;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.NeuralController;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionObjective;
import io.github.tiraaamisuuu.mineogenesis.fabric.acquisition.AcquisitionTarget;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import io.github.tiraaamisuuu.mineogenesis.fabric.Mineogenesis;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.MinecraftAgentEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import io.github.tiraaamisuuu.mineogenesis.core.storage.CheckpointStore;
import io.github.tiraaamisuuu.mineogenesis.core.storage.RunArchive;
import io.github.tiraaamisuuu.mineogenesis.core.storage.TrainingWriter;
import java.io.IOException;
import java.nio.file.Path;

/** Server-owned integration object with one active world per server instance. */
public final class ExperimentSession {
    private final MinecraftAgentEnvironment environment = new MinecraftAgentEnvironment();
    private final ExperimentManager manager = new ExperimentManager(environment);
    private EvolutionRun evolution;
    private boolean evolutionMode;
    private int reportedGeneration;
    private NeuralController replayController;
    private SpawnPoint trainingSpawn;
    private AcquisitionTarget selectedTarget;
    private AcquisitionTarget trainingTarget;
    private final CheckpointStore store;
    private final TrainingWriter writer;
    private final TrainingMonitor monitor = new TrainingMonitor();
    private boolean loadedContinuation;
    private String reportedStorageError = "";

    public ExperimentSession() { store = null; writer = null; }
    public ExperimentSession(Path directory) { store = new CheckpointStore(directory); writer = new TrainingWriter(store); }
    public TrainingMonitor monitor() { return monitor; }
    public EvolutionRun evolutionOrNull() { return evolution; }

    private void requireStorage() {
        if (store == null) throw new IllegalStateException("Training storage is unavailable for this session");
    }
    private TrainingPlacement placement() {
        if (trainingSpawn == null) throw new IllegalStateException("No training placement exists");
        return new TrainingPlacement(trainingSpawn.world(), trainingSpawn.position(), trainingSpawn.yaw(), trainingSpawn.pitch(), trainingTarget);
    }
    private RunArchive archive() {
        return new RunArchive(evolution().checkpoint().orElseThrow(() -> new IllegalStateException(
                "No completed generation to save yet; wait for generation one to finish")), placement().metadata());
    }
    public void saveTraining(String name, boolean csv) {
        requireStorage(); writer.submit(name, archive(), csv);
    }
    public void loadTraining(ServerCommandSource source, String name, int additionalGenerations) {
        requireIdle(); requireStorage();
        try {
            var archive = store.load(name);
            var point = TrainingPlacement.decode(source.getServer(), archive);
            var restored = EvolutionRun.restore(environment, archive.checkpoint(), additionalGenerations);
            // Commit replacement only after the entire file and fixture have validated.
            trainingSpawn = new SpawnPoint(point.world(), point.position(), point.yaw(), point.pitch());
            trainingTarget = selectedTarget = point.target();
            evolution = restored; evolutionMode = true; replayController = null;
            reportedGeneration = archive.checkpoint().completedGeneration(); loadedContinuation = true;
            monitor.update(evolution);
        } catch (IOException failure) { throw new IllegalStateException("Cannot load checkpoint: " + failure.getMessage(), failure); }
    }
    public void continueTraining() {
        requireIdle();
        if (!loadedContinuation || evolution().state() != EvolutionRun.State.READY) {
            throw new IllegalStateException("Load a checkpoint before using evolution continue");
        }
        placement().verify(); trainingSpawn.configure(environment); environment.configureAcquisition(trainingTarget);
        evolution.start(); evolutionMode = true; loadedContinuation = false; monitor.update(evolution);
    }
    public String savedTrainingNames() {
        requireStorage();
        try { return "Saved checkpoints: " + String.join(", ", store.names()); }
        catch (IOException failure) { throw new IllegalStateException("Cannot list checkpoints: " + failure.getMessage(), failure); }
    }
    public String storageStatus() { requireStorage(); return writer.status() + "; directory=" + store.directory(); }
    public boolean storageIdle() { return writer == null || writer.idle(); }

    private record SpawnPoint(ServerWorld world, Vec3d position, float yaw, float pitch) {
        void configure(MinecraftAgentEnvironment environment) { environment.configure(world, position, yaw, pitch); }
    }

    public ExperimentManager manager() { return manager; }

    public EvolutionRun evolution() {
        if (evolution == null) throw new IllegalStateException("No evolution run exists; use evolution start");
        return evolution;
    }

    public boolean start(ServerCommandSource source) {
        if (evolutionMode && evolution != null && evolution.state() == EvolutionRun.State.PAUSED) {
            return evolution.resume();
        }
        if (evolutionActive()) return false;
        if (manager.state() == ExperimentState.READY || manager.state() == ExperimentState.STOPPED) {
            configureSpawn(source);
            environment.configureAcquisition(null);
            replayController = null;
        } else if (manager.state() == ExperimentState.PAUSED && replayController != null) {
            replayController.resetMemory();
        }
        evolutionMode = false;
        return manager.start();
    }

    public void startEvolution(ServerCommandSource source, EvolutionConfig config) {
        startEvolution(source, config, EvolutionObjective.EXPLORATION);
    }

    public void startAcquisition(ServerCommandSource source, EvolutionConfig config) {
        targetFor(source).verifyOriginal();
        startEvolution(source, config, EvolutionObjective.OAK_LOG_ACQUISITION);
    }

    private void requireIdle() {
        if (manager.state() == ExperimentState.RUNNING || manager.state() == ExperimentState.PAUSED || evolutionActive()) {
            throw new IllegalStateException("Stop the active experiment before changing modes or targets");
        }
    }

    private void startEvolution(ServerCommandSource source, EvolutionConfig config, EvolutionObjective objective) {
        requireIdle();
        trainingSpawn = configureSpawn(source);
        trainingTarget = objective == EvolutionObjective.OAK_LOG_ACQUISITION ? targetFor(source) : null;
        environment.configureAcquisition(trainingTarget);
        replayController = null;
        evolution = new EvolutionRun(environment, config, objective);
        evolutionMode = true;
        reportedGeneration = 0;
        loadedContinuation = false;
        evolution.start();
        Mineogenesis.LOGGER.info("Evolution started: objective={}, seed={}, population={}, ticks={}, generations={}",
                objective, config.seed(), config.populationSize(), config.episodeTicks(), config.generations());
    }

    public boolean pause() { return evolutionMode ? evolution().pause() : manager.pause(); }

    public void selectTarget(ServerCommandSource source) {
        requireIdle();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            throw new IllegalArgumentException("A player must look at the oak log to select it");
        }
        Vec3d eye = player.getEyePos();
        var hit = source.getWorld().raycast(new RaycastContext(eye, eye.add(player.getRotationVec(1).multiply(8)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) throw new IllegalArgumentException("Look at an oak log within eight blocks");
        selectedTarget = AcquisitionTarget.capture(source.getWorld(), hit.getBlockPos());
    }

    /** Also supports command-block/test fixtures without manufacturing a player. */
    public void selectTarget(ServerCommandSource source, net.minecraft.util.math.BlockPos position) {
        requireIdle();
        if (source.getPosition().squaredDistanceTo(Vec3d.ofCenter(position)) > 64) {
            throw new IllegalArgumentException("Target must be within eight blocks of the command source");
        }
        selectedTarget = AcquisitionTarget.capture(source.getWorld(), position);
    }

    public void clearTarget() { requireIdle(); selectedTarget = null; }

    public String targetStatus() {
        return selectedTarget == null ? "No acquisition target selected" : "Selected oak log at " + selectedTarget.position().toShortString()
                + " in " + selectedTarget.world().getRegistryKey().getValue();
    }

    public String acquisitionStatus() {
        String body = manager.observation().map(observation -> String.format(java.util.Locale.ROOT,
                "; targetInView=%s mining=%.2f collectedLogs=%d", observation.targetAhead(),
                observation.miningProgress(), observation.collectedTarget())).orElse("; no active body");
        return targetStatus() + "; " + status() + body;
    }

    private AcquisitionTarget targetFor(ServerCommandSource source) {
        if (selectedTarget == null) throw new IllegalStateException("Select an oak log with acquisition target first");
        if (selectedTarget.world() != source.getWorld()) throw new IllegalArgumentException("Selected target is in a different world");
        return selectedTarget;
    }

    public void startManualAcquisition(ServerCommandSource source) {
        requireIdle();
        AcquisitionTarget target = targetFor(source);
        target.verifyOriginal();
        configureSpawn(source);
        environment.configureAcquisition(target);
        replayController = null;
        evolutionMode = false;
        manager.start();
    }

    public void setManualMine(boolean mine) {
        var old = manager.input();
        setManualInput(new AgentInput(old.forward(), old.strafe(), old.yawDelta(), old.pitchDelta(), old.jump(), mine));
    }

    public void setManualView(float yaw, float pitch) {
        var old = manager.input();
        setManualInput(new AgentInput(old.forward(), old.strafe(), 0, 0, old.jump(), old.mine()));
        environment.setView(yaw, pitch);
    }

    public void replayChampion() {
        if (manager.state() == ExperimentState.RUNNING || manager.state() == ExperimentState.PAUSED || evolutionActive()) {
            throw new IllegalStateException("Stop the active experiment before replaying the champion");
        }
        var genome = evolution().champion().orElseThrow(() -> new IllegalStateException("No completed candidate to replay yet"));
        trainingSpawn.configure(environment);
        environment.configureAcquisition(trainingTarget);
        manager.start();
        environment.setRunning(false);
        environment.label("Best network | fitness " + evolution.bestFitness());
        replayController = new NeuralController(genome);
        evolutionMode = false;
    }

    public void stop() {
        if (evolution != null) evolution.stop();
        manager.stop();
        replayController = null;
    }

    public void reset() {
        if (evolution != null) evolution.reset();
        manager.reset();
        reportedGeneration = 0;
        replayController = null;
        loadedContinuation = false;
    }

    public void setManualInput(AgentInput input) {
        if (evolutionActive() || replayController != null) {
            throw new IllegalStateException("Manual inputs are disabled during neural control; use pause or stop");
        }
        manager.setInput(input);
    }

    public String status() {
        if (!evolutionMode) return (replayController == null ? "" : "Champion replay: ")
                + "State=" + manager.state() + ", ticks=" + manager.elapsedTicks();
        return evolutionStatus();
    }

    public String evolutionStatus() {
        EvolutionRun run = evolution();
        String previous = run.lastStats().map(stats -> String.format(java.util.Locale.ROOT,
                ", lastGen=%d best=%.1f mean=%.2f worst=%.1f successes=%d/%d", stats.generation(), stats.bestFitness(),
                stats.meanFitness(), stats.worstFitness(), stats.successfulCandidates(), run.config().populationSize())).orElse("");
        return "Evolution " + run.state() + ", objective=" + run.objective() + ", seed=" + run.config().seed()
                + ", generation=" + run.generation() + "/" + run.config().generations()
                + ", agent=" + run.individual() + "/" + run.config().populationSize()
                + ", episodeTicks=" + run.episodeTicks() + "/" + run.config().episodeTicks()
                + ", totalTicks=" + run.totalTicks() + ", fitness=" + run.currentFitness()
                + ", bestEver=" + run.bestFitness() + ", collectedEpisodes=" + run.totalSuccesses() + previous
                + (run.failureReason().isBlank() ? "" : ", reason=" + run.failureReason());
    }

    private boolean evolutionActive() {
        return evolution != null && (evolution.state() == EvolutionRun.State.RUNNING
                || evolution.state() == EvolutionRun.State.PAUSED);
    }

    private SpawnPoint configureSpawn(ServerCommandSource source) {
        float yaw = source.getRotation().y;
        double radians = Math.toRadians(yaw);
        // Captured once per run; every candidate restarts at this same position/orientation.
        Vec3d spawn = source.getPosition().add(-Math.sin(radians) * 2, 0, Math.cos(radians) * 2);
        var point = new SpawnPoint(source.getWorld(), spawn, yaw, source.getRotation().x);
        point.configure(environment);
        return point;
    }

    public void tick() {
        monitor.tick(evolution);
        if (writer != null) {
            String status = writer.status(); int errorIndex = status.indexOf("lastError=");
            if (errorIndex >= 0 && !status.substring(errorIndex).equals(reportedStorageError)) {
                reportedStorageError = status.substring(errorIndex); Mineogenesis.LOGGER.error("Training storage: {}", reportedStorageError);
            }
        }
        if (evolutionMode && evolution != null) {
            var before = evolution.state();
            try { evolution.tick(); }
            catch (RuntimeException failure) { Mineogenesis.LOGGER.warn("Evolution aborted: " + evolution.failureReason(), failure); }
            evolution.lastStats().ifPresent(stats -> {
                if (stats.generation() > reportedGeneration) {
                    reportedGeneration = stats.generation();
                    if (writer != null) writer.autosave(archive());
                    Mineogenesis.LOGGER.info("Generation {}: best={}, mean={}, worst={}, successes={}, bestEver={}",
                            stats.generation(), stats.bestFitness(), stats.meanFitness(), stats.worstFitness(),
                            stats.successfulCandidates(), evolution.bestFitness());
                }
            });
            if (before != EvolutionRun.State.COMPLETED && evolution.state() == EvolutionRun.State.COMPLETED) {
                Mineogenesis.LOGGER.info("Evolution completed: {}", status());
            }
            return;
        }
        if (manager.state() != ExperimentState.RUNNING && manager.state() != ExperimentState.PAUSED) return;
        if (!environment.available()) {
            manager.stop();
            replayController = null;
            Mineogenesis.LOGGER.info("Experiment stopped: agent removed or outside entity-ticking chunks");
            return;
        }
        if (replayController != null && manager.state() == ExperimentState.RUNNING) {
            if (manager.elapsedTicks() >= evolution.config().episodeTicks()) {
                manager.stop();
                replayController = null;
                Mineogenesis.LOGGER.info("Champion replay finished");
                return;
            }
            try {
                environment.setRunning(true);
                manager.setInput(replayController.decide(manager.observation().orElseThrow()));
            } catch (RuntimeException failure) {
                manager.stop();
                replayController = null;
                Mineogenesis.LOGGER.warn("Champion replay aborted", failure);
                return;
            }
        }
        manager.tick();
    }

    public void close() {
        try { reset(); }
        finally {
            monitor.close();
            if (writer != null) try { writer.close(); }
            catch (IOException failure) { Mineogenesis.LOGGER.error("Could not flush training storage", failure); }
        }
    }
}
