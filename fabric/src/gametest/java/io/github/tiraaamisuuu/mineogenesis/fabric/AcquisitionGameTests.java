package io.github.tiraaamisuuu.mineogenesis.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.*;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.MinecraftAgentEnvironment;
import io.github.tiraaamisuuu.mineogenesis.fabric.acquisition.AcquisitionTarget;
import io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.List;

public final class AcquisitionGameTests implements FabricGameTest {
    private static final BlockPos TARGET = new BlockPos(3, 3, 5);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 130)
    public void timedMiningRealPickupAndExactRestoration(TestContext ctx) {
        fixture(ctx);
        var original = ctx.getBlockState(TARGET);
        var session = manual(ctx);
        AgentEntity agent = body(ctx);
        var unrelated = new ItemEntity(ctx.getWorld(), agent.getX(), agent.getY(), agent.getZ(), new ItemStack(Items.OAK_LOG));
        unrelated.setVelocity(Vec3d.ZERO); unrelated.setPickupDelay(0);
        ctx.getWorld().spawnEntity(unrelated);
        session.setManualMine(true);
        drive(ctx, session, 115);
        ctx.runAtTick(35, () -> {
            ctx.assertTrue(ctx.getBlockState(TARGET).equals(original), "Mining must not break oak instantly");
            var observation = session.manager().observation().orElseThrow();
            ctx.assertTrue(observation.targetAhead() && observation.miningProgress() > 0 && observation.miningProgress() < 1,
                    "Target and partial breaking progress must be observable");
            ctx.assertTrue(agent.collectedLogs() == 0 && !unrelated.isRemoved(), "Nearby unrelated loot must not enter inventory");
        });
        ctx.runAtTick(72, () -> {
            ctx.assertTrue(ctx.getBlockState(TARGET).isAir(), "Sustained valid hand mining must break the selected log");
            ctx.assertTrue(agent.collectedLogs() == 0, "Breaking at reach is not inventory collection");
            ctx.assertTrue(drops(ctx).stream().anyMatch(drop -> agent.getUuid().equals(drop.getOwnerUuid())), "Real owned loot entity");
            session.setManualInput(new AgentInput(1, 0, 0, false));
        });
        ctx.runAtTick(100, () -> {
            ctx.assertTrue(agent.collectedLogs() == 1, "Walking into owned loot must place the log in inventory");
            ctx.assertTrue(session.manager().observation().orElseThrow().collectedTarget() == 1, "Inventory observation");
            session.stop();
            ctx.assertTrue(ctx.getBlockState(TARGET).equals(original), "Stop restores exact selected state including axis");
            ctx.assertTrue(ctx.getBlockState(TARGET.down()).isOf(Blocks.STONE), "Support terrain must remain unchanged");
            ctx.assertTrue(drops(ctx).size() == 1 && !unrelated.isRemoved(), "Cleanup preserves unrelated loot");
            ctx.assertTrue(agent.isRemoved(), "Stop removes the carrying body without duplicating inventory");
            unrelated.discard(); ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 125)
    public void interruptionPausePitchAndOcclusionGateMining(TestContext ctx) {
        fixture(ctx);
        var session = manual(ctx);
        session.setManualMine(true);
        drive(ctx, session, 115);
        ctx.runAtTick(20, () -> session.setManualMine(false));
        ctx.runAtTick(25, () -> {
            ctx.assertTrue(progress(session) == 0, "Releasing mine resets progress");
            session.setManualMine(true);
        });
        ctx.runAtTick(40, () -> session.setManualView(90, 0));
        ctx.runAtTick(45, () -> {
            ctx.assertTrue(progress(session) == 0, "Looking away resets progress");
            session.setManualView(0, 0);
        });
        ctx.runAtTick(55, () -> {
            double pausedProgress = progress(session);
            ctx.assertTrue(pausedProgress > 0, "Aiming back at target permits mining");
            session.pause();
            Vec3d pausedPosition = body(ctx).getPos();
            ctx.runAtTick(70, () -> {
                ctx.assertTrue(progress(session) == pausedProgress && body(ctx).getPos().equals(pausedPosition), "Pause freezes physics and mining");
                session.start(source(ctx));
            });
        });
        ctx.runAtTick(75, () -> {
            ctx.assertTrue(progress(session) == 0, "Resume clears held mine input");
            session.setManualView(0, 90);
            session.setManualMine(true);
        });
        ctx.runAtTick(80, () -> {
            ctx.assertTrue(body(ctx).getPitch() == 90 && progress(session) == 0, "Pitch changes aim; no auto-aim toward target");
            ctx.setBlockState(new BlockPos(3, 3, 4), Blocks.STONE);
            session.setManualView(0, 0);
        });
        ctx.runAtTick(100, () -> {
            ctx.assertTrue(progress(session) == 0 && ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG), "Occluding wall blocks mining");
            session.reset();
            ctx.assertTrue(bodies(ctx).isEmpty() && drops(ctx).isEmpty(), "Reset clears trial resources");
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void anotherOakLogIsOutsideTheMiningAllowlist(TestContext ctx) {
        fixture(ctx);
        var other = TARGET.east();
        ctx.setBlockState(other, Blocks.OAK_LOG);
        var session = manual(ctx);
        aim(session, body(ctx), Vec3d.ofCenter(ctx.getAbsolutePos(other)));
        session.setManualMine(true);
        drive(ctx, session, 90);
        ctx.runAtTick(80, () -> {
            ctx.assertTrue(ctx.getBlockState(other).isOf(Blocks.OAK_LOG) && ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG),
                    "Holding mine on a different oak log cannot destroy it");
            ctx.assertTrue(progress(session) == 0, "Only the selected target accumulates progress");
            session.close(); ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void externalReplacementIsPreservedAndOwnedDropsAreRemoved(TestContext ctx) {
        fixture(ctx);
        var session = manual(ctx);
        session.setManualMine(true);
        drive(ctx, session, 90);
        ctx.runAtTick(72, () -> {
            ctx.assertTrue(ctx.getBlockState(TARGET).isAir() && drops(ctx).size() == 1, "Broken target has one uncollected owned drop");
            ctx.setBlockState(TARGET, Blocks.STONE);
            session.tick();
            ctx.assertTrue(session.manager().state() == ExperimentState.STOPPED, "External replacement aborts the active trial");
            ctx.assertTrue(ctx.getBlockState(TARGET).isOf(Blocks.STONE), "Cleanup must not overwrite another actor's edit");
            ctx.assertTrue(drops(ctx).isEmpty() && bodies(ctx).isEmpty(), "Abort removes owned resources");
            session.close(); ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 55)
    public void acquisitionCommandsResetCandidatesAndReplayTheTrainingTarget(TestContext ctx) {
        fixture(ctx);
        var session = new ExperimentSession();
        var dispatcher = dispatcher(session);
        var source = source(ctx);
        ctx.assertTrue(command(dispatcher, source, "mineogenesis acquisition start") == 0, "Target is required");
        BlockPos absolute = ctx.getAbsolutePos(TARGET);
        ctx.assertTrue(command(dispatcher, source, "mineogenesis acquisition target " + absolute.toShortString().replace(",", "")) == 1,
                "Explicit position target selection");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis acquisition start 42 2 2 2") == 1, "Acquisition command parses");
        ctx.assertTrue(session.evolution().objective() == EvolutionObjective.OAK_LOG_ACQUISITION, "Acquisition evaluator selected");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis agent mine true") == 0, "Neural ownership blocks manual mining");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis acquisition clear") == 0, "Cannot change active target");
        drive(ctx, session, 45);
        ctx.runAtTick(16, () -> {
            ctx.assertTrue(session.evolution().state() == EvolutionRun.State.COMPLETED && session.evolution().totalTicks() == 8,
                    "Short acquisition candidates complete at their budget");
            ctx.assertTrue(session.evolution().totalSuccesses() == 0 && ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG), "No fabricated collections");
            ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution replay") == 1, "Acquisition champion replay");
            ctx.assertTrue(session.manager().observation().isPresent(), "Replay trial is observable");
            ctx.runAtTick(25, () -> {
                ctx.assertTrue(bodies(ctx).isEmpty() && drops(ctx).isEmpty() && ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG),
                        "Replay cleanup restores its training target");
                command(dispatcher, source, "mineogenesis acquisition clear");
                ctx.assertTrue(session.targetStatus().contains("No acquisition target"), "Idle selection can be cleared");
                session.close(); ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 115)
    public void neuralActuatorsCanMineAndCollectWithoutWorldShortcuts(TestContext ctx) {
        fixture(ctx);
        var session = manual(ctx);
        double[] genes = new double[NetworkGenome.GENE_COUNT];
        int outputs = (NetworkGenome.INPUTS + 1) * NetworkGenome.HIDDEN;
        // A fixed conformance controller, not a learned policy or production navigation code.
        genes[outputs + NetworkGenome.HIDDEN] = 0.5;
        genes[outputs + 5 * (NetworkGenome.HIDDEN + 1) + NetworkGenome.HIDDEN] = 1;
        var controller = new NeuralController(new NetworkGenome(genes));
        for (int tick = 1; tick <= 105; tick++) {
            ctx.runAtTick(tick, () -> {
                session.setManualInput(controller.decide(session.manager().observation().orElseThrow()));
                session.tick();
            });
        }
        ctx.runAtTick(100, () -> {
            ctx.assertTrue(body(ctx).collectedLogs() == 1, "A neural forward/mine output must result in real inventory collection");
            session.stop();
            ctx.assertTrue(ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG) && drops(ctx).isEmpty(), "Neural action cleanup");
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectedTargetsAndFailedSpawnLeaveWorldUntouched(TestContext ctx) {
        fixture(ctx);
        var session = new ExperimentSession();
        try {
            session.selectTarget(source(ctx), ctx.getAbsolutePos(new BlockPos(2, 1, 2)));
            throw new AssertionError("Stone must not be accepted as an oak-log target");
        } catch (IllegalArgumentException expected) { }
        var environment = new MinecraftAgentEnvironment();
        environment.configure(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 5))), 0);
        environment.configureAcquisition(AcquisitionTarget.capture(ctx.getWorld(), ctx.getAbsolutePos(TARGET)));
        try { environment.spawn(); throw new AssertionError("Obstructed body spawn must fail"); }
        catch (IllegalStateException expected) { }
        environment.remove(); environment.remove();
        ctx.assertTrue(bodies(ctx).isEmpty() && drops(ctx).isEmpty() && ctx.getBlockState(TARGET).isOf(Blocks.OAK_LOG),
                "Rejected spawn leaves no trial resources or block changes");
        ctx.complete();
    }

    private static ExperimentSession manual(TestContext ctx) {
        var session = new ExperimentSession();
        session.selectTarget(source(ctx), ctx.getAbsolutePos(TARGET));
        session.startManualAcquisition(source(ctx));
        return session;
    }
    private static double progress(ExperimentSession session) { return session.manager().observation().orElseThrow().miningProgress(); }
    private static void aim(ExperimentSession session, AgentEntity agent, Vec3d point) {
        Vec3d difference = point.subtract(agent.getEyePos());
        session.setManualView((float) -Math.toDegrees(Math.atan2(difference.x, difference.z)),
                (float) -Math.toDegrees(Math.atan2(difference.y, Math.hypot(difference.x, difference.z))));
    }
    private static void fixture(TestContext ctx) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        ctx.setBlockState(TARGET.down(), Blocks.STONE);
        ctx.setBlockState(TARGET, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
    }
    private static ServerCommandSource source(TestContext ctx) {
        return ctx.getWorld().getServer().getCommandSource().withWorld(ctx.getWorld())
                .withPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 1))))
                .withRotation(new Vec2f(0, 0)).withLevel(2);
    }
    private static CommandDispatcher<ServerCommandSource> dispatcher(ExperimentSession session) {
        var dispatcher = new CommandDispatcher<ServerCommandSource>();
        ExperimentCommands.register(dispatcher, server -> session); return dispatcher;
    }
    private static int command(CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source, String command) {
        try { return dispatcher.execute(command, source); }
        catch (CommandSyntaxException error) { throw new AssertionError("Command failed: " + command, error); }
    }
    private static void drive(TestContext ctx, ExperimentSession session, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) ctx.runAtTick(tick, session::tick);
    }
    private static Box area(TestContext ctx) {
        return new Box(Vec3d.of(ctx.getAbsolutePos(new BlockPos(0, 0, 0))), Vec3d.of(ctx.getAbsolutePos(new BlockPos(8, 8, 8))));
    }
    private static List<AgentEntity> bodies(TestContext ctx) {
        return ctx.getWorld().getEntitiesByClass(AgentEntity.class, area(ctx), entity -> !entity.isRemoved());
    }
    private static List<ItemEntity> drops(TestContext ctx) {
        return ctx.getWorld().getEntitiesByClass(ItemEntity.class, area(ctx), entity -> !entity.isRemoved());
    }
    private static AgentEntity body(TestContext ctx) {
        var bodies = bodies(ctx); ctx.assertTrue(bodies.size() == 1, "Exactly one body must exist"); return bodies.getFirst();
    }
}
