package com.scrambler.replacement;

import com.scrambler.exception.FileProcessingException;
import com.scrambler.inventory.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Overwrites inventoried binary files with bundled placeholder assets.
 */
public final class BinaryPlaceholderCopier {

    private final PlaceholderAssetProvider placeholderAssetProvider;

    /**
     * Creates a copier using the default placeholder asset provider.
     */
    public BinaryPlaceholderCopier() {
        this(new PlaceholderAssetProvider());
    }

    /**
     * Creates a copier with the supplied placeholder asset provider.
     *
     * @param placeholderAssetProvider provider for bundled placeholder bytes
     */
    public BinaryPlaceholderCopier(PlaceholderAssetProvider placeholderAssetProvider) {
        this.placeholderAssetProvider = Objects.requireNonNull(
                placeholderAssetProvider, "placeholderAssetProvider must not be null");
    }

    /**
     * Replaces the target file content with the placeholder bytes for its extension.
     * The original filename and repository-relative path are preserved.
     *
     * @param fileInfo inventoried file metadata
     * @throws FileProcessingException when replacement fails
     */
    public void replace(FileInfo fileInfo) {
        Objects.requireNonNull(fileInfo, "fileInfo must not be null");
        String extension = extractExtension(fileInfo.getRepoRelativePath());
        byte[] placeholderBytes = placeholderAssetProvider.loadPlaceholder(extension);
        Path targetPath = fileInfo.getAbsolutePath();
        try {
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(targetPath, placeholderBytes);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to replace file with placeholder: " + targetPath, e);
        }
    }

    private static String extractExtension(String repoRelativePath) {
        int lastDot = repoRelativePath.lastIndexOf('.');
        if (lastDot < 0 || lastDot == repoRelativePath.length() - 1) {
            throw new FileProcessingException("Cannot resolve extension for replacement target: " + repoRelativePath);
        }
        return repoRelativePath.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
