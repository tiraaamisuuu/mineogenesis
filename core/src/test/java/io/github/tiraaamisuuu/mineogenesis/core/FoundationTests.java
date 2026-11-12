package io.github.tiraaamisuuu.mineogenesis.core;

import java.util.Optional;

/** Dependency-free contract tests. A failed assertion exits with a nonzero status. */
public final class FoundationTests {
    private static int passed;

    public static void main(String[] args) {
        test("initial state and inactive ticks", () -> {
            var world = new FakeEnvironment();
            var manager = new ExperimentManager(world);
            equal(manager.state(), ExperimentState.READY);
            manager.tick();
            equal(manager.elapsedTicks(), 0L);
            equal(world.steps, 0);
            check(!manager.pause(), "pause before start must be a no-op");
            rejects(IllegalStateException.class, () -> manager.setInput(AgentInput.IDLE));
        });
        test("start is idempotent and forwards explicit inputs", () -> {
            var world = new FakeEnvironment();
            var manager = new ExperimentManager(world);
            check(manager.start(), "first start");
            check(!manager.start(), "duplicate start");
            var input = new AgentInput(1, 0, 2, true);
            manager.setInput(input);
            manager.tick();
            equal(world.lastInput, input);
            equal(world.spawns, 1);
            equal(manager.elapsedTicks(), 1L);
        });
        test("pause freezes and resume clears stale controls without spawning", () -> {
            var world = new FakeEnvironment();
            var manager = new ExperimentManager(world);
            manager.start();
            manager.setInput(new AgentInput(1, 0, 0, false));
            manager.tick();
            check(manager.pause(), "pause running experiment");
            check(!world.running, "physics must freeze");
            manager.tick();
            equal(manager.elapsedTicks(), 1L);
            equal(world.steps, 1);
            check(!manager.pause(), "repeat pause");
            rejects(IllegalStateException.class, () -> manager.setInput(AgentInput.IDLE));
            manager.start();
            manager.tick();
            check(world.running, "resume physics");
            equal(world.spawns, 1);
            equal(world.lastInput, AgentInput.IDLE);
            equal(manager.elapsedTicks(), 2L);
        });
        test("stop removes and a fresh start resets ticks", () -> {
            var world = new FakeEnvironment();
            var manager = new ExperimentManager(world);
            manager.start();
            manager.tick();
            manager.pause();
            check(manager.stop(), "stop paused experiment");
            check(!world.present, "stop must remove the body");
            check(!manager.stop(), "repeat stop");
            manager.tick();
            equal(manager.elapsedTicks(), 1L);
            equal(manager.state(), ExperimentState.STOPPED);
            manager.start();
            equal(world.spawns, 2);
            equal(manager.elapsedTicks(), 0L);
        });
        test("reset from every state is safe and clears the run", () -> {
            for (var state : ExperimentState.values()) {
                var world = new FakeEnvironment();
                var manager = new ExperimentManager(world);
                if (state != ExperimentState.READY) { manager.start(); manager.tick(); }
                if (state == ExperimentState.PAUSED) manager.pause();
                if (state == ExperimentState.STOPPED) manager.stop();
                manager.reset();
                manager.reset();
                equal(manager.state(), ExperimentState.READY);
                equal(manager.elapsedTicks(), 0L);
                check(!world.present, "reset must remove the body");
                manager.start();
                manager.tick();
                equal(world.lastInput, AgentInput.IDLE);
            }
        });
        test("failed spawn does not begin a run", () -> {
            var world = new FakeEnvironment();
            world.failSpawn = true;
            var manager = new ExperimentManager(world);
            rejects(IllegalStateException.class, manager::start);
            equal(manager.state(), ExperimentState.READY);
            equal(manager.elapsedTicks(), 0L);
            check(!world.present, "failed spawn must not leave a body");
        });
        test("failed tick is not counted", () -> {
            var world = new FakeEnvironment();
            var manager = new ExperimentManager(world);
            manager.start();
            world.failStep = true;
            rejects(IllegalStateException.class, manager::tick);
            equal(manager.elapsedTicks(), 0L);
            manager.stop();
            check(!world.present, "cleanup after failure");
        });
        test("independent experiments do not share state", () -> {
            var first = new ExperimentManager(new FakeEnvironment());
            var second = new ExperimentManager(new FakeEnvironment());
            first.start();
            first.tick();
            equal(second.state(), ExperimentState.READY);
            equal(second.elapsedTicks(), 0L);
        });
        test("diagonal input normalization preserves direction", () -> {
            var diagonal = new AgentInput(1, -1, 0, false);
            check(Math.abs(Math.hypot(diagonal.forward(), diagonal.strafe()) - 1) < 0.000001,
                    "diagonal speed must equal cardinal speed");
            check(diagonal.forward() > 0 && diagonal.strafe() < 0, "direction preserved");
            equal(new AgentInput(0.5f, 0, 0, false).forward(), 0.5f);
        });
        test("invalid and nonfinite actions are rejected", () -> {
            for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -2, 2}) {
                rejects(IllegalArgumentException.class, () -> new AgentInput(value, 0, 0, false));
                rejects(IllegalArgumentException.class, () -> new AgentInput(0, value, 0, false));
            }
            for (float value : new float[] {Float.NaN, Float.NEGATIVE_INFINITY, -181, 181}) {
                rejects(IllegalArgumentException.class, () -> new AgentInput(0, 0, value, false));
            }
            rejects(NullPointerException.class, () -> new ExperimentManager(null));
        });
        System.out.println("Passed " + passed + " foundation tests");
    }

    private static void test(String name, Runnable test) {
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object actual, Object expected) {
        check(actual.equals(expected), "Expected " + expected + ", got " + actual);
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static final class FakeEnvironment implements AgentEnvironment {
        int spawns, steps;
        boolean present, running, failSpawn, failStep;
        AgentInput lastInput = AgentInput.IDLE;

        public void spawn() {
            if (failSpawn) throw new IllegalStateException("spawn failed");
            check(!present, "duplicate physical body");
            present = true;
            spawns++;
        }
        public void setRunning(boolean value) { running = value; }
        public void apply(AgentInput input) {
            check(present && running, "cannot step an inactive body");
            if (failStep) throw new IllegalStateException("step failed");
            lastInput = input;
            steps++;
        }
        public Optional<AgentObservation> observe() { return Optional.empty(); }
        public void remove() { present = false; running = false; }
    }
}
