package io.github.tiraaamisuuu.mineogenesis.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentManager;
import io.github.tiraaamisuuu.mineogenesis.core.ExperimentState;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.AgentEntity;
import io.github.tiraaamisuuu.mineogenesis.fabric.agent.MinecraftAgentEnvironment;
import io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class FoundationGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void movementPauseResumeAndCleanup(TestContext ctx) {
        floor(ctx);
        Vec3d origin = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2)));
        var environment = new MinecraftAgentEnvironment();
        environment.configure(ctx.getWorld(), origin, 0);
        var manager = new ExperimentManager(environment);
        manager.start();
        AgentEntity body = body(ctx, origin);
        ctx.assertTrue(!body.saveNbt(new NbtCompound()), "Experiment bodies must not be saved");
        manager.setInput(new AgentInput(1, 0, 0, false));
        manager.tick();

        ctx.runAtTick(6, () -> {
            ctx.assertTrue(body.getZ() > origin.z + 0.05, "Forward input must move the body");
            ctx.assertTrue(manager.observation().isPresent(), "A running body exposes observations");
            manager.pause();
            Vec3d paused = body.getPos();
            ctx.runAtTick(10, () -> {
                ctx.assertTrue(body.getPos().squaredDistanceTo(paused) < 0.000001,
                        "A paused body must not drift or fall");
                ctx.assertTrue(manager.elapsedTicks() == 1, "Paused ticks must not be counted");
                manager.start();
                manager.tick();
                ctx.runAtTick(14, () -> {
                    ctx.assertTrue(Math.abs(body.getZ() - paused.z) < 0.001,
                            "Resuming must clear the previous forward input");
                    manager.stop();
                    ctx.assertTrue(body.isRemoved(), "Stop must discard the body");
                    ctx.assertTrue(manager.observation().isEmpty(), "Stop must clear observations");
                    manager.reset();
                    ctx.assertTrue(manager.state() == ExperimentState.READY && manager.elapsedTicks() == 0,
                            "Reset must clear the run");
                    manager.start();
                    AgentEntity replacement = body(ctx, origin);
                    ctx.assertTrue(replacement != body, "A new run needs a fresh body");
                    manager.reset();
                    ctx.assertTrue(replacement.isRemoved(), "Reset must remove the new body");
                    ctx.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
    public void rotationAndJumpAreExplicitActuators(TestContext ctx) {
        floor(ctx);
        Vec3d origin = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 3)));
        var environment = new MinecraftAgentEnvironment();
        environment.configure(ctx.getWorld(), origin, 0);
        var manager = new ExperimentManager(environment);
        manager.start();
        AgentEntity body = body(ctx, origin);
        manager.setInput(new AgentInput(0, 0, 10, false));
        manager.tick();
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(body.getYaw() > 0, "Yaw input must rotate the body");
            ctx.assertTrue(Math.abs(body.getX() - origin.x) < 0.001
                    && Math.abs(body.getZ() - origin.z) < 0.001, "Rotation alone must not walk");
            manager.setInput(new AgentInput(0, 0, 0, true));
            manager.tick();
            ctx.runAtTick(8, () -> {
                ctx.assertTrue(body.getY() > origin.y + 0.1, "Grounded jump input must lift the body");
                manager.reset();
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void obstructedSpawnDoesNotCreateAgent(TestContext ctx) {
        floor(ctx);
        BlockPos relative = new BlockPos(2, 2, 2);
        ctx.setBlockState(relative, Blocks.STONE);
        Vec3d origin = Vec3d.ofBottomCenter(ctx.getAbsolutePos(relative));
        var environment = new MinecraftAgentEnvironment();
        environment.configure(ctx.getWorld(), origin, 0);
        var manager = new ExperimentManager(environment);
        try {
            manager.start();
            throw new AssertionError("An obstructed spawn must fail");
        } catch (IllegalStateException expected) {
            ctx.assertTrue(manager.state() == ExperimentState.READY, "Failed spawn must stay READY");
            ctx.assertTrue(environment.observe().isEmpty(), "Failed spawn must not leave an agent");
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void operatorCommandsControlLifecycle(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var dispatcher = new CommandDispatcher<ServerCommandSource>();
        ExperimentCommands.register(dispatcher, server -> session);
        var source = ctx.getWorld().getServer().getCommandSource()
                .withWorld(ctx.getWorld())
                .withPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))))
                .withRotation(new Vec2f(0, 0)).withLevel(2);
        try {
            ctx.assertTrue(dispatcher.execute("mineogenesis start", source) == 1, "Start command");
            ctx.assertTrue(dispatcher.execute("mineogenesis agent control 1 0 0 false", source) == 1,
                    "Control command");
            session.tick();
            dispatcher.execute("mineogenesis pause", source);
            ctx.assertTrue(session.manager().state() == ExperimentState.PAUSED, "Pause command");
            ctx.assertTrue(dispatcher.execute("mineogenesis agent control 1 0 0 false", source) == 0,
                    "Controls while paused must fail");
            dispatcher.execute("mineogenesis start", source);
            dispatcher.execute("mineogenesis agent idle", source);
            dispatcher.execute("mineogenesis agent observe", source);
            dispatcher.execute("mineogenesis status", source);
            dispatcher.execute("mineogenesis stop", source);
            ctx.assertTrue(session.manager().state() == ExperimentState.STOPPED, "Stop command");
            dispatcher.execute("mineogenesis reset", source);
            ctx.assertTrue(session.manager().state() == ExperimentState.READY, "Reset command");
            try {
                dispatcher.execute("mineogenesis start", source.withLevel(0));
                throw new AssertionError("Non-operators must not start experiments");
            } catch (CommandSyntaxException expected) {
                ctx.assertTrue(session.manager().state() == ExperimentState.READY, "Permission denial preserves state");
            }
        } catch (CommandSyntaxException error) {
            throw new AssertionError("Registered command failed to parse", error);
        } finally {
            session.close();
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void strafeCollisionAndBlockObservation(TestContext ctx) {
        floor(ctx);
        // At yaw zero the body faces south; left strafe is east (increasing X).
        for (int z = 1; z < 7; z++) {
            ctx.setBlockState(new BlockPos(5, 2, z), Blocks.STONE);
            ctx.setBlockState(new BlockPos(5, 3, z), Blocks.STONE);
        }
        Vec3d origin = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 3)));
        var environment = new MinecraftAgentEnvironment();
        environment.configure(ctx.getWorld(), origin, 0);
        var manager = new ExperimentManager(environment);
        manager.start();
        AgentEntity agent = body(ctx, origin);
        manager.setInput(new AgentInput(0, 1, 0, false));
        manager.tick();
        ctx.runAtTick(25, () -> {
            ctx.assertTrue(agent.getX() > origin.x + 0.1, "Strafe must move left relative to yaw");
            ctx.assertTrue(agent.horizontalCollision, "Solid wall must block the body");
            agent.setYaw(-90);
            manager.pause();
            ctx.assertTrue(manager.observation().orElseThrow().blockAhead().equals("minecraft:stone"),
                    "Raycast must report the wall ahead");
            manager.reset();
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removedBodyStopsSessionAndAllowsRestart(TestContext ctx) {
        floor(ctx);
        var session = new ExperimentSession();
        var source = ctx.getWorld().getServer().getCommandSource()
                .withWorld(ctx.getWorld())
                .withPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 2, 2))))
                .withRotation(new Vec2f(0, 0)).withLevel(2);
        session.start(source);
        body(ctx, source.getPosition().add(0, 0, 2)).discard();
        session.tick();
        ctx.assertTrue(session.manager().state() == ExperimentState.STOPPED,
                "Losing the body must stop the session");
        ctx.assertTrue(session.manager().observation().isEmpty(), "Removed body must not be observed");
        session.start(source);
        ctx.assertTrue(session.manager().state() == ExperimentState.RUNNING, "A fresh run must be possible");
        AgentEntity replacement = body(ctx, source.getPosition().add(0, 0, 2));
        session.close();
        session.close();
        ctx.assertTrue(replacement.isRemoved(), "Shutdown cleanup must discard the body idempotently");
        ctx.complete();
    }

    private static void floor(TestContext ctx) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        }
    }

    private static AgentEntity body(TestContext ctx, Vec3d origin) {
        var bodies = ctx.getWorld().getEntitiesByClass(AgentEntity.class,
                new Box(BlockPos.ofFloored(origin)).expand(5), entity -> !entity.isRemoved());
        ctx.assertTrue(bodies.size() == 1, "Exactly one physical body must exist");
        return bodies.getFirst();
    }
}
