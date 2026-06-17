package com.scrambler.config;

import com.scrambler.classify.FileCategory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for extension-to-category mappings used by {@code FileClassifier}.
 * Only explicitly listed extensions are scanned as TEXT; everything else is SKIP, DOCUMENT, or IMAGE.
 */
public final class SupportedExtensions {

    private static final Set<String> TEXT = Set.of(
            "java", "kt", "groovy",
            "py",
            "js", "jsx", "ts", "tsx",
            "sql",
            "json", "xml",
            "yaml", "yml",
            "properties",
            "env",
            "conf", "cfg",
            "txt",
            "sh", "bash", "bat", "ps1",
            "html", "css",
            "jsp",
            "pem", "key", "crt", "p12",
            "csv", "log");

    private static final Set<String> DOCUMENT = Set.of(
            "pdf",
            "doc", "docx",
            "xls", "xlsx",
            "ppt", "pptx",
            "odt", "ods", "odp");

    private static final Set<String> IMAGE = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg");

    private static final Set<String> SKIP = Set.of(
            "zip", "tar", "gz", "7z", "rar",
            "jar", "war", "ear",
            "class",
            "exe", "dll", "so", "dylib",
            "bin", "dat",
            "md");

    private static final Map<FileCategory, Set<String>> BY_CATEGORY = Map.of(
            FileCategory.TEXT, TEXT,
            FileCategory.DOCUMENT, DOCUMENT,
            FileCategory.IMAGE, IMAGE,
            FileCategory.SKIP, SKIP);

    private SupportedExtensions() {
    }

    /**
     * Returns the category for a normalized extension, or {@link FileCategory#SKIP} when unknown.
     *
     * @param extension lowercase extension without leading dot; {@code null} maps to SKIP
     * @return assigned category
     */
    public static FileCategory categoryForExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return FileCategory.SKIP;
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (TEXT.contains(normalized)) {
            return FileCategory.TEXT;
        }
        if (DOCUMENT.contains(normalized)) {
            return FileCategory.DOCUMENT;
        }
        if (IMAGE.contains(normalized)) {
            return FileCategory.IMAGE;
        }
        if (SKIP.contains(normalized)) {
            return FileCategory.SKIP;
        }
        return FileCategory.SKIP;
    }

    /**
     * Returns the immutable extension set for a category.
     *
     * @param category file category
     * @return extensions assigned to the category
     */
    public static Set<String> extensionsFor(FileCategory category) {
        return BY_CATEGORY.getOrDefault(category, Set.of());
    }

    /**
     * Returns whether the extension is an approved TEXT scan target.
     *
     * @param extension lowercase extension without leading dot
     * @return {@code true} when the extension is TEXT
     */
    public static boolean isTextExtension(String extension) {
        return extension != null && TEXT.contains(extension.toLowerCase(Locale.ROOT));
    }
}
