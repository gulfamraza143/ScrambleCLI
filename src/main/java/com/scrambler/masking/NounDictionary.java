package com.scrambler.masking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic noun seed dictionary used for format-preserving synthetic value generation.
 */
public final class NounDictionary {

    private static final String DEFAULT_RESOURCE = "/noun-dictionary.txt";

    private final List<String> nouns;

    private NounDictionary(List<String> nouns) {
        this.nouns = List.copyOf(nouns);
    }

    /**
     * Returns the default noun dictionary loaded from the bundled resource file.
     *
     * @return default dictionary with 5000+ nouns
     */
    public static NounDictionary defaults() {
        return loadFromResource(DEFAULT_RESOURCE);
    }

    /**
     * Loads a noun dictionary from a classpath resource.
     *
     * @param resourcePath classpath resource path
     * @return loaded dictionary
     */
    public static NounDictionary loadFromResource(String resourcePath) {
        try (InputStream inputStream = NounDictionary.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Noun dictionary resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                List<String> nouns = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .toList();
                if (nouns.size() < 100) {
                    throw new IllegalStateException("Noun dictionary too small: " + nouns.size());
                }
                return new NounDictionary(nouns);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load noun dictionary: " + resourcePath, e);
        }
    }

    /**
     * Returns the noun at the given index, wrapping when the index exceeds the dictionary size.
     *
     * @param index zero-based index
     * @return noun at the resolved index
     */
    public String nounAt(int index) {
        if (nouns.isEmpty()) {
            throw new IllegalStateException("Noun dictionary is empty");
        }
        int normalized = Math.floorMod(index, nouns.size());
        return nouns.get(normalized);
    }

    /**
     * Returns a deterministic noun derived from the hash of the supplied seed string.
     *
     * @param seed input seed
     * @return selected noun
     */
    public String selectNoun(String seed) {
        Objects.requireNonNull(seed, "seed must not be null");
        return nounAt(stableHash(seed));
    }

    /**
     * Returns a deterministic noun derived from the hash of the seed and collision attempt.
     *
     * @param seed    input seed
     * @param attempt collision-resolution attempt number
     * @return selected noun
     */
    public String selectNoun(String seed, int attempt) {
        Objects.requireNonNull(seed, "seed must not be null");
        return nounAt(stableHash(seed + "#" + attempt));
    }

    /**
     * Returns the number of nouns in this dictionary.
     *
     * @return noun count
     */
    public int size() {
        return nouns.size();
    }

    private static int stableHash(String value) {
        int hash = 0;
        for (int index = 0; index < value.length(); index++) {
            hash = 31 * hash + value.charAt(index);
        }
        return hash;
    }
}
