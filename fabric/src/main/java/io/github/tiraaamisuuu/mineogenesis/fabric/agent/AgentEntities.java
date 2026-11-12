package io.github.tiraaamisuuu.mineogenesis.fabric.agent;

import io.github.tiraaamisuuu.mineogenesis.fabric.Mineogenesis;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class AgentEntities {
    public static final EntityType<AgentEntity> AGENT = Registry.register(
            Registries.ENTITY_TYPE, Identifier.of(Mineogenesis.ID, "agent"),
            EntityType.Builder.<AgentEntity>create(AgentEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .maxTrackingRange(8)
                    .trackingTickInterval(2)
                    .disableSaving()
                    .build(Mineogenesis.ID + ":agent"));

    private AgentEntities() { }

    public static void register() {
        FabricDefaultAttributeRegistry.register(AGENT, AgentEntity.attributes());
    }
}
