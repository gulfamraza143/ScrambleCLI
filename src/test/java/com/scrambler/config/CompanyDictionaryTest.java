package com.scrambler.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyDictionaryTest {

    @Test
    void defaultsLoadsBundledTerms() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();

        assertEquals(
                List.of("ICICI", "ICICI Bank", "ICICI Lombard", "ICICI Prudential",
                        "ICICIBANK", "ICICILABS", "FXTP", "WECARE", "SCRAMBLE"),
                dictionary.getTerms());
    }

    @Test
    void compilePatternMatchesConfiguredTerms() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();

        assertTrue(dictionary.compilePattern().matcher("partner ICICI team").find());
        assertTrue(dictionary.compilePattern().matcher("SCRAMBLE").find());
    }
}
