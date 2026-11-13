package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.List;
import java.util.Objects;

/** Complete generation boundary, containing the NEXT population and RNG stream. */
public record TrainingCheckpoint(EvolutionConfig config, EvolutionObjective objective, int completedGeneration,
                                 EvolutionPopulation.Snapshot population, NetworkGenome champion, double bestFitness,
                                 long totalTicks, long totalSuccesses, List<GenerationRecord> history) {
    public TrainingCheckpoint {
        Objects.requireNonNull(config); Objects.requireNonNull(objective);
        Objects.requireNonNull(population); Objects.requireNonNull(champion);
        history = List.copyOf(history);
        if (completedGeneration < 1 || completedGeneration > config.generations() || history.size() != completedGeneration
                || population.genomes().size() != config.populationSize() || !Double.isFinite(bestFitness)) {
            throw new IllegalArgumentException("Invalid checkpoint generation or population");
        }
        long previousTicks = 0, successes = 0;
        double historicalBest = Double.NEGATIVE_INFINITY;
        double maximum = objective == EvolutionObjective.EXPLORATION ? config.episodeTicks() : 111.5;
        for (int i = 0; i < history.size(); i++) {
            var row = history.get(i);
            var stats = row.statistics();
            long ticks = row.cumulativeTicks() - previousTicks;
            successes += stats.successfulCandidates();
            if (stats.generation() != i + 1 || stats.worstFitness() < 0 || stats.bestFitness() > maximum + 1e-9
                    || stats.successfulCandidates() > config.populationSize()
                    || ticks < config.populationSize() || ticks > (long) config.populationSize() * config.episodeTicks()
                    || row.cumulativeSuccesses() != successes
                    || (objective == EvolutionObjective.EXPLORATION && stats.successfulCandidates() != 0)) {
                throw new IllegalArgumentException("Inconsistent checkpoint history");
            }
            historicalBest = Math.max(historicalBest, stats.bestFitness());
            previousTicks = row.cumulativeTicks();
        }
        if (totalTicks != previousTicks || totalSuccesses != successes || Math.abs(historicalBest - bestFitness) > 1e-9) {
            throw new IllegalArgumentException("Checkpoint totals do not match completed history");
        }
    }
}
