package com.scrambler.path;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathEntityReportIntegrationTest {

    @Test
    void maskedArchiveEmbedsPathEntityRowsInEntityReport(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/ICICI_Config/ICICI_Secrets.txt", "admin@icici.com\n",
                "ICICI_CODE_BANK/README.md", "# docs\n"));

        int exitCode = new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        assertEquals(0, exitCode);

        Path outputZip = findMaskedZip(tempDir);
        String repoToken = outputZip.getFileName().toString().replace(".zip", "");
        Path reportPath = extractReport(outputZip, tempDir);

        List<EntityReportRecord> records = new XlsxReportReader().read(reportPath);

        EntityReportRecord repositoryRow = findPathRecord(records, EntityType.REPOSITORY_NAME, "ICICI_CODE_BANK");
        EntityReportRecord folderRow = findPathRecord(records, EntityType.FOLDER_NAME, "ICICI_Config");
        EntityReportRecord fileRow = findPathRecord(records, EntityType.FILE_NAME, "ICICI_Secrets.txt");

        assertEquals(repoToken, repositoryRow.getMaskedValue());
        assertEquals(".", repositoryRow.getRepoRelativePath());
        assertEquals(0, repositoryRow.getStartOffset());
        assertEquals(0, repositoryRow.getEndOffset());

        assertTrue(folderRow.getMaskedValue().matches("[0-9A-F]{8}"));
        assertEquals(".", folderRow.getRepoRelativePath());

        assertTrue(fileRow.getMaskedValue().matches("[0-9A-F]{8}\\.txt"));
        assertEquals(".", fileRow.getRepoRelativePath());

        assertTrue(records.stream().anyMatch(record -> record.getEntityType() == EntityType.EMAIL));
    }

    @Test
    void nestedIdenticalSegmentsProduceConsistentPathRows(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/ICICI_CODE_BANK/ICICI_CODE_BANK.txt", "hello\n"));

        new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        Path outputZip = findMaskedZip(tempDir);
        String repoToken = outputZip.getFileName().toString().replace(".zip", "");

        List<EntityReportRecord> records = new XlsxReportReader().read(extractReport(outputZip, tempDir));

        assertEquals(
                repoToken,
                findPathRecord(records, EntityType.REPOSITORY_NAME, "ICICI_CODE_BANK").getMaskedValue());
        assertEquals(
                repoToken,
                findPathRecord(records, EntityType.FOLDER_NAME, "ICICI_CODE_BANK").getMaskedValue());
        assertEquals(
                repoToken + ".txt",
                findPathRecord(records, EntityType.FILE_NAME, "ICICI_CODE_BANK.txt").getMaskedValue());
    }

    private static EntityReportRecord findPathRecord(
            List<EntityReportRecord> records,
            EntityType entityType,
            String original) {
        EntityReportRecord record = records.stream()
                .filter(row -> row.getEntityType() == entityType && row.getOriginalValue().equals(original))
                .findFirst()
                .orElse(null);
        assertNotNull(record, "Missing report row for " + entityType + " / " + original);
        return record;
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static Path findMaskedZip(Path tempDir) throws IOException {
        try (var paths = Files.list(tempDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".zip")
                            && !path.getFileName().toString().equals("ICICI_CODE_BANK.zip"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Masked output ZIP not found in " + tempDir));
        }
    }

    private static Path extractReport(Path zipPath, Path tempDir) throws IOException {
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(ReportSchema.REPORT_FILENAME)) {
                    Files.write(reportPath, zipInputStream.readAllBytes());
                    return reportPath;
                }
            }
        }
        throw new IOException("Report not found in " + zipPath);
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
