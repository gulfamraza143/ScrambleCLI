package com.scrambler.replacement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderAssetProviderTest {

    private final PlaceholderAssetProvider provider = new PlaceholderAssetProvider();

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "jpeg", "gif", "bmp", "webp", "svg"})
    void resolvesImageExtensionsToDummyImage(String extension) {
        assertEquals("DUMMY_IMAGE.png", provider.resolveAssetName(extension));
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "jpeg", "gif", "bmp", "webp", "svg"})
    void loadsNonEmptyImagePlaceholder(String extension) {
        byte[] placeholder = provider.loadPlaceholder(extension);

        assertTrue(placeholder.length > 0, "placeholder bytes must not be empty for " + extension);
    }

    @Test
    void dummyImagePlaceholderExistsOnClasspath() {
        byte[] png = provider.loadPlaceholder("png");

        assertTrue(png.length >= 8);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }
}
