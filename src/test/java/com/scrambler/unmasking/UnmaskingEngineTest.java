package com.scrambler.unmasking;

import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.EntityType;
import com.scrambler.exception.MaskingException;
import com.scrambler.exception.ReportException;
import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import com.scrambler.report.CsvReportWriter;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnmaskingEngineTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            128L);

    private UnmaskingEngine unmaskingEngine;
    private MappingIndex mappingIndex;

    @BeforeEach
    void setUp() {
        unmaskingEngine = new UnmaskingEngine();
        mappingIndex = MappingIndex.from(List.of(
                record("config/application.yml", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 9, 24),
                record("config/application.yml", EntityType.URL, "https://internal.icici.com", "URL_000001", 40, 65)));
    }

    @Test
    void restoresSingleToken() {
        String masked = "contact: EMAIL_000001";

        String restored = unmaskingEngine.unmask(masked, singleTokenIndex(), null);

        assertEquals("contact: admin@icici.com", restored);
    }

    @Test
    void restoresMultipleTokens() {
        String masked = """
                admin_email: EMAIL_000001
                portal: URL_000001
                """;

        String restored = unmaskingEngine.unmask(masked, mappingIndex, null);

        assertEquals("""
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                """, restored);
    }

    @Test
    void restoresRepeatedTokenOccurrences() {
        MappingIndex repeatedIndex = MappingIndex.from(List.of(
                record("notes.txt", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 0, 15)));

        String masked = "primary: EMAIL_000001 backup: EMAIL_000001";
        RestoreResult restoreResult = new RestoreResult();

        String restored = unmaskingEngine.unmask(masked, repeatedIndex, restoreResult);

        assertEquals("primary: admin@icici.com backup: admin@icici.com", restored);
        assertEquals(2, restoreResult.getTokensRestored());
    }

    @Test
    void failsOnMissingMapping() {
        String masked = "contact: EMAIL_000999";

        MaskingException exception = assertThrows(
                MaskingException.class,
                () -> unmaskingEngine.unmask(masked, singleTokenIndex(), null));

        assertTrue(exception.getMessage().contains("Missing mapping for masked token: EMAIL_000999"));
    }

    @Test
    void failsOnDuplicateMapping() {
        List<EntityReportRecord> duplicateRows = List.of(
                record("a.txt", EntityType.EMAIL, "first@icici.com", "EMAIL_000001", 0, 15),
                record("b.txt", EntityType.EMAIL, "second@icici.com", "EMAIL_000001", 0, 16));

        ReportException exception = assertThrows(ReportException.class, () -> MappingIndex.from(duplicateRows));
        assertTrue(exception.getMessage().contains("Duplicate masked value"));
    }

    @Test
    void rejectsUnsupportedVersion(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        Files.writeString(reportPath, """
                report_version,2.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                """, StandardCharsets.UTF_8);

        MappingLoader mappingLoader = new MappingLoader();

        ReportException exception = assertThrows(ReportException.class, () -> mappingLoader.load(reportPath));
        assertTrue(exception.getMessage().contains("Unsupported report version: 2.0"));
    }

    @Test
    void roundtripMaskThenUnmask() throws Exception {
        String original = """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                """;

        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, original));
        String masked = maskingEngine.mask(original, detection, mappingRegistry);

        Path reportPath = writeReport(mappingRegistry);
        MappingIndex index = MappingIndex.from(new MappingLoader().load(reportPath));
        RestoreResult restoreResult = new RestoreResult();

        String restored = unmaskingEngine.unmask(masked, index, restoreResult);

        assertEquals(original, restored);
        assertEquals(2, restoreResult.getTokensRestored());
    }

    @Test
    void preservesLineBreaksAndFileStructure() {
        MappingIndex index = MappingIndex.from(List.of(
                record("config/application.yml", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 7, 22),
                record("config/application.yml", EntityType.URL, "https://example.com", "URL_000001", 40, 59)));

        String masked = "line1: EMAIL_000001\nline2: unchanged\nline3: URL_000001\n";

        String restored = unmaskingEngine.unmask(masked, index, null);

        assertEquals("line1: admin@icici.com\nline2: unchanged\nline3: https://example.com\n", restored);
    }

    @Test
    void ignoresNonTokenIdentifiers() {
        MappingIndex emptyIndex = MappingIndex.from(List.of());
        String content = "const MY_CONSTANT_123456 = 1;";

        assertEquals(content, unmaskingEngine.unmask(content, emptyIndex, null));
    }

    @Test
    void returnsOriginalContentWhenNoTokensPresent() {
        MappingIndex emptyIndex = MappingIndex.from(List.of());

        String content = "plain configuration values only";

        assertEquals(content, unmaskingEngine.unmask(content, emptyIndex, null));
    }

    private static MappingIndex singleTokenIndex() {
        return MappingIndex.from(List.of(
                record("config/application.yml", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 9, 24)));
    }

    private static Path writeReport(MappingRegistry mappingRegistry) throws Exception {
        Path reportPath = Files.createTempFile("entity-report", ".csv");
        new CsvReportWriter().write(mappingRegistry, reportPath);
        return reportPath;
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
}
