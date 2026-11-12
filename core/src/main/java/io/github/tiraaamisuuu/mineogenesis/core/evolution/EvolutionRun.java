package io.github.tiraaamisuuu.mineogenesis.core.evolution;

import java.util.Objects;
import java.util.Optional;

/** Sequential evaluation; tick() is called BEFORE each world physics step. */
public final class EvolutionRun {
    public enum State { READY, RUNNING, PAUSED, STOPPED, COMPLETED }

    private final EvolutionEnvironment environment;
    private final EvolutionConfig config;
    private final EvolutionObjective objective;
    private EvolutionPopulation population;
    private final double[] scores;
    private NeuralController controller;
    private EpisodeFitness fitness;
    private State state = State.READY;
    private int generation = 1, index, episodeTicks;
    private long totalTicks;
    private GenerationStats lastStats;
    private NetworkGenome champion;
    private double bestFitness;
    private String failureReason = "";
    private int generationSuccesses;
    private long totalSuccesses;

    public EvolutionRun(EvolutionEnvironment environment, EvolutionConfig config) {
        this(environment, config, EvolutionObjective.EXPLORATION);
    }

    public EvolutionRun(EvolutionEnvironment environment, EvolutionConfig config, EvolutionObjective objective) {
        this.environment = Objects.requireNonNull(environment);
        this.config = Objects.requireNonNull(config);
        this.objective = Objects.requireNonNull(objective);
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
        if (state != State.RUNNING && state != State.PAUSED) return;
        try {
            AgentFrame frame = requireFrame();
            if (state == State.PAUSED) return;
            // This frame is the outcome of the LAST submitted action, including the final one.
            if (episodeTicks > 0) fitness.record(frame);
            if (episodeTicks == config.episodeTicks() || (episodeTicks > 0 && fitness.successful())) {
                finishEpisode();
                if (state != State.RUNNING) return;
                frame = requireFrame();
            }
            environment.setRunning(true);
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
        generationSuccesses = 0;
        totalSuccesses = 0;
        failureReason = "";
        state = State.READY;
    }

    private void startEpisode() {
        environment.spawn();
        environment.label("Gen " + generation + " | Agent " + (index + 1) + "/" + config.populationSize());
        // Commands may arrive between world ticks. No idle physics before the first action.
        environment.setRunning(false);
        fitness = new EpisodeFitness(requireFrame(), objective);
        controller = new NeuralController(population.genome(index));
        episodeTicks = 0;
    }

    private void finishEpisode() {
        double score = fitness.score();
        if (fitness.successful()) { generationSuccesses++; totalSuccesses++; }
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
            lastStats = new GenerationStats(generation, max, sum / scores.length, min, generationSuccesses);
            if (generation == config.generations()) {
                state = State.COMPLETED;
                return;
            }
            population.breed(scores);
            generation++;
            generationSuccesses = 0;
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
    public double currentFitness() { return fitness == null ? 0 : fitness.score(); }
    public EvolutionObjective objective() { return objective; }
    public long totalSuccesses() { return totalSuccesses; }
    public double bestFitness() { return bestFitness; }
    public Optional<GenerationStats> lastStats() { return Optional.ofNullable(lastStats); }
    public Optional<NetworkGenome> champion() { return Optional.ofNullable(champion); }
    public String failureReason() { return failureReason; }
}
