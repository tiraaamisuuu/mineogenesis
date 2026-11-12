package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Objects;
import java.util.Optional;

/** Sequential evaluation; tick() is called BEFORE each world physics step. */
public final class EvolutionRun {
    public enum State { READY, RUNNING, PAUSED, STOPPED, COMPLETED }

    private final EvolutionEnvironment environment;
    private final EvolutionConfig config;
    private EvolutionPopulation population;
    private final double[] scores;
    private NeuralController controller;
    private GroundExplorationFitness fitness;
    private State state = State.READY;
    private int generation = 1, index, episodeTicks;
    private long totalTicks;
    private GenerationStats lastStats;
    private NetworkGenome champion;
    private double bestFitness;
    private String failureReason = "";

    public EvolutionRun(EvolutionEnvironment environment, EvolutionConfig config) {
        this.environment = Objects.requireNonNull(environment);
        this.config = Objects.requireNonNull(config);
        population = new EvolutionPopulation(config);
        scores = new double[config.populationSize()];
    }

    public void start() {
        if (state != State.READY) throw new IllegalStateException("Reset before starting a fresh evolution run");
        try {
            startEpisode();
            state = State.RUNNING;
        } catch (RuntimeException failure) { abort(failure); throw failure; }
    }

    public void tick() {
        if (state != State.RUNNING) return;
        try {
            AgentFrame frame = requireFrame();
            // This frame is the outcome of the LAST submitted action, including the final one.
            if (episodeTicks > 0) fitness.record(frame);
            if (episodeTicks == config.episodeTicks()) {
                finishEpisode();
                if (state != State.RUNNING) return;
                frame = requireFrame();
            }
            environment.apply(controller.decide(frame.observation()));
            episodeTicks++;
            totalTicks++;
        } catch (RuntimeException failure) { abort(failure); throw failure; }
    }

    public boolean pause() {
        if (state != State.RUNNING) return false;
        environment.setRunning(false);
        state = State.PAUSED;
        return true;
    }

    public boolean resume() {
        if (state != State.PAUSED) return false;
        try {
            requireFrame();
            controller.resetMemory();
            environment.setRunning(true);
            state = State.RUNNING;
            return true;
        } catch (RuntimeException failure) { abort(failure); throw failure; }
    }

    public void stop() {
        environment.remove();
        state = State.STOPPED;
    }

    public void reset() {
        environment.remove();
        population = new EvolutionPopulation(config);
        java.util.Arrays.fill(scores, 0);
        controller = null;
        fitness = null;
        lastStats = null;
        champion = null;
        bestFitness = 0;
        generation = 1;
        index = episodeTicks = 0;
        totalTicks = 0;
        failureReason = "";
        state = State.READY;
    }

    private void startEpisode() {
        environment.spawn();
        environment.setRunning(true);
        fitness = new GroundExplorationFitness(requireFrame());
        controller = new NeuralController(population.genome(index));
        episodeTicks = 0;
    }

    private void finishEpisode() {
        double score = fitness.score();
        scores[index] = score;
        if (champion == null || score > bestFitness) {
            champion = population.genome(index);
            bestFitness = score;
        }
        environment.remove();
        index++;
        if (index == population.size()) {
            double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (double value : scores) { sum += value; min = Math.min(min, value); max = Math.max(max, value); }
            lastStats = new GenerationStats(generation, max, sum / scores.length, min);
            if (generation == config.generations()) {
                state = State.COMPLETED;
                return;
            }
            population.breed(scores);
            generation++;
            index = 0;
        }
        startEpisode();
    }

    private AgentFrame requireFrame() {
        return environment.frame().orElseThrow(() -> new IllegalStateException(
                "Agent unavailable: removed, dead or outside entity-ticking chunks"));
    }

    private void abort(RuntimeException failure) {
        state = State.STOPPED;
        failureReason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        try { environment.remove(); }
        catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
    }

    public State state() { return state; }
    public EvolutionConfig config() { return config; }
    public int generation() { return generation; }
    public int individual() { return Math.min(index + 1, config.populationSize()); }
    public int episodeTicks() { return episodeTicks; }
    public long totalTicks() { return totalTicks; }
    public int currentFitness() { return fitness == null ? 0 : fitness.score(); }
    public double bestFitness() { return bestFitness; }
    public Optional<GenerationStats> lastStats() { return Optional.ofNullable(lastStats); }
    public Optional<NetworkGenome> champion() { return Optional.ofNullable(champion); }
    public String failureReason() { return failureReason; }
}
