package com.scrambler.masking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.MaskingException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global original-to-masked dictionary guaranteeing bijective mappings within a masking run.
 * <p>
 * Same original value always maps to the same masked value; different originals receive different masked values.
 */
public final class GlobalValueMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalValueMapper.class);

    private static final int MAX_COLLISION_ATTEMPTS = 10_000;

    private final FormatPreservingGenerator generator;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final Map<String, String> originalToMasked;
    private final Map<String, String> maskedToOriginal;

    /**
     * Creates a global value mapper with the default format-preserving generator.
     */
    public GlobalValueMapper() {
        this(new FormatPreservingGenerator(), new OpaqueTokenGenerator());
    }

    /**
     * Creates a global value mapper with a supplied generator.
     *
     * @param generator format-preserving value generator
     */
    GlobalValueMapper(FormatPreservingGenerator generator) {
        this(generator, new OpaqueTokenGenerator());
    }

    GlobalValueMapper(FormatPreservingGenerator generator, OpaqueTokenGenerator opaqueTokenGenerator) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.opaqueTokenGenerator = Objects.requireNonNull(opaqueTokenGenerator, "opaqueTokenGenerator must not be null");
        this.originalToMasked = new HashMap<>();
        this.maskedToOriginal = new HashMap<>();
    }

    /**
     * Resolves the masked value for an original entity value, creating a new mapping when needed.
     *
     * @param entityType entity type
     * @param original   original matched text
     * @return masked value for this original
     */
    public String resolve(EntityType entityType, String original) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(original, "original must not be null");

        String existing = originalToMasked.get(original);
        if (existing != null) {
            return existing;
        }

        if (entityType == EntityType.COMPANY_BRAND) {
            String replacement = generator.generate(entityType, original, 0);
            String sharedCaseVariant = findSharedCaseVariantMapping(original, replacement);
            if (sharedCaseVariant != null) {
                originalToMasked.put(original, sharedCaseVariant);
                return sharedCaseVariant;
            }
        }

        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            String candidate = generateCandidate(entityType, original, attempt);
            String mappedOriginal = maskedToOriginal.get(candidate);
            if (mappedOriginal == null) {
                register(entityType, original, candidate);
                return candidate;
            }
            if (mappedOriginal.equals(original)) {
                return candidate;
            }
        }

        throw new MaskingException(
                "Unable to generate unique masked value for: " + entityType + " / " + original);
    }

    /**
     * Returns whether the mapper already contains a mapping for the original value.
     *
     * @param original original value
     * @return true when mapped
     */
    public boolean containsOriginal(String original) {
        return originalToMasked.containsKey(original);
    }

    /**
     * Returns the number of unique original values mapped.
     *
     * @return mapping count
     */
    public int size() {
        return originalToMasked.size();
    }

    private void register(EntityType entityType, String original, String masked) {
        originalToMasked.put(original, masked);
        maskedToOriginal.put(masked, original);
        if (entityType == EntityType.REPOSITORY_NAME
                || entityType == EntityType.FOLDER_NAME
                || entityType == EntityType.FILE_NAME) {
            LOGGER.debug("Path mapping registered for {}: {} -> {}", entityType, original, masked);
        }
    }

    private String findSharedCaseVariantMapping(String original, String replacement) {
        for (Map.Entry<String, String> entry : originalToMasked.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(original) && entry.getValue().equals(replacement)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String generateCandidate(EntityType entityType, String original, int attempt) {
        return switch (entityType) {
            case REPOSITORY_NAME, FOLDER_NAME -> opaqueTokenGenerator.generate(attempt);
            case FILE_NAME -> generateMaskedFileName(original, attempt);
            default -> generator.generate(entityType, original, attempt);
        };
    }

    private String generateMaskedFileName(String original, int attempt) {
        int lastDot = original.lastIndexOf('.');
        if (lastDot > 0 && lastDot < original.length() - 1) {
            String basename = original.substring(0, lastDot);
            String extension = original.substring(lastDot);
            String existingBasenameToken = originalToMasked.get(basename);
            if (existingBasenameToken != null) {
                return existingBasenameToken + extension;
            }
            return opaqueTokenGenerator.generate(attempt) + extension;
        }
        return opaqueTokenGenerator.generate(attempt);
    }
}
