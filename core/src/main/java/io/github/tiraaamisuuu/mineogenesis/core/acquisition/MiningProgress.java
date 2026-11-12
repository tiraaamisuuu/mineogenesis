package io.github.tiraaamisuuu.mineogenesis.core.acquisition;

/** Tick-based actuator progress. Release/invalid aim resets; pause does not call advance. */
public final class MiningProgress {
    private double progress;

    public boolean advance(boolean heldAndValid, double delta) {
        if (!Double.isFinite(delta) || delta < 0 || delta > 1) {
            throw new IllegalArgumentException("Mining delta must be finite and within [0, 1]");
        }
        if (!heldAndValid) { reset(); return false; }
        progress = Math.min(1, progress + delta);
        if (progress >= 1 - 1e-9) progress = 1;
        return progress == 1;
    }

    public void reset() { progress = 0; }
    public double value() { return progress; }
}
