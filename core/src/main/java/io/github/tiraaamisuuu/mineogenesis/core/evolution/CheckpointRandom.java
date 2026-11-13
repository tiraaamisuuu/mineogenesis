package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Random;

/** Java Random-compatible stream with an explicit, non-serialized checkpoint state. */
public final class CheckpointRandom extends Random {
    private static final long serialVersionUID = 1L;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long MASK = (1L << 48) - 1;
    private long state;
    private boolean cached;
    private double gaussian;

    public record State(long bits, boolean hasGaussian, double gaussian) {
        public State {
            if (bits < 0 || bits > MASK || !Double.isFinite(gaussian)) {
                throw new IllegalArgumentException("Invalid genetic random state");
            }
            if (!hasGaussian) gaussian = 0;
        }
    }

    public CheckpointRandom(long seed) { super(0); setSeed(seed); }

    public static CheckpointRandom restore(State snapshot) {
        var random = new CheckpointRandom(0);
        random.state = snapshot.bits();
        random.cached = snapshot.hasGaussian();
        random.gaussian = snapshot.gaussian();
        return random;
    }

    @Override
    public synchronized void setSeed(long seed) {
        state = (seed ^ MULTIPLIER) & MASK;
        cached = false;
        gaussian = 0;
    }

    @Override
    protected synchronized int next(int bits) {
        state = (state * MULTIPLIER + 11) & MASK;
        return (int) (state >>> (48 - bits));
    }

    @Override
    public synchronized double nextGaussian() {
        if (cached) { cached = false; return gaussian; }
        double first, second, radius;
        do {
            first = nextDouble() * 2 - 1;
            second = nextDouble() * 2 - 1;
            radius = first * first + second * second;
        } while (radius == 0 || radius >= 1);
        double scale = StrictMath.sqrt(-2 * StrictMath.log(radius) / radius);
        gaussian = second * scale;
        cached = true;
        return first * scale;
    }

    public synchronized State snapshot() { return new State(state, cached, cached ? gaussian : 0); }
}
