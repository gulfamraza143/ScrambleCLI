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
            "p12, SKIP",
            "csv, TEXT",
            "log, TEXT",
            "md, TEXT",
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
    void isTextExtensionIncludesMarkdown() {
        assertEquals(FileCategory.TEXT, SupportedExtensions.categoryForExtension("md"));
        assertEquals(FileCategory.TEXT, SupportedExtensions.categoryForExtension("MD"));
    }

    @Test
    void mapsNullExtensionToSkip() {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension(null));
    }

    @Test
    void mapsEmptyExtensionToSkip() {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension(""));
    }

    @Test
    void p12IsNotTextExtension() {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension("p12"));
        assertEquals(false, SupportedExtensions.isTextExtension("p12"));
    }

    @ParameterizedTest
    @CsvSource({
            "p12",
            "P12"
    })
    void p12CaseInsensitiveClassification(String extension) {
        assertEquals(FileCategory.SKIP, SupportedExtensions.categoryForExtension(extension));
        assertEquals(false, SupportedExtensions.isTextExtension(extension));
    }
}
