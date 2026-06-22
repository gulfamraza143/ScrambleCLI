package com.scrambler.security;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolicLinkGuardTest {

    @Test
    void detectsUnixSymlinkInZipEntry() {
        ZipArchiveEntry entry = new ZipArchiveEntry("secret.txt");
        entry.setUnixMode(UnixStat.LINK_FLAG | UnixStat.DEFAULT_LINK_PERM);

        assertTrue(SymbolicLinkGuard.isZipSymlink(entry));
    }

    @Test
    void ignoresRegularZipEntry() {
        ZipArchiveEntry entry = new ZipArchiveEntry("App.java");
        entry.setUnixMode(UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM);

        assertFalse(SymbolicLinkGuard.isZipSymlink(entry));
    }

    @Test
    void detectsTarSymlinkEntry() {
        TarArchiveEntry entry = new TarArchiveEntry("secret.txt", TarArchiveEntry.LF_SYMLINK);
        entry.setLinkName("/etc/passwd");

        assertTrue(SymbolicLinkGuard.isTarSymlink(entry));
    }

    @Test
    void ignoresRegularTarEntry() {
        TarArchiveEntry entry = new TarArchiveEntry("App.java");

        assertFalse(SymbolicLinkGuard.isTarSymlink(entry));
    }
}
