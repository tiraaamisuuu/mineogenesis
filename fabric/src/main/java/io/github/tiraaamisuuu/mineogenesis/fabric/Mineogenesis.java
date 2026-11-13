package io.github.tiraaamisuuu.mineogenesis.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntities;
import io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.WorldSavePath;

public final class Mineogenesis implements ModInitializer {
    public static final String ID = "mineogenesis";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private final Map<MinecraftServer, ExperimentSession> sessions = new IdentityHashMap<>();

    @Override
    public void onInitialize() {
        AgentEntities.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> sessions.put(server,
                new ExperimentSession(server.getSavePath(WorldSavePath.ROOT).resolve("mineogenesis"))));
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            ExperimentSession session = sessions.get(server);
            if (session != null) session.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ExperimentSession session = sessions.remove(server);
            if (session != null) session.close();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ExperimentCommands.register(dispatcher, sessions::get));
        LOGGER.info("Mineogenesis foundations loaded");
    }
}
