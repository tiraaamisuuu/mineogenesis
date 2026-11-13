package io.github.tiraaamisuuu.mineogenesis.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import io.github.tiraaamisuuu.mineogenesis.fabric.experiment.ExperimentSession;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import java.util.function.Function;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static io.github.tiraaamisuuu.mineogenesis.fabric.command.ExperimentCommands.execute;

public final class AcquisitionCommands {
    private AcquisitionCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                Function<MinecraftServer, ExperimentSession> sessions) {
        dispatcher.register(literal("mineogenesis").requires(source -> source.hasPermissionLevel(2))
                .then(literal("acquisition")
                        .then(literal("target").executes(ctx -> execute(ctx, sessions, session -> {
                            session.selectTarget(ctx.getSource()); return session.targetStatus();
                        })).then(argument("position", BlockPosArgumentType.blockPos()).executes(ctx -> execute(ctx, sessions, session -> {
                            // BlockPosArgumentType resolves relative coordinates against this source.
                            var pos = BlockPosArgumentType.getBlockPos(ctx, "position");
                            session.selectTarget(ctx.getSource(), pos); return session.targetStatus();
                        }))))
                        .then(literal("clear").executes(ctx -> execute(ctx, sessions, session -> {
                            session.clearTarget(); return "Acquisition target cleared";
                        })))
                        .then(literal("status").executes(ctx -> execute(ctx, sessions, session ->
                                session.acquisitionStatus())))
                        .then(literal("manual").executes(ctx -> execute(ctx, sessions, session -> {
                            session.startManualAcquisition(ctx.getSource()); return "Manual acquisition active; only the selected log can be mined";
                        })))
                        .then(EvolutionStartArguments.command(defaults(42), (ctx, config) -> start(ctx, sessions, config)))));

    }

    private static EvolutionConfig defaults(long seed) { return new EvolutionConfig(seed, 16, 200, 10, 0.1, 0.2); }

    private static int start(CommandContext<ServerCommandSource> ctx,
                             Function<MinecraftServer, ExperimentSession> sessions, EvolutionConfig config) {
        return execute(ctx, sessions, session -> {
            session.startAcquisition(ctx.getSource(), config);
            return "Oak-log acquisition running: " + config.populationSize() + " candidates, " + config.episodeTicks()
                    + " ticks maximum, " + config.generations() + " generations; seed=" + config.seed()
                    + ", mutationProbability=" + config.mutationProbability() + ", mutationSigma=" + config.mutationSigma();
        });
    }
}
