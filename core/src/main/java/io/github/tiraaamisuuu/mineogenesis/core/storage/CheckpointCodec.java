package io.github.tiraaamisuuu.mineogenesis.core.storage;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded, versioned data format. No object serialization or reflective construction. */
public final class CheckpointCodec {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x4d4e4743, VERSION = 1, RNG_VERSION = 1;
    private CheckpointCodec() { }

    public static byte[] encode(RunArchive archive) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            var c = archive.checkpoint(); var config = c.config();
            out.writeInt(MAGIC); out.writeInt(VERSION);
            out.writeInt(NetworkGenome.INPUTS); out.writeInt(NetworkGenome.HIDDEN); out.writeInt(NetworkGenome.OUTPUTS);
            out.writeInt(RNG_VERSION);
            out.writeLong(config.seed()); out.writeInt(config.populationSize()); out.writeInt(config.episodeTicks()); out.writeInt(config.generations());
            out.writeDouble(config.mutationProbability()); out.writeDouble(config.mutationSigma());
            out.writeUTF(c.objective().name()); out.writeInt(c.completedGeneration());
            out.writeDouble(c.bestFitness()); out.writeLong(c.totalTicks()); out.writeLong(c.totalSuccesses());
            var rng = c.population().random(); out.writeLong(rng.bits()); out.writeBoolean(rng.hasGaussian()); out.writeDouble(rng.gaussian());
            for (var genome : c.population().genomes()) writeGenome(out, genome);
            writeGenome(out, c.champion());
            out.writeInt(c.history().size());
            for (var row : c.history()) {
                var s = row.statistics(); out.writeInt(s.generation());
                out.writeDouble(s.bestFitness()); out.writeDouble(s.meanFitness()); out.writeDouble(s.worstFitness());
                out.writeInt(s.successfulCandidates()); out.writeLong(row.cumulativeTicks()); out.writeLong(row.cumulativeSuccesses());
            }
            out.writeInt(archive.metadata().size());
            for (var entry : new TreeMap<>(archive.metadata()).entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); }
        }
        byte[] body = bytes.toByteArray();
        if (body.length + 32 > MAX_BYTES) throw new IOException("Checkpoint exceeds size limit");
        bytes.write(digest(body));
        return bytes.toByteArray();
    }

    public static RunArchive decode(byte[] bytes) throws IOException {
        if (bytes.length < 64 || bytes.length > MAX_BYTES) throw new IOException("Invalid checkpoint size");
        byte[] body = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(bytes, body.length, bytes.length))) {
            throw new IOException("Checkpoint checksum mismatch");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported checkpoint format/version");
            if (in.readInt() != NetworkGenome.INPUTS || in.readInt() != NetworkGenome.HIDDEN || in.readInt() != NetworkGenome.OUTPUTS
                    || in.readInt() != RNG_VERSION) throw new IOException("Incompatible network topology or genetic RNG");
            var config = new EvolutionConfig(in.readLong(), in.readInt(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble());
            var objective = EvolutionObjective.valueOf(in.readUTF()); int completed = in.readInt();
            double best = in.readDouble(); long ticks = in.readLong(), successes = in.readLong();
            var rng = new CheckpointRandom.State(in.readLong(), in.readBoolean(), in.readDouble());
            var genomes = new ArrayList<NetworkGenome>(config.populationSize());
            for (int i = 0; i < config.populationSize(); i++) genomes.add(readGenome(in));
            var champion = readGenome(in);
            int count = in.readInt();
            if (count < 1 || count > config.generations() || count != completed) throw new IOException("Invalid generation history size");
            var history = new ArrayList<GenerationRecord>(count);
            for (int i = 0; i < count; i++) {
                var s = new GenerationStats(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readInt());
                history.add(new GenerationRecord(s, in.readLong(), in.readLong()));
            }
            int fields = in.readInt();
            if (fields < 0 || fields > 32) throw new IOException("Invalid metadata field count");
            var metadata = new HashMap<String, String>();
            for (int i = 0; i < fields; i++) {
                if (metadata.put(in.readUTF(), in.readUTF()) != null) throw new IOException("Duplicate metadata field");
            }
            if (in.available() != 0) throw new IOException("Unexpected trailing checkpoint data");
            return new RunArchive(new TrainingCheckpoint(config, objective, completed,
                    new EvolutionPopulation.Snapshot(genomes, rng), champion, best, ticks, successes, history), metadata);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid checkpoint data: " + invalid.getMessage(), invalid);
        }
    }
    private static void writeGenome(DataOutputStream out, NetworkGenome genome) throws IOException {
        for (double gene : genome.genes()) out.writeDouble(gene);
    }
    private static NetworkGenome readGenome(DataInputStream in) throws IOException {
        double[] genes = new double[NetworkGenome.GENE_COUNT];
        for (int i = 0; i < genes.length; i++) genes[i] = in.readDouble();
        return new NetworkGenome(genes);
    }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
