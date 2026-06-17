package com.scrambler.config;

import com.scrambler.classify.FileCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportedExtensionsTest {

    @ParameterizedTest
    @CsvSource({
            "java, TEXT",
            "txt, TEXT",
            "env, TEXT",
            "pem, TEXT",
            "key, TEXT",
            "crt, TEXT",
            "p12, TEXT",
            "csv, TEXT",
            "log, TEXT",
            "md, SKIP",
            "pdf, DOCUMENT",
            "png, IMAGE",
            "jar, SKIP",
            "class, SKIP",
            "xyz, SKIP"
    })
    void mapsExtensionsToCategories(String extension, FileCategory expectedCategory) {
        assertEquals(expectedCategory, SupportedExtensions.categoryForExtension(extension));
    }

    @Test
    void mapsNullExtensionToSkip() {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension(null));
    }

    @Test
    void mapsEmptyExtensionToSkip() {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension(""));
    }
}
