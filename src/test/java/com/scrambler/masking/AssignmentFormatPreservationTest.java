package com.scrambler.masking;

import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.inventory.FileInfo;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.TestReportWriter;
import com.scrambler.unmasking.MappingIndex;
import com.scrambler.unmasking.MappingLoader;
import com.scrambler.unmasking.UnmaskingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentFormatPreservationTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.properties"),
            "config/application.properties",
            256L);

    private DetectionEngine detectionEngine;
    private MaskingEngine maskingEngine;
    private UnmaskingEngine unmaskingEngine;
    private MappingRegistry mappingRegistry;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
        maskingEngine = new MaskingEngine();
        unmaskingEngine = new UnmaskingEngine();
        mappingRegistry = new MappingRegistry();
    }

    @Test
    void masksPropertiesPasswordAssignment() {
        assertAssignmentPreserved("password=secret123", "password=");
    }

    @Test
    void masksPropertiesDbPasswordAssignment() {
        assertAssignmentPreserved("db.password=secret123", "db.password=");
    }

    @Test
    void masksYamlPasswordAssignment() {
        assertAssignmentPreserved("password: secret123", "password: ");
    }

    @Test
    void masksJsonPasswordAssignment() {
        String original = """
                {
                "password": "secret123"
                }
                """;
        String masked = mask(original);
        assertTrue(masked.contains("\"password\": \""));
        assertFalse(masked.contains("secret123"));
    }

    @Test
    void masksApiKeyAssignment() {
        assertAssignmentPreserved("api.key=abcdef", "api.key=");
    }

    @Test
    void masksSecretKeyAssignment() {
        assertAssignmentPreserved("client.secret=xyz", "client.secret=");
    }

    @Test
    void masksJwtAssignment() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJ1c2VyIjoiaWNpY2kifQ."
                + "SIGNATURETOKEN1234567890ABCDEF";
        assertAssignmentPreserved("jwt.token=" + token, "jwt.token=");
    }

    @Test
    void masksJdbcUrlAssignment() {
        String original = "jdbc.url=jdbc:postgresql://db.icici.internal:5432/test";
        String masked = mask(original);
        assertTrue(masked.startsWith("jdbc.url="));
        assertFalse(masked.contains("icici"));
        assertTrue(masked.contains("jdbc:postgresql://db."));
        assertTrue(masked.contains(".internal:5432/test"));
    }

    @Test
    void preservesJdbcUrlWithoutBrandTerms() {
        String original = "jdbc.url=jdbc:postgresql://localhost:5432/test";
        String masked = mask(original);
        assertEquals(original, masked);
    }

    @Test
    void roundtripRestoresOriginalContent(@TempDir Path tempDir) throws Exception {
        String original = """
                password=secret123
                db.password=root456
                api.key=abcdef
                client.secret=xyz
                jwt.token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoidGVzdCJ9.c2ln
                jdbc.url=jdbc:postgresql://localhost:5432/test
                """;

        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, original));
        String masked = maskingEngine.mask(original, detection, mappingRegistry);

        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        TestReportWriter.writeXlsx(mappingRegistry, reportPath);

        MappingIndex mappingIndex = MappingIndex.from(new MappingLoader().load(reportPath));
        String restored = unmaskingEngine.unmask(masked, mappingIndex, null);

        assertEquals(original, restored);
    }

    private void assertAssignmentPreserved(String original, String assignmentPrefix) {
        String masked = mask(original);
        assertTrue(masked.startsWith(assignmentPrefix));
        assertFalse(masked.contains(extractSecret(original)));
    }

    private String mask(String original) {
        mappingRegistry = new MappingRegistry();
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, original));
        return maskingEngine.mask(original, detection, mappingRegistry);
    }

    private static String extractSecret(String original) {
        int separator = Math.max(original.indexOf('='), original.indexOf(':'));
        if (separator < 0) {
            return original;
        }
        return original.substring(separator + 1).trim().replace("\"", "");
    }
}
