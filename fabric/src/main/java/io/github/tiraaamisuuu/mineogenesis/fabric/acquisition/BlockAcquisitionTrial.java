package io.github.tiraaamisuuu.mineogenesis.fabric.acquisition;

import io.github.tiraaamisuuu.mineogenesis.core.acquisition.MiningProgress;
import io.github.tiraaamisuuu.mineogenesis.fabric.Mineogenesis;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One selected-log transaction. No navigation, auto-aim or proximity mining decisions. */
public final class BlockAcquisitionTrial implements AutoCloseable {
    private static final ChunkTicketType<UUID> TARGET_TICKET = ChunkTicketType.create(
            "mineogenesis_target", Comparator.<UUID>naturalOrder());
    private final AcquisitionTarget target;
    private final AgentEntity agent;
    private final MiningProgress mining = new MiningProgress();
    private final List<ItemEntity> ownedDrops = new ArrayList<>();
    private boolean broken, closed;
    private int crackStage = -1;
    private String failure = "";

    public BlockAcquisitionTrial(AcquisitionTarget target, AgentEntity agent) {
        this.target = target;
        this.agent = agent;
        target.verifyOriginal();
        // Radius zero keeps this target chunk FULL for restoration; it is not a persistent force flag.
        target.world().getChunkManager().addTicket(TARGET_TICKET, new ChunkPos(target.position()), 0, agent.getUuid());
    }

    public boolean matches(BlockHitResult hit) {
        return !broken && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.position());
    }

    public double progress() { return broken ? 1 : mining.value(); }
    public boolean healthy() {
        if (closed || !failure.isBlank() || !target.world().isChunkLoaded(target.position())) return false;
        var state = target.world().getBlockState(target.position());
        if (broken ? !state.isAir() : !state.equals(target.originalState())) return false;
        return !broken || agent.collectedLogs() > 0 || ownedDrops.stream().anyMatch(drop -> !drop.isRemoved());
    }

    public void requireHealthy() {
        if (!healthy()) throw new IllegalStateException(failure.isBlank()
                ? "Selected target or owned drop changed during evaluation; experiment aborted" : failure);
    }

    /** Called after the agent's movement tick, only while its physics is running. */
    public void tick() {
        try {
            requireHealthy();
            if (!broken) {
                Vec3d eye = agent.getEyePos();
                var hit = target.world().raycast(new RaycastContext(eye,
                        eye.add(agent.getRotationVec(1).multiply(3)), RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, agent));
                boolean valid = agent.currentInput().mine() && matches(hit);
                double hardness = target.originalState().getHardness(target.world(), target.position());
                double delta = hardness > 0 ? 1 / (hardness * 30) : 0;
                if (!agent.isOnGround()) delta /= 5;
                if (agent.isSubmergedInWater()) delta /= 5;
                boolean finished = mining.advance(valid, Math.min(1, delta));
                if (valid) agent.swingHand(Hand.MAIN_HAND);
                cracks(valid ? Math.min(9, (int) (mining.value() * 10)) : -1);
                if (finished) breakTarget();
            }
            for (ItemEntity drop : ownedDrops) {
                if (!drop.isRemoved() && !drop.cannotPickup()
                        && agent.getBoundingBox().expand(0.5, 0.25, 0.5).intersects(drop.getBoundingBox())) {
                    agent.collectOwnedLog(drop);
                }
            }
        } catch (RuntimeException error) {
            failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            agent.setRunning(false);
            Mineogenesis.LOGGER.warn("Acquisition trial aborted: " + failure, error);
        }
    }

    private void breakTarget() {
        var world = target.world();
        ItemStack loot = Block.getDroppedStacks(target.originalState(), world, target.position(), null, agent, ItemStack.EMPTY)
                .stream().filter(stack -> stack.isOf(Items.OAK_LOG)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected log's loot table did not produce an oak log"));
        if (!world.breakBlock(target.position(), false, agent)) throw new IllegalStateException("Minecraft rejected target break");
        broken = true;
        cracks(-1);
        Vec3d center = Vec3d.ofCenter(target.position());
        var drop = new ItemEntity(world, center.x, center.y, center.z, loot.copyWithCount(1));
        drop.setVelocity(Vec3d.ZERO);
        drop.setOwner(agent.getUuid());
        drop.setPickupDelay(10);
        ownedDrops.add(drop);
        if (!world.spawnEntity(drop)) throw new IllegalStateException("Minecraft rejected target loot spawn");
    }

    private void cracks(int stage) {
        if (crackStage != stage) {
            target.world().setBlockBreakingInfo(agent.getId(), target.position(), stage);
            crackStage = stage;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            cracks(-1);
            for (var drop : ownedDrops) drop.discard();
            ownedDrops.clear();
            if (broken) {
                var current = target.world().getBlockState(target.position());
                if (current.isAir()) {
                    if (!target.world().setBlockState(target.position(), target.originalState(), Block.NOTIFY_ALL)) {
                        Mineogenesis.LOGGER.warn("Could not restore selected target at {}", target.position());
                    }
                } else if (!current.equals(target.originalState())) {
                    Mineogenesis.LOGGER.warn("Preserved external replacement of selected target at {}", target.position());
                }
            }
        } finally {
            target.world().getChunkManager().removeTicket(TARGET_TICKET, new ChunkPos(target.position()), 0, agent.getUuid());
        }
    }
}
