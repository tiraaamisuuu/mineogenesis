package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Immutable weights for a 12-8-6 fully connected tanh network. */
public final class NetworkGenome {
    public static final int INPUTS = 12;
    public static final int HIDDEN = 8;
    public static final int OUTPUTS = 6;
    public static final int GENE_COUNT = (INPUTS + 1) * HIDDEN + (HIDDEN + 1) * OUTPUTS;
    private final double[] genes;

    public NetworkGenome(double[] genes) {
        Objects.requireNonNull(genes);
        if (genes.length != GENE_COUNT) throw new IllegalArgumentException("Expected " + GENE_COUNT + " genes");
        this.genes = genes.clone();
        for (double gene : this.genes) {
            if (!Double.isFinite(gene) || Math.abs(gene) > 8) {
                throw new IllegalArgumentException("Genes must be finite and within [-8, 8]");
            }
        }
    }

    public static NetworkGenome random(RandomGenerator random) {
        double[] genes = new double[GENE_COUNT];
        for (int i = 0; i < genes.length; i++) genes[i] = random.nextDouble(-1, 1);
        return new NetworkGenome(genes);
    }

    public double[] genes() { return genes.clone(); }

    public NetworkGenome mutate(RandomGenerator random, double probability, double sigma) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1
                || !Double.isFinite(sigma) || sigma < 0 || sigma > 8) {
            throw new IllegalArgumentException("Invalid mutation probability or sigma");
        }
        double[] child = genes.clone();
        for (int i = 0; i < child.length; i++) {
            if (random.nextDouble() < probability) {
                child[i] = Math.clamp(child[i] + random.nextGaussian() * sigma, -8, 8);
            }
        }
        return new NetworkGenome(child);
    }

    /** Rows of incoming weights followed by a bias, hidden layer then output layer. */
    public void evaluate(double[] input, double[] hidden, double[] output) {
        if (input.length != INPUTS || hidden.length != HIDDEN || output.length != OUTPUTS
                || input == hidden || input == output || hidden == output) {
            throw new IllegalArgumentException("Distinct inference buffers must have lengths " + INPUTS + ", " + HIDDEN + ", " + OUTPUTS);
        }
        for (double value : input) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Network inputs must be finite");
        }
        int offset = 0;
        for (int row = 0; row < HIDDEN; row++) {
            double sum = 0;
            for (double value : input) sum += value * genes[offset++];
            hidden[row] = Math.tanh(sum + genes[offset++]);
        }
        for (int row = 0; row < OUTPUTS; row++) {
            double sum = 0;
            for (double value : hidden) sum += value * genes[offset++];
            output[row] = Math.tanh(sum + genes[offset++]);
        }
    }
}
