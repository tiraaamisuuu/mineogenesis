package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public final class EvolutionTests {
    private static int passed;

    public static void main(String[] args) {
        test("network zero weights and known bias output", () -> {
            double[] genes = new double[NetworkGenome.GENE_COUNT];
            double[] result = output(new NetworkGenome(genes));
            check(Arrays.equals(result, new double[4]), "Zero network must output zero");
            genes[80] = 1; // First output bias: hidden rows occupy 72 genes.
            result = output(new NetworkGenome(genes));
            close(result[0], Math.tanh(1));
            close(result[1], 0);
            check(NetworkGenome.GENE_COUNT == 108, "Fixed topology gene count");
        });
        test("network validates genes and inference buffers", () -> {
            rejects(IllegalArgumentException.class, () -> new NetworkGenome(new double[1]));
            double[] bad = new double[NetworkGenome.GENE_COUNT];
            bad[0] = Double.NaN;
            rejects(IllegalArgumentException.class, () -> new NetworkGenome(bad));
            bad[0] = 9;
            rejects(IllegalArgumentException.class, () -> new NetworkGenome(bad));
            var genome = NetworkGenome.random(new Random(1));
            rejects(IllegalArgumentException.class, () -> genome.evaluate(new double[1], new double[8], new double[4]));
            double[] input = new double[8];
            rejects(IllegalArgumentException.class, () -> genome.evaluate(input, input, new double[4]));
            input[0] = Double.POSITIVE_INFINITY;
            rejects(IllegalArgumentException.class, () -> genome.evaluate(input, new double[8], new double[4]));
        });
        test("genome copies and mutation never alter the parent", () -> {
            double[] genes = new double[NetworkGenome.GENE_COUNT];
            var parent = new NetworkGenome(genes);
            genes[0] = 1;
            close(parent.genes()[0], 0);
            double[] exported = parent.genes();
            exported[0] = 1;
            close(parent.genes()[0], 0);
            check(Arrays.equals(parent.genes(), parent.mutate(new Random(1), 0, 1).genes()), "No mutation at probability zero");
            var child = parent.mutate(new Random(1), 1, 8);
            check(!Arrays.equals(parent.genes(), child.genes()), "Mutation must change a child");
            check(Arrays.stream(parent.genes()).allMatch(value -> value == 0), "Parent must remain intact");
            check(Arrays.stream(child.genes()).allMatch(value -> Math.abs(value) <= 8), "Mutation remains bounded");
        });
        test("seeded populations and breeding reproduce exactly", () -> {
            var config = config(4, 3, 2);
            var first = new EvolutionPopulation(config);
            var second = new EvolutionPopulation(config);
            for (int generation = 0; generation < 5; generation++) {
                for (int i = 0; i < first.size(); i++) {
                    check(Arrays.equals(first.genome(i).genes(), second.genome(i).genes()), "Seeded weights differ");
                }
                first.breed(new double[] {1, 4, 2, 3});
                second.breed(new double[] {1, 4, 2, 3});
            }
        });
        test("elitism preserves the highest finite fitness, including negative scores", () -> {
            var population = new EvolutionPopulation(config(4, 3, 2));
            NetworkGenome elite = population.genome(2);
            population.breed(new double[] {-4, -3, -1, -2});
            check(population.genome(0) == elite, "Immutable elite must survive unchanged");
            rejects(IllegalArgumentException.class, () -> population.breed(new double[] {1}));
            rejects(IllegalArgumentException.class, () -> population.breed(new double[] {0, 0, Double.NaN, 0}));
        });
        test("seeded mutation and selection improve a synthetic neural objective", () -> {
            // A numerical smoke objective, not a scripted Minecraft action policy.
            var population = new EvolutionPopulation(new EvolutionConfig(42, 64, 1, 100, 0.12, 0.2));
            double first = 0, previous = Double.NEGATIVE_INFINITY;
            for (int generation = 0; generation < 100; generation++) {
                double[] scores = new double[population.size()];
                for (int i = 0; i < scores.length; i++) scores[i] = xorFitness(population.genome(i));
                double best = scores[population.bestIndex(scores)];
                if (generation == 0) first = best;
                check(best + 1e-12 >= previous, "Elitism must retain best fitness on a deterministic objective");
                previous = best;
                population.breed(scores);
            }
            check(previous > first + 0.05, "Synthetic optimisation did not improve sufficiently: " + first + " -> " + previous);
            System.out.println("Synthetic XOR fitness: " + first + " -> " + previous);
        });
        test("controller output bounds and memory reset", () -> {
            double[] genes = new double[NetworkGenome.GENE_COUNT];
            genes[80] = 1; // constant forward drive
            genes[73] = 1; // output forward also uses hidden neuron 1
            genes[15] = 1; // hidden neuron 1 receives previous forward (input 6)
            var controller = new NeuralController(new NetworkGenome(genes));
            AgentInput first = controller.decide(observation(true));
            AgentInput second = controller.decide(observation(true));
            check(second.forward() > first.forward(), "Previous action input must affect the controller");
            controller.resetMemory();
            check(controller.decide(observation(true)).equals(first), "Reset must clear actuator memory");
            for (int i = 0; i < 100; i++) {
                AgentInput input = new NeuralController(NetworkGenome.random(new Random(i))).decide(observation(true));
                check(Math.hypot(input.forward(), input.strafe()) <= 1.000001 && Math.abs(input.yawDelta()) <= 10,
                        "Neural outputs must obey actuator limits");
            }
        });
        test("grounded novelty excludes origin, revisits and airborne cells", () -> {
            var fitness = new GroundExplorationFitness(frame(0.5, 0.5, false));
            fitness.record(frame(0.5, 0.5, true));
            fitness.record(frame(1.5, 0.5, true));
            fitness.record(frame(1.9, 0.9, true));
            fitness.record(frame(2.5, 0.5, false));
            check(fitness.score() == 1, "Only one new grounded cell should count");
            fitness.record(frame(-0.1, 0.5, true));
            fitness.record(frame(0.5, -0.1, true));
            check(fitness.score() == 3, "Negative coordinates must floor correctly and distinct axes must not collide");
        });
        test("episodes score the final physics outcome and replace bodies exactly", () -> {
            var world = new FakeEnvironment();
            var run = new EvolutionRun(world, config(2, 2, 2));
            run.start();
            for (int i = 0; i < 8; i++) {
                run.tick();
                world.physics();
            }
            check(run.state() == EvolutionRun.State.RUNNING, "Final outcome is still pending");
            run.tick();
            check(run.state() == EvolutionRun.State.COMPLETED, "Run must complete after every outcome is sampled");
            check(run.totalTicks() == 8 && world.applications == 8, "Exact action budget");
            check(world.spawns == 4 && world.removals == 4 && !world.present, "One fresh body per candidate; final body removed");
            check(run.lastStats().orElseThrow().bestFitness() == 2, "Final action must earn its new-cell reward");
            check(run.champion().isPresent() && run.generation() == 2 && run.individual() == 2, "Final statistics");
        });
        test("new and resumed bodies wait for the controller before ticking physics", () -> {
            var world = new FakeEnvironment();
            var run = new EvolutionRun(world, config(2, 2, 1));
            run.start(); world.physics();
            check(world.x == 0.5 && !world.running, "No uncounted physics before the first control tick");
            run.tick(); world.physics(); run.pause(); run.resume(); world.physics();
            check(world.x == 1.5 && !world.running, "Resume must wait for the next driver tick");
            run.tick(); world.physics();
            check(world.x == 2.5 && run.totalTicks() == 2, "Exactly two controlled physics steps");
            run.stop();
        });
        test("pause and resume preserve counters and pending outcomes", () -> {
            var world = new FakeEnvironment();
            var run = new EvolutionRun(world, config(2, 2, 1));
            run.start(); run.tick(); world.physics();
            check(run.pause() && !world.running, "Pause must freeze the body");
            for (int i = 0; i < 10; i++) { run.tick(); world.physics(); }
            check(run.totalTicks() == 1 && world.x == 1.5, "No action or physics while paused");
            check(!run.pause() && run.resume(), "Idempotent pause and valid resume");
            run.tick(); world.physics();
            check(run.currentFitness() == 1 && run.totalTicks() == 2, "Pending reward survives pause");
            run.stop(); run.stop();
            check(!world.present && run.totalTicks() == 2, "Stop retains counters and removes idempotently");
        });
        test("reset replays the same seed and clears all statistics", () -> {
            var world = new FakeEnvironment();
            var run = new EvolutionRun(world, config(2, 2, 1));
            run.start(); run.tick();
            AgentInput first = world.input;
            world.physics(); run.tick(); world.physics(); run.tick();
            run.reset(); run.reset();
            check(run.state() == EvolutionRun.State.READY && run.totalTicks() == 0 && run.champion().isEmpty(), "Clean reset");
            run.start(); run.tick();
            check(world.input.equals(first), "Reset must replay the original controller for the same frame");
            run.stop();
        });
        test("unavailable frames abort without breeding partial generations", () -> {
            var world = new FakeEnvironment();
            var run = new EvolutionRun(world, config(2, 2, 2));
            run.start(); world.present = false;
            rejects(IllegalStateException.class, run::tick);
            check(run.state() == EvolutionRun.State.STOPPED && run.totalTicks() == 0, "Abort without fabricated progress");
            check(run.lastStats().isEmpty() && !run.failureReason().isBlank(), "Failure reason and no partial statistics");
            run.reset(); run.start(); run.pause(); world.present = false;
            rejects(IllegalStateException.class, run::tick);
            check(run.state() == EvolutionRun.State.STOPPED, "Missing paused body must also abort");
        });
        test("spawn and actuator failures clean up and do not count unsubmitted ticks", () -> {
            var world = new FakeEnvironment();
            world.failSpawn = true;
            var run = new EvolutionRun(world, config(2, 2, 1));
            rejects(IllegalStateException.class, run::start);
            check(run.state() == EvolutionRun.State.STOPPED && !world.present, "Spawn failure cleanup");
            world.failSpawn = false; run.reset(); run.start(); world.failApply = true;
            rejects(IllegalStateException.class, run::tick);
            check(run.totalTicks() == 0 && !world.present, "Actuator failure cleanup and tick accounting");
        });
        test("configuration bounds reject unsafe or nonfinite values", () -> {
            rejects(IllegalArgumentException.class, () -> config(1, 2, 1));
            rejects(IllegalArgumentException.class, () -> config(2, 0, 1));
            rejects(IllegalArgumentException.class, () -> config(2, 2, 0));
            rejects(IllegalArgumentException.class, () -> new EvolutionConfig(1, 2, 2, 2, Double.NaN, 0.2));
            rejects(IllegalArgumentException.class, () -> new AgentFrame(observation(true), Double.NaN, 0));
        });
        System.out.println("Passed " + passed + " evolution tests");
    }

    private static EvolutionConfig config(int population, int ticks, int generations) {
        return new EvolutionConfig(42, population, ticks, generations, 0.1, 0.2);
    }
    private static AgentObservation observation(boolean grounded) {
        return new AgentObservation(0, 0, 0, 0, 0, grounded, false, "minecraft:air");
    }
    private static AgentFrame frame(double x, double z, boolean grounded) {
        return new AgentFrame(observation(grounded), x, z);
    }
    private static double[] output(NetworkGenome genome) {
        double[] result = new double[4]; genome.evaluate(new double[8], new double[8], result); return result;
    }
    private static double xorFitness(NetworkGenome genome) {
        double loss = 0;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            double[] input = new double[8], result = new double[4]; input[0] = x; input[1] = y;
            genome.evaluate(input, new double[8], result);
            double expected = x == y ? -1 : 1;
            loss += Math.pow(expected - result[0], 2);
        }
        return -loss / 4;
    }
    private static void test(String name, Runnable test) { test.run(); passed++; System.out.println("PASS " + name); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void close(double actual, double expected) { check(Math.abs(actual - expected) < 1e-9, "Unexpected numerical result: " + actual); }
    private static void rejects(Class<? extends Throwable> type, Runnable test) {
        try { test.run(); } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static final class FakeEnvironment implements EvolutionEnvironment {
        boolean present, running, failSpawn, failApply;
        int spawns, removals, applications;
        double x;
        AgentInput input = AgentInput.IDLE;
        public void spawn() {
            if (failSpawn) throw new IllegalStateException("Failed spawn");
            check(!present, "Duplicate body"); present = true; x = 0.5; spawns++;
        }
        public void setRunning(boolean running) { this.running = running; if (!running) input = AgentInput.IDLE; }
        public void apply(AgentInput input) {
            if (failApply) throw new IllegalStateException("Failed actuator");
            check(present && running, "Actuator requires an active body"); this.input = input; applications++;
        }
        // Deliberately advances only when the simulated world ticks, NOT in apply().
        void physics() { if (present && running) x += 1; }
        public Optional<AgentObservation> observe() { return present ? Optional.of(observation(true)) : Optional.empty(); }
        public Optional<AgentFrame> frame() { return present ? Optional.of(EvolutionTests.frame(x, 0.5, true)) : Optional.empty(); }
        public void remove() { if (present) removals++; present = running = false; }
    }
}
