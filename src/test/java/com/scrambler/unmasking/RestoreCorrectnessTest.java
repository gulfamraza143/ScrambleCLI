package com.scrambler.unmasking;

import com.scrambler.app.MaskingApplication;
import com.scrambler.app.UnmaskingApplication;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.EntityType;
import com.scrambler.exception.MaskingException;
import com.scrambler.exception.ReportException;
import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import com.scrambler.masking.TokenFormatSpec;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.TestReportWriter;
import com.scrambler.report.XlsxReportWriter;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreCorrectnessTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            256L);

    private DetectionEngine detectionEngine;
    private MaskingEngine maskingEngine;
    private UnmaskingEngine unmaskingEngine;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
        maskingEngine = new MaskingEngine();
        unmaskingEngine = new UnmaskingEngine();
    }

    @Test
    void restoresDatabaseUrlWithoutCorruptingViaUrlSubstring() throws Exception {
        String original = """
                primary=jdbc:postgresql://db.icici.internal:5432/loan
                docs=https://internal.icici.com
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void preservesEmailLiteralCollisionsInSource() throws Exception {
        String original = """
                contact=admin@icici.com
                // EMAIL_000001 placeholder token for collision testing
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void preservesPasswordLiteralCollisionsInSource() throws Exception {
        String original = """
                password=hunter2
                // PASSWORD_000001 placeholder token for collision testing
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void preservesJwtLiteralCollisionsInSource() throws Exception {
        String original = """
                token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.sig
                // JWT_000001 placeholder token for collision testing
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void preservesUrlLiteralCollisionsInSource() throws Exception {
        String original = """
                portal=https://internal.icici.com
                // URL_000001 placeholder token for collision testing
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void roundtripWithNestedFolders() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("nested/level1/app.yml", "email=admin@icici.com\n");
        files.put("nested/level2/db.properties", "jdbc.url=jdbc:postgresql://db.icici.internal:5432/loan\n");

        assertRepositoryRoundtrip(files);
    }

    @Test
    void roundtripWithRepeatedValues() throws Exception {
        String original = "first=admin@icici.com second=admin@icici.com\n";
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void roundtripWithDuplicateValuesAcrossFiles() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("a.txt", "email=admin@icici.com\n");
        files.put("b.txt", "email=admin@icici.com\n");

        assertRepositoryRoundtrip(files);
    }

    @Test
    void rejectsBlankOriginalValue() {
        ReportException exception = assertThrows(
                ReportException.class,
                () -> new RestoreValidator().validate(List.of(record(
                        "a.txt", EntityType.EMAIL, "   ", TokenFormatSpec.format(EntityType.EMAIL, 1), 0, 7))));
        assertTrue(exception.getMessage().contains("original_value is missing"));
    }

    @Test
    void rejectsBlankOriginalValueViaCsv(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("tampered.csv");
        writeReport(reportPath, List.of(
                "a.txt,EMAIL,   ," + TokenFormatSpec.format(EntityType.EMAIL, 1) + ",0,15"));

        ReportException exception = assertThrows(
                ReportException.class,
                () -> new RestoreValidator().validate(new MappingLoader().load(reportPath)));
        assertTrue(exception.getMessage().contains("original_value is missing"));
    }

    @Test
    void rejectsBlankMaskedValueViaCsv(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("invalid.csv");
        Files.writeString(reportPath, """
                report_version,1.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                a.txt,EMAIL,admin@icici.com,   ,0,15
                """, StandardCharsets.UTF_8);

        ReportException exception = assertThrows(
                ReportException.class,
                () -> new MappingLoader().load(reportPath));
        assertTrue(exception.getMessage().contains("Invalid entity report row")
                || exception.getMessage().contains("masked_value"));
    }

    @Test
    void rejectsBlankRepoRelativePathViaCsv(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("invalid.csv");
        Files.writeString(reportPath, """
                report_version,1.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                   ,EMAIL,admin@icici.com,EMAIL_000001,0,15
                """, StandardCharsets.UTF_8);

        ReportException exception = assertThrows(
                ReportException.class,
                () -> new RestoreValidator().validate(new MappingLoader().load(reportPath)));
        assertTrue(exception.getMessage().contains("repo_relative_path is missing")
                || exception.getMessage().contains("repoRelativePath must not be blank")
                || exception.getMessage().contains("Invalid entity report row"));
    }

    @Test
    void legacyReportStillRestoresWithLongestMatchFirst() throws Exception {
        MappingIndex index = MappingIndex.from(List.of(
                record("db.properties", EntityType.DATABASE_URL,
                        "jdbc:postgresql://db.icici.internal:5432/loan", "DATABASE_URL_000001", 9, 54),
                record("app.yml", EntityType.URL,
                        "https://internal.icici.com", "URL_000001", 8, 33)));

        String masked = """
                jdbc.url=DATABASE_URL_000001
                portal=URL_000001
                """;
        String restored = unmaskingEngine.unmask(masked, index, null);

        assertEquals("""
                jdbc.url=jdbc:postgresql://db.icici.internal:5432/loan
                portal=https://internal.icici.com
                """, restored);
    }

    @Test
    void legacyOrphanDetectionStillFailsForUnknownLegacyToken() {
        MappingIndex index = MappingIndex.from(List.of(
                record("a.txt", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 0, 15)));

        MaskingException exception = assertThrows(
                MaskingException.class,
                () -> unmaskingEngine.unmask("contact: EMAIL_000999", index, null));
        assertTrue(exception.getMessage().contains("EMAIL_000999"));
    }

    @Test
    void literalLegacyTokensDoNotTriggerOrphanForCurrentFormatReports() throws Exception {
        String original = """
                contact=admin@icici.com
                // EMAIL_000001 placeholder token for collision testing
                """;
        String restored = roundtrip(original);
        assertEquals(original, restored);
    }

    @Test
    void largeRepositoryRoundtrip(@TempDir Path tempDir) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        for (int index = 0; index < 50; index++) {
            files.put("files/file-" + index + ".txt",
                    "email=user" + index + "@icici.com url=https://svc" + index + ".icici.com\n");
        }
        assertRepositoryRoundtrip(files);
    }

    private String roundtrip(String original) throws Exception {
        MappingRegistry mappingRegistry = new MappingRegistry();
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, original));
        String masked = maskingEngine.mask(original, detection, mappingRegistry);

        Path reportPath = Files.createTempFile("entity-report", ".xlsx");
        new XlsxReportWriter().write(mappingRegistry, reportPath);
        MappingIndex index = MappingIndex.from(new MappingLoader().load(reportPath));
        return unmaskingEngine.unmask(masked, index, null);
    }

    private void assertRepositoryRoundtrip(Map<String, String> originalFiles) throws Exception {
        MaskingEngine runEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();
        Map<String, String> maskedFiles = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            FileInfo fileInfo = new FileInfo(Path.of("/workspace/" + entry.getKey()), entry.getKey(), entry.getValue().length());
            DetectionResult detection = detectionEngine.detect(new DetectionContext(fileInfo, entry.getValue()));
            maskedFiles.put(entry.getKey(), runEngine.mask(entry.getValue(), detection, mappingRegistry));
        }

        Path tempDir = Files.createTempDirectory("restore-roundtrip");
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, maskedFiles);
        new XlsxReportWriter().write(mappingRegistry, reportPath);

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(0, exitCode);
        Path restoredZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            assertEquals(entry.getValue(), readZipEntry(restoredZip, entry.getKey()), "Mismatch for " + entry.getKey());
        }
    }

    private static EntityReportRecord record(
            String path,
            EntityType type,
            String original,
            String masked,
            int start,
            int end) {
        return new EntityReportRecord(
                ReportSchema.CURRENT_VERSION,
                path,
                type,
                original,
                masked,
                start,
                end);
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
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

    private static void writeReport(Path reportPath, List<String> dataRows) throws IOException {
        List<com.scrambler.masking.MappingRecord> rows = new java.util.ArrayList<>();
        for (String row : dataRows) {
            String[] parts = row.split(",", -1);
            rows.add(TestReportWriter.record(
                    parts[0],
                    EntityType.valueOf(parts[1]),
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])));
        }
        if (reportPath.getFileName().toString().endsWith(".csv")) {
            TestReportWriter.writeCsv(reportPath, rows);
        } else {
            TestReportWriter.writeXlsx(reportPath, rows);
        }
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
}
