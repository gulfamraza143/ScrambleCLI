package com.scrambler.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraProjectKeysTest {

    @Test
    void defaultsLoadsBundledKeys() {
        JiraProjectKeys keys = JiraProjectKeys.defaults();

        assertEquals(List.of("ENG", "RISK", "OPS", "SEC", "SCRAMBLE"), keys.getKeys());
    }

    @Test
    void compileWorkItemPatternMatchesAllowlistedJiraKeys() {
        JiraProjectKeys keys = JiraProjectKeys.defaults();

        assertTrue(keys.compileWorkItemPattern().matcher("story=ENG-1234").find());
        assertTrue(keys.compileWorkItemPattern().matcher("risk=RISK-5678").find());
        assertTrue(keys.compileWorkItemPattern().matcher("ops=OPS-9999").find());
    }

    @Test
    void compileWorkItemPatternRejectsNonAllowlistedJiraKeys() {
        JiraProjectKeys keys = JiraProjectKeys.defaults();

        assertFalse(keys.compileWorkItemPattern().matcher("story=ABC-123").find());
        assertFalse(keys.compileWorkItemPattern().matcher("task=TEST-456").find());
    }

    @Test
    void compileWorkItemPatternMatchesServiceNowIds() {
        JiraProjectKeys keys = JiraProjectKeys.defaults();

        assertTrue(keys.compileWorkItemPattern().matcher("ticket=INC0012345").find());
        assertTrue(keys.compileWorkItemPattern().matcher("change=RITM0012345").find());
    }

    @Test
    void compileWorkItemPatternPrefersLongestConfiguredKey() {
        Matcher matcher = JiraProjectKeys.defaults().compileWorkItemPattern().matcher("epic=SCRAMBLE-42");

        assertTrue(matcher.find());
        assertEquals("SCRAMBLE-42", matcher.group());
    }
}
