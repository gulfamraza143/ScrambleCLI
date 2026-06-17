package com.scrambler.replacement;

import com.scrambler.exception.FileProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Supplies bundled placeholder binary assets for document and image replacement.
 */
public final class PlaceholderAssetProvider {

    private static final String RESOURCE_ROOT = "/com/scrambler/replacement/placeholders/";

    private static final String DUMMY_IMAGE = "DUMMY_IMAGE.png";
    private static final String DUMMY_PDF = "DUMMY_PDF.pdf";
    private static final String DUMMY_SPREADSHEET = "DUMMY_SPREADSHEET.xlsx";
    private static final String DUMMY_DOCUMENT = "DUMMY_DOCUMENT.docx";

    private static final Map<String, String> EXTENSION_TO_ASSET = Map.ofEntries(
            Map.entry("pdf", DUMMY_PDF),
            Map.entry("doc", DUMMY_DOCUMENT),
            Map.entry("docx", DUMMY_DOCUMENT),
            Map.entry("odt", DUMMY_DOCUMENT),
            Map.entry("ppt", DUMMY_DOCUMENT),
            Map.entry("pptx", DUMMY_DOCUMENT),
            Map.entry("odp", DUMMY_DOCUMENT),
            Map.entry("xls", DUMMY_SPREADSHEET),
            Map.entry("xlsx", DUMMY_SPREADSHEET),
            Map.entry("ods", DUMMY_SPREADSHEET),
            Map.entry("png", DUMMY_IMAGE),
            Map.entry("jpg", DUMMY_IMAGE),
            Map.entry("jpeg", DUMMY_IMAGE),
            Map.entry("gif", DUMMY_IMAGE),
            Map.entry("bmp", DUMMY_IMAGE),
            Map.entry("webp", DUMMY_IMAGE),
            Map.entry("svg", DUMMY_IMAGE));

    /**
     * Returns placeholder bytes for the given file extension.
     *
     * @param extension lowercase extension without leading dot
     * @return bundled placeholder bytes
     * @throws FileProcessingException when no placeholder exists for the extension
     */
    public byte[] loadPlaceholder(String extension) {
        String assetName = resolveAssetName(extension);
        String resourcePath = RESOURCE_ROOT + assetName;
        try (InputStream inputStream = PlaceholderAssetProvider.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new FileProcessingException("Placeholder asset not found on classpath: " + resourcePath);
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new FileProcessingException("Failed to load placeholder asset: " + resourcePath, e);
        }
    }

    /**
     * Returns the bundled asset filename used for an extension.
     *
     * @param extension lowercase extension without leading dot
     * @return bundled asset filename
     */
    public String resolveAssetName(String extension) {
        Objects.requireNonNull(extension, "extension must not be null");
        String normalized = extension.toLowerCase(Locale.ROOT);
        String assetName = EXTENSION_TO_ASSET.get(normalized);
        if (assetName == null) {
            throw new FileProcessingException("No placeholder asset configured for extension: " + extension);
        }
        return assetName;
    }
}
