package io.github.tiraaamisuuu.mineogenesis.fabric.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionConfig;
import net.minecraft.server.command.ServerCommandSource;
import java.util.function.BiFunction;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** One bounded start grammar shared by exploration and acquisition. */
final class EvolutionStartArguments {
    private EvolutionStartArguments() { }
    static LiteralArgumentBuilder<ServerCommandSource> command(EvolutionConfig defaults,
            BiFunction<CommandContext<ServerCommandSource>, EvolutionConfig, Integer> start) {
        var generations = argument("generations", IntegerArgumentType.integer(1, 10000))
                .executes(ctx -> start.apply(ctx, configured(ctx, defaults.mutationProbability(), defaults.mutationSigma())))
                .then(argument("mutationProbability", DoubleArgumentType.doubleArg(0, 1))
                        .then(argument("mutationSigma", DoubleArgumentType.doubleArg(0, 8))
                                .executes(ctx -> start.apply(ctx, configured(ctx,
                                        DoubleArgumentType.getDouble(ctx, "mutationProbability"),
                                        DoubleArgumentType.getDouble(ctx, "mutationSigma"))))));
        return literal("start").executes(ctx -> start.apply(ctx, defaults))
                .then(argument("seed", LongArgumentType.longArg()).executes(ctx -> start.apply(ctx,
                                new EvolutionConfig(LongArgumentType.getLong(ctx, "seed"), defaults.populationSize(), defaults.episodeTicks(),
                                        defaults.generations(), defaults.mutationProbability(), defaults.mutationSigma())))
                        .then(argument("population", IntegerArgumentType.integer(2, 256))
                                .then(argument("ticks", IntegerArgumentType.integer(1, 12000)).then(generations))));
    }
    private static EvolutionConfig configured(CommandContext<ServerCommandSource> ctx, double probability, double sigma) {
        return new EvolutionConfig(LongArgumentType.getLong(ctx, "seed"), IntegerArgumentType.getInteger(ctx, "population"),
                IntegerArgumentType.getInteger(ctx, "ticks"), IntegerArgumentType.getInteger(ctx, "generations"), probability, sigma);
    }
}
