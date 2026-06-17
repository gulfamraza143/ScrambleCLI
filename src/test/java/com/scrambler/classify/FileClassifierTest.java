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
            "src/Main.kt, TEXT",
            "scripts/build.groovy, TEXT",
            "lib/util.py, TEXT",
            "web/app.js, TEXT",
            "web/component.jsx, TEXT",
            "web/app.ts, TEXT",
            "web/component.tsx, TEXT",
            "sql/schema.sql, TEXT",
            "config/settings.json, TEXT",
            "config/logback.xml, TEXT",
            "config/application.yml, TEXT",
            "config/application.YAML, TEXT",
            "config/db.properties, TEXT",
            "secrets/.env, TEXT",
            "config/app.conf, TEXT",
            "config/server.cfg, TEXT",
            "notes/readme.txt, TEXT",
            "scripts/deploy.sh, TEXT",
            "scripts/deploy.bash, TEXT",
            "scripts/run.bat, TEXT",
            "scripts/run.ps1, TEXT",
            "public/index.html, TEXT",
            "public/styles.css, TEXT",
            "views/page.jsp, TEXT",
            "certs/server.pem, TEXT",
            "certs/private.key, TEXT",
            "certs/root.crt, TEXT",
            "certs/keystore.p12, TEXT",
            "data/customers.csv, TEXT",
            "logs/application.log, TEXT"
    })
    void classifiesApprovedTextExtensions(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
        assertEquals(repoRelativePath, result.getFileInfo().getRepoRelativePath());
    }

    @ParameterizedTest
    @CsvSource({
            "README.md, SKIP",
            "docs/ARCHITECTURE.md, SKIP",
            "data/unknown, SKIP",
            "Makefile, SKIP",
            "Dockerfile, SKIP",
            "archive/file., SKIP",
            "random/file.xyz, SKIP",
            "legacy/module.go, SKIP",
            "native/app.exe, SKIP"
    })
    void classifiesUnknownOrSkippedPaths(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @ParameterizedTest
    @CsvSource({
            "report.pdf, DOCUMENT",
            "docs/guide.DOCX, DOCUMENT",
            "finance/budget.xlsx, DOCUMENT",
            "slides/deck.pptx, DOCUMENT",
            "docs/manual.odt, DOCUMENT",
            "finance/data.ods, DOCUMENT",
            "slides/show.odp, DOCUMENT"
    })
    void classifiesDocumentFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @ParameterizedTest
    @CsvSource({
            "logo.png, IMAGE",
            "assets/photo.JPEG, IMAGE",
            "icons/favicon.webp, IMAGE",
            "assets/icon.svg, IMAGE",
            "assets/photo.gif, IMAGE",
            "assets/photo.bmp, IMAGE"
    })
    void classifiesImageFiles(String repoRelativePath, FileCategory expectedCategory) {
        ClassificationResult result = fileClassifier.classify(fileInfo(repoRelativePath));

        assertEquals(expectedCategory, result.getCategory());
    }

    @ParameterizedTest
    @CsvSource({
            "app.jar, SKIP",
            "lib/service.war, SKIP",
            "lib/service.ear, SKIP",
            "build/App.CLASS, SKIP",
            "dist/archive.tar.gz, SKIP",
            "backup/data.7z, SKIP",
            "backup/data.rar, SKIP",
            "nested/path/archive.zip, SKIP",
            "runtime/app.dat, SKIP",
            "native/lib.so, SKIP",
            "native/lib.dylib, SKIP",
            "native/app.dll, SKIP",
            "native/app.bin, SKIP"
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
