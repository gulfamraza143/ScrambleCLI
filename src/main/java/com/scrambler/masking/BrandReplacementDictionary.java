package com.scrambler.masking;

import com.scrambler.config.CompanyDictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Brand term replacement mappings loaded from a classpath resource with exact and case-insensitive lookup.
 */
public final class BrandReplacementDictionary {

    private static final String DEFAULT_RESOURCE = "/brand-replacements.txt";

    private final List<BrandMapping> mappings;
    private final Map<String, BrandMapping> mappingsBySource;

    private BrandReplacementDictionary(List<BrandMapping> mappings, Map<String, BrandMapping> mappingsBySource) {
        this.mappings = List.copyOf(mappings);
        this.mappingsBySource = Map.copyOf(mappingsBySource);
    }

    /**
     * Returns the default brand replacement dictionary loaded from the bundled resource file
     * and validated against the default company dictionary.
     *
     * @return validated default brand dictionary
     */
    public static BrandReplacementDictionary defaults() {
        BrandReplacementDictionary dictionary = loadFromResource(DEFAULT_RESOURCE);
        dictionary.validateCoverage(CompanyDictionary.defaults());
        return dictionary;
    }

    /**
     * Loads a brand replacement dictionary from a classpath resource.
     *
     * @param resourcePath classpath resource path
     * @return loaded brand dictionary
     */
    public static BrandReplacementDictionary loadFromResource(String resourcePath) {
        try (InputStream inputStream = BrandReplacementDictionary.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Brand replacement resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return fromParsedLines(reader.lines().toList(), resourcePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load brand replacements: " + resourcePath, e);
        }
    }

    /**
     * Validates that every configured company dictionary term has a replacement mapping.
     *
     * @param companyDictionary company brand detection dictionary
     */
    public void validateCoverage(CompanyDictionary companyDictionary) {
        Objects.requireNonNull(companyDictionary, "companyDictionary must not be null");
        for (String term : companyDictionary.getTerms()) {
            if (!hasMapping(term)) {
                throw new IllegalStateException("Missing replacement mapping for COMPANY_BRAND: " + term);
            }
        }
    }

    /**
     * Validates that every replacement value is unique so GlobalValueMapper bijection is preserved.
     */
    public void validateUniqueReplacements() {
        Set<String> seenReplacements = new HashSet<>();
        for (BrandMapping mapping : mappings) {
            if (!seenReplacements.add(mapping.replacement())) {
                throw new IllegalStateException(
                        "Duplicate brand replacement value for COMPANY_BRAND: " + mapping.replacement());
            }
        }
    }

    /**
     * Replaces a matched brand term with its configured replacement.
     *
     * @param original matched brand text
     * @return configured replacement text
     */
    public String replace(String original) {
        Objects.requireNonNull(original, "original must not be null");
        BrandMapping mapping = resolveMapping(original);
        if (mapping == null) {
            throw new IllegalStateException("Missing replacement mapping for COMPANY_BRAND: " + original);
        }
        return mapping.replacement();
    }

    /**
     * Returns brand mappings sorted longest-first.
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
            return source.equals(value);
        }
    }

    /**
     * Builds a brand dictionary from explicit mappings, sorted longest-first.
     *
     * @param mappings brand mappings
     * @return configured dictionary
     */
    public static BrandReplacementDictionary of(List<BrandMapping> mappings) {
        Objects.requireNonNull(mappings, "mappings must not be null");
        Map<String, BrandMapping> bySource = new LinkedHashMap<>();
        for (BrandMapping mapping : mappings) {
            validateMapping(mapping.source(), mapping.replacement(), "explicit mappings");
            if (bySource.containsKey(mapping.source())) {
                throw new IllegalStateException("Duplicate brand replacement source: " + mapping.source());
            }
            bySource.put(mapping.source(), mapping);
        }
        BrandReplacementDictionary dictionary = buildSorted(bySource);
        dictionary.validateUniqueReplacements();
        return dictionary;
    }

    private static BrandReplacementDictionary fromParsedLines(List<String> lines, String resourcePath) {
        Map<String, BrandMapping> bySource = new LinkedHashMap<>();
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String rawLine = lines.get(lineNumber - 1);
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex < 0) {
                throw new IllegalStateException(
                        "Malformed brand replacement line at " + resourcePath + ":" + lineNumber + ": " + rawLine);
            }

            String source = line.substring(0, equalsIndex).trim();
            String replacement = line.substring(equalsIndex + 1).trim();
            validateMapping(source, replacement, resourcePath + ":" + lineNumber);

            if (bySource.containsKey(source)) {
                throw new IllegalStateException(
                        "Duplicate brand replacement source at " + resourcePath + ":" + lineNumber + ": " + source);
            }
            bySource.put(source, new BrandMapping(source, replacement));
        }
        BrandReplacementDictionary dictionary = buildSorted(bySource);
        dictionary.validateUniqueReplacements();
        return dictionary;
    }

    private static BrandReplacementDictionary buildSorted(Map<String, BrandMapping> bySource) {
        List<BrandMapping> sorted = new ArrayList<>(bySource.values());
        sorted.sort(Comparator.comparingInt((BrandMapping mapping) -> mapping.source().length()).reversed());
        return new BrandReplacementDictionary(sorted, bySource);
    }

    private static void validateMapping(String source, String replacement, String location) {
        if (source.isBlank()) {
            throw new IllegalStateException("Blank source key in brand replacement at " + location);
        }
        if (replacement.isBlank()) {
            throw new IllegalStateException("Blank replacement value in brand replacement at " + location + ": " + source);
        }
    }

    private BrandMapping resolveMapping(String original) {
        BrandMapping exact = mappingsBySource.get(original);
        if (exact != null) {
            return exact;
        }

        BrandMapping caseInsensitiveMatch = null;
        for (BrandMapping mapping : mappings) {
            if (!mapping.source().equalsIgnoreCase(original)) {
                continue;
            }
            if (caseInsensitiveMatch != null) {
                throw new IllegalStateException(
                        "Ambiguous brand replacement mapping for COMPANY_BRAND: " + original);
            }
            caseInsensitiveMatch = mapping;
        }
        return caseInsensitiveMatch;
    }

    private boolean hasMapping(String source) {
        if (mappingsBySource.containsKey(source)) {
            return true;
        }
        BrandMapping caseInsensitiveMatch = null;
        for (BrandMapping mapping : mappings) {
            if (!mapping.source().equalsIgnoreCase(source)) {
                continue;
            }
            if (caseInsensitiveMatch != null) {
                return false;
            }
            caseInsensitiveMatch = mapping;
        }
        return caseInsensitiveMatch != null;
    }
}
