package io.github.tiraaamisuuu.mineogenesis.core;

import java.util.Objects;
import java.util.Optional;

/** One experiment, called only by its owning simulation thread. No game imports. */
public final class ExperimentManager {
    private final AgentEnvironment environment;
    private ExperimentState state = ExperimentState.READY;
    private AgentInput input = AgentInput.IDLE;
    private long elapsedTicks;

    public ExperimentManager(AgentEnvironment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    /** Start a fresh run or resume a paused run. Returns false if already running. */
    public boolean start() {
        if (state == ExperimentState.RUNNING) return false;
        if (state != ExperimentState.PAUSED) {
            environment.spawn();
            elapsedTicks = 0;
        }
        environment.setRunning(true);
        input = AgentInput.IDLE;
        state = ExperimentState.RUNNING;
        return true;
    }

    public boolean pause() {
        if (state != ExperimentState.RUNNING) return false;
        environment.setRunning(false);
        input = AgentInput.IDLE;
        state = ExperimentState.PAUSED;
        return true;
    }

    /** End the run and remove the body; preserve elapsed ticks for inspection. */
    public boolean stop() {
        if (state == ExperimentState.STOPPED) return false;
        environment.remove();
        input = AgentInput.IDLE;
        state = ExperimentState.STOPPED;
        return true;
    }

    /** Return to READY. This does not restore terrain or other world state. */
    public void reset() {
        environment.remove();
        input = AgentInput.IDLE;
        elapsedTicks = 0;
        state = ExperimentState.READY;
    }

    public void setInput(AgentInput input) {
        if (state != ExperimentState.RUNNING) {
            throw new IllegalStateException("Start the experiment before supplying input");
        }
        this.input = Objects.requireNonNull(input);
    }

    /** Count successfully supplied simulation ticks, never wall-clock time. */
    public void tick() {
        if (state != ExperimentState.RUNNING) return;
        environment.apply(input);
        elapsedTicks++;
    }

    public ExperimentState state() { return state; }
    public long elapsedTicks() { return elapsedTicks; }
    public Optional<AgentObservation> observation() { return environment.observe(); }
}
