package com.scrambler.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Company brand terms used by dictionary-based detection rules.
 */
public final class CompanyDictionary {

    private static final List<String> DEFAULT_TERMS = List.of(
            "ICICI",
            "ICICIBANK",
            "ICICILABS",
            "FXTP",
            "WECARE",
            "SCRAMBLE"
    );

    private final List<String> terms;

    private CompanyDictionary(List<String> terms) {
        this.terms = List.copyOf(terms);
    }

    /**
     * Returns the default company dictionary.
     *
     * @return default dictionary
     */
    public static CompanyDictionary defaults() {
        return new CompanyDictionary(DEFAULT_TERMS);
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
