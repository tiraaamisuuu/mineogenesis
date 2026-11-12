package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import java.util.Objects;

/** Converts local observations to actuators; never sees position, fitness or a target. */
public final class NeuralController {
    private final NetworkGenome genome;
    private final double[] inputs = new double[NetworkGenome.INPUTS];
    private final double[] hidden = new double[NetworkGenome.HIDDEN];
    private final double[] outputs = new double[NetworkGenome.OUTPUTS];
    private AgentInput previous = AgentInput.IDLE;

    public NeuralController(NetworkGenome genome) { this.genome = Objects.requireNonNull(genome); }

    public AgentInput decide(AgentObservation observation) {
        double yaw = Math.toRadians(observation.yaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        inputs[0] = unit(-observation.velocityX() * sin + observation.velocityZ() * cos);
        inputs[1] = unit(observation.velocityX() * cos + observation.velocityZ() * sin);
        inputs[2] = unit(observation.velocityY());
        inputs[3] = observation.grounded() ? 1 : 0;
        inputs[4] = observation.horizontalCollision() ? 1 : 0;
        inputs[5] = observation.blockAhead().equals("minecraft:air") ? 0 : 1;
        inputs[6] = previous.forward();
        inputs[7] = previous.strafe();
        genome.evaluate(inputs, hidden, outputs);
        previous = new AgentInput((float) outputs[0], (float) outputs[1],
                (float) (outputs[2] * 10), outputs[3] > 0.5);
        return previous;
    }

    public void resetMemory() { previous = AgentInput.IDLE; }

    private static double unit(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite observation");
        return Math.clamp(value, -1, 1);
    }
}
