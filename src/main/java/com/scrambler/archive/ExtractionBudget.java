package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Tracks global extraction limits across the repository and all nested archives.
 */
public final class ExtractionBudget {

    private static final Logger log = LoggerFactory.getLogger(ExtractionBudget.class);

    private final ScramblerConfig config;
    private long totalExtractedBytes;
    private long totalFileCount;

    public ExtractionBudget(ScramblerConfig config) {
        this.config = config;
    }

    /**
     * Computes nested archive depth from the archive path relative to the extraction root.
     * An archive directly under the extraction root is depth {@code 1}.
     */
    public static int computeNestedDepth(Path archivePath, Path extractionRoot) {
        Path parent = archivePath.getParent();
        if (parent == null || !parent.startsWith(extractionRoot)) {
            return 1;
        }
        Path relativeParent = extractionRoot.relativize(parent);
        if (relativeParent.toString().isEmpty()) {
            return 1;
        }
        return relativeParent.getNameCount() + 1;
    }

    public void validateNestedDepth(int depth) {
        if (depth > config.getMaxNestedArchiveDepth()) {
            throw new ArchiveException("Nested archive depth exceeded: depth=" + depth);
        }
    }

    public boolean skipIfOversized(String entryName, long declaredSize) {
        if (declaredSize > 0L && declaredSize > config.getMaxSingleFileSize()) {
            log.warn("Skipping oversized file: {}", entryName);
            return true;
        }
        return false;
    }

    public void validateEntryCompressionRatio(long compressedBytes, long uncompressedBytes) {
        if (compressedBytes <= 0L || uncompressedBytes <= 0L) {
            return;
        }
        if (uncompressedBytes > compressedBytes * config.getMaxCompressionRatio()) {
            throw new ArchiveException("Suspicious compression ratio detected");
        }
    }

    public void validateArchiveCompressionRatio(long compressedBytes, long uncompressedBytes) {
        validateEntryCompressionRatio(compressedBytes, uncompressedBytes);
    }

    public void recordExtractedFile(String entryName, long bytesWritten) {
        if (bytesWritten > config.getMaxSingleFileSize()) {
            log.warn("Skipping oversized file: {}", entryName);
            return;
        }
        totalFileCount++;
        if (totalFileCount > config.getMaxExtractedFiles()) {
            throw new ArchiveException("Extraction file count limit exceeded");
        }
        recordBytes(bytesWritten);
    }

    public void ensureAdditionalBytesAllowed(long additionalBytes) {
        if (additionalBytes <= 0L) {
            return;
        }
        if (totalExtractedBytes + additionalBytes > config.getMaxExtractedBytes()) {
            throw new ArchiveException("Extraction size limit exceeded");
        }
    }

    public void recordBytes(long bytes) {
        if (bytes <= 0L) {
            return;
        }
        totalExtractedBytes += bytes;
        if (totalExtractedBytes > config.getMaxExtractedBytes()) {
            throw new ArchiveException("Extraction size limit exceeded");
        }
    }

    public long getTotalExtractedBytes() {
        return totalExtractedBytes;
    }

    public long getTotalFileCount() {
        return totalFileCount;
    }
}
