package com.scrambler.classify;

import com.scrambler.inventory.FileInfo;

import java.util.Objects;

/**
 * Immutable classification outcome for a single inventoried file.
 */
public final class ClassificationResult {

    private final FileInfo fileInfo;
    private final FileCategory category;

    /**
     * Creates a classification result for an inventoried file.
     *
     * @param fileInfo inventoried file metadata
     * @param category assigned file category
     */
    public ClassificationResult(FileInfo fileInfo, FileCategory category) {
        this.fileInfo = Objects.requireNonNull(fileInfo, "fileInfo must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    /**
     * Returns the inventoried file metadata.
     *
     * @return file metadata
     */
    public FileInfo getFileInfo() {
        return fileInfo;
    }

    /**
     * Returns the assigned file category.
     *
     * @return file category
     */
    public FileCategory getCategory() {
        return category;
    }
}
