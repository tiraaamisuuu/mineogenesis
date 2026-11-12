package io.github.tiraaamisuuu.mineogenesis.core;

/** A small local observation; deliberately contains no target position or route. */
public record AgentObservation(double velocityX, double velocityY, double velocityZ,
                               float yaw, float pitch, boolean grounded,
                               boolean horizontalCollision, String blockAhead) {
}
