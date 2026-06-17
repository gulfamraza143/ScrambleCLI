package com.scrambler.masking;

import com.scrambler.detection.EntityType;

import java.util.Objects;

/**
 * In-memory mapping between an original entity span and its masked token.
 */
public final class MappingRecord {

    private final String repoRelativePath;
    private final EntityType entityType;
    private final String originalValue;
    private final String maskedValue;
    private final int startOffset;
    private final int endOffset;

    /**
     * Creates a mapping record for one masked entity occurrence.
     *
     * @param repoRelativePath repository-relative path using forward slashes
     * @param entityType       detected entity type
     * @param originalValue    matched text
     * @param maskedValue      replacement token
     * @param startOffset      inclusive start offset in the original content
     * @param endOffset        exclusive end offset in the original content
     */
    public MappingRecord(
            String repoRelativePath,
            EntityType entityType,
            String originalValue,
            String maskedValue,
            int startOffset,
            int endOffset) {
        this.repoRelativePath = Objects.requireNonNull(repoRelativePath, "repoRelativePath must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.originalValue = Objects.requireNonNull(originalValue, "originalValue must not be null");
        this.maskedValue = Objects.requireNonNull(maskedValue, "maskedValue must not be null");
        if (repoRelativePath.isBlank()) {
            throw new IllegalArgumentException("repoRelativePath must not be blank");
        }
        if (maskedValue.isBlank()) {
            throw new IllegalArgumentException("maskedValue must not be blank");
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must not be negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must not be before startOffset");
        }
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public String getRepoRelativePath() {
        return repoRelativePath;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }
}
