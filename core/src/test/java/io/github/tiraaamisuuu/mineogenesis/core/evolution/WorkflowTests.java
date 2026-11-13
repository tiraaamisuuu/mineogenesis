package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.io.IOException;
import io.github.tiraaamisuuu.mineogenesis.core.storage.*;

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
        test("binary archives round-trip all genomes, history, RNG and adapter metadata", () -> {
            var archive = archive(); byte[] bytes = CheckpointCodec.encode(archive);
            var decoded = CheckpointCodec.decode(bytes);
            check(Arrays.equals(bytes, CheckpointCodec.encode(decoded)), "Binary round trip differs");
            check(decoded.metadata().equals(archive.metadata()), "Metadata differs");
            rejects(UnsupportedOperationException.class, () -> decoded.metadata().clear());
            var expected = EvolutionPopulation.restore(decoded.checkpoint().config(), archive.checkpoint().population());
            var actual = EvolutionPopulation.restore(decoded.checkpoint().config(), decoded.checkpoint().population());
            expected.breed(new double[]{3, 2, 1}); actual.breed(new double[]{3, 2, 1});
            check(Arrays.equals(expected.genome(1).genes(), actual.genome(1).genes()), "Decoded genetic stream differs");
        });
        test("corruption, truncation, unsupported versions, topology and unbounded sizes are rejected", () -> {
            byte[] good = CheckpointCodec.encode(archive());
            byte[] damaged = good.clone(); damaged[100] ^= 1;
            rejects(IOException.class, () -> CheckpointCodec.decode(damaged));
            rejects(IOException.class, () -> CheckpointCodec.decode(Arrays.copyOf(good, good.length - 1)));
            rejects(IOException.class, () -> CheckpointCodec.decode(new byte[CheckpointCodec.MAX_BYTES + 1]));
            for (int offset : new int[]{4, 8, 20, 24, 36, 40, 44}) {
                byte[] malformed = good.clone(); ByteBuffer.wrap(malformed).putInt(offset, Integer.MAX_VALUE); rehash(malformed);
                rejects(IOException.class, () -> CheckpointCodec.decode(malformed));
            }
            rejects(IllegalArgumentException.class, () -> new RunArchive(archive().checkpoint(), Map.of("invalid/key", "x")));
        });
        test("inconsistent statistics and excessive continuation limits are rejected", () -> {
            var c = archive().checkpoint();
            rejects(IllegalArgumentException.class, () -> new TrainingCheckpoint(c.config(), c.objective(), c.completedGeneration(),
                    c.population(), c.champion(), c.bestFitness(), c.totalTicks() + 1, c.totalSuccesses(), c.history()));
            rejects(IllegalArgumentException.class, () -> new GenerationStats(1, 0, Double.NaN, 0));
            rejects(IllegalArgumentException.class, () -> EvolutionRun.restore(new World(), c, 10000));
        });
        test("atomic storage replaces complete files and limits names, files and symlinks", () -> {
            Path root = Files.createTempDirectory("mineogenesis-store-");
            try {
                var store = new CheckpointStore(root); var a = archive();
                store.save("trial_42", a); byte[] before = Files.readAllBytes(root.resolve("trial_42.mng"));
                store.save("trial_42", a);
                check(Arrays.equals(before, CheckpointCodec.encode(store.load("trial_42"))), "Stored file differs");
                check(store.names().equals(java.util.List.of("trial_42")), "Saved name list");
                for (String name : new String[]{"../escape", "/absolute", "", ".", "a b", "x".repeat(49)}) {
                    rejects(IllegalArgumentException.class, () -> store.save(name, a));
                }
                Files.createSymbolicLink(root.resolve("link.mng"), root.resolve("trial_42.mng"));
                rejects(IOException.class, () -> store.load("link"));
                rejects(IOException.class, () -> store.save("link", a));
                check(Arrays.equals(before, Files.readAllBytes(root.resolve("trial_42.mng"))), "Rejected writes changed previous file");
                try (var paths = Files.list(root)) { check(paths.noneMatch(p -> p.toString().endsWith(".tmp")), "Temporary file leaked"); }
            } finally { delete(root); }
        });
        test("CSV contains completed generations and consistent tick/success totals", () -> {
            var a = archive(); String[] lines = CheckpointStore.csv(a).lines().toArray(String[]::new);
            check(lines.length == 3 && lines[0].contains("success_rate"), "CSV header/rows");
            String[] last = lines[2].split(",");
            check(last.length == 20 && last[0].equals("2") && last[9].equals("30") && last[12].equals("EXPLORATION"), "CSV counters/columns");
            check(last[15].equals("12-8-6") && last[16].equals("1") && last[17].equals(CheckpointStore.fixtureId(a)), "CSV provenance");
            check(!CheckpointStore.fixtureId(a).equals(CheckpointStore.fixtureId(new RunArchive(a.checkpoint(), Map.of("world", "other:world")))), "Different fixture descriptions must have distinct IDs");
        });
        test("background writer coalesces autosaves and flushes explicit saves and exports", () -> {
            Path root = Files.createTempDirectory("mineogenesis-writer-");
            try {
                var store = new CheckpointStore(root); var writer = new TrainingWriter(store); var a = archive();
                for (int i = 0; i < 100; i++) writer.autosave(a);
                writer.submit("named", a, false); writer.submit("metrics", a, true);
                rejects(IllegalArgumentException.class, () -> writer.submit("latest", a, false));
                writer.close(); writer.close();
                check(writer.idle() && store.names().equals(java.util.List.of("latest", "named")), "Flush must finish all accepted files");
                check(Files.readString(root.resolve("metrics.csv")).equals(CheckpointStore.csv(a)), "Export contents");
                rejects(IllegalStateException.class, () -> writer.autosave(a));
            } finally { delete(root); }
        });
        test("background errors are visible and shutdown does not claim successful saving", () -> {
            Path root = Files.createTempDirectory("mineogenesis-write-error-");
            try {
                Path blocked = root.resolve("not-a-directory"); Files.writeString(blocked, "block writes");
                var writer = new TrainingWriter(new CheckpointStore(blocked)); writer.autosave(archive());
                rejects(IOException.class, writer::close);
                check(writer.status().contains("Write failed"), "Status must expose disk error");
            } finally { delete(root); }
        });
        System.out.println("Passed " + passed + " workflow tests");
    }
    private static EvolutionConfig config(int generations) { return new EvolutionConfig(42, 3, 5, generations, .35, .2); }
    private static RunArchive archive() {
        var world = new World(); var run = new EvolutionRun(world, config(2)); run.start(); finish(run, world);
        return new RunArchive(run.checkpoint().orElseThrow(), Map.of("world", "minecraft:overworld", "spawn_x", "1.5"));
    }
    private static void rehash(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes, bytes.length - 32));
        System.arraycopy(hash, 0, bytes, bytes.length - 32, 32);
    }
    private static void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
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
