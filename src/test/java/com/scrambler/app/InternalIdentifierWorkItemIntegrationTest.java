package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportReader;
import com.scrambler.unmasking.MappingLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalIdentifierWorkItemIntegrationTest {

    @Test
    void masksInternalIdentifiersAndWorkItemsInArchive(@TempDir Path tempDir) throws Exception {
        String original = """
                employeeId=E123456
                banId=BAN123456
                orderId=ORD123456
                ticket=INC0012345
                story=ENG-1234
                """;
        Path repoZip = tempDir.resolve("repo.zip");
        createZip(repoZip, Map.of("config/ids.properties", original));

        int exitCode = new MaskingApplication(ScramblerConfig.builder().workspaceBasePath(tempDir).build())
                .run(new String[]{repoZip.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);

        Path maskedZip = tempDir.resolve("repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        String masked = readZipEntry(maskedZip, "repo/config/ids.properties");

        assertFalse(masked.contains("E123456"));
        assertFalse(masked.contains("BAN123456"));
        assertFalse(masked.contains("INC0012345"));
        assertFalse(masked.contains("ENG-1234"));
        assertTrue(masked.contains("orderId=ORD123456"));
        assertTrue(masked.startsWith("employeeId="));
        assertTrue(masked.contains("\nbanId="));

        List<EntityReportRecord> records = new MappingLoader().load(reportPath);
        assertEquals(4, records.size());
        assertTrue(records.stream().anyMatch(record ->
                record.getEntityType() == EntityType.INTERNAL_IDENTIFIER
                        && "E123456".equals(record.getOriginalValue())));
        assertTrue(records.stream().anyMatch(record ->
                record.getEntityType() == EntityType.INTERNAL_IDENTIFIER
                        && "BAN123456".equals(record.getOriginalValue())));
        assertTrue(records.stream().anyMatch(record ->
                record.getEntityType() == EntityType.WORK_ITEM_ID
                        && "INC0012345".equals(record.getOriginalValue())));
        assertTrue(records.stream().anyMatch(record ->
                record.getEntityType() == EntityType.WORK_ITEM_ID
                        && "ENG-1234".equals(record.getOriginalValue())));
        assertTrue(records.stream().noneMatch(record -> "ORD123456".equals(record.getOriginalValue())));
    }

    @Test
    void doesNotDetectNonAllowlistedJiraKeysOrStandaloneInternalIds(@TempDir Path tempDir) throws Exception {
        String original = """
                E123456
                ABC-123
                TEST-456
                orderId=ORD123456
                """;
        Path repoZip = tempDir.resolve("repo.zip");
        createZip(repoZip, Map.of("config/ids.properties", original));

        new MaskingApplication(ScramblerConfig.builder().workspaceBasePath(tempDir).build())
                .run(new String[]{repoZip.toString()});

        Path maskedZip = tempDir.resolve("repo.zip");
        List<EntityReportRecord> records = new MappingLoader().load(tempDir.resolve(ReportSchema.REPORT_FILENAME));
        assertTrue(records.isEmpty());
        assertEquals(original, readZipEntry(maskedZip, "repo/config/ids.properties"));
    }

    @Test
    void roundtripMaskAndUnmaskInternalIdentifiersAndWorkItems(@TempDir Path tempDir) throws Exception {
        String original = """
                employeeId=E123456
                banId=BAN123456
                ticket=INC0012345
                story=ENG-1234
                """;
        Path repoZip = tempDir.resolve("repo.zip");
        createZip(repoZip, Map.of("config/ids.properties", original));

        ScramblerConfig config = ScramblerConfig.builder().workspaceBasePath(tempDir).build();
        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(config).run(new String[]{repoZip.toString()}));

        Path maskedZip = tempDir.resolve("repo.zip");
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);

        int restoreExit = new UnmaskingApplication(config).run(new String[]{
                maskedZip.toString(),
                reportPath.toString()
        });

        assertEquals(UnmaskingApplication.EXIT_SUCCESS, restoreExit);
        assertEquals(original, readZipEntry(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME), "repo/config/ids.properties"));
    }

    @Test
    void entityReportContainsExpectedEntityTypes(@TempDir Path tempDir) throws Exception {
        String original = """
                employeeId=E123456
                banId=BAN123456
                ticket=INC0012345
                story=ENG-1234
                """;
        Path repoZip = tempDir.resolve("repo.zip");
        createZip(repoZip, Map.of("config/ids.properties", original));
        new MaskingApplication(ScramblerConfig.builder().workspaceBasePath(tempDir).build())
                .run(new String[]{repoZip.toString()});

        List<EntityReportRecord> records = new XlsxReportReader()
                .read(tempDir.resolve(ReportSchema.REPORT_FILENAME));

        EntityReportRecord employee = findRecord(records, EntityType.INTERNAL_IDENTIFIER, "E123456");
        EntityReportRecord ban = findRecord(records, EntityType.INTERNAL_IDENTIFIER, "BAN123456");
        EntityReportRecord incident = findRecord(records, EntityType.WORK_ITEM_ID, "INC0012345");
        EntityReportRecord jira = findRecord(records, EntityType.WORK_ITEM_ID, "ENG-1234");

        assertFalse(employee.getMaskedValue().isBlank());
        assertFalse(ban.getMaskedValue().isBlank());
        assertFalse(incident.getMaskedValue().isBlank());
        assertFalse(jira.getMaskedValue().isBlank());
        assertNotEquals(employee.getMaskedValue(), ban.getMaskedValue());
        assertNotEquals(incident.getMaskedValue(), jira.getMaskedValue());
    }

    private static EntityReportRecord findRecord(List<EntityReportRecord> records, EntityType type, String original) {
        return records.stream()
                .filter(record -> record.getEntityType() == type && original.equals(record.getOriginalValue()))
                .findFirst()
                .orElseThrow();
    }

    private static void createZip(Path zipPath, Map<String, String> entries) throws Exception {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
    }

    private static byte[] readZipEntryBytes(Path zipPath, String entryName) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return zipInputStream.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("ZIP entry not found: " + entryName);
    }

    private static String readZipEntry(Path zipPath, String entryName) throws Exception {
        return new String(readZipEntryBytes(zipPath, entryName), StandardCharsets.UTF_8);
    }
}
