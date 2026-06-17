package com.scrambler.detection;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rule-driven detection definition executed by {@link DetectionEngine}.
 */
public final class DetectionRule {

    private final EntityType entityType;
    private final EntityDomain entityDomain;
    private final Pattern pattern;
    private final int priority;

    /**
     * Creates a detection rule.
     *
     * @param entityType   type to assign on match
     * @param entityDomain domain to assign on match
     * @param pattern      matching pattern
     * @param priority     overlap tie-breaker; higher values win
     */
    public DetectionRule(EntityType entityType, EntityDomain entityDomain, Pattern pattern, int priority) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.entityDomain = Objects.requireNonNull(entityDomain, "entityDomain must not be null");
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.priority = priority;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public EntityDomain getEntityDomain() {
        return entityDomain;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public int getPriority() {
        return priority;
    }
}
