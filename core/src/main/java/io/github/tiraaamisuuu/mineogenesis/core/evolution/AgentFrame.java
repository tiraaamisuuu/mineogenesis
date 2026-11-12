package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import java.util.Objects;

/** Position is supplied to the evaluator only, never to NeuralController. */
public record AgentFrame(AgentObservation observation, double x, double z) {
    public AgentFrame {
        Objects.requireNonNull(observation);
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            throw new IllegalArgumentException("Frame position must be within Minecraft world bounds");
        }
    }
}
