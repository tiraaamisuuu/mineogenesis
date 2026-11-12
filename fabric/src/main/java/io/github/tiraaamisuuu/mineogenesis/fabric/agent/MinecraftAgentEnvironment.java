package io.github.tiraaamisuuu.mineogenesis.fabric.agent;

import io.github.tiraaamisuuu.mineogenesis.core.AgentEnvironment;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

/** One body, owned by one experiment. All methods run on the server thread. */
public final class MinecraftAgentEnvironment implements AgentEnvironment {
    private ServerWorld world;
    private Vec3d origin;
    private float yaw;
    private AgentEntity agent;

    public void configure(ServerWorld world, Vec3d origin, float yaw) {
        if (agent != null) throw new IllegalStateException("Stop or reset before changing the spawn point");
        this.world = world;
        this.origin = origin;
        this.yaw = yaw;
    }

    @Override
    public void spawn() {
        if (world == null || origin == null) throw new IllegalStateException("No spawn point configured");
        if (agent != null) throw new IllegalStateException("An agent already exists");
        if (!world.isChunkLoaded(BlockPos.ofFloored(origin))
                || !world.shouldTickEntity(BlockPos.ofFloored(origin))) {
            throw new IllegalStateException("Spawn point must be in an entity-ticking chunk near an observer");
        }
        var candidate = new AgentEntity(AgentEntities.AGENT, world);
        candidate.refreshPositionAndAngles(origin.x, origin.y, origin.z, yaw, 0);
        candidate.setCustomName(Text.literal("Agent 1"));
        candidate.setCustomNameVisible(true);
        if (origin.y < world.getBottomY() || origin.y + candidate.getHeight() > world.getTopY()
                || !world.getWorldBorder().contains(candidate.getBoundingBox())
                || !world.isSpaceEmpty(candidate)) {
            throw new IllegalStateException("Spawn point is obstructed or outside the world bounds");
        }
        if (!world.spawnEntity(candidate)) throw new IllegalStateException("Minecraft rejected agent spawn");
        agent = candidate;
    }

    public boolean available() {
        return agent != null && !agent.isRemoved() && agent.isAlive()
                && world.getEntity(agent.getUuid()) == agent
                && world.isChunkLoaded(agent.getBlockPos())
                && world.shouldTickEntity(agent.getBlockPos());
    }

    @Override
    public void setRunning(boolean running) {
        if (agent == null) throw new IllegalStateException("No agent exists");
        agent.setRunning(running);
    }

    @Override
    public void apply(AgentInput input) {
        if (!available()) throw new IllegalStateException("Agent was removed or left entity-ticking chunks");
        agent.setInput(input);
    }

    @Override
    public Optional<AgentObservation> observe() {
        if (!available()) return Optional.empty();
        Vec3d eye = agent.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(eye,
                eye.add(agent.getRotationVec(1).multiply(3)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, agent));
        String block = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? "minecraft:air"
                : Registries.BLOCK.getId(world.getBlockState(hit.getBlockPos()).getBlock()).toString();
        Vec3d velocity = agent.getVelocity();
        return Optional.of(new AgentObservation(velocity.x, velocity.y, velocity.z,
                agent.getYaw(), agent.getPitch(), agent.isOnGround(), agent.horizontalCollision, block));
    }

    @Override
    public void remove() {
        if (agent != null) {
            agent.setRunning(false);
            agent.discard();
            agent = null;
        }
    }
}
