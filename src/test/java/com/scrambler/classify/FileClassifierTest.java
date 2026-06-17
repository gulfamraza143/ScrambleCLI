package com.scrambler.classify;

import com.scrambler.inventory.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileClassifierTest {

    private static final Path DUMMY_ABSOLUTE_PATH = Paths.get("/workspace/repo/example.txt");

    private FileClassifier fileClassifier;

    @BeforeEach
    void setUp() {
        fileClassifier = new FileClassifier();
    }

    @ParameterizedTest
    @CsvSource({
            "src/App.java, TEXT",
            "config/application.yml, TEXT",
            "config/application.YAML, TEXT",
            "README.md, TEXT",
            "secrets/.env, TEXT",
            "certs/server.PEM, TEXT",
            "data/unknown, TEXT",
            "Makefile, TEXT",
            "archive/file., TEXT"
    })
    void classifiesTextFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
        assertEquals(repoRelativePath, result.getFileInfo().getRepoRelativePath());
    }

    @ParameterizedTest
    @CsvSource({
            "report.pdf, DOCUMENT",
            "docs/guide.DOCX, DOCUMENT",
            "finance/budget.xlsx, DOCUMENT"
    })
    void classifiesDocumentFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @ParameterizedTest
    @CsvSource({
            "logo.png, IMAGE",
            "assets/photo.JPEG, IMAGE",
            "icons/favicon.webp, IMAGE"
    })
    void classifiesImageFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @ParameterizedTest
    @CsvSource({
            "app.jar, SKIP",
            "lib/service.war, SKIP",
            "build/App.CLASS, SKIP",
            "dist/archive.tar.gz, SKIP",
            "backup/data.7z, SKIP"
    })
    void classifiesSkipFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @Test
    void returnsSameFileInfoInstance() {
        FileInfo fileInfo = fileInfo("src/App.java");

        ClassificationResult result = fileClassifier.classify(fileInfo);

        assertSame(fileInfo, result.getFileInfo());
    }

    @Test
    void rejectsNullFileInfo() {
        assertThrows(NullPointerException.class, () -> fileClassifier.classify(null));
    }

    private static FileInfo fileInfo(String repoRelativePath) {
        return new FileInfo(DUMMY_ABSOLUTE_PATH, repoRelativePath, 1L);
    }
}
