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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingEngineTest {

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
    void masksSingleReplacement() {
        String content = "contact: admin@icici.com";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("contact: SCRAMBLE_EMAIL_000001", masked);
        assertEquals(1, mappingRegistry.getRecords().size());

        MappingRecord record = mappingRegistry.getRecords().get(0);
        assertEquals("config/application.yml", record.getRepoRelativePath());
        assertEquals(EntityType.EMAIL, record.getEntityType());
        assertEquals("admin@icici.com", record.getOriginalValue());
        assertEquals("SCRAMBLE_EMAIL_000001", record.getMaskedValue());
        assertEquals(9, record.getStartOffset());
        assertEquals(24, record.getEndOffset());
    }

    @Test
    void masksMultipleReplacements() {
        String content = """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("""
                admin_email: SCRAMBLE_EMAIL_000001
                portal: SCRAMBLE_URL_000001
                """, masked);
        assertEquals(2, mappingRegistry.getRecords().size());
        assertEquals("SCRAMBLE_EMAIL_000001", mappingRegistry.getRecords().get(0).getMaskedValue());
        assertEquals("SCRAMBLE_URL_000001", mappingRegistry.getRecords().get(1).getMaskedValue());
    }

    @Test
    void masksMultipleEntityTypes() {
        String content = """
                pan: ABCPA1234F
                phone: +91-9876543210
                password: hunter2
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("""
                pan: SCRAMBLE_PAN_000001
                phone: +SCRAMBLE_PHONE_000001
                password: SCRAMBLE_PASSWORD_000001
                """, masked);
        assertEquals(3, mappingRegistry.getRecords().size());
        assertEquals(
                List.of(EntityType.PAN, EntityType.PHONE, EntityType.PASSWORD),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
        assertEquals("hunter2", mappingRegistry.getRecords().get(2).getOriginalValue());
    }

    @Test
    void assignsDistinctTokensForRepeatedValues() {
        String content = "primary: admin@icici.com backup: admin@icici.com";

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("primary: SCRAMBLE_EMAIL_000001 backup: SCRAMBLE_EMAIL_000002", masked);
        assertEquals(2, mappingRegistry.getRecords().size());
        assertEquals("SCRAMBLE_EMAIL_000001", mappingRegistry.getRecords().get(0).getMaskedValue());
        assertEquals("SCRAMBLE_EMAIL_000002", mappingRegistry.getRecords().get(1).getMaskedValue());
        assertEquals("admin@icici.com", mappingRegistry.getRecords().get(0).getOriginalValue());
        assertEquals("admin@icici.com", mappingRegistry.getRecords().get(1).getOriginalValue());
    }

    @Test
    void appliesReplacementsFromEndToStart() {
        String content = "email=short@x.com phone=+91-9876543210 tail";
        DetectionResult detection = new DetectionResult(
                FILE_INFO,
                List.of(
                        new Entity(
                                EntityDomain.PII,
                                EntityType.EMAIL,
                                "short@x.com",
                                6,
                                17),
                        new Entity(
                                EntityDomain.PII,
                                EntityType.PHONE,
                                "+91-9876543210",
                                24,
                                38)));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("email=SCRAMBLE_EMAIL_000001 phone=SCRAMBLE_PHONE_000001 tail", masked);
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
    void returnsOriginalContentWhenNoEntitiesDetected() {
        String content = "plain configuration values only";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(content, masked);
        assertTrue(mappingRegistry.getRecords().isEmpty());
    }

    @Test
    void preservesLineBreaksAndFileStructure() {
        String content = "line1: admin@icici.com\nline2: unchanged\nline3: https://example.com\n";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("line1: SCRAMBLE_EMAIL_000001\nline2: unchanged\nline3: SCRAMBLE_URL_000001\n", masked);
    }

    @Test
    void numberingIsDeterministicWithinRun() {
        String firstContent = "user: admin@icici.com";
        String secondContent = "backup: admin@backup.com";

        DetectionResult firstDetection = detectionEngine.detect(new DetectionContext(FILE_INFO, firstContent));
        DetectionResult secondDetection = detectionEngine.detect(new DetectionContext(FILE_INFO, secondContent));

        MaskingEngine runEngine = new MaskingEngine();
        MappingRegistry runRegistry = new MappingRegistry();

        assertEquals("user: SCRAMBLE_EMAIL_000001", runEngine.mask(firstContent, firstDetection, runRegistry));
        assertEquals("backup: SCRAMBLE_EMAIL_000002", runEngine.mask(secondContent, secondDetection, runRegistry));
        assertEquals(
                List.of("SCRAMBLE_EMAIL_000001", "SCRAMBLE_EMAIL_000002"),
                runRegistry.getRecords().stream().map(MappingRecord::getMaskedValue).toList());
    }

    @Test
    void masksAadhaarUpiAndCreditCardEntities() {
        String content = "aadhaar=123412341232 upi=user@oksbi card=4111111111111111";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("aadhaar=SCRAMBLE_AADHAAR_000001 upi=SCRAMBLE_UPI_ID_000001 card=SCRAMBLE_CREDIT_CARD_000001", masked);
        assertEquals(
                List.of(EntityType.AADHAAR, EntityType.UPI_ID, EntityType.CREDIT_CARD),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }

    @Test
    void masksGstinTanAndCinEntities() {
        String content = "gstin=27AAPFU0939F1ZV tan=DELM12345L cin=L17110MH1973PLC019786";
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("gstin=SCRAMBLE_GSTIN_000001 tan=SCRAMBLE_TAN_000001 cin=SCRAMBLE_CIN_000001", masked);
        assertEquals(
                List.of(EntityType.GSTIN, EntityType.TAN, EntityType.CIN),
                mappingRegistry.getRecords().stream().map(MappingRecord::getEntityType).toList());
    }
}
