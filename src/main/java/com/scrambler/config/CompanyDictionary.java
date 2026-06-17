package com.scrambler.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Company brand terms used by dictionary-based detection rules.
 */
public final class CompanyDictionary {

    private static final String DEFAULT_RESOURCE = "/company-dictionary.txt";

    private final List<String> terms;

    private CompanyDictionary(List<String> terms) {
        this.terms = List.copyOf(terms);
    }

    /**
     * Returns the default company dictionary loaded from the bundled resource file.
     *
     * @return default dictionary
     */
    public static CompanyDictionary defaults() {
        return loadFromResource(DEFAULT_RESOURCE);
    }

    /**
     * Loads a company dictionary from a classpath resource.
     *
     * @param resourcePath classpath resource path
     * @return loaded dictionary
     */
    public static CompanyDictionary loadFromResource(String resourcePath) {
        try (InputStream inputStream = CompanyDictionary.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Company dictionary resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                List<String> terms = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
                return new CompanyDictionary(terms);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load company dictionary: " + resourcePath, e);
        }
    }

    /**
     * Returns immutable brand terms.
     *
     * @return brand terms
     */
    public List<String> getTerms() {
        return terms;
    }

    /**
     * Compiles a case-insensitive word-boundary pattern for all configured terms.
     *
     * @return compiled dictionary pattern
     */
    public Pattern compilePattern() {
        String alternation = String.join("|", terms.stream().map(Pattern::quote).toList());
        return Pattern.compile("\\b(?i)(?:" + alternation + ")\\b");
    }
}
