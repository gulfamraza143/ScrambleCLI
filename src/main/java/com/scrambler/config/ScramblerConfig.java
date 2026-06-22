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
    private static final int DEFAULT_MAX_NESTED_ARCHIVE_DEPTH = 5;
    private static final long DEFAULT_MAX_EXTRACTED_BYTES = 5L * 1024 * 1024 * 1024;
    private static final long DEFAULT_MAX_EXTRACTED_FILES = 100_000L;
    private static final long DEFAULT_MAX_SINGLE_FILE_SIZE = 500L * 1024 * 1024;
    private static final int DEFAULT_MAX_COMPRESSION_RATIO = 100;

    private final Path workspaceBasePath;
    private final long maxZipEntries;
    private final long maxUncompressedBytes;
    private final int maxNestedArchiveDepth;
    private final long maxExtractedBytes;
    private final long maxExtractedFiles;
    private final long maxSingleFileSize;
    private final int maxCompressionRatio;

    private ScramblerConfig(
            Path workspaceBasePath,
            long maxZipEntries,
            long maxUncompressedBytes,
            int maxNestedArchiveDepth,
            long maxExtractedBytes,
            long maxExtractedFiles,
            long maxSingleFileSize,
            int maxCompressionRatio) {
        this.workspaceBasePath = workspaceBasePath;
        this.maxZipEntries = maxZipEntries;
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxNestedArchiveDepth = maxNestedArchiveDepth;
        this.maxExtractedBytes = maxExtractedBytes;
        this.maxExtractedFiles = maxExtractedFiles;
        this.maxSingleFileSize = maxSingleFileSize;
        this.maxCompressionRatio = maxCompressionRatio;
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
     * Maximum number of ZIP entries permitted during a single archive read.
     *
     * @return maximum entry count
     */
    public long getMaxZipEntries() {
        return maxZipEntries;
    }

    /**
     * Maximum cumulative uncompressed bytes permitted during a single archive read.
     *
     * @return maximum uncompressed bytes
     */
    public long getMaxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    /**
     * Maximum nested archive expansion depth permitted for a repository.
     *
     * @return maximum nested archive depth
     */
    public int getMaxNestedArchiveDepth() {
        return maxNestedArchiveDepth;
    }

    /**
     * Maximum cumulative extracted bytes permitted across the entire repository.
     *
     * @return maximum extracted bytes
     */
    public long getMaxExtractedBytes() {
        return maxExtractedBytes;
    }

    /**
     * Maximum number of files permitted across the entire extracted repository.
     *
     * @return maximum extracted file count
     */
    public long getMaxExtractedFiles() {
        return maxExtractedFiles;
    }

    /**
     * Maximum size permitted for a single extracted file.
     *
     * @return maximum single file size in bytes
     */
    public long getMaxSingleFileSize() {
        return maxSingleFileSize;
    }

    /**
     * Maximum allowed uncompressed-to-compressed ratio for an archive or entry.
     *
     * @return maximum compression ratio
     */
    public int getMaxCompressionRatio() {
        return maxCompressionRatio;
    }

    /**
     * Builder for {@link ScramblerConfig}.
     */
    public static final class Builder {

        private Path workspaceBasePath = Paths.get(System.getProperty("java.io.tmpdir"));
        private long maxZipEntries = DEFAULT_MAX_ZIP_ENTRIES;
        private long maxUncompressedBytes = DEFAULT_MAX_UNCOMPRESSED_BYTES;
        private int maxNestedArchiveDepth = DEFAULT_MAX_NESTED_ARCHIVE_DEPTH;
        private long maxExtractedBytes = DEFAULT_MAX_EXTRACTED_BYTES;
        private long maxExtractedFiles = DEFAULT_MAX_EXTRACTED_FILES;
        private long maxSingleFileSize = DEFAULT_MAX_SINGLE_FILE_SIZE;
        private int maxCompressionRatio = DEFAULT_MAX_COMPRESSION_RATIO;

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
         * Sets the maximum ZIP entry count allowed during a single archive read.
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
         * Sets the maximum cumulative uncompressed bytes allowed during a single archive read.
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
         * Sets the maximum nested archive depth allowed for a repository.
         *
         * @param maxNestedArchiveDepth maximum nested archive depth
         * @return this builder
         */
        public Builder maxNestedArchiveDepth(int maxNestedArchiveDepth) {
            if (maxNestedArchiveDepth <= 0) {
                throw new IllegalArgumentException("maxNestedArchiveDepth must be positive");
            }
            this.maxNestedArchiveDepth = maxNestedArchiveDepth;
            return this;
        }

        /**
         * Sets the maximum cumulative extracted bytes allowed across the repository.
         *
         * @param maxExtractedBytes maximum extracted bytes
         * @return this builder
         */
        public Builder maxExtractedBytes(long maxExtractedBytes) {
            if (maxExtractedBytes <= 0) {
                throw new IllegalArgumentException("maxExtractedBytes must be positive");
            }
            this.maxExtractedBytes = maxExtractedBytes;
            return this;
        }

        /**
         * Sets the maximum number of files allowed across the extracted repository.
         *
         * @param maxExtractedFiles maximum extracted file count
         * @return this builder
         */
        public Builder maxExtractedFiles(long maxExtractedFiles) {
            if (maxExtractedFiles <= 0) {
                throw new IllegalArgumentException("maxExtractedFiles must be positive");
            }
            this.maxExtractedFiles = maxExtractedFiles;
            return this;
        }

        /**
         * Sets the maximum size allowed for a single extracted file.
         *
         * @param maxSingleFileSize maximum single file size in bytes
         * @return this builder
         */
        public Builder maxSingleFileSize(long maxSingleFileSize) {
            if (maxSingleFileSize <= 0) {
                throw new IllegalArgumentException("maxSingleFileSize must be positive");
            }
            this.maxSingleFileSize = maxSingleFileSize;
            return this;
        }

        /**
         * Sets the maximum allowed uncompressed-to-compressed ratio.
         *
         * @param maxCompressionRatio maximum compression ratio
         * @return this builder
         */
        public Builder maxCompressionRatio(int maxCompressionRatio) {
            if (maxCompressionRatio <= 0) {
                throw new IllegalArgumentException("maxCompressionRatio must be positive");
            }
            this.maxCompressionRatio = maxCompressionRatio;
            return this;
        }

        /**
         * Builds an immutable configuration instance.
         *
         * @return configuration snapshot
         */
        public ScramblerConfig build() {
            return new ScramblerConfig(
                    workspaceBasePath,
                    maxZipEntries,
                    maxUncompressedBytes,
                    maxNestedArchiveDepth,
                    maxExtractedBytes,
                    maxExtractedFiles,
                    maxSingleFileSize,
                    maxCompressionRatio);
        }
    }
}
