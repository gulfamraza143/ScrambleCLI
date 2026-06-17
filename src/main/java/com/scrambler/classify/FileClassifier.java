package com.scrambler.classify;

import com.scrambler.config.SupportedExtensions;
import com.scrambler.inventory.FileInfo;

import java.util.Locale;
import java.util.Objects;

/**
 * Assigns a {@link FileCategory} to inventoried files using a strict extension whitelist.
 * Unknown extensions and extensionless paths are classified as SKIP.
 */
public final class FileClassifier {

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
        return SupportedExtensions.categoryForExtension(extension);
    }

    private static String extractExtension(String repoRelativePath) {
        int lastDot = repoRelativePath.lastIndexOf('.');
        if (lastDot < 0 || lastDot == repoRelativePath.length() - 1) {
            return null;
        }
        return repoRelativePath.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
