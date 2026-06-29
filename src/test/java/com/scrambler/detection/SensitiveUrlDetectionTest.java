package com.scrambler.detection;

import com.scrambler.inventory.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveUrlDetectionTest {

    private static final FileInfo FILE_INFO = new FileInfo(
            Paths.get("/workspace/repo/config/application.yml"),
            "config/application.yml",
            128L);

    private static final List<String> SHOULD_DETECT = List.of(
            "https://api.icicibank.com",
            "https://uat.icici.internal",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://10.0.0.5",
            "http://192.168.1.20",
            "jdbc:postgresql://db.internal:5432/app",
            "postgres://user:pass@db.internal/app",
            "mongodb://10.0.0.10/db");

    private static final List<String> SHOULD_NOT_DETECT_AS_URL = List.of(
            "https://github.com",
            "https://docs.djangoproject.com",
            "https://stackoverflow.com",
            "https://code.jquery.com",
            "https://cdn.jsdelivr.net",
            "https://python.org",
            "https://maven.apache.org");

    private DetectionEngine detectionEngine;

    @BeforeEach
    void setUp() {
        detectionEngine = new DetectionEngine();
    }

    @Test
    void detectsSensitiveInfrastructureUrls() {
        for (String url : SHOULD_DETECT) {
            if (isNativeDatabaseUrl(url)) {
                continue;
            }
            DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "endpoint=" + url));
            assertTrue(
                    result.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.URL),
                    "expected URL detection for: " + url + " but found: " + entityTypeNames(result));
        }
    }

    @Test
    void detectsNativeDatabaseUrls() {
        List<String> databaseUrls = SHOULD_DETECT.stream().filter(SensitiveUrlDetectionTest::isNativeDatabaseUrl).toList();
        for (String url : databaseUrls) {
            DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "endpoint=" + url));
            assertTrue(
                    result.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.DATABASE_URL),
                    "expected DATABASE_URL detection for: " + url + " but found: " + entityTypeNames(result));
        }
    }

    @Test
    void rejectsPublicDocumentationAndPackageUrls() {
        for (String url : SHOULD_NOT_DETECT_AS_URL) {
            DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "docs=" + url));
            assertFalse(
                    result.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.URL),
                    "did not expect URL detection for: " + url);
        }
    }

    @Test
    void reducesFalsePositivesOnCombinedTestCorpus() {
        String corpus = String.join("\n",
                SHOULD_DETECT.stream().map(url -> "sensitive=" + url).toList())
                + "\n"
                + String.join("\n", SHOULD_NOT_DETECT_AS_URL.stream().map(url -> "public=" + url).toList());

        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, corpus));
        long urlCount = result.getEntities().stream().filter(entity -> entity.getType() == EntityType.URL).count();
        long databaseUrlCount = result.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.DATABASE_URL)
                .count();

        assertEquals(6, urlCount, "sensitive HTTP(S) URLs in corpus");
        assertEquals(3, databaseUrlCount, "native database URLs in corpus");
        assertEquals(0, result.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.URL)
                .filter(entity -> SHOULD_NOT_DETECT_AS_URL.stream().anyMatch(entity.getOriginalValue()::contains))
                .count());
    }

    @Test
    void detectsInternalTldHosts() {
        assertDetectedUrl("https://db.internal/schema");
        assertDetectedUrl("https://api.local/health");
        assertDetectedUrl("https://redis.corp/cache");
        assertDetectedUrl("https://gateway.company.internal/route");
    }

    @Test
    void detectsPrivateIpRange172() {
        assertDetectedUrl("http://172.16.5.2/api");
        assertDetectedUrl("http://172.31.10.20/api");
    }

    @Test
    void doesNotDetectPublicIpAddressesInUrls() {
        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "cdn=https://8.8.8.8/query"));
        assertFalse(result.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.URL));
    }

    private void assertDetectedUrl(String url) {
        DetectionResult result = detectionEngine.detect(new DetectionContext(FILE_INFO, "endpoint=" + url));
        assertTrue(
                result.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.URL),
                "expected URL detection for: " + url);
    }

    private static boolean isNativeDatabaseUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("jdbc:")
                || lower.startsWith("postgres://")
                || lower.startsWith("postgresql://")
                || lower.startsWith("mysql://")
                || lower.startsWith("mongodb://")
                || lower.startsWith("mongodb+srv://")
                || lower.startsWith("redis://");
    }

    private static List<String> entityTypeNames(DetectionResult result) {
        return result.getEntities().stream()
                .map(entity -> entity.getType().name())
                .collect(Collectors.toList());
    }
}
