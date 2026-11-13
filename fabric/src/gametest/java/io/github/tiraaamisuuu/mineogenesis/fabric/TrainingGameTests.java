package io.github.tiraaamisuuu.mineogenesis.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionRun;
import io.github.tiraaamisuuu.mineogenesis.core.storage.*;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.TrainingPlacement;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class TrainingGameTests implements FabricGameTest {
    // Register all timed tasks up front: scheduling a large batch inside a task can
    // resize Minecraft's task map while its iterator removes the current entry.

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void namedSaveReloadContinueAndCsvWithRealBodies(TestContext ctx) {
        floor(ctx); Path root = temporary(); var first = new ExperimentSession(root);
        var dispatcher = dispatcher(first); var source = source(ctx);
        var resumed = new ExperimentSession[1];
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start 42 2 3 1") == 1, "Initial command must start training");
        drive(ctx, first, 1, 11);
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution save premature") == 0, "No save before a completed generation");
        ctx.runAtTick(12, () -> {
            ctx.assertTrue(first.evolution().state() == EvolutionRun.State.COMPLETED, "Initial training completes: " + first.evolutionStatus());
            ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution save named") == 1, "Named save parses");
            ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution export metrics") == 1, "CSV export parses");
            first.close(); // Normal lifecycle flush must finish both explicit files and the autosave.
            var next = new ExperimentSession(root); var commands = dispatcher(next);
            ctx.assertTrue(next.savedTrainingNames().contains("latest") && next.savedTrainingNames().contains("named"), "Autosave and named checkpoint survive session shutdown");
            ctx.assertTrue(read(root.resolve("metrics.csv")).lines().count() == 2, "One completed CSV row");
            ctx.assertTrue(command(commands, source, "mineogenesis evolution load named 2") == 1, "Load command stages saved run");
            ctx.assertTrue(next.evolution().state() == EvolutionRun.State.READY && next.evolution().generation() == 2
                    && next.evolution().totalTicks() == 6 && bodies(ctx).isEmpty(), "Load creates no body and restores counters");
            ctx.assertTrue(command(commands, source.withPosition(source.getPosition().add(1, 0, 0)), "mineogenesis evolution continue") == 1, "Explicit continuation");
            ctx.assertTrue(bodies(ctx).getFirst().getPos().squaredDistanceTo(source.getPosition().add(0, 0, 2)) < 1e-8, "Saved pose used regardless of caller position");
            ctx.assertTrue(command(commands, source, "mineogenesis evolution load named 1") == 0, "Active run cannot be replaced");
            command(commands, source, "mineogenesis pause");
            ctx.assertTrue(command(commands, source, "mineogenesis evolution load named 1") == 0, "Paused run cannot be replaced");
            command(commands, source, "mineogenesis start");
            resumed[0] = next;
        });
        for (int tick = 13; tick <= 39; tick++) ctx.runAtTick(tick, () -> resumed[0].tick());
        ctx.runAtTick(40, () -> {
                var next = resumed[0]; var commands = dispatcher(next);
                ctx.assertTrue(next.evolution().state() == EvolutionRun.State.COMPLETED && next.evolution().history().size() == 3
                        && next.evolution().totalTicks() == 18 && bodies(ctx).isEmpty(), "Continuation adds exactly two generations");
                next.monitor().update(next.evolution());
                ctx.assertTrue(next.monitor().progress() == 1 && next.monitor().title().contains("COMPLETED"), "Monitor shows completion");
                var previous = next.evolution(); write(root.resolve("broken.mng"), new byte[]{1, 2, 3});
                ctx.assertTrue(command(commands, source, "mineogenesis evolution load broken 1") == 0 && next.evolution() == previous,
                        "Corrupt load preserves existing results");
                next.close(); delete(root); ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
    public void acquisitionMetadataRefusesOutsideEditsAndRechecksBeforeContinue(TestContext ctx) {
        floor(ctx); var target = new BlockPos(3, 3, 6);
        ctx.setBlockState(target.down(), Blocks.OAK_LOG); ctx.setBlockState(target, Blocks.OAK_LOG);
        Path root = temporary(); var session = new ExperimentSession(root); var source = source(ctx);
        var resumed = new ExperimentSession[1];
        session.selectTarget(source, ctx.getAbsolutePos(target)); session.startAcquisition(source, new EvolutionConfig(7, 2, 2, 1, .1, .2));
        drive(ctx, session, 1, 9);
        ctx.runAtTick(10, () -> {
            session.saveTraining("log_trial", false); session.close();
            var next = new ExperimentSession(root); var commands = dispatcher(next);
            ctx.setBlockState(target, Blocks.DIAMOND_BLOCK);
            ctx.assertTrue(command(commands, source, "mineogenesis evolution load log_trial 1") == 0, "Changed fixture cannot be loaded");
            ctx.assertTrue(ctx.getBlockState(target).isOf(Blocks.DIAMOND_BLOCK) && bodies(ctx).isEmpty(), "Rejected load preserves terrain and spawns nothing");
            ctx.setBlockState(target, Blocks.OAK_LOG);
            ctx.assertTrue(command(commands, source, "mineogenesis evolution load log_trial 1") == 1, "Restored original fixture loads");
            ctx.assertTrue(next.targetStatus().contains("Selected oak log"), "Target selection is restored");
            ctx.setBlockState(target, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
            ctx.assertTrue(command(commands, source, "mineogenesis evolution continue") == 0 && bodies(ctx).isEmpty(), "Axis changes are detected again before spawn");
            ctx.setBlockState(target, Blocks.OAK_LOG);
            ctx.assertTrue(command(commands, source, "mineogenesis evolution continue") == 1, "Retry after restoring exact fixture");
            resumed[0] = next;
        });
        for (int tick = 11; tick <= 25; tick++) ctx.runAtTick(tick, () -> resumed[0].tick());
        ctx.runAtTick(26, () -> {
                var next = resumed[0];
                ctx.assertTrue(next.evolution().state() == EvolutionRun.State.COMPLETED && next.evolution().history().size() == 2, "Acquisition continues after reload");
                ctx.assertTrue(ctx.getBlockState(target).equals(Blocks.OAK_LOG.getDefaultState()), "Fixture remains original after completion");
                next.close(); delete(root); ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void placementValidationAndMonitorPauseReset(TestContext ctx) {
        floor(ctx); Path root = temporary(); var session = new ExperimentSession(root); var source = source(ctx);
        session.startEvolution(source, new EvolutionConfig(1, 2, 2, 1, .1, .2)); drive(ctx, session, 1, 8);
        ctx.runAtTick(9, () -> {
            session.saveTraining("valid", false); session.close();
            var store = new CheckpointStore(root); var archive = load(store, "valid");
            for (Map<String, String> change : List.of(Map.of("world", "missing:dimension"), Map.of("world", "bad identifier!"), Map.of("spawn_x", "NaN"),
                    Map.of("pitch", "91"), Map.of("fixture_version", "99"), Map.of("extra", "unsupported"))) {
                var metadata = new HashMap<>(archive.metadata()); metadata.putAll(change);
                boolean rejected = false;
                try { TrainingPlacement.decode(source.getServer(), new RunArchive(archive.checkpoint(), metadata)); }
                catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
                ctx.assertTrue(rejected && bodies(ctx).isEmpty(), "Malformed fixture must not create a body");
            }
            var next = new ExperimentSession(root); var commands = dispatcher(next);
            command(commands, source, "mineogenesis evolution load valid 1"); next.monitor().update(next.evolution());
            float boundary = next.monitor().progress();
            ctx.assertTrue(boundary == .5f, "Loaded progress counts previous completed generations");
            command(commands, source, "mineogenesis evolution continue"); next.pause(); next.monitor().update(next.evolution());
            ctx.assertTrue(next.monitor().title().contains("PAUSED") && next.monitor().progress() == boundary, "Pause monitor retains progress");
            ctx.assertTrue(command(commands, source, "mineogenesis monitor on") == 0, "Console cannot subscribe as a player");
            next.reset(); next.monitor().update(next.evolution());
            ctx.assertTrue(next.monitor().progress() == 0 && bodies(ctx).isEmpty(), "Reset clears monitor progress");
            next.close(); delete(root); ctx.complete();
        });
    }

    private static RunArchive load(CheckpointStore store, String name) { try { return store.load(name); } catch (IOException e) { throw new AssertionError(e); } }
    private static Path temporary() { try { return Files.createTempDirectory("mineogenesis-game-test-"); } catch (IOException e) { throw new AssertionError(e); } }
    private static String read(Path path) { try { return Files.readString(path); } catch (IOException e) { throw new AssertionError(e); } }
    private static void write(Path path, byte[] bytes) { try { Files.write(path, bytes); } catch (IOException e) { throw new AssertionError(e); } }
    private static void delete(Path root) {
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        catch (IOException e) { throw new AssertionError(e); }
    }
    private static CommandDispatcher<ServerCommandSource> dispatcher(ExperimentSession session) {
        var dispatcher = new CommandDispatcher<ServerCommandSource>(); ExperimentCommands.register(dispatcher, server -> session); return dispatcher;
    }
    private static int command(CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source, String text) {
        try { return dispatcher.execute(text, source); } catch (CommandSyntaxException e) { throw new AssertionError(text, e); }
    }
    private static void drive(TestContext ctx, ExperimentSession session, int start, int end) { for (int tick = start; tick <= end; tick++) ctx.runAtTick(tick, session::tick); }
    private static ServerCommandSource source(TestContext ctx) {
        return ctx.getWorld().getServer().getCommandSource().withWorld(ctx.getWorld())
                .withPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 2)))).withRotation(new Vec2f(0, 0)).withLevel(2);
    }
    private static List<AgentEntity> bodies(TestContext ctx) {
        return ctx.getWorld().getEntitiesByClass(AgentEntity.class, new Box(Vec3d.of(ctx.getAbsolutePos(new BlockPos(0, 0, 0))),
                Vec3d.of(ctx.getAbsolutePos(new BlockPos(8, 8, 8)))), entity -> !entity.isRemoved());
    }
    private static void floor(TestContext ctx) { for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE); }
}
