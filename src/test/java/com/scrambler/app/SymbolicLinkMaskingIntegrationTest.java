package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SymbolicLinkMaskingIntegrationTest {

    @Test
    void maskingSucceedsWhenRepositoryContainsSymlinks(@TempDir Path tempDir) throws Exception {
        assumeSymbolicLinksSupported();

        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");
        Files.writeString(repoRoot.resolve("Config.yml"), "service: icici\n");

        Path sensitiveTarget = tempDir.resolve("outside-target.txt");
        Files.writeString(sensitiveTarget, "SENSITIVE-SYMLINK-TARGET-MUST-NOT-APPEAR");
        Files.createSymbolicLink(repoRoot.resolve("secret.txt"), sensitiveTarget);

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);

        Path maskedZip = tempDir.resolve(MaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertTrue(Files.isRegularFile(maskedZip));
        assertTrue(Files.isRegularFile(maskedZip.resolveSibling("entity_report.xlsx")));

        assertFalse(zipContainsEntry(maskedZip, "secret.txt"));
        String maskedJava = readZipEntry(maskedZip, "App.java");
        assertFalse(maskedJava.contains("SENSITIVE-SYMLINK-TARGET-MUST-NOT-APPEAR"));
        assertTrue(Files.isRegularFile(maskedZip.getParent().resolve("entity_report.xlsx")));
    }

    @Test
    void maskingSucceedsWhenZipArchiveContainsSymlinkEntry(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("repo.zip");
        createZipWithSymlink(
                repoZip,
                "App.java",
                "public class App { String email = \"admin@icici.com\"; }",
                "secret.txt",
                "/etc/passwd");

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);

        Path maskedZip = tempDir.resolve(MaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertFalse(zipContainsEntry(maskedZip, "secret.txt"));
        assertTrue(zipContainsEntry(maskedZip, "App.java"));
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static void createZipWithSymlink(
            Path zipPath,
            String regularName,
            String regularContent,
            String symlinkName,
            String symlinkTarget) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream =
                     new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            ZipArchiveEntry regularEntry = new ZipArchiveEntry(regularName);
            byte[] regularBytes = regularContent.getBytes(StandardCharsets.UTF_8);
            regularEntry.setSize(regularBytes.length);
            zipOutputStream.putArchiveEntry(regularEntry);
            zipOutputStream.write(regularBytes);
            zipOutputStream.closeArchiveEntry();

            ZipArchiveEntry symlinkEntry = new ZipArchiveEntry(symlinkName);
            symlinkEntry.setUnixMode(org.apache.commons.compress.archivers.zip.UnixStat.LINK_FLAG
                    | org.apache.commons.compress.archivers.zip.UnixStat.DEFAULT_LINK_PERM);
            byte[] linkBytes = symlinkTarget.getBytes(StandardCharsets.UTF_8);
            symlinkEntry.setSize(linkBytes.length);
            zipOutputStream.putArchiveEntry(symlinkEntry);
            zipOutputStream.write(linkBytes);
            zipOutputStream.closeArchiveEntry();
        }
    }

    private static boolean zipContainsEntry(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String readZipEntry(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private static void assumeSymbolicLinksSupported() {
        try {
            Path probe = Files.createTempDirectory("symlink-probe");
            Path target = probe.resolve("target.txt");
            Files.writeString(target, "probe");
            Path link = probe.resolve("link.txt");
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(probe);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "Symbolic links not supported in this environment");
        }
    }
}
