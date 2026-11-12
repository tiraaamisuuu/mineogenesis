package io.github.tiraaamisuuu.mineogenesis.fabric.agent;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.AgentFrame;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionEnvironment;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import io.github.tiraaamisuuu.mineogenesis.fabric.acquisition.AcquisitionTarget;
import io.github.tiraaamisuuu.mineogenesis.fabric.acquisition.BlockAcquisitionTrial;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

/** One body, owned by one experiment. All methods run on the server thread. */
public final class MinecraftAgentEnvironment implements EvolutionEnvironment {
    private ServerWorld world;
    private Vec3d origin;
    private float yaw;
    private float pitch;
    private AgentEntity agent;
    private AcquisitionTarget target;
    private BlockAcquisitionTrial trial;

    public void configure(ServerWorld world, Vec3d origin, float yaw) {
        configure(world, origin, yaw, 0);
    }

    public void configure(ServerWorld world, Vec3d origin, float yaw, float pitch) {
        if (agent != null) throw new IllegalStateException("Stop or reset before changing the spawn point");
        this.world = world;
        this.origin = origin;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void configureAcquisition(AcquisitionTarget target) {
        if (agent != null) throw new IllegalStateException("Stop before changing the acquisition target");
        if (target != null && target.world() != world) throw new IllegalArgumentException("Target must be in the spawn world");
        this.target = target;
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
        candidate.refreshPositionAndAngles(origin.x, origin.y, origin.z, yaw, pitch);
        candidate.setHeadYaw(yaw);
        candidate.setBodyYaw(yaw);
        candidate.setCustomName(Text.literal("Agent 1"));
        candidate.setCustomNameVisible(true);
        if (origin.y < world.getBottomY() || origin.y + candidate.getHeight() > world.getTopY()
                || !world.getWorldBorder().contains(candidate.getBoundingBox())
                || !world.isSpaceEmpty(candidate)) {
            throw new IllegalStateException("Spawn point is obstructed or outside the world bounds");
        }
        BlockAcquisitionTrial pending = target == null ? null : new BlockAcquisitionTrial(target, candidate);
        try {
            if (!world.spawnEntity(candidate)) throw new IllegalStateException("Minecraft rejected agent spawn");
        } catch (RuntimeException failure) {
            if (pending != null) pending.close();
            candidate.discard();
            throw failure;
        }
        agent = candidate;
        trial = pending;
        if (trial != null) agent.setInteractionTick(trial::tick);
    }

    public boolean available() {
        return agent != null && !agent.isRemoved() && agent.isAlive()
                && world.getEntity(agent.getUuid()) == agent
                && world.isChunkLoaded(agent.getBlockPos())
                && world.shouldTickEntity(agent.getBlockPos())
                && (trial == null || trial.healthy());
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

    public void setView(float yaw, float pitch) {
        if (!available()) throw new IllegalStateException("No agent is available");
        agent.setYaw(yaw);
        agent.setHeadYaw(yaw);
        agent.setBodyYaw(yaw);
        agent.setPitch(pitch);
    }

    @Override
    public Optional<AgentObservation> observe() {
        if (trial != null) trial.requireHealthy();
        if (!available()) return Optional.empty();
        Vec3d eye = agent.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(eye,
                eye.add(agent.getRotationVec(1).multiply(3)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, agent));
        String block = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? "minecraft:air"
                : Registries.BLOCK.getId(world.getBlockState(hit.getBlockPos()).getBlock()).toString();
        Vec3d velocity = agent.getVelocity();
        return Optional.of(new AgentObservation(velocity.x, velocity.y, velocity.z,
                agent.getYaw(), agent.getPitch(), agent.isOnGround(), agent.horizontalCollision, block,
                trial != null && trial.matches(hit), trial == null ? 0 : trial.progress(), agent.collectedLogs()));
    }

    @Override
    public Optional<AgentFrame> frame() {
        return observe().map(observation -> new AgentFrame(observation, agent.getX(), agent.getZ()));
    }

    @Override
    public void label(String label) {
        if (agent != null) agent.setCustomName(Text.literal(label));
    }

    @Override
    public void remove() {
        try {
            if (trial != null) trial.close();
        } finally {
            trial = null;
            if (agent != null) {
                agent.setInteractionTick(null);
                agent.setRunning(false);
                agent.discard();
                agent = null;
            }
        }
    }
}
