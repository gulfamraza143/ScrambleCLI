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
                broker: ICICI Securities
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("EMAIL", "URL", "COMPANY_BRAND"), entityTypeNames(result));
        assertEquals("admin@icici.com", result.getEntities().get(0).getOriginalValue());
        assertEquals("https://internal.icici.com", result.getEntities().get(1).getOriginalValue());
        assertEquals("ICICI Securities", result.getEntities().get(2).getOriginalValue());
    }

    @Test
    void detectsPanIfscPhoneAndIpAddress() {
        String content = """
                pan: ABCPA1234F
                ifsc: HDFC0001234
                phone: +91-9876543210
                host: 192.168.1.10
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("PAN", "IFSC", "PHONE", "IP_ADDRESS"), entityTypeNames(result));
    }

    @Test
    void detectsConfiguredCompanyBrandTerms() {
        String content = "broker: ICICI Securities platform: ICICI Direct app: iMobile wallet: Pockets card: ICICI Coral";

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

        DetectionResult result = engine.detect(new DetectionContext(FILE_INFO, "broker: ICICI Securities"));

        assertEquals(1, result.getEntities().size());
        assertEquals("ICICI Securities", result.getEntities().get(0).getOriginalValue());
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

    @Test
    void detectsPasswordAssignments() {
        String content = """
                password=admin123
                db.password=secret123
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("PASSWORD", "PASSWORD"), entityTypeNames(result));
        assertEquals("admin123", result.getEntities().get(0).getOriginalValue());
        assertEquals("secret123", result.getEntities().get(1).getOriginalValue());
        assertEquals("admin123", content.substring(
                result.getEntities().get(0).getStartOffset(),
                result.getEntities().get(0).getEndOffset()));
        assertEquals("secret123", content.substring(
                result.getEntities().get(1).getStartOffset(),
                result.getEntities().get(1).getEndOffset()));
    }

    @Test
    void detectsApiKeyAssignments() {
        String content = """
                api.key=abc123
                api_key=abc123
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("API_KEY", "API_KEY"), entityTypeNames(result));
    }

    @Test
    void detectsSecretKeyAssignments() {
        String content = """
                secret=xyz
                client.secret=xyz
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("SECRET_KEY", "SECRET_KEY"), entityTypeNames(result));
    }

    @Test
    void detectsJwtStructure() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJzdWIiOiIxMjM0NTY3ODkwIn0."
                + "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String content = "token: " + token;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals("JWT", result.getEntities().get(0).getType().name());
        assertEquals(token, result.getEntities().get(0).getOriginalValue());
    }

    @Test
    void detectsPrivateKeyBlocks() {
        String content = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA
                -----END RSA PRIVATE KEY-----
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals("PRIVATE_KEY", result.getEntities().get(0).getType().name());
        assertTrue(result.getEntities().get(0).getOriginalValue().contains("BEGIN RSA PRIVATE KEY"));
    }

    @Test
    void detectsDatabaseUrls() {
        String content = """
                primary=jdbc:postgresql://db.internal:5432/app
                replica=jdbc:mysql://db.internal:3306/app
                legacy=jdbc:oracle:thin:@//db.internal:1521/app
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("DATABASE_URL", "DATABASE_URL", "DATABASE_URL"), entityTypeNames(result));
    }

    @Test
    void avoidsCommentedPasswordAssignments() {
        String content = "# password=admin123\npassword_policy=enabled";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    void prefersLongerApiKeyAssignmentOverEmbeddedJwt() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJzdWIiOiIxMjM0NTY3ODkwIn0."
                + "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String content = "api_key=" + token;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals("API_KEY", result.getEntities().get(0).getType().name());
        assertEquals(token, result.getEntities().get(0).getOriginalValue());
    }

    @Test
    void preservesSecretEntityOffsets() {
        String content = "client.secret=xyz";

        Entity entity = detectionEngine.detect(new DetectionContext(FILE_INFO, content)).getEntities().get(0);

        assertEquals("xyz", content.substring(entity.getStartOffset(), entity.getEndOffset()));
        assertEquals(EntityDomain.SECRETS, entity.getDomain());
    }

    @Test
    void detectsAadhaarInCommonFormatsWhenChecksumIsValid() {
        String content = """
                compact=123412341232
                spaced=1234 1234 1232
                dashed=1234-1234-1232
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("AADHAAR", "AADHAAR", "AADHAAR"), entityTypeNames(result));
        assertEquals("123412341232", result.getEntities().get(0).getOriginalValue());
        assertEquals("1234 1234 1232", result.getEntities().get(1).getOriginalValue());
        assertEquals("1234-1234-1232", result.getEntities().get(2).getOriginalValue());
    }

    @Test
    void rejectsInvalidAadhaarChecksum() {
        String content = """
                invalid=123412341234
                random=999999999999
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.AADHAAR));
    }

    @Test
    void rejectsAadhaarCandidatesWithInvalidLength() {
        String content = """
                short=12345678901
                long=1234567890123
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.AADHAAR));
    }

    @Test
    void detectsUpiIds() {
        String content = """
                primary=user@oksbi
                backup=customer@okicici
                alt=abc123@ybl
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("UPI_ID", "UPI_ID", "UPI_ID"), entityTypeNames(result));
        assertEquals("user@oksbi", result.getEntities().get(0).getOriginalValue());
        assertEquals("customer@okicici", result.getEntities().get(1).getOriginalValue());
        assertEquals("abc123@ybl", result.getEntities().get(2).getOriginalValue());
    }

    @Test
    void detectsCreditCardsInCommonFormats() {
        String content = """
                visa=4111111111111111
                mastercard=5555555555554444
                dashed=4012-8888-8888-1881
                spaced=4012 8888 8888 1881
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(
                List.of("CREDIT_CARD", "CREDIT_CARD", "CREDIT_CARD", "CREDIT_CARD"),
                entityTypeNames(result));
    }

    @Test
    void rejectsInvalidCreditCardNumbers() {
        String content = "invalid=4012-8888-8888-1882";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.CREDIT_CARD));
    }

    @Test
    void masksAndReportsNewEntityTypes() {
        String content = "aadhaar=123412341232 upi=user@oksbi card=4111111111111111";
        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("AADHAAR", "UPI_ID", "CREDIT_CARD"), entityTypeNames(result));
        assertEquals(EntityDomain.PII, result.getEntities().get(0).getDomain());
        assertEquals(EntityDomain.SPII, result.getEntities().get(1).getDomain());
        assertEquals(EntityDomain.SPII, result.getEntities().get(2).getDomain());
    }

    @Test
    void detectsValidGstinWithChecksum() {
        String content = """
                primary=27AAPFU0939F1ZV
                secondary=09AAAUP8175A1ZG
                vendor=29AAACB1234C1ZB
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("GSTIN", "GSTIN", "GSTIN"), entityTypeNames(result));
        assertEquals("27AAPFU0939F1ZV", result.getEntities().get(0).getOriginalValue());
        assertEquals(EntityDomain.SPII, result.getEntities().get(0).getDomain());
    }

    @Test
    void rejectsInvalidGstinChecksumAndFormat() {
        String content = """
                bad_checksum=29ABCDE1234F1Z5
                wrong_digit=27AAPFU0939F1ZA
                bad_state=99ABCDE1234F1Z5
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.GSTIN));
    }

    @Test
    void prefersGstinOverEmbeddedPan() {
        String content = "gstin=27AAPFU0939F1ZV";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(1, result.getEntities().size());
        assertEquals("GSTIN", result.getEntities().get(0).getType().name());
    }

    @Test
    void detectsValidPanWithSemanticHolderType() {
        String content = """
                person=ABCPA1234F
                company=AAACB5678D
                trust=XYZPT9012K
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("PAN", "PAN", "PAN"), entityTypeNames(result));
    }

    @Test
    void rejectsInvalidPanHolderType() {
        String content = """
                invalid=ABCDE1234F
                random=ABCDX1234F
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.PAN));
    }

    @Test
    void detectsValidTan() {
        String content = """
                primary=DELM12345L
                backup=MUMD12345F
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("TAN", "TAN"), entityTypeNames(result));
        assertEquals(EntityDomain.SPII, result.getEntities().get(0).getDomain());
    }

    @Test
    void rejectsInvalidTanStructure() {
        String content = """
                zero_seq=ABCD00000E
                short=ABCD1234E
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.TAN));
    }

    @Test
    void detectsValidCin() {
        String content = """
                listed=L17110MH1973PLC019786
                private=U72200KA2021PTC123456
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertEquals(List.of("CIN", "CIN"), entityTypeNames(result));
        assertEquals(EntityDomain.COMPANY, result.getEntities().get(0).getDomain());
    }

    @Test
    void rejectsInvalidCinStructure() {
        String content = """
                bad_listing=X17110MH1973PLC019786
                bad_state=L17110XX1973PLC019786
                bad_type=L17110MH1973ZZZ019786
                """;

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.CIN));
    }

    @Test
    void rejectsPanLikeFalsePositivesNearGstinContext() {
        String content = "note=ABCDE1234F is not a valid holder type";

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, content));

        assertTrue(result.getEntities().stream().noneMatch(entity -> entity.getType() == EntityType.PAN));
    }

    private static List<String> entityTypeNames(DetectionResult result) {
        return result.getEntities().stream()
                .map(entity -> entity.getType().name())
                .collect(Collectors.toList());
    }
}
