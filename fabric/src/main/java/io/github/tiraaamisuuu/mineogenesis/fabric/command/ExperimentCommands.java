package io.github.tiraaamisuuu.mineogenesis.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ExperimentCommands {
    private ExperimentCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                Function<MinecraftServer, ExperimentSession> sessions) {
        dispatcher.register(literal("mineogenesis").requires(source -> source.hasPermissionLevel(2))
                .then(literal("start").executes(ctx -> execute(ctx, sessions, session ->
                        session.start(ctx.getSource()) ? "Experiment running" : "Experiment already running")))
                .then(literal("pause").executes(ctx -> execute(ctx, sessions, session ->
                        session.pause() ? "Experiment paused; controls cleared" : "Experiment is not running")))
                .then(literal("stop").executes(ctx -> execute(ctx, sessions, session -> {
                    session.stop();
                    return "Experiment stopped; agent removed";
                })))
                .then(literal("reset").executes(ctx -> execute(ctx, sessions, session -> {
                    session.reset();
                    return "Experiment reset to READY; agent removed and ticks cleared";
                })))
                .then(literal("status").executes(ctx -> execute(ctx, sessions, session ->
                        session.status())))
                .then(literal("evolution")
                        .then(literal("start")
                                .executes(ctx -> startEvolution(ctx, sessions, EvolutionConfig.defaults(42)))
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> startEvolution(ctx, sessions,
                                                EvolutionConfig.defaults(LongArgumentType.getLong(ctx, "seed"))))
                                        .then(argument("population", IntegerArgumentType.integer(2, 256))
                                                .then(argument("ticks", IntegerArgumentType.integer(1, 12000))
                                                        .then(argument("generations", IntegerArgumentType.integer(1, 10000))
                                                                .executes(ctx -> startEvolution(ctx, sessions, new EvolutionConfig(
                                                                        LongArgumentType.getLong(ctx, "seed"),
                                                                        IntegerArgumentType.getInteger(ctx, "population"),
                                                                        IntegerArgumentType.getInteger(ctx, "ticks"),
                                                                        IntegerArgumentType.getInteger(ctx, "generations"), 0.1, 0.2))))))))
                        .then(literal("status").executes(ctx -> execute(ctx, sessions, session -> {
                            return session.evolutionStatus();
                        })))
                        .then(literal("replay").executes(ctx -> execute(ctx, sessions, session -> {
                            session.replayChampion();
                            return "Replaying the best evolved network at the training spawn; training scores are unchanged";
                        })))
                        .then(literal("pause").executes(ctx -> execute(ctx, sessions, session ->
                                session.evolution().pause() ? "Evolution paused" : "Evolution is not running")))
                        .then(literal("resume").executes(ctx -> execute(ctx, sessions, session ->
                                session.evolution().resume() ? "Evolution resumed" : "Evolution is not paused")))
                        .then(literal("stop").executes(ctx -> execute(ctx, sessions, session -> {
                            session.evolution(); session.stop(); return "Evolution stopped; summary retained";
                        })))
                        .then(literal("reset").executes(ctx -> execute(ctx, sessions, session -> {
                            session.evolution(); session.reset(); return "Evolution reset; population reseeded";
                        }))))
                .then(literal("agent")
                        .then(literal("mine").then(argument("held", BoolArgumentType.bool()).executes(ctx -> execute(ctx, sessions, session -> {
                            session.setManualMine(BoolArgumentType.getBool(ctx, "held"));
                            return "Manual mine input updated";
                        }))))
                        .then(literal("view").then(argument("yaw", FloatArgumentType.floatArg(-180, 180))
                                .then(argument("pitch", FloatArgumentType.floatArg(-90, 90)).executes(ctx -> execute(ctx, sessions, session -> {
                                    session.setManualView(FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"));
                                    return "Manual view set";
                                })))))
                        .then(literal("idle").executes(ctx -> execute(ctx, sessions, session -> {
                            session.setManualInput(AgentInput.IDLE);
                            return "Agent input cleared (normal momentum and gravity still apply)";
                        })))
                        .then(literal("observe").executes(ctx -> execute(ctx, sessions, session ->
                                session.manager().observation().map(Object::toString).orElse("No agent available"))))
                        .then(literal("control")
                                .then(argument("forward", FloatArgumentType.floatArg(-1, 1))
                                        .then(argument("strafe", FloatArgumentType.floatArg(-1, 1))
                                                .then(argument("yawDelta", FloatArgumentType.floatArg(-180, 180))
                                                        .then(argument("jump", BoolArgumentType.bool())
                                                                .executes(ctx -> execute(ctx, sessions, session -> {
                                                                    session.setManualInput(new AgentInput(
                                                                            FloatArgumentType.getFloat(ctx, "forward"),
                                                                            FloatArgumentType.getFloat(ctx, "strafe"),
                                                                            FloatArgumentType.getFloat(ctx, "yawDelta"),
                                                                            BoolArgumentType.getBool(ctx, "jump")));
                                                                    return "Agent input set; use agent idle to clear it";
                                                                })))))))));
        AcquisitionCommands.register(dispatcher, sessions);
    }

    private static int startEvolution(CommandContext<ServerCommandSource> ctx,
                                      Function<MinecraftServer, ExperimentSession> sessions,
                                      EvolutionConfig config) {
        return execute(ctx, sessions, session -> {
            session.startEvolution(ctx.getSource(), config);
            return "Evolution running: " + config.populationSize() + " candidates, " + config.episodeTicks()
                    + " ticks each, " + config.generations() + " generations; seed=" + config.seed();
        });
    }

    static int execute(CommandContext<ServerCommandSource> ctx,
                               Function<MinecraftServer, ExperimentSession> sessions,
                               Function<ExperimentSession, String> action) {
        ExperimentSession session = sessions.apply(ctx.getSource().getServer());
        if (session == null) {
            ctx.getSource().sendError(Text.literal("Experiment service is not available"));
            return 0;
        }
        try {
            String message = action.apply(session);
            ctx.getSource().sendFeedback(() -> Text.literal(message), false);
            return 1;
        } catch (IllegalStateException | IllegalArgumentException error) {
            ctx.getSource().sendError(Text.literal(error.getMessage()));
            return 0;
        }
    }
}
