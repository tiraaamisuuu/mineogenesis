package io.github.tiraaamisuuu.mineogenesis.core.evolution;

public record EvolutionConfig(long seed, int populationSize, int episodeTicks, int generations,
                              double mutationProbability, double mutationSigma) {
    public static EvolutionConfig defaults(long seed) {
        return new EvolutionConfig(seed, 16, 100, 10, 0.10, 0.20);
    }

    public EvolutionConfig {
        if (populationSize < 2 || populationSize > 256) throw new IllegalArgumentException("Population must be 2..256");
        if (episodeTicks < 1 || episodeTicks > 12000) throw new IllegalArgumentException("Episode ticks must be 1..12000");
        if (generations < 1 || generations > 10000) throw new IllegalArgumentException("Generations must be 1..10000");
        if (!Double.isFinite(mutationProbability) || mutationProbability < 0 || mutationProbability > 1
                || !Double.isFinite(mutationSigma) || mutationSigma < 0 || mutationSigma > 8) {
            throw new IllegalArgumentException("Invalid mutation settings");
        }
    }
}
