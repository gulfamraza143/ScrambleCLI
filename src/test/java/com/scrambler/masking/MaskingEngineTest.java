package com.scrambler.masking;

import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.detection.Entity;
import com.scrambler.detection.EntityDomain;
import com.scrambler.detection.EntityType;
import com.scrambler.inventory.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingEngineTest {

    private static final Pattern EMAIL_MASK = Pattern.compile("@example\\.com");
    private static final Pattern PAN_MASK = Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\d");

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            128L);

    private DetectionEngine detectionEngine;
    private MaskingEngine maskingEngine;
    private MappingRegistry mappingRegistry;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
        maskingEngine = new MaskingEngine();
        mappingRegistry = new MappingRegistry();
    }

    @Test
    void masksSingleReplacementWithFormatPreservingEmail() {
        String content = "contact: admin@icici.com";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("admin@icici.com"));
        assertTrue(EMAIL_MASK.matcher(masked).find());
        assertEquals(1, mappingRegistry.getRecords().size());

        MappingRecord record = mappingRegistry.getRecords().get(0);
        assertEquals(EntityType.EMAIL, record.getEntityType());
        assertEquals("admin@icici.com", record.getOriginalValue());
        assertEquals(record.getMaskedValue(), mappingRegistry.getRecords().get(0).getMaskedValue());
    }

    @Test
    void masksMultipleReplacements() {
        String content = """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("admin@icici.com"));
        assertFalse(masked.contains("https://internal.icici.com"));
        assertEquals(2, mappingRegistry.getRecords().size());
    }

    @Test
    void masksMultipleEntityTypesWithFormatPreservation() {
        String content = """
                pan: ABCPA1234F
                phone: +91-9876543210
                password: hunter2
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertTrue(PAN_MASK.matcher(masked).find());
        assertFalse(masked.contains("ABCPA1234F"));
        assertFalse(masked.contains("hunter2"));
        assertEquals(3, mappingRegistry.getRecords().size());
        assertEquals(
                List.of(EntityType.PAN, EntityType.PHONE, EntityType.PASSWORD),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void assignsSameMaskedValueForRepeatedOriginalValues() {
        String content = "primary: admin@icici.com backup: admin@icici.com";

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        String firstMasked = mappingRegistry.getRecords().get(0).getMaskedValue();
        String secondMasked = mappingRegistry.getRecords().get(1).getMaskedValue();
        assertEquals(firstMasked, secondMasked);
        assertEquals(2, masked.split(firstMasked, -1).length - 1);
    }

    @Test
    void appliesReplacementsFromEndToStart() {
        String content = "email=short@x.com phone=+91-9876543210 tail";
        DetectionResult detection = new DetectionResult(
                FILE_INFO,
                List.of(
                        new Entity(EntityDomain.PII, EntityType.EMAIL, "short@x.com", 6, 17),
                        new Entity(EntityDomain.PII, EntityType.PHONE, "+91-9876543210", 24, 38)));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("short@x.com"));
        assertFalse(masked.contains("9876543210"));
        assertTrue(masked.endsWith(" tail"));
        assertEquals(2, mappingRegistry.getRecords().size());
    }

    @Test
    void returnsOriginalContentForEmptyDetectionResult() {
        DetectionResult detection = new DetectionResult(FILE_INFO, List.of());
        String content = "no secrets here";

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(content, masked);
        assertTrue(mappingRegistry.getRecords().isEmpty());
    }

    @Test
    void preservesLineBreaksAndFileStructure() {
        String content = "line1: admin@icici.com\nline2: unchanged\nline3: https://example.com\n";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertTrue(masked.startsWith("line1: "));
        assertTrue(masked.contains("\nline2: unchanged\n"));
        assertTrue(masked.endsWith("\n"));
    }

    @Test
    void mappingsAreDeterministicWithinRun() {
        String firstContent = "user: admin@icici.com";
        String secondContent = "backup: admin@backup.com";

        DetectionResult firstDetection = detectionEngine.detect(new DetectionContext(FILE_INFO, firstContent));
        DetectionResult secondDetection = detectionEngine.detect(new DetectionContext(FILE_INFO, secondContent));

        MaskingEngine runEngine = new MaskingEngine();
        MappingRegistry runRegistry = new MappingRegistry();

        runEngine.mask(firstContent, firstDetection, runRegistry);
        runEngine.mask(secondContent, secondDetection, runRegistry);

        assertEquals(2, runEngine.getGlobalValueMapper().size());
        assertEquals(2, runRegistry.getRecords().size());
    }

    @Test
    void masksAadhaarUpiAndCreditCardEntities() {
        String content = "aadhaar=123412341232 upi=user@oksbi card=4111111111111111";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("123412341232"));
        assertFalse(masked.contains("user@oksbi"));
        assertFalse(masked.contains("4111111111111111"));
        assertEquals(
                List.of(EntityType.AADHAAR, EntityType.UPI_ID, EntityType.CREDIT_CARD),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void masksGstinTanAndCinEntities() {
        String content = "gstin=27AAPFU0939F1ZV tan=DELM12345L cin=L17110MH1973PLC019786";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("27AAPFU0939F1ZV"));
        assertFalse(masked.contains("DELM12345L"));
        assertFalse(masked.contains("L17110MH1973PLC019786"));
        assertEquals(
                List.of(EntityType.GSTIN, EntityType.TAN, EntityType.CIN),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void masksInternalIdentifiersPreservingAssignmentSyntax() {
        String content = """
                employeeId=E123456
                banId=BAN123456
                ldapId=rajesh.singh
                """;
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertTrue(masked.startsWith("employeeId="));
        assertTrue(masked.contains("\nbanId="));
        assertTrue(masked.contains("\nldapId="));
        assertFalse(masked.contains("E123456"));
        assertFalse(masked.contains("BAN123456"));
        assertFalse(masked.contains("rajesh.singh"));
        assertEquals(
                List.of(EntityType.INTERNAL_IDENTIFIER, EntityType.INTERNAL_IDENTIFIER, EntityType.INTERNAL_IDENTIFIER),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void masksWorkItemIdsWithFormatPreservation() {
        String content = "ticket=INC0012345 story=ENG-1234";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertFalse(masked.contains("INC0012345"));
        assertFalse(masked.contains("ENG-1234"));
        assertEquals(
                List.of(EntityType.WORK_ITEM_ID, EntityType.WORK_ITEM_ID),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void assignsSameMaskedValueForRepeatedInternalIdentifiers() {
        String content = """
                employeeId=E123456
                empId=E123456
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(2, mappingRegistry.getRecords().size());
        assertEquals(
                mappingRegistry.getRecords().get(0).getMaskedValue(),
                mappingRegistry.getRecords().get(1).getMaskedValue());
    }

    @Test
    void assignsDifferentMaskedValuesForDifferentInternalIdentifiers() {
        String content = """
                employeeId=E123456
                banId=BAN123456
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(2, mappingRegistry.getRecords().size());
        assertFalse(mappingRegistry.getRecords().get(0).getMaskedValue()
                .equals(mappingRegistry.getRecords().get(1).getMaskedValue()));
    }
}
