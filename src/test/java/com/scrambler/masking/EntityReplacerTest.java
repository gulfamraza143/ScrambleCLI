package com.scrambler.masking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityReplacerTest {

    private final EntityReplacer entityReplacer = new EntityReplacer();

    @Test
    void replacesSpansFromHighestOffsetToLowest() {
        String content = "0123456789";

        String masked = entityReplacer.replace(content, List.of(
                new EntityReplacer.Replacement(0, 3, "AAA"),
                new EntityReplacer.Replacement(7, 10, "BBB")));

        assertEquals("AAA3456BBB", masked);
    }

    @Test
    void returnsOriginalContentWhenNoReplacementsProvided() {
        String content = "unchanged";

        assertEquals(content, entityReplacer.replace(content, List.of()));
    }

    @Test
    void rejectsReplacementBeyondContentLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> entityReplacer.replace("abc", List.of(new EntityReplacer.Replacement(1, 5, "X"))));
    }
}
