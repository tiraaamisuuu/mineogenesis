package io.github.tiraaamisuuu.mineogenesis.core;

/** A small local observation; deliberately contains no target position or route. */
public record AgentObservation(double velocityX, double velocityY, double velocityZ,
                               float yaw, float pitch, boolean grounded,
                               boolean horizontalCollision, String blockAhead,
                               boolean targetAhead, double miningProgress, int collectedTarget) {
    public AgentObservation(double velocityX, double velocityY, double velocityZ, float yaw, float pitch,
                            boolean grounded, boolean horizontalCollision, String blockAhead) {
        this(velocityX, velocityY, velocityZ, yaw, pitch, grounded, horizontalCollision, blockAhead, false, 0, 0);
    }

    public AgentObservation {
        java.util.Objects.requireNonNull(blockAhead);
        if (!Double.isFinite(miningProgress) || miningProgress < 0 || miningProgress > 1
                || collectedTarget < 0 || collectedTarget > 64) {
            throw new IllegalArgumentException("Invalid mining progress or inventory observation");
        }
    }
}
