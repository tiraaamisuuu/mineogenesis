package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

/** Dependency-free regression checks for training continuation and storage. */
public final class WorkflowTests {
    private static int passed;
    public static void main(String[] args) throws Exception {
        test("checkpoint RNG preserves the Java Random stream and cached Gaussian", () -> {
            for (long seed : new long[]{0, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                var expected = new Random(seed);
                var actual = new CheckpointRandom(seed);
                for (int i = 0; i < 1000; i++) {
                    check(expected.nextInt(157) == actual.nextInt(157), "Bounded draw differs");
                    check(expected.nextDouble() == actual.nextDouble(), "Uniform draw differs");
                    check(expected.nextGaussian() == actual.nextGaussian(), "Gaussian draw differs");
                    actual = CheckpointRandom.restore(actual.snapshot());
                }
                actual.setSeed(seed); expected.setSeed(seed);
                check(actual.nextGaussian() == expected.nextGaussian(), "Reseeding clears cached Gaussian");
            }
        });
        test("saved population is immutable and restores the future breeding stream", () -> {
            var config = config(4);
            var population = new EvolutionPopulation(config);
            var snapshot = population.snapshot();
            var restored = EvolutionPopulation.restore(config, snapshot);
            rejects(UnsupportedOperationException.class, () -> snapshot.genomes().clear());
            double[] first = snapshot.genomes().getFirst().genes();
            for (int i = 0; i < 5; i++) {
                population.breed(new double[]{1, 2, 3}); restored.breed(new double[]{1, 2, 3});
                for (int g = 0; g < 3; g++) check(Arrays.equals(population.genome(g).genes(), restored.genome(g).genes()), "Future genome differs");
            }
            check(Arrays.equals(first, snapshot.genomes().getFirst().genes()), "Snapshot changed after breeding");
        });
        test("generation-boundary continuation matches uninterrupted actions and results", () -> {
            var fullWorld = new World(); var full = new EvolutionRun(fullWorld, config(4));
            full.start(); finish(full, fullWorld);
            var splitWorld = new World(); var split = new EvolutionRun(splitWorld, config(2));
            split.start(); finish(split, splitWorld);
            var resumedWorld = new World();
            var resumed = EvolutionRun.restore(resumedWorld, split.checkpoint().orElseThrow(), 2);
            check(!resumedWorld.present && resumed.state() == EvolutionRun.State.READY, "Restore must not spawn");
            resumed.start(); finish(resumed, resumedWorld);
            var actions = new ArrayList<>(splitWorld.actions); actions.addAll(resumedWorld.actions);
            check(actions.equals(fullWorld.actions), "Continued controller actions differ");
            check(full.history().equals(resumed.history()), "Continued history differs");
            check(Arrays.equals(full.champion().orElseThrow().genes(), resumed.champion().orElseThrow().genes()), "Champion differs");
            check(full.checkpoint().orElseThrow().population().random().equals(resumed.checkpoint().orElseThrow().population().random()), "RNG differs");
        });
        test("partial generations never replace a boundary and reset clears saved state", () -> {
            var world = new World(); var run = new EvolutionRun(world, config(3));
            check(run.checkpoint().isEmpty(), "No initial boundary"); run.start();
            double progress = 0;
            while (run.checkpoint().isEmpty()) {
                run.tick(); world.physics();
                check(run.progressFraction() >= progress && run.progressFraction() <= 1, "Progress bounds/order");
                progress = run.progressFraction();
            }
            var saved = run.checkpoint().orElseThrow();
            run.tick(); world.physics(); run.pause(); run.tick();
            check(run.checkpoint().orElseThrow() == saved, "Partial progress replaced checkpoint");
            rejects(UnsupportedOperationException.class, () -> saved.history().clear());
            run.reset(); check(run.history().isEmpty() && run.checkpoint().isEmpty() && run.progressFraction() == 0, "Reset clears history");
        });
        System.out.println("Passed " + passed + " workflow tests");
    }
    private static EvolutionConfig config(int generations) { return new EvolutionConfig(42, 3, 5, generations, .35, .2); }
    private static void finish(EvolutionRun run, World world) {
        int budget = 10000;
        while (run.state() == EvolutionRun.State.RUNNING && --budget > 0) { run.tick(); world.physics(); }
        check(run.state() == EvolutionRun.State.COMPLETED && run.progressFraction() == 1, "Run must finish within budget");
    }
    private static final class World implements EvolutionEnvironment {
        boolean present, running; double x, z; AgentInput input = AgentInput.IDLE;
        final ArrayList<AgentInput> actions = new ArrayList<>();
        public void spawn() { check(!present, "Duplicate body"); present = true; x = z = .5; }
        public void remove() { present = running = false; }
        public void setRunning(boolean value) { running = value; }
        public void apply(AgentInput value) { input = value; actions.add(value); }
        void physics() { if (present && running) { x += input.forward() * 2; z += input.strafe() * 2; } }
        public Optional<AgentObservation> observe() { return present ? Optional.of(new AgentObservation(0, 0, 0, 0, 0, true, false, "minecraft:air")) : Optional.empty(); }
        public Optional<AgentFrame> frame() { return observe().map(o -> new AgentFrame(o, x, z)); }
    }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void test(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void rejects(Class<? extends Throwable> type, Check action) {
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError(error); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
