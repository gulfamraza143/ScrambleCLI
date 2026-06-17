package com.scrambler.inventory;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable descriptor for a file discovered in a repository inventory.
 */
public final class FileInfo {

    private final Path absolutePath;
    private final String repoRelativePath;
    private final long sizeBytes;

    /**
     * Creates a file descriptor for an inventoried repository file.
     *
     * @param absolutePath      absolute filesystem path to the file
     * @param repoRelativePath  repository-relative path using forward slashes
     * @param sizeBytes         file size in bytes
     */
    public FileInfo(Path absolutePath, String repoRelativePath, long sizeBytes) {
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        this.repoRelativePath = Objects.requireNonNull(repoRelativePath, "repoRelativePath must not be null");
        if (repoRelativePath.isBlank()) {
            throw new IllegalArgumentException("repoRelativePath must not be blank");
        }
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        this.sizeBytes = sizeBytes;
    }

    /**
     * Returns the absolute filesystem path.
     *
     * @return absolute path
     */
    public Path getAbsolutePath() {
        return absolutePath;
    }

    /**
     * Returns the normalized repository-relative path.
     *
     * @return repository-relative path
     */
    public String getRepoRelativePath() {
        return repoRelativePath;
    }

    /**
     * Returns the file size in bytes.
     *
     * @return size in bytes
     */
    public long getSizeBytes() {
        return sizeBytes;
    }
}
