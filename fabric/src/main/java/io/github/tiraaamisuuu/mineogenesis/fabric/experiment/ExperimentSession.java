package io.github.tiraaamisuuu.mineogenesis.fabric.experiment;

import io.github.tiraaamisuuu.mineogenesis.core.ExperimentManager;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionRun;
import io.github.tiraaamisuuu.mineogenesis.fabric.Mineogenesis;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.MinecraftAgentEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Vec3d;

/** Server-owned integration object with one active world per server instance. */
public final class ExperimentSession {
    private final MinecraftAgentEnvironment environment = new MinecraftAgentEnvironment();
    private final ExperimentManager manager = new ExperimentManager(environment);
    private EvolutionRun evolution;
    private boolean evolutionMode;
    private int reportedGeneration;

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
        }
        evolutionMode = false;
        return manager.start();
    }

    public void startEvolution(ServerCommandSource source, EvolutionConfig config) {
        if (manager.state() == ExperimentState.RUNNING || manager.state() == ExperimentState.PAUSED || evolutionActive()) {
            throw new IllegalStateException("Stop the active experiment before starting a new evolution run");
        }
        configureSpawn(source);
        evolution = new EvolutionRun(environment, config);
        evolutionMode = true;
        reportedGeneration = 0;
        evolution.start();
        Mineogenesis.LOGGER.info("Evolution started: seed={}, population={}, ticks={}, generations={}",
                config.seed(), config.populationSize(), config.episodeTicks(), config.generations());
    }

    public boolean pause() { return evolutionMode ? evolution().pause() : manager.pause(); }

    public void stop() {
        if (evolution != null) evolution.stop();
        manager.stop();
    }

    public void reset() {
        if (evolution != null) evolution.reset();
        manager.reset();
        reportedGeneration = 0;
    }

    public void setManualInput(AgentInput input) {
        if (evolutionActive()) throw new IllegalStateException("Manual inputs are disabled during evolution; use pause or stop");
        manager.setInput(input);
    }

    public String status() {
        if (!evolutionMode) return "State=" + manager.state() + ", ticks=" + manager.elapsedTicks();
        EvolutionRun run = evolution();
        String previous = run.lastStats().map(stats -> String.format(java.util.Locale.ROOT,
                ", lastGen=%d best=%.1f mean=%.2f worst=%.1f", stats.generation(), stats.bestFitness(),
                stats.meanFitness(), stats.worstFitness())).orElse("");
        return "Evolution " + run.state() + ", seed=" + run.config().seed()
                + ", generation=" + run.generation() + "/" + run.config().generations()
                + ", agent=" + run.individual() + "/" + run.config().populationSize()
                + ", episodeTicks=" + run.episodeTicks() + "/" + run.config().episodeTicks()
                + ", totalTicks=" + run.totalTicks() + ", fitness=" + run.currentFitness()
                + ", bestEver=" + run.bestFitness() + previous
                + (run.failureReason().isBlank() ? "" : ", reason=" + run.failureReason());
    }

    private boolean evolutionActive() {
        return evolution != null && (evolution.state() == EvolutionRun.State.RUNNING
                || evolution.state() == EvolutionRun.State.PAUSED);
    }

    private void configureSpawn(ServerCommandSource source) {
        float yaw = source.getRotation().y;
        double radians = Math.toRadians(yaw);
        // Captured once per run; every candidate restarts at this same position/orientation.
        Vec3d spawn = source.getPosition().add(-Math.sin(radians) * 2, 0, Math.cos(radians) * 2);
        environment.configure(source.getWorld(), spawn, yaw);
    }

    public void tick() {
        if (evolutionMode && evolution != null) {
            var before = evolution.state();
            try { evolution.tick(); }
            catch (RuntimeException failure) { Mineogenesis.LOGGER.warn("Evolution aborted: " + evolution.failureReason(), failure); }
            evolution.lastStats().ifPresent(stats -> {
                if (stats.generation() > reportedGeneration) {
                    reportedGeneration = stats.generation();
                    Mineogenesis.LOGGER.info("Generation {}: best={}, mean={}, worst={}, bestEver={}",
                            stats.generation(), stats.bestFitness(), stats.meanFitness(), stats.worstFitness(), evolution.bestFitness());
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
            Mineogenesis.LOGGER.info("Experiment stopped: agent removed or outside entity-ticking chunks");
            return;
        }
        manager.tick();
    }

    public void close() { reset(); }
}
