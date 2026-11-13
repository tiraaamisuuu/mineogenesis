package io.github.tiraaamisuuu.mineogenesis.core.evolution;

public record GenerationRecord(GenerationStats statistics, long cumulativeTicks, long cumulativeSuccesses) {
    public GenerationRecord {
        java.util.Objects.requireNonNull(statistics);
        if (cumulativeTicks < 1 || cumulativeSuccesses < 0) throw new IllegalArgumentException("Invalid generation counters");
    }
}
