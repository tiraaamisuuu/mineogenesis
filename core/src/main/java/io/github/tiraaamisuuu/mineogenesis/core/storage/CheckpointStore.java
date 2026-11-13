package io.github.tiraaamisuuu.mineogenesis.core.storage;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.TrainingCheckpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;

/** Atomic files scoped to one world directory. Caller supplies immutable archives. */
public final class CheckpointStore {
    private final Path directory;
    public CheckpointStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Path directory() { return directory; }
    public static void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,47}")) {
            throw new IllegalArgumentException("Use 1-48 letters, digits, underscores or hyphens; start with a letter or digit");
        }
    }
    private Path file(String name, String extension) throws IOException {
        validateName(name);
        Path file = directory.resolve(name + extension);
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(file)) throw new IOException("Symbolic links are not supported for training files");
        return file;
    }
    public RunArchive load(String name) throws IOException {
        Path file = file(name, ".mng");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Checkpoint does not exist: " + name);
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return CheckpointCodec.decode(stream.readNBytes(CheckpointCodec.MAX_BYTES + 1));
        }
    }
    public void save(String name, RunArchive archive) throws IOException { replace(file(name, ".mng"), CheckpointCodec.encode(archive)); }
    public void export(String name, TrainingCheckpoint checkpoint) throws IOException {
        replace(file(name, ".csv"), csv(checkpoint).getBytes(StandardCharsets.UTF_8));
    }
    public List<String> names() throws IOException {
        if (!Files.exists(directory)) return List.of();
        if (Files.isSymbolicLink(directory)) throw new IOException("Symbolic links are not supported for training files");
        try (var files = Files.list(directory)) {
            return files.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                    .map(f -> f.getFileName().toString()).filter(n -> n.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,47}\\.mng"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        }
    }
    private void replace(Path destination, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".training-", ".tmp");
        try {
            Files.write(temporary, bytes);
            // Reject a symlink introduced since validation as well.
            if (Files.isSymbolicLink(destination)) throw new IOException("Checkpoint destination is a symbolic link");
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String csv(TrainingCheckpoint checkpoint) {
        var c = checkpoint.config();
        var text = new StringBuilder("generation,best_fitness,mean_fitness,worst_fitness,successful_candidates,population,success_rate,episode_tick_budget,generation_ticks,total_ticks,total_successes,seed,objective\n");
        long previous = 0;
        for (var row : checkpoint.history()) {
            var s = row.statistics();
            text.append(String.format(Locale.ROOT, "%d,%.17g,%.17g,%.17g,%d,%d,%.17g,%d,%d,%d,%d,%d,%s%n",
                    s.generation(), s.bestFitness(), s.meanFitness(), s.worstFitness(), s.successfulCandidates(), c.populationSize(),
                    (double) s.successfulCandidates() / c.populationSize(), c.episodeTicks(), row.cumulativeTicks() - previous,
                    row.cumulativeTicks(), row.cumulativeSuccesses(), c.seed(), checkpoint.objective()));
            previous = row.cumulativeTicks();
        }
        return text.toString();
    }
}
