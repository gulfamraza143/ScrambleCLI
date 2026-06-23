package com.scrambler.app;

import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileCategory;
import com.scrambler.classify.FileClassifier;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import com.scrambler.report.TestReportWriter;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnmaskingApplicationIntegrationTest {

    @Test
    void restoresMaskedZipAndReportToOriginalZip(@TempDir Path tempDir) throws Exception {
        String originalContent = "contact: admin@icici.com\n";
        String maskedContent = "contact: EMAIL_000001\n";

        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("config/app.txt", maskedContent));
        writeReport(reportPath, List.of(
                "config/app.txt,EMAIL,admin@icici.com,EMAIL_000001,9,24"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path outputZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertTrue(Files.isRegularFile(outputZip));
        assertEquals(originalContent, readZipEntry(outputZip, "config/app.txt"));
    }

    @Test
    void restoresMultipleTextFiles(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of(
                "src/a.txt", "email: EMAIL_000001\n",
                "src/b.txt", "url: URL_000001\n"));
        writeReport(reportPath, List.of(
                "src/a.txt,EMAIL,admin@icici.com,EMAIL_000001,7,22",
                "src/b.txt,URL,https://internal.icici.com,URL_000001,5,30"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path outputZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertEquals("email: admin@icici.com\n", readZipEntry(outputZip, "src/a.txt"));
        assertEquals("url: https://internal.icici.com\n", readZipEntry(outputZip, "src/b.txt"));
    }

    @Test
    void leavesNonTextFilesUnchanged(@TempDir Path tempDir) throws Exception {
        byte[] imageBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of(
                "docs/readme.txt", "value: EMAIL_000001\n",
                "assets/logo.png", imageBytes));
        writeReport(reportPath, List.of(
                "docs/readme.txt,EMAIL,admin@icici.com,EMAIL_000001,7,22"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path outputZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertEquals("value: admin@icici.com\n", readZipEntry(outputZip, "docs/readme.txt"));
        assertArrayEquals(imageBytes, readZipEntryBytes(outputZip, "assets/logo.png"));
    }

    @Test
    void failsWhenReportFileIsMissing(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        createZip(maskedZip, Map.of("app.txt", "contact: EMAIL_000001\n"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                tempDir.resolve("missing_report.csv").toString()
        });

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    @Test
    void failsOnUnsupportedReportVersion(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("app.txt", "contact: EMAIL_000001\n"));
        Files.writeString(tempDir.resolve("unsupported.csv"), """
                report_version,3.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                app.txt,EMAIL,admin@icici.com,EMAIL_000001,9,24
                """, StandardCharsets.UTF_8);

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                tempDir.resolve("unsupported.csv").toString()
        });

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    @Test
    void failsOnOrphanToken(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("app.txt", "contact: EMAIL_000999\n"));
        writeReport(reportPath, List.of(
                "app.txt,EMAIL,admin@icici.com,EMAIL_000001,9,24"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    @Test
    void restoresRepositoryWithNoMaskedTokens(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("notes.txt", "plain repository content\n"));
        writeReport(reportPath, List.of());

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path outputZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertEquals("plain repository content\n", readZipEntry(outputZip, "notes.txt"));
    }

    @Test
    void cleansUpWorkspaceAfterRun(@TempDir Path tempDir) throws Exception {
        Path workspaceBase = tempDir.resolve("workspaces");
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("app.txt", "contact: EMAIL_000001\n"));
        writeReport(reportPath, List.of(
                "app.txt,EMAIL,admin@icici.com,EMAIL_000001,9,24"));

        int exitCode = new UnmaskingApplication(configFor(workspaceBase)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        try (Stream<Path> paths = Files.list(workspaceBase)) {
            assertTrue(paths.toList().isEmpty());
        }
    }

    @Test
    void createsOutputZipWithPreservedStructure(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of(
                "nested/path/app.txt", "email: EMAIL_000001\n",
                "nested/path/config.json", "{\"token\":\"EMAIL_000002\"}\n"));
        writeReport(reportPath, List.of(
                "nested/path/app.txt,EMAIL,admin@icici.com,EMAIL_000001,7,22",
                "nested/path/config.json,EMAIL,secret@icici.com,EMAIL_000002,10,25"));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path outputZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        assertEquals("email: admin@icici.com\n", readZipEntry(outputZip, "nested/path/app.txt"));
        assertEquals("{\"token\":\"secret@icici.com\"}\n", readZipEntry(outputZip, "nested/path/config.json"));
        assertEquals(List.of("nested/path/app.txt", "nested/path/config.json"), listZipEntries(outputZip));
    }

    @Test
    void roundtripMaskReportUnmaskRestoresOriginalRepository(@TempDir Path tempDir) throws Exception {
        Map<String, String> originalFiles = new LinkedHashMap<>();
        originalFiles.put("config/application.yml", """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                """);
        originalFiles.put("docs/readme.txt", "Support: support@icici.com\n");

        Path originalZip = tempDir.resolve("repo.zip");
        createZip(originalZip, originalFiles);

        MaskedRepository maskedRepository = maskRepository(originalFiles);
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        createZip(maskedZip, maskedRepository.maskedFiles());
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        new com.scrambler.report.XlsxReportWriter().write(maskedRepository.mappingRegistry(), reportPath);

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        Path restoredZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            assertEquals(entry.getValue(), readZipEntry(restoredZip, entry.getKey()),
                    "Mismatch for " + entry.getKey());
        }
    }

    @Test
    void rejectsInvalidUsage(@TempDir Path tempDir) {
        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{});
        assertEquals(UnmaskingApplication.EXIT_INVALID_USAGE, exitCode);
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static void createZip(Path zipPath, Map<String, ?> entries) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof byte[] bytes) {
                    zipOutputStream.write(bytes);
                } else {
                    zipOutputStream.write(value.toString().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
        }
    }

    private static void writeReport(Path reportPath, List<String> dataRows) throws IOException {
        List<com.scrambler.masking.MappingRecord> rows = new java.util.ArrayList<>();
        for (String row : dataRows) {
            String[] parts = row.split(",", -1);
            rows.add(TestReportWriter.record(
                    parts[0],
                    com.scrambler.detection.EntityType.valueOf(parts[1]),
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])));
        }
        TestReportWriter.writeXlsx(reportPath, rows);
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

    private static MaskedRepository maskRepository(Map<String, String> originalFiles) {
        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();
        Map<String, String> maskedFiles = new LinkedHashMap<>();
        FileClassifier fileClassifier = new FileClassifier();

        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            FileInfo fileInfo = new FileInfo(Path.of("/workspace/" + entry.getKey()), entry.getKey(), entry.getValue().length());
            ClassificationResult classification = fileClassifier.classify(fileInfo);
            if (classification.getCategory() != FileCategory.TEXT) {
                maskedFiles.put(entry.getKey(), entry.getValue());
                continue;
            }
            DetectionResult detectionResult = detectionEngine.detect(new DetectionContext(fileInfo, entry.getValue()));
            String maskedContent = maskingEngine.mask(entry.getValue(), detectionResult, mappingRegistry);
            maskedFiles.put(entry.getKey(), maskedContent);
        }

        return new MaskedRepository(maskedFiles, mappingRegistry);
    }

    private record MaskedRepository(Map<String, String> maskedFiles, MappingRegistry mappingRegistry) {
    }
}
