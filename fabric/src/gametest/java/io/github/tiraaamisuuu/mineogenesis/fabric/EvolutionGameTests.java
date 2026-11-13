package io.github.tiraaamisuuu.mineogenesis.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionRun;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.ArrayList;

public final class EvolutionGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void candidatesAndGenerationsCompleteWithOneBody(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var dispatcher = dispatcher(session);
        var source = source(ctx);
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start 42 2 3 2") == 1, "Evolution command parses");
        AgentEntity initial = body(ctx);
        ctx.assertTrue(initial.getName().getString().equals("Gen 1 | Agent 1/2"), "Visible candidate label");
        drive(ctx, session, 20);
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(initial.isRemoved(), "Each candidate must get a new body");
            ctx.assertTrue(body(ctx).getName().getString().equals("Gen 1 | Agent 2/2"), "Second candidate label");
        });
        ctx.runAtTick(9, () -> ctx.assertTrue(body(ctx).getName().getString().startsWith("Gen 2"), "Second generation label"));
        ctx.runAtTick(16, () -> {
            ctx.assertTrue(session.evolution().state() == EvolutionRun.State.COMPLETED, "Two generations must finish");
            ctx.assertTrue(session.evolution().totalTicks() == 12, "2 candidates x 3 physics ticks x 2 generations");
            ctx.assertTrue(session.evolution().lastStats().orElseThrow().generation() == 2, "Completed generation statistics");
            ctx.assertTrue(session.evolution().champion().isPresent(), "Completed run retains its champion");
            ctx.assertTrue(bodies(ctx).isEmpty(), "Completion must remove the final body");
            session.close();
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 35)
    public void pauseResumeResetAndReturnToManual(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var dispatcher = dispatcher(session);
        var source = source(ctx);
        command(dispatcher, source, "mineogenesis evolution start 42 2 20 2");
        drive(ctx, session, 25);
        ctx.runAtTick(4, () -> {
            command(dispatcher, source, "mineogenesis pause");
            long ticks = session.evolution().totalTicks();
            AgentEntity agent = body(ctx);
            Vec3d paused = agent.getPos();
            ctx.runAtTick(8, () -> {
                ctx.assertTrue(session.evolution().totalTicks() == ticks, "Paused evolution must not advance");
                ctx.assertTrue(agent.getPos().squaredDistanceTo(paused) < 0.000001, "Paused evolution body must not drift");
                ctx.assertTrue(command(dispatcher, source, "mineogenesis agent control 1 0 0 false") == 0,
                        "Manual input cannot overwrite a neural controller");
                command(dispatcher, source, "mineogenesis start");
                ctx.runAtTick(12, () -> {
                    ctx.assertTrue(session.evolution().totalTicks() > ticks, "Root start resumes evolution");
                    command(dispatcher, source, "mineogenesis evolution reset");
                    ctx.assertTrue(session.evolution().state() == EvolutionRun.State.READY
                            && session.evolution().totalTicks() == 0, "Reset clears progress and reseeds");
                    ctx.assertTrue(bodies(ctx).isEmpty(), "Reset removes the body");
                    command(dispatcher, source, "mineogenesis start");
                    ctx.assertTrue(session.manager().state() == ExperimentState.RUNNING, "Manual mode remains usable");
                    ctx.assertTrue(body(ctx).getName().getString().equals("Agent 1"), "Manual body label");
                    command(dispatcher, source, "mineogenesis stop");
                    ctx.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modeOwnershipAndPermissions(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var dispatcher = dispatcher(session);
        var source = source(ctx);
        command(dispatcher, source, "mineogenesis start");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start") == 0, "Cannot replace an active manual run");
        command(dispatcher, source, "mineogenesis pause");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start") == 0, "Paused manual run also owns its body");
        command(dispatcher, source, "mineogenesis stop");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start 42 2 3 1") == 1, "Start after stop");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start") == 0, "Cannot replace active evolution");
        command(dispatcher, source, "mineogenesis start");
        ctx.assertTrue(bodies(ctx).size() == 1, "Repeated root start cannot create another body");
        ctx.assertTrue(command(dispatcher, source, "mineogenesis agent idle") == 0, "Manual idle cannot overwrite evolution");
        command(dispatcher, source, "mineogenesis evolution pause");
        command(dispatcher, source, "mineogenesis evolution resume");
        command(dispatcher, source, "mineogenesis evolution status");
        command(dispatcher, source, "mineogenesis evolution stop");
        try {
            dispatcher.execute("mineogenesis evolution start", source.withLevel(0));
            throw new AssertionError("Non-operators must not start evolution");
        } catch (CommandSyntaxException expected) {
            ctx.assertTrue(bodies(ctx).isEmpty(), "Permission rejection cannot spawn a body");
        }
        session.close();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void missingBodiesAbortAndShutdownCleansUp(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var config = new EvolutionConfig(42, 2, 20, 2, 0.1, 0.2);
        session.startEvolution(source(ctx), config);
        session.tick();
        body(ctx).discard();
        session.tick();
        ctx.assertTrue(session.evolution().state() == EvolutionRun.State.STOPPED, "Missing body aborts evolution");
        ctx.assertTrue(!session.evolution().failureReason().isBlank() && session.evolution().lastStats().isEmpty(),
                "Abort records the cause without scoring a partial generation");
        session.startEvolution(source(ctx), config);
        session.pause(); body(ctx).discard(); session.tick();
        ctx.assertTrue(session.evolution().state() == EvolutionRun.State.STOPPED, "Paused missing body also aborts");
        session.startEvolution(source(ctx), config);
        AgentEntity finalBody = body(ctx);
        session.close(); session.close();
        ctx.assertTrue(finalBody.isRemoved() && session.evolution().state() == EvolutionRun.State.READY,
                "Idempotent server shutdown cleanup");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 35)
    public void championReplayPreservesTrainingResults(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var dispatcher = dispatcher(session);
        var source = source(ctx);
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution replay") == 0, "Cannot replay without a champion");
        command(dispatcher, source, "mineogenesis evolution start 42 2 3 1");
        drive(ctx, session, 25);
        ctx.runAtTick(10, () -> {
            ctx.assertTrue(session.evolution().state() == EvolutionRun.State.COMPLETED, "Training has finished");
            double best = session.evolution().bestFitness();
            ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution replay") == 1, "Replay command");
            ctx.assertTrue(body(ctx).getName().getString().startsWith("Best network"), "Visible replay label");
            ctx.assertTrue(command(dispatcher, source, "mineogenesis agent control 1 0 0 false") == 0, "Replay owns its controller");
            ctx.runAtTick(17, () -> {
                ctx.assertTrue(bodies(ctx).isEmpty() && session.manager().state() == ExperimentState.STOPPED,
                        "Replay must finish after the training episode budget");
                ctx.assertTrue(session.evolution().totalTicks() == 6 && session.evolution().bestFitness() == best,
                        "Replay must not change training fitness or tick accounting");
                ctx.assertTrue(session.evolutionStatus().contains("COMPLETED"), "Training summary remains accessible after replay");
                session.close(); ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
    public void registeredServerTicksDriveEvolutionWithoutTestScheduling(TestContext ctx) {
        floor(ctx);
        List<String> feedback = new ArrayList<>();
        var output = new CommandOutput() {
            @Override public void sendMessage(Text text) { feedback.add(text.getString()); }
            @Override public boolean shouldReceiveFeedback() { return true; }
            @Override public boolean shouldTrackOutput() { return true; }
            @Override public boolean shouldBroadcastConsoleToOps() { return false; }
        };
        var source = source(ctx).withOutput(output);
        var dispatcher = ctx.getWorld().getServer().getCommandManager().getDispatcher();
        String checkpointName = "event_" + java.util.UUID.randomUUID();
        ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution start 7 2 2 1") == 1,
                "Real command registration must resolve a live server-owned session");
        ctx.runAtTick(12, () -> {
            command(dispatcher, source, "mineogenesis evolution status");
            ctx.assertTrue(feedback.stream().anyMatch(line -> line.contains("Evolution COMPLETED") && line.contains("totalTicks=4")),
                    "Registered lifecycle/server tick events must complete the run: " + feedback);
            ctx.assertTrue(bodies(ctx).isEmpty(), "Automatic completion cleans up the real session's body");
            ctx.assertTrue(command(dispatcher, source, "mineogenesis evolution save " + checkpointName) == 1,
                    "Real server session exposes world-scoped persistence");
            command(dispatcher, source, "mineogenesis evolution storage");
            ctx.assertTrue(feedback.stream().anyMatch(line -> line.contains("directory=") && line.contains("pending=")),
                    "Storage status is connected to the registered service");
            command(dispatcher, source, "mineogenesis reset");
            var directory = source.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("mineogenesis");
            ctx.addInstantFinalTask(() -> {
                ctx.assertTrue(java.nio.file.Files.exists(directory.resolve(checkpointName + ".mng")), "Wait for background checkpoint write");
                try {
                    var archive = new io.github.tiraaamisuuu.mineogenesis.core.storage.CheckpointStore(directory).load(checkpointName);
                    ctx.assertTrue(archive.checkpoint().totalTicks() == 4 && archive.checkpoint().config().seed() == 7,
                            "Real registered session persisted its completed boundary after reset");
                    java.nio.file.Files.delete(directory.resolve(checkpointName + ".mng"));
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
        });
    }

    private static CommandDispatcher<ServerCommandSource> dispatcher(ExperimentSession session) {
        var dispatcher = new CommandDispatcher<ServerCommandSource>();
        ExperimentCommands.register(dispatcher, server -> session);
        return dispatcher;
    }
    private static int command(CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source, String text) {
        try { return dispatcher.execute(text, source); }
        catch (CommandSyntaxException error) { throw new AssertionError("Command failed: " + text, error); }
    }
    private static void drive(TestContext ctx, ExperimentSession session, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) ctx.runAtTick(tick, session::tick);
    }
    private static ServerCommandSource source(TestContext ctx) {
        return ctx.getWorld().getServer().getCommandSource().withWorld(ctx.getWorld())
                .withPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 2))))
                .withRotation(new Vec2f(0, 0)).withLevel(2);
    }
    private static List<AgentEntity> bodies(TestContext ctx) {
        return ctx.getWorld().getEntitiesByClass(AgentEntity.class,
                new Box(Vec3d.of(ctx.getAbsolutePos(new BlockPos(0, 0, 0))),
                        Vec3d.of(ctx.getAbsolutePos(new BlockPos(8, 8, 8)))),
                entity -> !entity.isRemoved());
    }
    private static AgentEntity body(TestContext ctx) {
        var bodies = bodies(ctx);
        ctx.assertTrue(bodies.size() == 1, "Exactly one body must exist, found " + bodies.size());
        return bodies.getFirst();
    }
    private static void floor(TestContext ctx) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
    }
}
