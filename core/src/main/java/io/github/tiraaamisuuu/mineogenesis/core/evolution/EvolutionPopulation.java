package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Objects;
import java.util.List;

/** Fixed-size population; maximises finite fitness using one elite and tournaments of 3. */
public final class EvolutionPopulation {
    private final EvolutionConfig config;
    private final CheckpointRandom random;
    private NetworkGenome[] genomes;

    public EvolutionPopulation(EvolutionConfig config) {
        this.config = Objects.requireNonNull(config);
        random = new CheckpointRandom(config.seed());
        genomes = new NetworkGenome[config.populationSize()];
        for (int i = 0; i < genomes.length; i++) genomes[i] = NetworkGenome.random(random);
    }

    public int size() { return genomes.length; }
    public NetworkGenome genome(int index) { return genomes[index]; }

    public record Snapshot(List<NetworkGenome> genomes, CheckpointRandom.State random) {
        public Snapshot {
            genomes = List.copyOf(genomes);
            Objects.requireNonNull(random);
            if (genomes.size() < 2 || genomes.size() > 256) throw new IllegalArgumentException("Invalid snapshot population size");
        }
    }

    private EvolutionPopulation(EvolutionConfig config, Snapshot snapshot) {
        this.config = Objects.requireNonNull(config);
        if (snapshot.genomes().size() != config.populationSize()) throw new IllegalArgumentException("Snapshot population mismatch");
        random = CheckpointRandom.restore(snapshot.random());
        genomes = snapshot.genomes().toArray(NetworkGenome[]::new);
    }

    public static EvolutionPopulation restore(EvolutionConfig config, Snapshot snapshot) {
        return new EvolutionPopulation(config, snapshot);
    }

    public Snapshot snapshot() { return new Snapshot(java.util.Arrays.asList(genomes), random.snapshot()); }

    public int bestIndex(double[] fitness) {
        validate(fitness);
        int best = 0;
        for (int i = 1; i < fitness.length; i++) if (fitness[i] > fitness[best]) best = i;
        return best;
    }

    public void breed(double[] fitness) {
        int best = bestIndex(fitness);
        var next = new NetworkGenome[genomes.length];
        next[0] = genomes[best];
        for (int i = 1; i < next.length; i++) {
            int parent = random.nextInt(genomes.length);
            for (int candidate = 1; candidate < 3; candidate++) {
                int index = random.nextInt(genomes.length);
                if (fitness[index] > fitness[parent]) parent = index;
            }
            next[i] = genomes[parent].mutate(random, config.mutationProbability(), config.mutationSigma());
        }
        genomes = next;
    }

    private void validate(double[] fitness) {
        if (fitness.length != genomes.length) throw new IllegalArgumentException("Incomplete fitness population");
        for (double value : fitness) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Fitness must be finite");
        }
    }
}
