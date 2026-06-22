package com.scrambler.detection;

import com.scrambler.config.CompanyDictionary;
import com.scrambler.inventory.FileInfo;
import com.scrambler.masking.BrandReplacementDictionary;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyBrandCorrectnessTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            128L);

    private DetectionEngine detectionEngine;
    private MaskingEngine maskingEngine;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
        maskingEngine = new MaskingEngine();
    }

    @Test
    void compilePatternOrdersAlternationLongestFirst() {
        String pattern = CompanyDictionary.defaults().compilePattern().pattern();

        assertTrue(pattern.indexOf("Industrial Credit and Investment Corporation of India")
                < pattern.indexOf("ICICI Securities"));
        assertTrue(pattern.indexOf("ICICI Securities") < pattern.indexOf("ICICI Direct"));
        assertTrue(pattern.indexOf("Amazon Pay ICICI") < pattern.indexOf("ICICI Coral"));
    }

    @Test
    void detectsFullIciciSecuritiesPhrase() {
        assertDetectedOriginal("team: ICICI Securities", "ICICI Securities");
    }

    @Test
    void detectsFullIciciDirectPhrase() {
        assertDetectedOriginal("platform: ICICI Direct", "ICICI Direct");
    }

    @Test
    void detectsImobilePhrase() {
        assertDetectedOriginal("app: iMobile", "iMobile");
    }

    @Test
    void standaloneIciciDoesNotConsumeLongerPhrases() {
        String content = "broker: ICICI Securities platform: ICICI Direct wallet: Pockets";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(
                List.of("ICICI Securities", "ICICI Direct", "Pockets"),
                result.getEntities().stream().map(Entity::getOriginalValue).toList());
    }

    @Test
    void entityReportReceivesFullOriginalValue() {
        String content = "team: ICICI Securities";
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals(1, detection.getEntities().size());
        assertEquals("ICICI Securities", detection.getEntities().get(0).getOriginalValue());
        assertEquals("ICICI Securities", mappingRegistry.getRecords().get(0).getOriginalValue());
        assertEquals("Orion Capital", mappingRegistry.getRecords().get(0).getMaskedValue());
        assertFalse(masked.contains("ICICI Securities"));
        assertTrue(masked.contains("Orion Capital"));
    }

    @Test
    void masksIciciDirectToFalconTrade() {
        BrandReplacementDictionary dictionary = BrandReplacementDictionary.defaults();

        assertEquals("Falcon Trade", dictionary.replace("ICICI Direct"));
        assertEquals("Falcon Trade", dictionary.replace("icici direct"));
        assertEquals("Falcon Trade", dictionary.replace("ICICI DIRECT"));
    }

    @Test
    void masksIciciDirectEndToEnd() {
        String content = "platform: ICICI Direct";
        MappingRegistry mappingRegistry = new MappingRegistry();

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, content));
        String masked = maskingEngine.mask(content, detection, mappingRegistry);

        assertEquals("ICICI Direct", detection.getEntities().get(0).getOriginalValue());
        assertEquals("Falcon Trade", mappingRegistry.getRecords().get(0).getMaskedValue());
        assertFalse(masked.contains("ICICI Direct"));
        assertTrue(masked.contains("Falcon Trade"));
    }

    @Test
    void detectsConfiguredCompanyBrandTermsWithoutLegacyTokens() {
        String content = """
                admin_email: admin@icici.com
                portal: https://internal.icici.com
                broker: ICICI Securities
                platform: ICICI Direct
                app: iMobile
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(
                List.of("EMAIL", "URL", "COMPANY_BRAND", "COMPANY_BRAND", "COMPANY_BRAND"),
                result.getEntities().stream().map(entity -> entity.getType().name()).toList());
        assertEquals("ICICI Securities", result.getEntities().get(2).getOriginalValue());
        assertEquals("ICICI Direct", result.getEntities().get(3).getOriginalValue());
        assertEquals("iMobile", result.getEntities().get(4).getOriginalValue());
    }

    @Test
    void compilePatternMatchesStandaloneConfiguredTerms() {
        CompanyDictionary dictionary = CompanyDictionary.defaults();
        Pattern pattern = dictionary.compilePattern();

        assertTrue(pattern.matcher("partner ICICI Securities team").find());
        assertTrue(pattern.matcher("iMobile").find());

        Matcher securitiesMatcher = pattern.matcher("team: ICICI Securities");
        assertTrue(securitiesMatcher.find());
        assertEquals("ICICI Securities", securitiesMatcher.group());
    }

    private void assertDetectedOriginal(String content, String expectedOriginal) {
        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals(EntityType.COMPANY_BRAND, result.getEntities().get(0).getType());
        assertEquals(expectedOriginal, result.getEntities().get(0).getOriginalValue());
        assertEquals(expectedOriginal, content.substring(
                result.getEntities().get(0).getStartOffset(),
                result.getEntities().get(0).getEndOffset()));
    }
}
