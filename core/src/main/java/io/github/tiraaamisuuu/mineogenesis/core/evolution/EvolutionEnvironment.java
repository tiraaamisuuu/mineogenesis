package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentEnvironment;
import java.util.Optional;

/** Adds an evaluation frame to the existing actuator/body lifecycle port. */
public interface EvolutionEnvironment extends AgentEnvironment {
    Optional<AgentFrame> frame();
}
