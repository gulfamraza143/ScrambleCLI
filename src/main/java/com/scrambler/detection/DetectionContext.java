package com.scrambler.detection;

import com.scrambler.inventory.FileInfo;

import java.util.Objects;

/**
 * Immutable input for a single-file detection run.
 */
public final class DetectionContext {

    private final FileInfo fileInfo;
    private final String content;

    /**
     * Creates a detection context for one text file.
     *
     * @param fileInfo inventoried file metadata
     * @param content  full text content to scan
     */
    public DetectionContext(FileInfo fileInfo, String content) {
        this.fileInfo = Objects.requireNonNull(fileInfo, "fileInfo must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public String getContent() {
        return content;
    }
}
