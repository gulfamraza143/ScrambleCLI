package com.scrambler.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsMetadataGuardTest {

    @Test
    void detectsMacOsXDirectorySegment() {
        assertTrue(OsMetadataGuard.isOsMetadata(Path.of("repo", "__MACOSX", "file.txt")));
        assertTrue(OsMetadataGuard.isOsMetadataEntryName("fxtp-develop/__MACOSX/._pom.xml"));
    }

    @Test
    void detectsAppleDoubleFilePrefix() {
        assertTrue(OsMetadataGuard.isOsMetadata(Path.of("repo", "._pre-commit-config.yaml")));
        assertTrue(OsMetadataGuard.isOsMetadataEntryName("fxtp-develop/._README.md"));
    }

    @Test
    void detectsDsStoreAndThumbsDb() {
        assertTrue(OsMetadataGuard.isOsMetadata(Path.of("repo", ".DS_Store")));
        assertTrue(OsMetadataGuard.isOsMetadata(Path.of("repo", "Thumbs.db")));
        assertTrue(OsMetadataGuard.isOsMetadataEntryName("assets/.DS_Store"));
        assertTrue(OsMetadataGuard.isOsMetadataEntryName("docs/Thumbs.db"));
    }

    @Test
    void ignoresRegularRepositoryPaths() {
        assertFalse(OsMetadataGuard.isOsMetadata(Path.of("fxtp-develop", "src", "app.py")));
        assertFalse(OsMetadataGuard.isOsMetadata(Path.of("fxtp-develop", ".pre-commit-config.yaml")));
        assertFalse(OsMetadataGuard.isOsMetadataEntryName("fxtp-develop/pom.xml"));
        assertFalse(OsMetadataGuard.isOsMetadataEntryName("fxtp-develop/README.md"));
    }

    @Test
    void logsWarningWhenSkippingMetadata(@TempDir Path tempDir) throws Exception {
        Path metadataFile = tempDir.resolve("._secret.yaml");
        Files.writeString(metadataFile, "binary");

        String logOutput = captureStderr(() ->
                assertTrue(OsMetadataGuard.skipIfOsMetadata(metadataFile, "._secret.yaml")));

        assertTrue(logOutput.contains("WARN Skipping OS metadata: ._secret.yaml"));
    }

    private static String captureStderr(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(originalErr);
        }
    }
}
