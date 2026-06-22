package com.scrambler.security;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase-1 symbolic link protection: detect symlinks and log warnings without failing processing.
 */
public final class SymbolicLinkGuard {

    private static final Logger log = LoggerFactory.getLogger(SymbolicLinkGuard.class);

    /** Windows {@code FILE_ATTRIBUTE_REPARSE_POINT}. */
    private static final int WINDOWS_REPARSE_POINT = 0x400;

    private SymbolicLinkGuard() {
    }

    /**
     * Returns {@code true} when the path is a symbolic link.
     */
    public static boolean isSymbolicLink(Path path) {
        return Files.isSymbolicLink(path);
    }

    /**
     * Logs a warning and returns {@code true} when the path is a symbolic link.
     *
     * @param path    path to inspect
     * @param logPath path label included in the warning message
     */
    public static boolean skipIfSymbolicLink(Path path, Object logPath) {
        if (!Files.isSymbolicLink(path)) {
            return false;
        }
        log.warn("Skipping symbolic link: {}", logPath);
        return true;
    }

    /**
     * Logs a warning for an archive entry path that represents a symbolic link.
     */
    public static void logSkippedArchiveSymlink(String entryPath) {
        log.warn("Skipping symbolic link: {}", entryPath);
    }

    /**
     * Returns {@code true} when a ZIP entry represents a Unix symbolic link.
     */
    public static boolean isZipSymlink(ZipArchiveEntry entry) {
        if (entry.isUnixSymlink()) {
            return true;
        }
        long externalAttributes = entry.getExternalAttributes();
        if (externalAttributes == 0L) {
            return false;
        }
        int mode = (int) ((externalAttributes >> 16) & 0xFFFF);
        return (mode & UnixStat.FILE_TYPE_FLAG) == UnixStat.LINK_FLAG;
    }

    /**
     * Returns {@code true} when a TAR entry represents a symbolic link.
     */
    public static boolean isTarSymlink(ArchiveEntry entry) {
        return entry instanceof TarArchiveEntry tarEntry && tarEntry.isSymbolicLink();
    }

    /**
     * Returns {@code true} when a 7Z entry represents a symbolic link.
     */
    public static boolean isSevenZSymlink(SevenZArchiveEntry entry) {
        return entry.getHasWindowsAttributes() && (entry.getWindowsAttributes() & WINDOWS_REPARSE_POINT) != 0;
    }
}
