package com.scrambler.masking;

import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.inventory.FileInfo;
import com.scrambler.report.CsvReportWriter;
import com.scrambler.report.ReportSchema;
import com.scrambler.unmasking.MappingIndex;
import com.scrambler.unmasking.MappingLoader;
import com.scrambler.unmasking.UnmaskingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertMasked(
                "password=secret123",
                "password=SCRAMBLE_PASSWORD_000001");
    }

    @Test
    void masksPropertiesDbPasswordAssignment() {
        assertMasked(
                "db.password=secret123",
                "db.password=SCRAMBLE_PASSWORD_000001");
    }

    @Test
    void masksYamlPasswordAssignment() {
        assertMasked(
                "password: secret123",
                "password: SCRAMBLE_PASSWORD_000001");
    }

    @Test
    void masksJsonPasswordAssignment() {
        assertMasked(
                """
                        {
                        "password": "secret123"
                        }
                        """,
                """
                        {
                        "password": "SCRAMBLE_PASSWORD_000001"
                        }
                        """);
    }

    @Test
    void masksApiKeyAssignment() {
        assertMasked(
                "api.key=abcdef",
                "api.key=SCRAMBLE_API_KEY_000001");
    }

    @Test
    void masksSecretKeyAssignment() {
        assertMasked(
                "client.secret=xyz",
                "client.secret=SCRAMBLE_SECRET_KEY_000001");
    }

    @Test
    void masksJwtAssignment() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJ1c2VyIjoiaWNpY2kifQ."
                + "SIGNATURETOKEN1234567890ABCDEF";
        assertMasked(
                "jwt.token=" + token,
                "jwt.token=SCRAMBLE_JWT_000001");
    }

    @Test
    void masksJdbcUrlAssignment() {
        assertMasked(
                "jdbc.url=jdbc:postgresql://localhost:5432/test",
                "jdbc.url=SCRAMBLE_DATABASE_URL_000001");
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
        new CsvReportWriter().write(mappingRegistry, reportPath);

        MappingIndex mappingIndex = MappingIndex.from(new MappingLoader().load(reportPath));
        String restored = unmaskingEngine.unmask(masked, mappingIndex, null);

        assertEquals(original, restored);
    }

    private void assertMasked(String original, String expectedMasked) {
        mappingRegistry = new MappingRegistry();
        DetectionResult detection = detectionEngine.detect(new DetectionContext(FILE_INFO, original));
        String masked = maskingEngine.mask(original, detection, mappingRegistry);
        assertEquals(expectedMasked, masked);
    }
}
