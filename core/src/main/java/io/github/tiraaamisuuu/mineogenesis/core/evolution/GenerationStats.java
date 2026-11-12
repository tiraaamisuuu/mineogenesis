package io.github.tiraaamisuuu.mineogenesis.core.evolution;

public record GenerationStats(int generation, double bestFitness, double meanFitness, double worstFitness,
                              int successfulCandidates) {
    public GenerationStats(int generation, double bestFitness, double meanFitness, double worstFitness) {
        this(generation, bestFitness, meanFitness, worstFitness, 0);
    }
}
