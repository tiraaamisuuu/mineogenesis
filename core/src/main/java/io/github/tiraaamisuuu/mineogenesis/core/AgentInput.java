package io.github.tiraaamisuuu.mineogenesis.core;

/** Explicit actuator input, never a navigation destination. Yaw is degrees per tick. */
public record AgentInput(float forward, float strafe, float yawDelta, boolean jump) {
    public static final AgentInput IDLE = new AgentInput(0, 0, 0, false);

    public AgentInput {
        requireRange(forward, -1, 1, "forward");
        requireRange(strafe, -1, 1, "strafe");
        requireRange(yawDelta, -180, 180, "yawDelta");
        // Equal speed in cardinal and diagonal directions.
        double length = Math.hypot(forward, strafe);
        if (length > 1) {
            forward = (float) (forward / length);
            strafe = (float) (strafe / length);
        }
    }

    private static void requireRange(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be finite and between " + min + " and " + max);
        }
    }
}
