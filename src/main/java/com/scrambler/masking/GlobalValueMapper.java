package com.scrambler.masking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.MaskingException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global original-to-masked dictionary guaranteeing bijective mappings within a masking run.
 * <p>
 * Same original value always maps to the same masked value; different originals receive different masked values.
 */
public final class GlobalValueMapper {

    private static final int MAX_COLLISION_ATTEMPTS = 10_000;

    private final FormatPreservingGenerator generator;
    private final Map<String, String> originalToMasked;
    private final Map<String, String> maskedToOriginal;

    /**
     * Creates a global value mapper with the default format-preserving generator.
     */
    public GlobalValueMapper() {
        this(new FormatPreservingGenerator());
    }

    /**
     * Creates a global value mapper with a supplied generator.
     *
     * @param generator format-preserving value generator
     */
    GlobalValueMapper(FormatPreservingGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
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

        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            String candidate = generator.generate(entityType, original, attempt);
            String mappedOriginal = maskedToOriginal.get(candidate);
            if (mappedOriginal == null) {
                register(original, candidate);
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

    private void register(String original, String masked) {
        originalToMasked.put(original, masked);
        maskedToOriginal.put(masked, original);
    }
}
