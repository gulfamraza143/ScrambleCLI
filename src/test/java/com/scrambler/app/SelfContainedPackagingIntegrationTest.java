package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.report.ReportDigest;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfContainedPackagingIntegrationTest {

    @Test
    void maskingProducesSelfContainedTokenNamedZipWithoutExternalReport(@TempDir Path tempDir) throws Exception {
        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(originalZip, Map.of(
                "ICICI_CODE_BANK/ICICI_Config/secrets.txt", "admin@icici.com\n",
                "ICICI_CODE_BANK/README.md", "# docs\n"));

        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(configFor(tempDir)).run(new String[]{
                originalZip.toString()
        }));

        Path maskedZip = findMaskedZip(tempDir);
        String repoToken = maskedZip.getFileName().toString().replace(".zip", "");

        assertTrue(repoToken.matches("[0-9A-F]{8}"));
        assertFalse(Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));
        assertFalse(Files.isRegularFile(tempDir.resolve(ReportDigest.DIGEST_FILENAME)));

        List<String> entries = listZipEntries(maskedZip);
        assertTrue(entries.contains(ReportSchema.REPORT_FILENAME));
        assertTrue(entries.contains(ReportDigest.DIGEST_FILENAME));
        assertTrue(entries.contains(repoToken + "/.scramble_metadata"));
        assertTrue(entries.stream().anyMatch(entry -> entry.startsWith(repoToken + "/") && entry.endsWith("/secrets.txt")));
        assertFalse(entries.stream().anyMatch(entry -> entry.contains("ICICI")));
    }

    @Test
    void singleArgumentUnmaskRestoresOriginalRepositoryFromEmbeddedReport(@TempDir Path tempDir) throws Exception {
        Map<String, String> originalFiles = new LinkedHashMap<>();
        originalFiles.put("ICICI_CODE_BANK/ICICI_Config/secrets.txt", "admin@icici.com\n");
        originalFiles.put("ICICI_CODE_BANK/ICICI_CODE_BANK/ICICI_CODE_BANK.txt", "hello\n");
        originalFiles.put("ICICI_CODE_BANK/README.md", "# ICICI portal\n");

        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(originalZip, originalFiles);

        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(configFor(tempDir)).run(new String[]{
                originalZip.toString()
        }));

        Path maskedZip = findMaskedZip(tempDir);
        assertEquals(UnmaskingApplication.EXIT_SUCCESS, new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString()
        }));

        Path restoredZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            assertEquals(entry.getValue(), readZipEntry(restoredZip, entry.getKey()), "Mismatch for " + entry.getKey());
        }
    }

    @Test
    void legacyTwoArgumentUnmaskRemainsSupported(@TempDir Path tempDir) throws Exception {
        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(originalZip, Map.of(
                "ICICI_CODE_BANK/app.txt", "admin@icici.com\n"));

        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(configFor(tempDir)).run(new String[]{
                originalZip.toString()
        }));

        Path maskedZip = findMaskedZip(tempDir);
        Path externalReport = tempDir.resolve("external-" + ReportSchema.REPORT_FILENAME);
        extractZipEntry(maskedZip, ReportSchema.REPORT_FILENAME, externalReport);

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                externalReport.toString()
        }));

        assertEquals("admin@icici.com\n", readZipEntry(
                tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME),
                "ICICI_CODE_BANK/app.txt"));
    }

    @Test
    void tamperedEmbeddedDigestAbortsSingleArgumentUnmask(@TempDir Path tempDir) throws Exception {
        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(originalZip, Map.of("ICICI_CODE_BANK/app.txt", "admin@icici.com\n"));

        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(configFor(tempDir)).run(new String[]{
                originalZip.toString()
        }));

        Path maskedZip = findMaskedZip(tempDir);
        Path tamperedZip = tempDir.resolve("tampered.zip");
        Files.copy(maskedZip, tamperedZip);

        byte[] digestBytes = readZipEntryBytes(tamperedZip, ReportDigest.DIGEST_FILENAME);
        digestBytes[digestBytes.length / 2] ^= 0x01;
        replaceZipEntry(tamperedZip, ReportDigest.DIGEST_FILENAME, digestBytes);

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                tamperedZip.toString()
        }));
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static Path findMaskedZip(Path tempDir) throws IOException {
        try (var paths = Files.list(tempDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".zip")
                            && !path.getFileName().toString().equals("ICICI_CODE_BANK.zip")
                            && !path.getFileName().toString().equals("tampered.zip"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Masked output ZIP not found in " + tempDir));
        }
    }

    private static void createZip(Path zipPath, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
    }

    private static List<String> listZipEntries(Path zipPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            List<String> entries = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
            return entries;
        }
    }

    private static String readZipEntry(Path zipPath, String entryName) throws IOException {
        return new String(readZipEntryBytes(zipPath, entryName), StandardCharsets.UTF_8);
    }

    private static byte[] readZipEntryBytes(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return zipInputStream.readAllBytes();
                }
            }
        }
        throw new IOException("ZIP entry not found: " + entryName);
    }

    private static void extractZipEntry(Path zipPath, String entryName, Path destination) throws IOException {
        Files.write(destination, readZipEntryBytes(zipPath, entryName));
    }

    private static void replaceZipEntry(Path zipPath, String entryName, byte[] content) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zipInputStream.readAllBytes());
                }
            }
        }
        entries.put(entryName, content);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
        }
    }
}
