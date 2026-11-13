package io.github.tiraaamisuuu.mineogenesis.core.storage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;

/** One writer, at most four explicit requests and one coalesced automatic snapshot. */
public final class TrainingWriter implements AutoCloseable {
    private record Request(String name, RunArchive archive, boolean csv) { }
    private final CheckpointStore store;
    private final ArrayDeque<Request> explicit = new ArrayDeque<>();
    private Request automatic;
    private boolean closing, writing;
    private String lastResult = "No writes yet", lastError = "";
    private final Thread worker;

    public TrainingWriter(CheckpointStore store) {
        this.store = Objects.requireNonNull(store);
        worker = new Thread(this::work, "mineogenesis-training-writer");
        worker.setDaemon(true); worker.start();
    }
    public synchronized void autosave(RunArchive archive) {
        if (closing) throw new IllegalStateException("Training writer is closed");
        automatic = new Request("latest", Objects.requireNonNull(archive), false);
        notifyAll();
    }
    public synchronized void submit(String name, RunArchive archive, boolean csv) {
        CheckpointStore.validateName(name); Objects.requireNonNull(archive);
        if (name.equalsIgnoreCase("latest")) throw new IllegalArgumentException("The name latest is reserved for autosaves");
        if (closing) throw new IllegalStateException("Training writer is closed");
        if (explicit.size() >= 4) throw new IllegalStateException("Training writer is busy; try again after pending writes finish");
        explicit.addLast(new Request(name, archive, csv)); notifyAll();
    }
    private void work() {
        while (true) {
            Request request;
            synchronized (this) {
                while (explicit.isEmpty() && automatic == null && !closing) {
                    try { wait(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                if (explicit.isEmpty() && automatic == null && closing) return;
                request = explicit.pollFirst();
                if (request == null) { request = automatic; automatic = null; }
                writing = true;
            }
            String result = "", error = "";
            try {
                if (request.csv()) store.export(request.name(), request.archive());
                else store.save(request.name(), request.archive());
                result = "Saved " + request.name() + (request.csv() ? ".csv" : ".mng") + " at generation " + request.archive().checkpoint().completedGeneration();
            } catch (IOException | RuntimeException failure) { error = "Write failed for " + request.name() + ": " + failure.getMessage(); }
            synchronized (this) {
                if (!result.isEmpty()) lastResult = result;
                if (!error.isEmpty()) lastError = error;
                writing = false; notifyAll();
            }
        }
    }
    public synchronized boolean idle() { return !writing && explicit.isEmpty() && automatic == null; }
    public synchronized String status() {
        return "pending=" + (explicit.size() + (automatic == null ? 0 : 1) + (writing ? 1 : 0))
                + "; " + lastResult + (lastError.isEmpty() ? "" : "; lastError=" + lastError);
    }
    @Override public void close() throws IOException {
        synchronized (this) { closing = true; notifyAll(); }
        try { worker.join(5000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted while flushing training files", interrupted); }
        synchronized (this) {
            if (worker.isAlive()) throw new IOException("Training file flush timed out; " + status());
            if (!lastError.isEmpty()) throw new IOException(lastError);
        }
    }
}
