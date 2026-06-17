package com.scrambler.classify;

import com.scrambler.inventory.FileInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Assigns a {@link FileCategory} to inventoried files using repository-relative extension rules.
 */
public final class FileClassifier {

    private static final Map<String, FileCategory> EXTENSIONS = Map.ofEntries(
            Map.entry("java", FileCategory.TEXT),
            Map.entry("py", FileCategory.TEXT),
            Map.entry("js", FileCategory.TEXT),
            Map.entry("ts", FileCategory.TEXT),
            Map.entry("go", FileCategory.TEXT),
            Map.entry("cpp", FileCategory.TEXT),
            Map.entry("c", FileCategory.TEXT),
            Map.entry("cs", FileCategory.TEXT),
            Map.entry("kt", FileCategory.TEXT),
            Map.entry("yml", FileCategory.TEXT),
            Map.entry("yaml", FileCategory.TEXT),
            Map.entry("properties", FileCategory.TEXT),
            Map.entry("json", FileCategory.TEXT),
            Map.entry("xml", FileCategory.TEXT),
            Map.entry("sql", FileCategory.TEXT),
            Map.entry("csv", FileCategory.TEXT),
            Map.entry("txt", FileCategory.TEXT),
            Map.entry("md", FileCategory.TEXT),
            Map.entry("log", FileCategory.TEXT),
            Map.entry("env", FileCategory.TEXT),
            Map.entry("pem", FileCategory.TEXT),
            Map.entry("key", FileCategory.TEXT),
            Map.entry("crt", FileCategory.TEXT),
            Map.entry("p12", FileCategory.TEXT),

            Map.entry("pdf", FileCategory.DOCUMENT),
            Map.entry("doc", FileCategory.DOCUMENT),
            Map.entry("docx", FileCategory.DOCUMENT),
            Map.entry("xls", FileCategory.DOCUMENT),
            Map.entry("xlsx", FileCategory.DOCUMENT),
            Map.entry("ppt", FileCategory.DOCUMENT),
            Map.entry("pptx", FileCategory.DOCUMENT),

            Map.entry("png", FileCategory.IMAGE),
            Map.entry("jpg", FileCategory.IMAGE),
            Map.entry("jpeg", FileCategory.IMAGE),
            Map.entry("gif", FileCategory.IMAGE),
            Map.entry("bmp", FileCategory.IMAGE),
            Map.entry("webp", FileCategory.IMAGE),

            Map.entry("jar", FileCategory.SKIP),
            Map.entry("war", FileCategory.SKIP),
            Map.entry("ear", FileCategory.SKIP),
            Map.entry("class", FileCategory.SKIP),
            Map.entry("exe", FileCategory.SKIP),
            Map.entry("dll", FileCategory.SKIP),
            Map.entry("so", FileCategory.SKIP),
            Map.entry("dylib", FileCategory.SKIP),
            Map.entry("bin", FileCategory.SKIP),
            Map.entry("7z", FileCategory.SKIP),
            Map.entry("rar", FileCategory.SKIP),
            Map.entry("tar", FileCategory.SKIP),
            Map.entry("gz", FileCategory.SKIP)
    );

    /**
     * Classifies an inventoried file by repository-relative extension.
     *
     * @param fileInfo inventoried file metadata
     * @return classification result containing the file and assigned category
     */
    public ClassificationResult classify(FileInfo fileInfo) {
        Objects.requireNonNull(fileInfo, "fileInfo must not be null");
        FileCategory category = classifyExtension(fileInfo.getRepoRelativePath());
        return new ClassificationResult(fileInfo, category);
    }

    private static FileCategory classifyExtension(String repoRelativePath) {
        String extension = extractExtension(repoRelativePath);
        if (extension == null) {
            return FileCategory.TEXT;
        }
        return EXTENSIONS.getOrDefault(extension, FileCategory.TEXT);
    }

    private static String extractExtension(String repoRelativePath) {
        int lastDot = repoRelativePath.lastIndexOf('.');
        if (lastDot < 0 || lastDot == repoRelativePath.length() - 1) {
            return null;
        }
        return repoRelativePath.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
