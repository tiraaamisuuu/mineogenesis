package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.HashSet;
import java.util.Set;

/** One point per new grounded X/Z block cell, excluding the starting cell. */
public final class GroundExplorationFitness {
    private final Set<Long> visited = new HashSet<>();

    public GroundExplorationFitness(AgentFrame initial) { visited.add(cell(initial)); }

    public void record(AgentFrame frame) {
        if (frame.observation().grounded()) visited.add(cell(frame));
    }

    public int score() { return visited.size() - 1; }

    private static long cell(AgentFrame frame) {
        int x = (int) Math.floor(frame.x()), z = (int) Math.floor(frame.z());
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
