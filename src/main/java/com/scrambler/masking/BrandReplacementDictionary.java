package com.scrambler.masking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Brand term replacement mappings (e.g. ICICI → LOTUS) with case-preserving substitution.
 */
public final class BrandReplacementDictionary {

    private final List<BrandMapping> mappings;

    private BrandReplacementDictionary(List<BrandMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    /**
     * Returns the default ICICI → LOTUS brand replacement dictionary.
     *
     * @return default brand dictionary
     */
    public static BrandReplacementDictionary defaults() {
        return new BrandReplacementDictionary(List.of(
                new BrandMapping("ICICI Prudential", "LOTUS Prudential"),
                new BrandMapping("ICICI Lombard", "LOTUS Lombard"),
                new BrandMapping("ICICI Bank", "LOTUS Bank"),
                new BrandMapping("ICICI", "LOTUS")));
    }

    /**
     * Replaces a matched brand term with its configured replacement, preserving letter casing.
     *
     * @param original matched brand text
     * @return replacement text with casing preserved
     */
    public String replace(String original) {
        Objects.requireNonNull(original, "original must not be null");
        for (BrandMapping mapping : mappings) {
            if (mapping.matches(original)) {
                return applyCase(original, mapping.replacement());
            }
        }
        return original;
    }

    /**
     * Returns brand mappings sorted longest-first for overlap-safe detection.
     *
     * @return immutable mapping list
     */
    public List<BrandMapping> getMappingsLongestFirst() {
        return mappings;
    }

    /**
     * One source brand term and its replacement.
     *
     * @param source      original brand text
     * @param replacement masked brand text
     */
    public record BrandMapping(String source, String replacement) {
        public BrandMapping {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(replacement, "replacement must not be null");
        }

        boolean matches(String value) {
            return source.equalsIgnoreCase(value);
        }
    }

    /**
     * Builds a brand dictionary from explicit mappings, sorted longest-first.
     *
     * @param mappings brand mappings
     * @return configured dictionary
     */
    public static BrandReplacementDictionary of(List<BrandMapping> mappings) {
        List<BrandMapping> sorted = new ArrayList<>(mappings);
        sorted.sort(Comparator.comparingInt((BrandMapping mapping) -> mapping.source().length()).reversed());
        return new BrandReplacementDictionary(sorted);
    }

    private static String applyCase(String original, String replacement) {
        if (original.equals(original.toUpperCase(Locale.ROOT))) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (original.equals(original.toLowerCase(Locale.ROOT))) {
            return replacement.toLowerCase(Locale.ROOT);
        }
        if (Character.isUpperCase(original.charAt(0))) {
            if (replacement.length() == 1) {
                return replacement.toUpperCase(Locale.ROOT);
            }
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1).toLowerCase(Locale.ROOT);
        }
        return replacement;
    }
}
