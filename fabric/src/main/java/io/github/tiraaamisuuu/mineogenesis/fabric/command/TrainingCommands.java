package io.github.tiraaamisuuu.mineogenesis.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.function.Function;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands.execute;

public final class TrainingCommands {
    private TrainingCommands() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, Function<MinecraftServer, ExperimentSession> sessions) {
        dispatcher.register(literal("mineogenesis").requires(source -> source.hasPermissionLevel(2))
                .then(literal("evolution")
                        .then(literal("save").then(argument("name", StringArgumentType.word()).executes(ctx -> execute(ctx, sessions, session -> {
                            session.saveTraining(StringArgumentType.getString(ctx, "name"), false);
                            return "Checkpoint queued; use evolution storage to confirm the write";
                        }))))
                        .then(literal("export").then(argument("name", StringArgumentType.word()).executes(ctx -> execute(ctx, sessions, session -> {
                            session.saveTraining(StringArgumentType.getString(ctx, "name"), true);
                            return "CSV export queued; use evolution storage to confirm the write";
                        }))))
                        .then(literal("load").then(argument("name", StringArgumentType.word())
                                .then(argument("additionalGenerations", IntegerArgumentType.integer(1, 9999)).executes(ctx -> execute(ctx, sessions, session -> {
                                    session.loadTraining(ctx.getSource(), StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "additionalGenerations"));
                                    return "Checkpoint loaded without spawning; use evolution continue when the training area is ready";
                                })))))
                        .then(literal("continue").executes(ctx -> execute(ctx, sessions, session -> {
                            session.continueTraining(); return "Continuing the saved population at its training spawn";
                        })))
                        .then(literal("files").executes(ctx -> execute(ctx, sessions, ExperimentSession::savedTrainingNames)))
                        .then(literal("storage").executes(ctx -> execute(ctx, sessions, ExperimentSession::storageStatus))))
                .then(literal("monitor")
                        .then(literal("on").executes(ctx -> monitor(ctx, sessions, true)))
                        .then(literal("off").executes(ctx -> monitor(ctx, sessions, false)))));
    }
    private static int monitor(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                               Function<MinecraftServer, ExperimentSession> sessions, boolean enabled) {
        return execute(ctx, sessions, session -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) throw new IllegalArgumentException("A player must enable their own monitor");
            session.monitor().update(session.evolutionOrNull()); session.monitor().subscribe(player, enabled);
            return "Training monitor " + (enabled ? "enabled" : "hidden");
        });
    }
}
