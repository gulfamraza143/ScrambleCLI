package com.scrambler.detection;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rule-driven detection definition executed by {@link DetectionEngine}.
 */
public final class DetectionRule {

    private static final Predicate<String> ACCEPT_ALL = value -> true;

    private final EntityType entityType;
    private final EntityDomain entityDomain;
    private final Pattern pattern;
    private final int priority;
    private final int valueGroupIndex;
    private final Predicate<String> valueValidator;

    /**
     * Creates a detection rule that masks the full matched span.
     *
     * @param entityType   type to assign on match
     * @param entityDomain domain to assign on match
     * @param pattern      matching pattern
     * @param priority     overlap tie-breaker; higher values win
     */
    public DetectionRule(EntityType entityType, EntityDomain entityDomain, Pattern pattern, int priority) {
        this(entityType, entityDomain, pattern, priority, 0, ACCEPT_ALL);
    }

    /**
     * Creates a detection rule.
     *
     * @param entityType       type to assign on match
     * @param entityDomain     domain to assign on match
     * @param pattern          matching pattern
     * @param priority         overlap tie-breaker; higher values win
     * @param valueGroupIndex  capturing group for the sensitive value; {@code 0} uses the full match
     */
    public DetectionRule(EntityType entityType, EntityDomain entityDomain, Pattern pattern, int priority, int valueGroupIndex) {
        this(entityType, entityDomain, pattern, priority, valueGroupIndex, ACCEPT_ALL);
    }

    /**
     * Creates a detection rule with an optional post-match value validator.
     *
     * @param entityType       type to assign on match
     * @param entityDomain     domain to assign on match
     * @param pattern          matching pattern
     * @param priority         overlap tie-breaker; higher values win
     * @param valueGroupIndex  capturing group for the sensitive value; {@code 0} uses the full match
     * @param valueValidator   validator applied to the matched value; {@code null} accepts all matches
     */
    public DetectionRule(
            EntityType entityType,
            EntityDomain entityDomain,
            Pattern pattern,
            int priority,
            int valueGroupIndex,
            Predicate<String> valueValidator) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.entityDomain = Objects.requireNonNull(entityDomain, "entityDomain must not be null");
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.priority = priority;
        if (valueGroupIndex < 0) {
            throw new IllegalArgumentException("valueGroupIndex must not be negative");
        }
        this.valueGroupIndex = valueGroupIndex;
        this.valueValidator = valueValidator == null ? ACCEPT_ALL : valueValidator;
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

    /**
     * Returns the capturing group index for the sensitive value, or {@code 0} when the full match is sensitive.
     *
     * @return value group index
     */
    public int getValueGroupIndex() {
        return valueGroupIndex;
    }

    /**
     * Returns the validator applied to matched values before entity creation.
     *
     * @return value validator
     */
    public Predicate<String> getValueValidator() {
        return valueValidator;
    }
}
