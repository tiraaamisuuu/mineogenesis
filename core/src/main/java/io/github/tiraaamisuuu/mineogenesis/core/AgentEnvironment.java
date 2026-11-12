package io.github.tiraaamisuuu.mineogenesis.core;

import java.util.Optional;

/** Server-thread-confined port. Implementations own the physical body and its cleanup. */
public interface AgentEnvironment {
    /** Create a single transient body. On failure, leave no body behind. */
    void spawn();

    /** Gate physics, including clearing previous velocity and inputs when freezing. */
    void setRunning(boolean running);

    /** Supply this tick's actuators. No sensing or decision-making is implicit. */
    void apply(AgentInput input);

    Optional<AgentObservation> observe();

    /** Idempotently remove the body, including a paused or unloaded body. */
    void remove();
}
