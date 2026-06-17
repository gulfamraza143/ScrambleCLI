package com.scrambler.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Immutable configuration snapshot for a SCRAMBLECLI run.
 * Milestone 1 exposes workspace and archive safety limits required by extraction.
 */
public final class ScramblerConfig {

    private static final long DEFAULT_MAX_ZIP_ENTRIES = 50_000L;
    private static final long DEFAULT_MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;

    private final Path workspaceBasePath;
    private final long maxZipEntries;
    private final long maxUncompressedBytes;

    private ScramblerConfig(Path workspaceBasePath, long maxZipEntries, long maxUncompressedBytes) {
        this.workspaceBasePath = workspaceBasePath;
        this.maxZipEntries = maxZipEntries;
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    /**
     * Returns a default configuration using the system temporary directory.
     *
     * @return default configuration
     */
    public static ScramblerConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a new configuration builder.
     *
     * @return configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Base directory under which per-run workspace directories are created.
     *
     * @return workspace base path
     */
    public Path getWorkspaceBasePath() {
        return workspaceBasePath;
    }

    /**
     * Maximum number of ZIP entries permitted during extraction.
     *
     * @return maximum entry count
     */
    public long getMaxZipEntries() {
        return maxZipEntries;
    }

    /**
     * Maximum cumulative uncompressed bytes permitted during extraction.
     *
     * @return maximum uncompressed bytes
     */
    public long getMaxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    /**
     * Builder for {@link ScramblerConfig}.
     */
    public static final class Builder {

        private Path workspaceBasePath = Paths.get(System.getProperty("java.io.tmpdir"));
        private long maxZipEntries = DEFAULT_MAX_ZIP_ENTRIES;
        private long maxUncompressedBytes = DEFAULT_MAX_UNCOMPRESSED_BYTES;

        private Builder() {
        }

        /**
         * Sets the base path for temporary workspaces.
         *
         * @param workspaceBasePath base directory for workspace creation
         * @return this builder
         */
        public Builder workspaceBasePath(Path workspaceBasePath) {
            if (workspaceBasePath == null) {
                throw new IllegalArgumentException("workspaceBasePath must not be null");
            }
            this.workspaceBasePath = workspaceBasePath;
            return this;
        }

        /**
         * Sets the maximum ZIP entry count allowed during extraction.
         *
         * @param maxZipEntries maximum entry count
         * @return this builder
         */
        public Builder maxZipEntries(long maxZipEntries) {
            if (maxZipEntries <= 0) {
                throw new IllegalArgumentException("maxZipEntries must be positive");
            }
            this.maxZipEntries = maxZipEntries;
            return this;
        }

        /**
         * Sets the maximum cumulative uncompressed bytes allowed during extraction.
         *
         * @param maxUncompressedBytes maximum uncompressed bytes
         * @return this builder
         */
        public Builder maxUncompressedBytes(long maxUncompressedBytes) {
            if (maxUncompressedBytes <= 0) {
                throw new IllegalArgumentException("maxUncompressedBytes must be positive");
            }
            this.maxUncompressedBytes = maxUncompressedBytes;
            return this;
        }

        /**
         * Builds an immutable configuration instance.
         *
         * @return configuration snapshot
         */
        public ScramblerConfig build() {
            return new ScramblerConfig(workspaceBasePath, maxZipEntries, maxUncompressedBytes);
        }
    }
}
