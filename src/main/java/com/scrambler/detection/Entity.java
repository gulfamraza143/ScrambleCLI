package com.scrambler.detection;

import java.util.Objects;

/**
 * A single detected sensitive value with mandatory character offsets.
 */
public final class Entity {

    private final EntityDomain domain;
    private final EntityType type;
    private final String originalValue;
    private final int startOffset;
    private final int endOffset;

    /**
     * Creates a detected entity span.
     *
     * @param domain         entity domain
     * @param type           entity type
     * @param originalValue  matched text
     * @param startOffset    inclusive start offset in the scanned content
     * @param endOffset      exclusive end offset in the scanned content
     */
    public Entity(EntityDomain domain, EntityType type, String originalValue, int startOffset, int endOffset) {
        this.domain = Objects.requireNonNull(domain, "domain must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.originalValue = Objects.requireNonNull(originalValue, "originalValue must not be null");
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must not be negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must not be before startOffset");
        }
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public EntityDomain getDomain() {
        return domain;
    }

    public EntityType getType() {
        return type;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }
}
