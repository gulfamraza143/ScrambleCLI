package com.scrambler.unmasking;

import com.scrambler.exception.MaskingException;
import com.scrambler.exception.ReportException;
import com.scrambler.masking.EntityReplacer;
import com.scrambler.masking.TokenFormatSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores masked text content using a pre-built mapping index.
 */
public final class UnmaskingEngine {

    private final EntityReplacer entityReplacer;

    /**
     * Creates an unmasking engine with default collaborators.
     */
    public UnmaskingEngine() {
        this(new EntityReplacer());
    }

    /**
     * Creates an unmasking engine with a supplied span replacer.
     *
     * @param entityReplacer replacement collaborator used for end-to-start substitution
     */
    UnmaskingEngine(EntityReplacer entityReplacer) {
        this.entityReplacer = Objects.requireNonNull(entityReplacer, "entityReplacer must not be null");
    }

    /**
     * Restores masked tokens in file content to their original values.
     *
     * @param maskedContent masked file content
     * @param mappingIndex  lookup from masked token to original value
     * @param restoreResult optional aggregate counters and warnings; may be {@code null}
     * @return restored file content with structure and line breaks preserved
     * @throws MaskingException when a masked token has no mapping
     */
    public String unmask(String maskedContent, MappingIndex mappingIndex, RestoreResult restoreResult) {
        Objects.requireNonNull(maskedContent, "maskedContent must not be null");
        Objects.requireNonNull(mappingIndex, "mappingIndex must not be null");

        Pattern tokenPattern = resolveTokenPattern(mappingIndex);
        detectMissingMappings(maskedContent, mappingIndex, tokenPattern);

        List<EntityReplacer.Replacement> replacements = collectReplacements(
                maskedContent,
                mappingIndex,
                tokenPattern);
        if (replacements.isEmpty()) {
            return maskedContent;
        }

        String restored = entityReplacer.replace(maskedContent, replacements);
        if (restoreResult != null) {
            restoreResult.addTokensRestored(replacements.size());
        }
        return restored;
    }

    private static Pattern resolveTokenPattern(MappingIndex mappingIndex) {
        boolean hasCurrent = false;
        boolean hasLegacy = false;

        for (String maskedValue : mappingIndex.getMaskedValues()) {
            if (TokenFormatSpec.isCurrentFormat(maskedValue)) {
                hasCurrent = true;
            } else if (TokenFormatSpec.isLegacyFormat(maskedValue)) {
                hasLegacy = true;
            } else {
                throw new ReportException("Unrecognized masked token format: " + maskedValue);
            }
        }

        if (hasCurrent && hasLegacy) {
            throw new ReportException("Mixed legacy and current masked token formats in entity report");
        }
        if (hasCurrent) {
            return TokenFormatSpec.currentFormatPattern();
        }
        if (hasLegacy) {
            return TokenFormatSpec.legacyFormatPattern();
        }
        return TokenFormatSpec.currentFormatPattern();
    }

    private static void detectMissingMappings(
            String maskedContent,
            MappingIndex mappingIndex,
            Pattern tokenPattern) {
        Matcher matcher = tokenPattern.matcher(maskedContent);
        while (matcher.find()) {
            String token = matcher.group();
            if (!mappingIndex.containsMaskedValue(token)) {
                throw new MaskingException("Missing mapping for masked token: " + token);
            }
        }
    }

    private static List<EntityReplacer.Replacement> collectReplacements(
            String maskedContent,
            MappingIndex mappingIndex,
            Pattern tokenPattern) {
        List<EntityReplacer.Replacement> replacements = new ArrayList<>();

        Matcher matcher = tokenPattern.matcher(maskedContent);
        while (matcher.find()) {
            String token = matcher.group();
            if (!mappingIndex.containsMaskedValue(token)) {
                continue;
            }
            replacements.add(new EntityReplacer.Replacement(
                    matcher.start(),
                    matcher.end(),
                    mappingIndex.getOriginalValue(token)));
        }

        replacements.sort(Comparator.comparingInt(EntityReplacer.Replacement::startOffset).reversed());
        return replacements;
    }
}
