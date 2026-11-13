package io.github.tiraaamisuuu.mineogenesis.fabric.experiment;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionObjective;
import io.github.tiraaamisuuu.mineogenesis.core.storage.RunArchive;
import io.github.tiraaamisuuu.mineogenesis.fabric.acquisition.AcquisitionTarget;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Minecraft fixture metadata. Decoding never loads chunks, spawns bodies or changes blocks. */
public record TrainingPlacement(ServerWorld world, Vec3d position, float yaw, float pitch, AcquisitionTarget target) {
    private static final Set<String> BASE = Set.of("fixture_version", "world", "spawn_x", "spawn_y", "spawn_z", "yaw", "pitch");
    private static final Set<String> TARGET = Set.of("target_x", "target_y", "target_z", "target_axis");
    public TrainingPlacement {
        java.util.Objects.requireNonNull(world); java.util.Objects.requireNonNull(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || Math.abs(position.x) > 29999984 || Math.abs(position.z) > 29999984
                || position.y < world.getBottomY() || position.y >= world.getTopY()
                || !Float.isFinite(yaw) || Math.abs(yaw) > 1_000_000 || !Float.isFinite(pitch) || Math.abs(pitch) > 90
                || (target != null && target.world() != world)) throw new IllegalArgumentException("Invalid training placement");
    }
    public void verify() {
        var pos = BlockPos.ofFloored(position);
        if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)) {
            throw new IllegalStateException("Training spawn must be loaded and inside the world border");
        }
        if (target != null) target.verifyOriginal();
    }
    public Map<String, String> metadata() {
        var m = new HashMap<String, String>();
        m.put("fixture_version", "1"); m.put("world", world.getRegistryKey().getValue().toString());
        m.put("spawn_x", Double.toString(position.x)); m.put("spawn_y", Double.toString(position.y)); m.put("spawn_z", Double.toString(position.z));
        m.put("yaw", Float.toString(yaw)); m.put("pitch", Float.toString(pitch));
        if (target != null) {
            m.put("target_x", Integer.toString(target.position().getX())); m.put("target_y", Integer.toString(target.position().getY()));
            m.put("target_z", Integer.toString(target.position().getZ())); m.put("target_axis", target.originalState().get(PillarBlock.AXIS).name());
        }
        return Map.copyOf(m);
    }
    public static TrainingPlacement decode(MinecraftServer server, RunArchive archive) {
        var m = archive.metadata(); var keys = new java.util.HashSet<>(BASE);
        boolean acquisition = archive.checkpoint().objective() == EvolutionObjective.OAK_LOG_ACQUISITION;
        if (acquisition) keys.addAll(TARGET);
        if (!keys.equals(m.keySet()) || !"1".equals(m.get("fixture_version"))) throw new IllegalArgumentException("Unsupported fixture metadata");
        var identifier = Identifier.tryParse(m.get("world"));
        if (identifier == null) throw new IllegalArgumentException("Invalid saved dimension identifier");
        var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier));
        if (world == null) throw new IllegalArgumentException("Saved training dimension is unavailable");
        AcquisitionTarget target = null;
        if (acquisition) {
            var pos = new BlockPos(Integer.parseInt(m.get("target_x")), Integer.parseInt(m.get("target_y")), Integer.parseInt(m.get("target_z")));
            if (Math.abs((long) pos.getX()) > 29999984 || Math.abs((long) pos.getZ()) > 29999984
                    || pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) throw new IllegalArgumentException("Saved target is outside world bounds");
            var state = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.valueOf(m.get("target_axis")));
            target = new AcquisitionTarget(world, pos, state);
        }
        var placement = new TrainingPlacement(world, new Vec3d(Double.parseDouble(m.get("spawn_x")), Double.parseDouble(m.get("spawn_y")),
                Double.parseDouble(m.get("spawn_z"))), Float.parseFloat(m.get("yaw")), Float.parseFloat(m.get("pitch")), target);
        placement.verify(); return placement;
    }
}
