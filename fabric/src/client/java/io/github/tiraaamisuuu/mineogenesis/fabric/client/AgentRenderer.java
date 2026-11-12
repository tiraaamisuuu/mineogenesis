package io.github.tiraaamisuuu.mineogenesis.fabric.client;

import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Uses the game's bundled wide Steve skin; no downloads or profile lookups. */
public final class AgentRenderer extends LivingEntityRenderer<AgentEntity, PlayerEntityModel<AgentEntity>> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    public AgentRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(AgentEntity entity) { return TEXTURE; }
}
