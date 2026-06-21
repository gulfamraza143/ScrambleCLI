package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.ReportException;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReportTest {

    @TempDir
    Path tempDir;

    private CsvReportWriter writer;
    private CsvReportReader reader;

    @BeforeEach
    void setUp() {
        writer = new CsvReportWriter();
        reader = new CsvReportReader();
    }

    @Test
    void writesReportWithVersionHeaderAndDataRows() throws Exception {
        MappingRegistry registry = registryWith(
                record("src/a.yml", EntityType.EMAIL, "admin@icici.com", "SCRAMBLE_EMAIL_000001", 10, 25),
                record("src/b.yml", EntityType.PASSWORD, "password=admin123", "SCRAMBLE_PASSWORD_000001", 150, 170));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<String> lines = Files.readAllLines(reportPath, StandardCharsets.UTF_8);
        assertEquals("report_version," + ReportSchema.LEGACY_CSV_VERSION, lines.get(0));
        assertEquals(String.join(",", ReportSchema.DATA_COLUMNS), lines.get(1));
        assertEquals(
                "src/a.yml,EMAIL,admin@icici.com,SCRAMBLE_EMAIL_000001,10,25",
                lines.get(2));
        assertEquals(
                "src/b.yml,PASSWORD,password=admin123,SCRAMBLE_PASSWORD_000001,150,170",
                lines.get(3));
    }

    @Test
    void readsReportIntoRecords() throws Exception {
        MappingRegistry registry = registryWith(
                record("config/app.yml", EntityType.EMAIL, "admin@icici.com", "SCRAMBLE_EMAIL_000001", 100, 115));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(1, records.size());

        EntityReportRecord record = records.get(0);
        assertEquals(ReportSchema.LEGACY_CSV_VERSION, record.getReportVersion());
        assertEquals("config/app.yml", record.getRepoRelativePath());
        assertEquals(EntityType.EMAIL, record.getEntityType());
        assertEquals("admin@icici.com", record.getOriginalValue());
        assertEquals("SCRAMBLE_EMAIL_000001", record.getMaskedValue());
        assertEquals(100, record.getStartOffset());
        assertEquals(115, record.getEndOffset());
    }

    @Test
    void roundtripPreservesAllFields() throws Exception {
        MappingRegistry registry = registryWith(
                record("src/main/resources/application.yml", EntityType.EMAIL, "admin@icici.com", "SCRAMBLE_EMAIL_000001", 100, 115),
                record("src/main/resources/application.yml", EntityType.PASSWORD, "password=admin123", "SCRAMBLE_PASSWORD_000001", 150, 170));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(2, records.size());
        assertEquals(
                List.of(
                        expectedRecord("src/main/resources/application.yml", EntityType.EMAIL, "admin@icici.com", "SCRAMBLE_EMAIL_000001", 100, 115),
                        expectedRecord("src/main/resources/application.yml", EntityType.PASSWORD, "password=admin123", "SCRAMBLE_PASSWORD_000001", 150, 170)),
                records);
    }

    @Test
    void preservesUnicodeValues() throws Exception {
        String original = "值,引\"号\uD83D\uDE00\n第二行";
        MappingRegistry registry = registryWith(
                record("unicode.txt", EntityType.COMPANY_BRAND, original, "COMPANY_BRAND_000001", 0, original.length()));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(1, records.size());
        assertEquals(original, records.get(0).getOriginalValue());
    }

    @Test
    void preservesCommasInValues() throws Exception {
        MappingRegistry registry = registryWith(
                record("data.csv", EntityType.SECRET_KEY, "key=a,b,c", "SCRAMBLE_SECRET_KEY_000001", 5, 14));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        String line = Files.readAllLines(reportPath, StandardCharsets.UTF_8).get(2);
        assertTrue(line.contains("\"key=a,b,c\""));

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals("key=a,b,c", records.get(0).getOriginalValue());
    }

    @Test
    void preservesQuotesInValues() throws Exception {
        MappingRegistry registry = registryWith(
                record("notes.txt", EntityType.PASSWORD, "pass=\"secret\"", "SCRAMBLE_PASSWORD_000001", 0, 14));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        String line = Files.readAllLines(reportPath, StandardCharsets.UTF_8).get(2);
        assertTrue(line.contains("\"pass=\"\"secret\"\"\""));

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals("pass=\"secret\"", records.get(0).getOriginalValue());
    }

    @Test
    void preservesLineBreaksInValues() throws Exception {
        String original = "line1\nline2";
        MappingRegistry registry = registryWith(
                record("multiline.txt", EntityType.API_KEY, original, "SCRAMBLE_API_KEY_000001", 0, original.length()));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(original, records.get(0).getOriginalValue());
    }

    @Test
    void emptyReportContainsOnlyHeaders() throws Exception {
        MappingRegistry registry = new MappingRegistry();
        Path reportPath = tempDir.resolve("entity_report.csv");

        writer.write(registry, reportPath);

        List<String> lines = Files.readAllLines(reportPath, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals(List.of(), reader.read(reportPath));
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        Path reportPath = tempDir.resolve("entity_report.csv");
        Files.writeString(reportPath, """
                report_version,3.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                """, StandardCharsets.UTF_8);

        ReportException exception = assertThrows(ReportException.class, () -> reader.read(reportPath));
        assertTrue(exception.getMessage().contains("Unsupported report version: 3.0"));
    }

    @Test
    void roundtripPreservesIndianTaxIdentifiers() throws Exception {
        MappingRegistry registry = registryWith(
                record("vendor.csv", EntityType.GSTIN, "27AAPFU0939F1ZV", "SCRAMBLE_GSTIN_000001", 0, 15),
                record("vendor.csv", EntityType.TAN, "DELM12345L", "SCRAMBLE_TAN_000001", 20, 30),
                record("vendor.csv", EntityType.CIN, "L17110MH1973PLC019786", "SCRAMBLE_CIN_000001", 40, 61));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(3, records.size());
        assertEquals(EntityType.GSTIN, records.get(0).getEntityType());
        assertEquals("27AAPFU0939F1ZV", records.get(0).getOriginalValue());
        assertEquals(EntityType.TAN, records.get(1).getEntityType());
        assertEquals(EntityType.CIN, records.get(2).getEntityType());
    }

    @Test
    void writesRowsInDeterministicOrder() throws Exception {
        MappingRegistry registry = new MappingRegistry();
        registry.register(record("z.txt", EntityType.URL, "https://z", "URL_000002", 20, 29));
        registry.register(record("a.txt", EntityType.EMAIL, "a@b.com", "SCRAMBLE_EMAIL_000001", 0, 7));
        registry.register(record("a.txt", EntityType.URL, "https://a", "SCRAMBLE_URL_000001", 10, 19));

        Path reportPath = tempDir.resolve("entity_report.csv");
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(List.of(
                expectedRecord("a.txt", EntityType.EMAIL, "a@b.com", "SCRAMBLE_EMAIL_000001", 0, 7),
                expectedRecord("a.txt", EntityType.URL, "https://a", "SCRAMBLE_URL_000001", 10, 19),
                expectedRecord("z.txt", EntityType.URL, "https://z", "URL_000002", 20, 29)), records);
    }

    @Test
    void overwritesExistingReport() throws Exception {
        Path reportPath = tempDir.resolve("entity_report.csv");
        Files.writeString(reportPath, "stale,data", StandardCharsets.UTF_8);

        MappingRegistry registry = registryWith(
                record("fresh.txt", EntityType.EMAIL, "x@y.com", "SCRAMBLE_EMAIL_000001", 1, 8));
        writer.write(registry, reportPath);

        List<EntityReportRecord> records = reader.read(reportPath);
        assertEquals(1, records.size());
        assertEquals("fresh.txt", records.get(0).getRepoRelativePath());
    }

    private static MappingRegistry registryWith(MappingRecord... records) {
        MappingRegistry registry = new MappingRegistry();
        for (MappingRecord record : records) {
            registry.register(record);
        }
        return registry;
    }

    private static MappingRecord record(
            String path,
            EntityType type,
            String original,
            String masked,
            int start,
            int end) {
        return new MappingRecord(path, type, original, masked, start, end);
    }

    private static EntityReportRecord expectedRecord(
            String path,
            EntityType type,
            String original,
            String masked,
            int start,
            int end) {
        return new EntityReportRecord(
                ReportSchema.LEGACY_CSV_VERSION,
                path,
                type,
                original,
                masked,
                start,
                end);
    }
}
