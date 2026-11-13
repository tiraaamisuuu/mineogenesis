package io.github.tiraaamisuuu.mineogenesis.fabric.experiment;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.EvolutionRun;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.List;
import java.util.Locale;

/** Vanilla boss-bar packets only; callers are always on the server thread. */
public final class TrainingMonitor {
    private final ServerBossBar bar = new ServerBossBar(Text.literal("Mineogenesis | No training run"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
    private int ticks;
    public void subscribe(ServerPlayerEntity player, boolean enabled) {
        if (enabled) bar.addPlayer(player); else bar.removePlayer(player);
    }
    public void tick(EvolutionRun run) {
        for (var player : List.copyOf(bar.getPlayers())) if (player.isRemoved()) bar.removePlayer(player);
        if (++ticks % 10 == 0) update(run);
    }
    public void update(EvolutionRun run) {
        if (run == null) { bar.setName(Text.literal("Mineogenesis | No training run")); bar.setPercent(0); return; }
        bar.setName(Text.literal(String.format(Locale.ROOT, "Mineogenesis %s | Gen %d/%d Agent %d/%d | Best %.2f | Collected %d",
                run.state(), run.generation(), run.config().generations(), run.individual(), run.config().populationSize(), run.bestFitness(), run.totalSuccesses())));
        bar.setPercent((float) run.progressFraction());
        bar.setColor(run.state() == EvolutionRun.State.PAUSED ? BossBar.Color.YELLOW : run.state() == EvolutionRun.State.COMPLETED ? BossBar.Color.GREEN : BossBar.Color.BLUE);
    }
    public String title() { return bar.getName().getString(); }
    public float progress() { return bar.getPercent(); }
    public void close() { bar.clearPlayers(); }
}
