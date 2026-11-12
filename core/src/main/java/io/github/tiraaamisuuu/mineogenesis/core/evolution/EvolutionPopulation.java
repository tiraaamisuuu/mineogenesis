package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Objects;
import java.util.Random;

/** Fixed-size population; maximises finite fitness using one elite and tournaments of 3. */
public final class EvolutionPopulation {
    private final EvolutionConfig config;
    private final Random random;
    private NetworkGenome[] genomes;

    public EvolutionPopulation(EvolutionConfig config) {
        this.config = Objects.requireNonNull(config);
        random = new Random(config.seed());
        genomes = new NetworkGenome[config.populationSize()];
        for (int i = 0; i < genomes.length; i++) genomes[i] = NetworkGenome.random(random);
    }

    public int size() { return genomes.length; }
    public NetworkGenome genome(int index) { return genomes[index]; }

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
