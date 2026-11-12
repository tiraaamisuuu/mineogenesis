package io.github.tiraaamisuuu.mineogenesis.core.evolution;

/** Local shaping plus a dominant real-inventory reward; position is evaluator-only. */
public final class EpisodeFitness {
    private final EvolutionObjective objective;
    private final GroundExplorationFitness exploration;
    private boolean seen, collected;
    private double bestProgress;

    public EpisodeFitness(AgentFrame initial, EvolutionObjective objective) {
        this.objective = java.util.Objects.requireNonNull(objective);
        exploration = new GroundExplorationFitness(initial);
    }

    public void record(AgentFrame frame) {
        exploration.record(frame);
        var observation = frame.observation();
        seen |= observation.targetAhead();
        bestProgress = Math.max(bestProgress, observation.miningProgress());
        collected |= observation.collectedTarget() > 0;
    }

    public boolean successful() { return objective == EvolutionObjective.OAK_LOG_ACQUISITION && collected; }
    public double score() {
        if (objective == EvolutionObjective.EXPLORATION) return exploration.score();
        return (collected ? 100 : 0) + (seen ? 1 : 0) + bestProgress * 10 + Math.min(5, exploration.score()) * 0.1;
    }
}
