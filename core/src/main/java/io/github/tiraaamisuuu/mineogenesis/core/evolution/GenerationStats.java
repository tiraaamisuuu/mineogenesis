package io.github.tiraaamisuuu.mineogenesis.core.evolution;

public record GenerationStats(int generation, double bestFitness, double meanFitness, double worstFitness,
                              int successfulCandidates) {
    public GenerationStats {
        if (generation < 1 || generation > 10000 || successfulCandidates < 0 || successfulCandidates > 256
                || !Double.isFinite(bestFitness) || !Double.isFinite(meanFitness) || !Double.isFinite(worstFitness)
                || worstFitness > meanFitness + 1e-9 || meanFitness > bestFitness + 1e-9) {
            throw new IllegalArgumentException("Invalid generation statistics");
        }
    }
    public GenerationStats(int generation, double bestFitness, double meanFitness, double worstFitness) {
        this(generation, bestFitness, meanFitness, worstFitness, 0);
    }
}
