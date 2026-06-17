package com.scrambler.masking;

import com.scrambler.detection.EntityType;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenFormatSpecTest {

    @Test
    void formatsCurrentTokensWithPrefix() {
        assertEquals("SCRAMBLE_EMAIL_000001", TokenFormatSpec.format(EntityType.EMAIL, 1));
        assertEquals("SCRAMBLE_DATABASE_URL_000042", TokenFormatSpec.format(EntityType.DATABASE_URL, 42));
    }

    @Test
    void databaseUrlTokenIsNotSubstringOfUrlToken() {
        String databaseUrlToken = TokenFormatSpec.format(EntityType.DATABASE_URL, 1);
        String urlToken = TokenFormatSpec.format(EntityType.URL, 1);

        assertFalse(databaseUrlToken.contains(urlToken));
        assertFalse(urlToken.contains(databaseUrlToken));
    }

    @Test
    void currentPatternMatchesLongestEntityTypeFirst() {
        String content = "jdbc=" + TokenFormatSpec.format(EntityType.DATABASE_URL, 1);
        Matcher matcher = TokenFormatSpec.currentFormatPattern().matcher(content);

        assertTrue(matcher.find());
        assertEquals(TokenFormatSpec.format(EntityType.DATABASE_URL, 1), matcher.group());
    }

    @Test
    void legacyPatternMatchesLegacyTokens() {
        assertTrue(TokenFormatSpec.isLegacyFormat("EMAIL_000001"));
        assertFalse(TokenFormatSpec.isLegacyFormat("SCRAMBLE_EMAIL_000001"));
    }
}
