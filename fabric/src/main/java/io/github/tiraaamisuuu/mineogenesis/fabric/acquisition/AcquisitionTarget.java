package io.github.tiraaamisuuu.mineogenesis.fabric.acquisition;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Read-only selection snapshot. Selecting does not acquire a ticket or change terrain. */
public record AcquisitionTarget(ServerWorld world, BlockPos position, BlockState originalState) {
    public AcquisitionTarget {
        java.util.Objects.requireNonNull(world);
        position = java.util.Objects.requireNonNull(position).toImmutable();
        if (!java.util.Objects.requireNonNull(originalState).isOf(Blocks.OAK_LOG)) {
            throw new IllegalArgumentException("This checkpoint supports one selected oak log");
        }
    }

    public static AcquisitionTarget capture(ServerWorld world, BlockPos position) {
        if (!world.isChunkLoaded(position)) throw new IllegalStateException("Target chunk is not loaded");
        var target = new AcquisitionTarget(world, position, world.getBlockState(position));
        target.verifyOriginal();
        return target;
    }

    public void verifyOriginal() {
        if (!world.isChunkLoaded(position) || !world.getWorldBorder().contains(position)
                || !world.getBlockState(position).equals(originalState)) {
            throw new IllegalStateException("Selected target is unavailable or changed; reselect an oak log");
        }
    }
}
