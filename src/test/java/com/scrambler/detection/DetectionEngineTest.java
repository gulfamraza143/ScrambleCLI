package com.scrambler.detection;

import com.scrambler.config.CompanyDictionary;
import com.scrambler.inventory.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionEngineTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            128L);

    private DetectionEngine detectionEngine;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
    }

    @Test
    void detectsExampleEntitiesFromMilestoneSpec() {
        String content = """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                bank: ICICI
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("EMAIL", "URL", "COMPANY_BRAND"), entityTypeNames(result));
        assertEquals("admin@icici.com", result.getEntities().get(0).getOriginalValue());
        assertEquals("https://internal.icici.com", result.getEntities().get(1).getOriginalValue());
        assertEquals("ICICI", result.getEntities().get(2).getOriginalValue());
    }

    @Test
    void detectsPanIfscPhoneAndIpAddress() {
        String content = """
                pan: ABCDE1234F
                ifsc: HDFC0001234
                phone: +91-9876543210
                host: 192.168.1.10
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("PAN", "IFSC", "PHONE", "IP_ADDRESS"), entityTypeNames(result));
    }

    @Test
    void detectsConfiguredCompanyBrandTerms() {
        String content = "teams: ICICIBANK ICICILABS FXTP WECARE SCRAMBLE";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(
                List.of("COMPANY_BRAND", "COMPANY_BRAND", "COMPANY_BRAND", "COMPANY_BRAND", "COMPANY_BRAND"),
                entityTypeNames(result));
    }

    @Test
    void resolvesOverlappingMatchesByLongestSpan() {
        String content = "contact admin@icici.com today";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals("EMAIL", result.getEntities().get(0).getType().name());
        assertEquals("admin@icici.com", result.getEntities().get(0).getOriginalValue());
    }

    @Test
    void preservesMandatoryOffsets() {
        String content = "email=admin@icici.com";

        Entity entity = detectionEngine.detect(new DetectionContext(FILE_INFO, content)).getEntities().get(0);

        assertEquals("admin@icici.com", content.substring(entity.getStartOffset(), entity.getEndOffset()));
    }

    @Test
    void usesProvidedCompanyDictionary() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();
        DetectionEngine engine = new DetectionEngine(dictionary);

        DetectionResult result = engine.detect(new DetectionContext(FILE_INFO, "partner: SCRAMBLE"));

        assertEquals(1, result.getEntities().size());
        assertEquals("SCRAMBLE", result.getEntities().get(0).getOriginalValue());
    }

    @Test
    void rejectsNullContext() {
        assertThrows(NullPointerException.class, () -> detectionEngine.detect(null));
    }

    @Test
    void returnsEmptyResultWhenNoMatchesFound() {
        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "no sensitive values here"));

        assertTrue(result.getEntities().isEmpty());
    }

    private static List<String> entityTypeNames(DetectionResult result) {
        return result.getEntities().stream()
                .map(entity -> entity.getType().name())
                .collect(Collectors.toList());
    }
}
