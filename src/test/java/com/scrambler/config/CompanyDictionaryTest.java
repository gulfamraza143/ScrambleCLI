package com.scrambler.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyDictionaryTest {

    @Test
    void defaultsLoadsBundledTerms() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();

        assertEquals(
                List.of(
                        "ICICI Securities",
                        "ICICI Direct",
                        "ICICIDirect",
                        "ICICI Venture",
                        "ICICI Foundation",
                        "ICICI Home Finance",
                        "ICICI HFC",
                        "ICICI Merchant Services",
                        "ICICI Investments",
                        "ICICI International",
                        "ICICI Stack",
                        "iMobile",
                        "iMobile Pay",
                        "InstaBIZ",
                        "InstaBiz",
                        "Pockets",
                        "Money2India",
                        "Money2World",
                        "iPal",
                        "Eazypay",
                        "Amazon Pay ICICI",
                        "ICICI Coral",
                        "ICICI Rubyx",
                        "ICICI Sapphiro",
                        "ICICI Emeralde",
                        "Industrial Credit and Investment Corporation of India"),
                dictionary.getTerms());
    }

    @Test
    void compilePatternMatchesConfiguredTerms() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();

        assertTrue(dictionary.compilePattern().matcher("partner ICICI Securities team").find());
        assertTrue(dictionary.compilePattern().matcher("iMobile").find());
    }

    @Test
    void compilePatternPrefersLongestConfiguredTerm() {
        Matcher matcher = CompanyDictionary.defaults().compilePattern().matcher("team: ICICI Securities");

        assertTrue(matcher.find());
        assertEquals("ICICI Securities", matcher.group());
    }
}
