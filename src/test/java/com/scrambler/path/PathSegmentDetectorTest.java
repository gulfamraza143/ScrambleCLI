package com.scrambler.path;

import com.scrambler.config.CompanyDictionary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSegmentDetectorTest {

    private final PathSegmentDetector detector = new PathSegmentDetector();

    @Test
    void detectsSensitiveRepositoryName() {
        assertTrue(detector.isSensitiveRepositoryName("ICICI_CODE_BANK"));
    }

    @Test
    void detectsSensitiveFolderSegment() {
        assertTrue(detector.isSensitiveFolderSegment("ICICI_Config"));
    }

    @Test
    void detectsSensitiveFileName() {
        assertTrue(detector.isSensitiveFileName("ICICI_Secrets.txt"));
    }

    @Test
    void leavesGenericFoldersUnchanged() {
        assertFalse(detector.isSensitiveFolderSegment("src"));
        assertFalse(detector.isSensitiveFolderSegment("main"));
        assertFalse(detector.isSensitiveFolderSegment("java"));
        assertFalse(detector.isSensitiveFolderSegment("resources"));
        assertFalse(detector.isSensitiveFolderSegment("public"));
        assertFalse(detector.isSensitiveFolderSegment("test"));
        assertFalse(detector.isSensitiveFolderSegment("docs"));
        assertFalse(detector.isSensitiveFolderSegment("config"));
        assertFalse(detector.isSensitiveFolderSegment("node_modules"));
    }

    @Test
    void leavesGenericFilesUnchanged() {
        assertFalse(detector.isSensitiveFileName("application.yml"));
        assertFalse(detector.isSensitiveFileName("README.md"));
        assertFalse(detector.isSensitiveFileName("pom.xml"));
        assertFalse(detector.isSensitiveFileName("package.json"));
        assertFalse(detector.isSensitiveFileName("Dockerfile"));
    }

    @Test
    void usesCompanyDictionaryForBrandDetection() {
        CompanyDictionary dictionary = CompanyDictionary.loadFromResource("/company-dictionary.txt");
        PathSegmentDetector customDetector = new PathSegmentDetector(dictionary, com.scrambler.config.JiraProjectKeys.defaults());
        assertTrue(customDetector.isSensitiveFolderSegment("ICICI_Config"));
    }
}
