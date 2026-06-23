package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxReportTest {

    private static final String PATH_CONTEXT = ".";

    @TempDir
    Path tempDir;

    private XlsxReportWriter writer;
    private XlsxReportReader reader;

    @BeforeEach
    void setUp() {
        writer = new XlsxReportWriter();
        reader = new XlsxReportReader();
    }

    @Test
    void writesPathEntityRowsUsingExistingSchema() throws Exception {
        MappingRegistry registry = new MappingRegistry();
        registry.register(pathRecord(EntityType.REPOSITORY_NAME, "ICICI_CODE_BANK", "A81D9F227C4B"));
        registry.register(pathRecord(EntityType.FOLDER_NAME, "ICICI_Config", "B7C91D4F88A1"));
        registry.register(pathRecord(EntityType.FILE_NAME, "ICICI_Secrets.txt", "F91A2C778D44.txt"));
        registry.register(new MappingRecord(
                "ICICI_Config/app.properties",
                EntityType.COMPANY_BRAND,
                "ICICI",
                "AcmeCorp",
                8,
                13));

        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(4, records.size());

        assertPathRow(records, EntityType.REPOSITORY_NAME, "ICICI_CODE_BANK", "A81D9F227C4B");
        assertPathRow(records, EntityType.FOLDER_NAME, "ICICI_Config", "B7C91D4F88A1");
        assertPathRow(records, EntityType.FILE_NAME, "ICICI_Secrets.txt", "F91A2C778D44.txt");

        EntityReportRecord contentRow = records.stream()
                .filter(record -> record.getEntityType() == EntityType.COMPANY_BRAND)
                .findFirst()
                .orElseThrow();
        assertEquals("ICICI_Config/app.properties", contentRow.getRepoRelativePath());
        assertEquals("ICICI", contentRow.getOriginalValue());
        assertEquals("AcmeCorp", contentRow.getMaskedValue());
        assertEquals(8, contentRow.getStartOffset());
        assertEquals(13, contentRow.getEndOffset());
    }

    @Test
    void roundtripPreservesPathAndContentRowsTogether() throws Exception {
        MappingRegistry registry = new MappingRegistry();
        registry.register(pathRecord(EntityType.FOLDER_NAME, "ICICI_Config", "B7C91D4F88A1"));
        registry.register(new MappingRecord(
                "ICICI_Config/secrets.txt",
                EntityType.EMAIL,
                "admin@icici.com",
                "desk123@example.com",
                0,
                15));

        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(2, records.size());
        assertPathRow(records, EntityType.FOLDER_NAME, "ICICI_Config", "B7C91D4F88A1");
        assertTrue(records.stream().anyMatch(record -> record.getEntityType() == EntityType.EMAIL));
    }

    private static MappingRecord pathRecord(EntityType entityType, String original, String masked) {
        return new MappingRecord(PATH_CONTEXT, entityType, original, masked, 0, 0);
    }

    private static void assertPathRow(
            List<EntityReportRecord> records,
            EntityType entityType,
            String original,
            String masked) {
        EntityReportRecord record = records.stream()
                .filter(row -> row.getEntityType() == entityType && row.getOriginalValue().equals(original))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing path row: " + entityType + " / " + original));
        assertEquals(PATH_CONTEXT, record.getRepoRelativePath());
        assertEquals(masked, record.getMaskedValue());
        assertEquals(0, record.getStartOffset());
        assertEquals(0, record.getEndOffset());
        assertEquals(ReportSchema.CURRENT_VERSION, record.getReportVersion());
    }
}
