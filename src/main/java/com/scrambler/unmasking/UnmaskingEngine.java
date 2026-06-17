package com.scrambler.unmasking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.MaskingException;
import com.scrambler.masking.EntityReplacer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Restores masked text content using a pre-built mapping index.
 */
public final class UnmaskingEngine {

    private static final Pattern MASKED_TOKEN_PATTERN = buildMaskedTokenPattern();

    private static Pattern buildMaskedTokenPattern() {
        String entityTypes = Arrays.stream(EntityType.values())
                .map(EntityType::name)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?:" + entityTypes + ")_\\d{6}");
    }

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

        detectMissingMappings(maskedContent, mappingIndex);

        List<EntityReplacer.Replacement> replacements = collectReplacements(maskedContent, mappingIndex);
        if (replacements.isEmpty()) {
            return maskedContent;
        }

        String restored = entityReplacer.replace(maskedContent, replacements);
        if (restoreResult != null) {
            restoreResult.addTokensRestored(replacements.size());
        }
        return restored;
    }

    private static void detectMissingMappings(String maskedContent, MappingIndex mappingIndex) {
        Matcher matcher = MASKED_TOKEN_PATTERN.matcher(maskedContent);
        while (matcher.find()) {
            String token = matcher.group();
            if (!mappingIndex.containsMaskedValue(token)) {
                throw new MaskingException("Missing mapping for masked token: " + token);
            }
        }
    }

    private static List<EntityReplacer.Replacement> collectReplacements(
            String maskedContent,
            MappingIndex mappingIndex) {
        List<EntityReplacer.Replacement> replacements = new ArrayList<>();

        for (String maskedValue : mappingIndex.getMaskedValues()) {
            if (!maskedContent.contains(maskedValue)) {
                continue;
            }

            String originalValue = mappingIndex.getOriginalValue(maskedValue);
            int fromIndex = 0;
            while (true) {
                int startOffset = maskedContent.indexOf(maskedValue, fromIndex);
                if (startOffset < 0) {
                    break;
                }
                replacements.add(new EntityReplacer.Replacement(
                        startOffset,
                        startOffset + maskedValue.length(),
                        originalValue));
                fromIndex = startOffset + maskedValue.length();
            }
        }

        replacements.sort(Comparator.comparingInt(EntityReplacer.Replacement::startOffset).reversed());
        return replacements;
    }
}
