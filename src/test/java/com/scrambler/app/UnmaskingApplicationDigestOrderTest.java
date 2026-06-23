package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.report.ReportDigest;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnmaskingApplicationDigestOrderTest {

    private static final Pattern RUN_METHOD_BODY = Pattern.compile(
            "Path extractionRoot = archiveExtractor\\.extract\\(maskedZipPath, workspace\\);\\s*"
                    + "Path reportPath = resolveReportPath\\(extractionRoot, externalReportPath\\);\\s*"
                    + "verifyReportDigest\\(reportPath\\);\\s*"
                    + "List<EntityReportRecord> records = mappingLoader\\.load\\(reportPath\\);",
            Pattern.DOTALL);

    @Test
    void verifyDigestPrecedesLoadInRunMethod() throws IOException {
        Path sourcePath = Path.of("src/main/java/com/scrambler/app/UnmaskingApplication.java");
        String source = Files.readString(sourcePath);

        assertTrue(
                RUN_METHOD_BODY.matcher(source).find(),
                "verifyReportDigest(reportPath) must execute before mappingLoader.load(reportPath)");
    }

    @Test
    void restoresWithValidXlsxDigest(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("app.txt", "contact: waypoint310@example.com\n"));
        writeXlsxReport(reportPath, List.of(
                mappingRecord("app.txt", EntityType.EMAIL, "admin@icici.com", "waypoint310@example.com", 9, 32)));
        ReportDigest.write(reportPath, tempDir.resolve(ReportDigest.DIGEST_FILENAME));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    @Test
    void tamperedXlsxAbortsBeforeWorkbookLoad(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        createZip(maskedZip, Map.of("app.txt", "contact: waypoint310@example.com\n"));
        writeXlsxReport(reportPath, List.of(
                mappingRecord("app.txt", EntityType.EMAIL, "admin@icici.com", "waypoint310@example.com", 9, 32)));
        ReportDigest.write(reportPath, tempDir.resolve(ReportDigest.DIGEST_FILENAME));

        byte[] intactReportBytes = Files.readAllBytes(reportPath);
        Files.write(reportPath, tamperReportBytes(intactReportBytes));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
        assertArrayEquals(
                Files.readAllBytes(reportPath),
                tamperReportBytes(intactReportBytes),
                "Tampered report bytes must remain unchanged when digest verification fails before load");
    }

    @Test
    void wrongDigestSidecarAbortsRestore(@TempDir Path tempDir) throws Exception {
        Path maskedZip = tempDir.resolve("masked_code.zip");
        Path reportPath = tempDir.resolve("entity_report.xlsx");
        Path otherReportPath = tempDir.resolve("entity_report_other.xlsx");
        createZip(maskedZip, Map.of("app.txt", "contact: waypoint310@example.com\n"));
        writeXlsxReport(reportPath, List.of(
                mappingRecord("app.txt", EntityType.EMAIL, "admin@icici.com", "waypoint310@example.com", 9, 32)));
        writeXlsxReport(otherReportPath, List.of(
                mappingRecord("app.txt", EntityType.EMAIL, "admin@icici.com", "stalwart252@example.com", 9, 31)));
        ReportDigest.write(otherReportPath, tempDir.resolve(ReportDigest.DIGEST_FILENAME));

        int exitCode = new UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME)));
    }

    private static byte[] tamperReportBytes(byte[] originalBytes) {
        byte[] tampered = originalBytes.clone();
        tampered[tampered.length / 2] ^= 0x01;
        return tampered;
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static MappingRecord mappingRecord(
            String repoRelativePath,
            EntityType entityType,
            String originalValue,
            String maskedValue,
            int startOffset,
            int endOffset) {
        return new MappingRecord(
                repoRelativePath,
                entityType,
                originalValue,
                maskedValue,
                startOffset,
                endOffset);
    }

    private static void writeXlsxReport(Path reportPath, List<MappingRecord> records) {
        MappingRegistry mappingRegistry = new MappingRegistry();
        for (MappingRecord record : records) {
            mappingRegistry.register(record);
        }
        new XlsxReportWriter().write(mappingRegistry, reportPath);
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
}
