package io.github.tiraaamisuuu.mineogenesis.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
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
                        session.manager().pause() ? "Experiment paused; controls cleared" : "Experiment is not running")))
                .then(literal("stop").executes(ctx -> execute(ctx, sessions, session -> {
                    session.manager().stop();
                    return "Experiment stopped; agent removed";
                })))
                .then(literal("reset").executes(ctx -> execute(ctx, sessions, session -> {
                    session.manager().reset();
                    return "Experiment reset to READY; agent removed and ticks cleared";
                })))
                .then(literal("status").executes(ctx -> execute(ctx, sessions, session ->
                        "State=" + session.manager().state() + ", ticks=" + session.manager().elapsedTicks())))
                .then(literal("agent")
                        .then(literal("idle").executes(ctx -> execute(ctx, sessions, session -> {
                            session.manager().setInput(AgentInput.IDLE);
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
                                                                    session.manager().setInput(new AgentInput(
                                                                            FloatArgumentType.getFloat(ctx, "forward"),
                                                                            FloatArgumentType.getFloat(ctx, "strafe"),
                                                                            FloatArgumentType.getFloat(ctx, "yawDelta"),
                                                                            BoolArgumentType.getBool(ctx, "jump")));
                                                                    return "Agent input set; use agent idle to clear it";
                                                                })))))))));
    }

    private static int execute(CommandContext<ServerCommandSource> ctx,
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
