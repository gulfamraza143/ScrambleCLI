package com.scrambler.masking;

import com.scrambler.config.CompanyDictionary;
import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.EntityType;
import com.scrambler.inventory.FileInfo;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportWriter;
import com.scrambler.unmasking.MappingIndex;
import com.scrambler.unmasking.MappingLoader;
import com.scrambler.unmasking.RestoreResult;
import com.scrambler.unmasking.UnmaskingEngine;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandReplacementDictionaryTest {

    @Test
    void loadsBrandReplacementFile() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.loadFromResource("/brand-replacements.txt");

        assertEquals(28, dictionary.getMappingsLongestFirst().size());
        assertEquals("Orion Capital", dictionary.replace("ICICI Securities"));
        assertEquals("Falcon Markets", dictionary.replace("ICICIDirect"));
    }

    @Test
    void defaultsValidatesCoverageAgainstCompanyDictionary() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.defaults();

        assertEquals(28, dictionary.getMappingsLongestFirst().size());
        dictionary.validateUniqueReplacements();
    }

    @Test
    void allReplacementValuesAreUnique() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.defaults();
        Set<String> replacements = dictionary.getMappingsLongestFirst().stream()
                .map(BrandReplacementDictionary.BrandMapping::replacement)
                .collect(Collectors.toSet());

        assertEquals(28, replacements.size());
    }

    @Test
    void caseInsensitiveMatchingResolvesToConfiguredReplacement() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.defaults();

        assertEquals("Orion Capital", dictionary.replace("ICICI Securities"));
        assertEquals("Orion Capital", dictionary.replace("icici securities"));
        assertEquals("Orion Capital", dictionary.replace("ICICI SECURITIES"));
    }

    @Test
    void missingReplacementFailsStartup() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.loadFromResource("/brand-replacements-partial.txt");
        CompanyDictionary companyDictionary = CompanyDictionary.loadFromResource("/company-dictionary.txt");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> dictionary.validateCoverage(companyDictionary));

        assertEquals("Missing replacement mapping for COMPANY_BRAND: ICICI", error.getMessage());
    }

    @Test
    void duplicateReplacementFailsStartup() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BrandReplacementDictionary.loadFromResource("/brand-replacements-duplicate-replacement.txt"));

        assertTrue(error.getMessage().contains("Duplicate brand replacement value"));
    }

    @Test
    void invalidLineFailsStartup() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BrandReplacementDictionary.loadFromResource("/brand-replacements-invalid-line.txt"));

        assertTrue(error.getMessage().contains("Malformed brand replacement line"));
    }

    @Test
    void blankReplacementFailsStartup() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BrandReplacementDictionary.loadFromResource("/brand-replacements-blank-replacement.txt"));

        assertTrue(error.getMessage().contains("Blank replacement value"));
    }

    @Test
    void replaceThrowsWhenMappingMissing() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.loadFromResource("/brand-replacements-partial.txt");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> dictionary.replace("ICICI Direct"));

        assertEquals("Missing replacement mapping for COMPANY_BRAND: ICICI Direct", error.getMessage());
    }

    @Test
    void collisionGroupsMaskWithoutException() {
        String content = String.join("\n",
                "ICICI Direct",
                "ICICIDirect",
                "ICICI Home Finance",
                "ICICI HFC",
                "InstaBIZ",
                "InstaBiz");

        FileInfo fileInfo = new FileInfo(
                Paths.get("/workspace/repo/collision-groups.txt"),
                "collision-groups.txt",
                content.length());

        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(fileInfo, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(6, detection.getEntities().size());
        assertEquals(List.of(
                "Falcon Trade",
                "Falcon Markets",
                "Horizon Housing",
                "Horizon Finance",
                "Vertex Business",
                "Vertex Commerce"), List.of(masked.split("\n")));

        Set<String> maskedValues = mappingRegistry.getRecords().stream()
                .map(MappingRecord::getMaskedValue)
                .collect(Collectors.toSet());
        assertEquals(6, maskedValues.size());

        for (MappingRecord record : mappingRegistry.getRecords()) {
            assertFalse(record.getOriginalValue().equals(record.getMaskedValue()));
        }
    }

    @Test
    void endToEndBrandMaskingProducesSyntheticReplacements(@TempDir Path tempDir) throws Exception {
        String content = String.join("\n",
                "ICICI Securities",
                "ICICI Direct",
                "iMobile",
                "Amazon Pay ICICI",
                "ICICI Coral",
                "ICICI Rubyx",
                "ICICI Sapphiro",
                "ICICI Emeralde");

        FileInfo fileInfo = new FileInfo(
                Paths.get("/workspace/repo/brands.txt"),
                "brands.txt",
                content.length());

        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(fileInfo, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        List<String> expectedMaskedLines = List.of(
                "Orion Capital",
                "Falcon Trade",
                "Nova Mobile",
                "Digital Rewards Card",
                "Horizon Card",
                "Crimson Card",
                "Zenith Card",
                "Prestige Card");

        assertEquals(expectedMaskedLines, List.of(masked.split("\n")));
        assertFalse(masked.contains("ICICI"));
        assertFalse(masked.contains("iMobile"));
        assertFalse(masked.contains("Amazon Pay ICICI"));

        for (MappingRecord record : mappingRegistry.getRecords()) {
            assertEquals(EntityType.COMPANY_BRAND, record.getEntityType());
            assertFalse(record.getOriginalValue().equals(record.getMaskedValue()));
        }

        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        new XlsxReportWriter().write(mappingRegistry, reportPath);

        try (InputStream inputStream = java.nio.file.Files.newInputStream(reportPath);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= expectedMaskedLines.size(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                String originalValue = row.getCell(2).getStringCellValue();
                String maskedValue = row.getCell(3).getStringCellValue();
                assertFalse(originalValue.equals(maskedValue));
                assertEquals("COMPANY_BRAND", row.getCell(0).getStringCellValue());
            }
        }
    }

    @Test
    void fullVirtualAuditFileMasksAndUnmasks(@TempDir Path tempDir) throws Exception {
        String content = String.join("\n",
                "ICICI Securities",
                "ICICI Direct",
                "ICICIDirect",
                "ICICI Venture",
                "ICICI Foundation",
                "ICICI Home Finance",
                "ICICI HFC",
                "ICICI Merchant Services",
                "ICICI Investments",
                "ICICI International",
                "ICICI Stack",
                "iMobile",
                "iMobile Pay",
                "InstaBIZ",
                "InstaBiz",
                "Pockets",
                "Money2India",
                "Money2World",
                "iPal",
                "Eazypay",
                "Amazon Pay ICICI",
                "ICICI Coral",
                "ICICI Rubyx",
                "ICICI Sapphiro",
                "ICICI Emeralde",
                "Industrial Credit and Investment Corporation of India");

        FileInfo fileInfo = new FileInfo(
                Paths.get("/workspace/repo/full-brand-audit.txt"),
                "full-brand-audit.txt",
                content.length());

        DetectionEngine detectionEngine = new DetectionEngine();
        MaskingEngine maskingEngine = new MaskingEngine();
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(fileInfo, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(26, detection.getEntities().size());
        assertEquals(26, mappingRegistry.getRecords().size());
        assertFalse(masked.contains("ICICI"));
        assertFalse(masked.contains("iMobile"));

        Set<String> maskedValues = new HashSet<>();
        for (MappingRecord record : mappingRegistry.getRecords()) {
            assertFalse(record.getOriginalValue().equals(record.getMaskedValue()));
            assertTrue(maskedValues.add(record.getMaskedValue()));
        }
        assertEquals(26, maskedValues.size());

        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        new XlsxReportWriter().write(mappingRegistry, reportPath);

        MappingIndex mappingIndex = MappingIndex.from(new MappingLoader().load(reportPath));
        String restored = new UnmaskingEngine().unmask(masked, mappingIndex, new RestoreResult());

        assertEquals(content, restored);
    }
}
