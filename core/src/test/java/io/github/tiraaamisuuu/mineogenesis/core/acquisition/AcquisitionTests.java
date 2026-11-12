package io.github.tiraaamisuuu.mineogenesis.core.acquisition;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import io.github.tiraaamisuuu.mineogenesis.core.AgentObservation;
import io.github.tiraaamisuuu.mineogenesis.core.evolution.*;
import java.util.Optional;

public final class AcquisitionTests {
    private static int passed;

    public static void main(String[] args) {
        test("legacy contracts default to neutral pitch/mining/target state", () -> {
            var input = new AgentInput(1, 0, 0, false);
            check(input.pitchDelta() == 0 && !input.mine(), "Legacy controls remain neutral");
            var observation = new AgentObservation(0, 0, 0, 0, 0, true, false, "minecraft:air");
            check(!observation.targetAhead() && observation.miningProgress() == 0 && observation.collectedTarget() == 0,
                    "Legacy observations remain neutral");
            rejects(() -> new AgentInput(0, 0, 0, Float.NaN, false, false));
            rejects(() -> new AgentInput(0, 0, 0, 91, false, false));
            rejects(() -> observation(true, 2, 0, 0));
            rejects(() -> observation(true, 0, -1, 0));
        });
        test("pitch jump and mine outputs map to separate actuators", () -> {
            double[] genes = new double[NetworkGenome.GENE_COUNT];
            int outputs = (NetworkGenome.INPUTS + 1) * NetworkGenome.HIDDEN;
            for (int row = 3; row <= 5; row++) genes[outputs + row * (NetworkGenome.HIDDEN + 1) + NetworkGenome.HIDDEN] = 1;
            AgentInput input = new NeuralController(new NetworkGenome(genes)).decide(observation(false, 0, 0, 0));
            check(input.pitchDelta() > 0 && input.jump() && input.mine(), "New actuator mapping");
            check(input.forward() == 0 && input.strafe() == 0 && input.yawDelta() == 0, "No implicit navigation");
        });
        test("target-in-view and own pitch are available as local feedback", () -> {
            double[] genes = new double[NetworkGenome.GENE_COUNT];
            int outputStart = (NetworkGenome.INPUTS + 1) * NetworkGenome.HIDDEN;
            genes[8] = 1; // Hidden neuron 0 sees target-in-view, not coordinates.
            genes[outputStart + 5 * (NetworkGenome.HIDDEN + 1)] = 1;
            var controller = new NeuralController(new NetworkGenome(genes));
            check(!controller.decide(observation(false, 0, 0, 0)).mine(), "No target signal");
            check(controller.decide(observation(true, 0, 0, 0)).mine(), "Target signal reaches mining output");
            genes = new double[NetworkGenome.GENE_COUNT];
            genes[11] = 1; genes[outputStart + 3 * (NetworkGenome.HIDDEN + 1)] = 1;
            controller = new NeuralController(new NetworkGenome(genes));
            check(controller.decide(observation(false, 0, 0, 45)).pitchDelta() > 0, "Own pitch input");
        });
        test("timed mining cannot finish early and invalid aim resets progress", () -> {
            var mining = new MiningProgress();
            for (int i = 0; i < 59; i++) check(!mining.advance(true, 1.0 / 60), "Oak-hand timing must not finish early");
            check(mining.advance(true, 1.0 / 60) && mining.value() == 1, "Sixtieth valid tick finishes");
            check(!mining.advance(false, 0) && mining.value() == 0, "Release or invalid aim resets");
            mining.advance(true, 0.25);
            double paused = mining.value();
            check(mining.value() == paused, "No call while paused means no progress");
            mining.reset(); check(mining.value() == 0, "Explicit reset");
            rejects(() -> mining.advance(true, Double.NaN));
            rejects(() -> mining.advance(true, -1));
        });
        test("collection dominates bounded shaping and breaking alone is not success", () -> {
            var initial = frame(false, 0, 0, 0.5);
            var fitness = new EpisodeFitness(initial, EvolutionObjective.OAK_LOG_ACQUISITION);
            for (int i = 1; i < 100; i++) fitness.record(frame(true, 1, 0, i + 0.5));
            check(!fitness.successful() && fitness.score() <= 11.5, "Breaking and exploration are bounded shaping only");
            fitness.record(frame(false, 1, 1, 100.5));
            check(fitness.successful() && fitness.score() >= 100, "Inventory pickup establishes success");
            var exploration = new EpisodeFitness(initial, EvolutionObjective.EXPLORATION);
            exploration.record(frame(true, 1, 1, 1.5));
            check(!exploration.successful() && exploration.score() == 1, "Exploration objective remains independent");
        });
        test("pickup outcomes end episodes early and populate generation success counters", () -> {
            var world = new PickupEnvironment(true);
            var run = new EvolutionRun(world, new EvolutionConfig(42, 2, 100, 1, 0.1, 0.2), EvolutionObjective.OAK_LOG_ACQUISITION);
            run.start(); run.tick();
            check(run.totalSuccesses() == 0, "An action submission is not a collection");
            world.physics(); run.tick(); world.physics(); run.tick();
            check(run.state() == EvolutionRun.State.COMPLETED && run.totalTicks() == 2, "Observed pickups end episodes early");
            check(run.totalSuccesses() == 2 && run.lastStats().orElseThrow().successfulCandidates() == 2, "Success counters");
            run.reset(); check(run.totalSuccesses() == 0 && run.lastStats().isEmpty(), "Reset clears collection statistics");
        });
        test("break-only outcomes consume the budget and never count as collected", () -> {
            var world = new PickupEnvironment(false);
            var run = new EvolutionRun(world, new EvolutionConfig(42, 2, 2, 1, 0.1, 0.2), EvolutionObjective.OAK_LOG_ACQUISITION);
            run.start();
            for (int i = 0; i < 4; i++) { run.tick(); world.physics(); }
            run.tick();
            check(run.totalTicks() == 4 && run.totalSuccesses() == 0 && run.state() == EvolutionRun.State.COMPLETED,
                    "No false success from a broken target");
        });
        System.out.println("Passed " + passed + " acquisition core tests");
    }

    private static AgentObservation observation(boolean target, double progress, int collected, float pitch) {
        return new AgentObservation(0, 0, 0, 0, pitch, true, false, "minecraft:air", target, progress, collected);
    }
    private static AgentFrame frame(boolean target, double progress, int collected, double x) {
        return new AgentFrame(observation(target, progress, collected, 0), x, 0.5);
    }
    private static void test(String name, Runnable test) { test.run(); passed++; System.out.println("PASS " + name); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid input rejection");
    }
    private static final class PickupEnvironment implements EvolutionEnvironment {
        private final boolean pickup;
        private boolean present, collected, broken;
        PickupEnvironment(boolean pickup) { this.pickup = pickup; }
        public void spawn() { present = true; collected = broken = false; }
        public void setRunning(boolean running) { }
        public void apply(AgentInput input) { }
        void physics() { broken = true; collected = pickup; }
        public Optional<AgentObservation> observe() { return frame().map(AgentFrame::observation); }
        public Optional<AgentFrame> frame() {
            return present ? Optional.of(AcquisitionTests.frame(!broken, broken ? 1 : 0, collected ? 1 : 0, 0.5)) : Optional.empty();
        }
        public void remove() { present = false; }
    }
}
