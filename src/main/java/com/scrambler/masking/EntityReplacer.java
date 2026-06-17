package com.scrambler.masking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies span replacements to text content.
 */
public final class EntityReplacer {

    /**
     * One replacement span within original content.
     *
     * @param startOffset inclusive start offset
     * @param endOffset   exclusive end offset
     * @param maskedValue replacement text
     */
    public record Replacement(int startOffset, int endOffset, String maskedValue) {

        public Replacement {
            Objects.requireNonNull(maskedValue, "maskedValue must not be null");
            if (startOffset < 0) {
                throw new IllegalArgumentException("startOffset must not be negative");
            }
            if (endOffset < startOffset) {
                throw new IllegalArgumentException("endOffset must not be before startOffset");
            }
        }
    }

    /**
     * Replaces the supplied spans from highest offset to lowest offset so earlier
     * offsets remain valid while the content is mutated.
     *
     * @param content       original text content
     * @param replacements  spans and replacement values
     * @return content with all replacements applied
     */
    public String replace(String content, List<Replacement> replacements) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(replacements, "replacements must not be null");

        if (replacements.isEmpty()) {
            return content;
        }

        List<Replacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(Replacement::startOffset).reversed());

        StringBuilder masked = new StringBuilder(content);
        for (Replacement replacement : ordered) {
            validateBounds(content, replacement);
            masked.replace(replacement.startOffset(), replacement.endOffset(), replacement.maskedValue());
        }
        return masked.toString();
    }

    private static void validateBounds(String content, Replacement replacement) {
        if (replacement.endOffset() > content.length()) {
            throw new IllegalArgumentException(
                    "replacement endOffset exceeds content length: " + replacement.endOffset());
        }
    }
}
