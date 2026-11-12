package io.github.tiraaamisuuu.mineogenesis.fabric.experiment;

import io.github.tiraaamisuuu.mineogenesis.core.ExperimentManager;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.fabric.Mineogenesis;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.MinecraftAgentEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Vec3d;

/** Server-owned integration object; never shared across worlds/server instances. */
public final class ExperimentSession {
    private final MinecraftAgentEnvironment environment = new MinecraftAgentEnvironment();
    private final ExperimentManager manager = new ExperimentManager(environment);

    public ExperimentManager manager() { return manager; }

    public boolean start(ServerCommandSource source) {
        if (manager.state() == ExperimentState.READY || manager.state() == ExperimentState.STOPPED) {
            float yaw = source.getRotation().y;
            double radians = Math.toRadians(yaw);
            // Place the body two blocks ahead of the operator; no destination or route is stored.
            Vec3d spawn = source.getPosition().add(-Math.sin(radians) * 2, 0, Math.cos(radians) * 2);
            environment.configure(source.getWorld(), spawn, yaw);
        }
        return manager.start();
    }

    public void tick() {
        if (manager.state() != ExperimentState.RUNNING && manager.state() != ExperimentState.PAUSED) return;
        if (!environment.available()) {
            manager.stop();
            Mineogenesis.LOGGER.info("Experiment stopped: agent removed or chunk unloaded");
            return;
        }
        manager.tick();
    }

    public void close() { manager.reset(); }
}
