package com.scrambler.masking;

import com.scrambler.detection.EntityType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Collision-safe masked token format for SCRAMBLECLI.
 * <p>
 * Current format: {@code SCRAMBLE_{EntityType}_{6-digit-sequence}} (e.g. {@code SCRAMBLE_EMAIL_000001}).
 * Legacy format ({@code EMAIL_000001}) is supported for restore of pre-migration reports only.
 */
public final class TokenFormatSpec {

    public static final String PREFIX = "SCRAMBLE_";

    private static final String ENTITY_TYPES_LONGEST_FIRST = Arrays.stream(EntityType.values())
            .map(EntityType::name)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .reduce((left, right) -> left + "|" + right)
            .orElse("");

    private static final Pattern CURRENT_FORMAT_PATTERN =
            Pattern.compile(PREFIX + "(?:" + ENTITY_TYPES_LONGEST_FIRST + ")_\\d{6}");

    private static final Pattern LEGACY_FORMAT_PATTERN =
            Pattern.compile("(?:" + ENTITY_TYPES_LONGEST_FIRST + ")_\\d{6}");

    private TokenFormatSpec() {
    }

    /**
     * Formats a collision-safe masked token for the given entity type and sequence number.
     *
     * @param entityType entity type being masked
     * @param sequence   1-based per-type sequence within a masking run
     * @return formatted token such as {@code SCRAMBLE_EMAIL_000001}
     */
    public static String format(EntityType entityType, int sequence) {
        return PREFIX + entityType.name() + "_" + String.format("%06d", sequence);
    }

    /**
     * Returns whether the token uses the current collision-safe format.
     *
     * @param maskedValue candidate masked token
     * @return true when the token starts with {@link #PREFIX}
     */
    public static boolean isCurrentFormat(String maskedValue) {
        return maskedValue != null && maskedValue.startsWith(PREFIX);
    }

    /**
     * Returns whether the token uses the legacy pre-migration format.
     *
     * @param maskedValue candidate masked token
     * @return true when the token matches legacy shape without the SCRAMBLE prefix
     */
    public static boolean isLegacyFormat(String maskedValue) {
        return maskedValue != null && !maskedValue.isBlank() && LEGACY_FORMAT_PATTERN.matcher(maskedValue).matches();
    }

    /**
     * Returns the regex pattern for current-format tokens with longest entity-type match first.
     *
     * @return compiled pattern for orphan detection and replacement scanning
     */
    public static Pattern currentFormatPattern() {
        return CURRENT_FORMAT_PATTERN;
    }

    /**
     * Returns the regex pattern for legacy-format tokens with longest entity-type match first.
     *
     * @return compiled pattern for backward-compatible restore
     */
    public static Pattern legacyFormatPattern() {
        return LEGACY_FORMAT_PATTERN;
    }
}
