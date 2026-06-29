package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingApplicationOsMetadataIntegrationTest {

    @Test
    void completesMaskingWhileIgnoringOsMetadataAndBinaryTextFiles(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("fxtp-develop.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(repoZip))) {
            writeEntry(zipOutputStream, "fxtp-develop/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "fxtp-develop/README.md", "# ICICI docs\n".getBytes(StandardCharsets.UTF_8));
            writeEntry(
                    zipOutputStream,
                    "fxtp-develop/.pre-commit-config.yaml",
                    "repos: []\n".getBytes(StandardCharsets.UTF_8));
            writeEntry(
                    zipOutputStream,
                    "fxtp-develop/binary-but-yaml.yaml",
                    new byte[]{(byte) 0x00, (byte) 0x05, (byte) 0x16, (byte) 0x07});
            writeEntry(
                    zipOutputStream,
                    "fxtp-develop/__MACOSX/fxtp-develop/._pom.xml",
                    new byte[]{(byte) 0x00, (byte) 0x05, (byte) 0x16, (byte) 0x07});
            writeEntry(
                    zipOutputStream,
                    "fxtp-develop/__MACOSX/fxtp-develop/._.pre-commit-config.yaml",
                    new byte[]{(byte) 0x00, (byte) 0x05, (byte) 0x16, (byte) 0x07});
            writeEntry(
                    zipOutputStream,
                    "__MACOSX/._fxtp-develop",
                    new byte[]{(byte) 0x00, (byte) 0x05, (byte) 0x16, (byte) 0x07});
            writeEntry(zipOutputStream, "fxtp-develop/.DS_Store", "metadata".getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "fxtp-develop/Thumbs.db", "metadata".getBytes(StandardCharsets.UTF_8));
            writeEntry(
                    zipOutputStream,
                    "fxtp-develop/._README.md",
                    new byte[]{(byte) 0x00, (byte) 0x05, (byte) 0x16, (byte) 0x07});
        }

        int exitCode = new MaskingApplication(ScramblerConfig.builder().workspaceBasePath(tempDir).build())
                .run(new String[]{repoZip.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));

        Path maskedZip = tempDir.resolve("fxtp-develop.zip");
        assertTrue(Files.isRegularFile(maskedZip));

        List<String> entries = listZipEntries(maskedZip);
        assertTrue(entries.stream().anyMatch(name -> name.endsWith("fxtp-develop/pom.xml")));
        assertTrue(entries.stream().anyMatch(name -> name.endsWith("fxtp-develop/README.md")));
        assertTrue(entries.stream().anyMatch(name -> name.endsWith("fxtp-develop/.pre-commit-config.yaml")));
        assertFalse(entries.stream().anyMatch(name -> name.contains("__MACOSX")));
        assertFalse(entries.stream().anyMatch(name -> name.contains("/._")));
        assertFalse(entries.stream().anyMatch(name -> name.endsWith(".DS_Store")));
        assertFalse(entries.stream().anyMatch(name -> name.endsWith("Thumbs.db")));
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] bytes) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(bytes);
        zipOutputStream.closeEntry();
    }

    private static List<String> listZipEntries(Path zipPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }
}
