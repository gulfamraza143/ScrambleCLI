package com.scrambler.detection;

import com.scrambler.inventory.FileInfo;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of scanning one text file for sensitive entities.
 */
public final class DetectionResult {

    private final FileInfo fileInfo;
    private final List<Entity> entities;

    /**
     * Creates a detection result.
     *
     * @param fileInfo scanned file metadata
     * @param entities detected entities after overlap resolution
     */
    public DetectionResult(FileInfo fileInfo, List<Entity> entities) {
        this.fileInfo = Objects.requireNonNull(fileInfo, "fileInfo must not be null");
        this.entities = List.copyOf(Objects.requireNonNull(entities, "entities must not be null"));
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    public boolean hasEntities() {
        return !entities.isEmpty();
    }
}
