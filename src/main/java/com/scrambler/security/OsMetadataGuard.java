package com.scrambler.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Filters operating-system metadata that must never enter repository processing.
 * Covers macOS {@code __MACOSX} trees, AppleDouble {@code ._} files, {@code .DS_Store},
 * and Windows {@code Thumbs.db} artifacts commonly found in repository archives.
 */
public final class OsMetadataGuard {

    private static final Logger log = LoggerFactory.getLogger(OsMetadataGuard.class);

    private static final String MACOSX_DIRECTORY = "__MACOSX";
    private static final String DS_STORE = ".DS_Store";
    private static final String THUMBS_DB = "Thumbs.db";

    private OsMetadataGuard() {
    }

    /**
     * Returns {@code true} when the path is OS metadata that should be excluded from processing.
     */
    public static boolean isOsMetadata(Path path) {
        if (path == null) {
            return false;
        }
        for (Path segment : path) {
            if (MACOSX_DIRECTORY.equals(segment.toString())) {
                return true;
            }
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        return isOsMetadataFileName(fileName.toString());
    }

    /**
     * Returns {@code true} when a ZIP or archive entry name refers to OS metadata.
     */
    public static boolean isOsMetadataEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return false;
        }
        return isOsMetadata(Path.of(normalized));
    }

    /**
     * Logs a warning and returns {@code true} when the path is OS metadata.
     *
     * @param path    path to inspect
     * @param logPath path label included in the warning message
     */
    public static boolean skipIfOsMetadata(Path path, Object logPath) {
        if (!isOsMetadata(path)) {
            return false;
        }
        log.warn("Skipping OS metadata: {}", logPath);
        return true;
    }

    /**
     * Logs a warning for an archive entry that represents OS metadata.
     */
    public static void logSkippedOsMetadataEntry(String entryName) {
        log.warn("Skipping OS metadata: {}", entryName);
    }

    private static boolean isOsMetadataFileName(String fileName) {
        return fileName.startsWith("._")
                || DS_STORE.equals(fileName)
                || THUMBS_DB.equals(fileName);
    }
}
