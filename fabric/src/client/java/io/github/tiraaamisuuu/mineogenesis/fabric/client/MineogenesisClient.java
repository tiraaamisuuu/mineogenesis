package io.github.tiraaamisuuu.mineogenesis.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntities;

public final class MineogenesisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AgentEntities.AGENT, AgentRenderer::new);
    }
}
