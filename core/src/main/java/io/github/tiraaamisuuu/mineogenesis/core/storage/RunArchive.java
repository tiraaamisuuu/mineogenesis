package io.github.tiraaamisuuu.mineogenesis.core.storage;

import io.github.tiraaamisuuu.mineogenesis.core.evolution.TrainingCheckpoint;
import java.util.Map;
import java.util.Objects;

/** Immutable adapter metadata; the engine never interprets world or fixture coordinates. */
public record RunArchive(TrainingCheckpoint checkpoint, Map<String, String> metadata) {
    public RunArchive {
        Objects.requireNonNull(checkpoint);
        metadata = Map.copyOf(metadata);
        if (metadata.size() > 32) throw new IllegalArgumentException("Too many archive metadata fields");
        metadata.forEach((key, value) -> {
            if (!key.matches("[a-z][a-z0-9_]{0,63}") || value.length() > 512 || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid archive metadata");
            }
        });
    }
}
