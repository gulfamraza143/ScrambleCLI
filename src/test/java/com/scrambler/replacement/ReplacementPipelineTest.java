package com.scrambler.replacement;

import com.scrambler.classify.FileClassifier;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.RepositoryInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplacementPipelineTest {

    private final PlaceholderAssetProvider placeholderAssetProvider = new PlaceholderAssetProvider();
    private final BinaryPlaceholderCopier binaryPlaceholderCopier = new BinaryPlaceholderCopier(placeholderAssetProvider);
    private final FileClassifier fileClassifier = new FileClassifier();

    @Test
    void replacesPdfWithDummyPdf(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("assets/report.pdf");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "secret customer report content");

        binaryPlaceholderCopier.replace(fileInfo(target, "assets/report.pdf"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("pdf"), Files.readAllBytes(target));
    }

    @Test
    void replacesDocxWithDummyDocument(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("docs/customer.docx");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "secret docx content");

        binaryPlaceholderCopier.replace(fileInfo(target, "docs/customer.docx"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("docx"), Files.readAllBytes(target));
    }

    @Test
    void replacesXlsxWithDummySpreadsheet(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("finance/customer.xlsx");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "secret spreadsheet content");

        binaryPlaceholderCopier.replace(fileInfo(target, "finance/customer.xlsx"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("xlsx"), Files.readAllBytes(target));
    }

    @Test
    void replacesPptxWithDummyDocument(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("slides/presentation.pptx");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "secret presentation content");

        binaryPlaceholderCopier.replace(fileInfo(target, "slides/presentation.pptx"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("pptx"), Files.readAllBytes(target));
    }

    @Test
    void replacesPngWithDummyImage(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("assets/logo.png");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        binaryPlaceholderCopier.replace(fileInfo(target, "assets/logo.png"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("png"), Files.readAllBytes(target));
    }

    @Test
    void replacesJpgWithDummyImage(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("assets/photo.jpg");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        binaryPlaceholderCopier.replace(fileInfo(target, "assets/photo.jpg"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("jpg"), Files.readAllBytes(target));
    }

    @Test
    void replacementPlanIncludesNestedDocumentAndImageFiles(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("nested/reports/report.pdf");
        Path png = tempDir.resolve("nested/assets/logo.png");
        Path java = tempDir.resolve("nested/src/App.java");
        Files.createDirectories(pdf.getParent());
        Files.createDirectories(png.getParent());
        Files.createDirectories(java.getParent());
        Files.writeString(pdf, "pdf");
        Files.writeString(png, "png");
        Files.writeString(java, "class App {}");

        RepositoryInventory inventory = new RepositoryInventory(List.of(
                fileInfo(pdf, "nested/reports/report.pdf"),
                fileInfo(png, "nested/assets/logo.png"),
                fileInfo(java, "nested/src/App.java")));

        ReplacementPlan plan = ReplacementPlan.from(inventory, fileClassifier);

        assertEquals(2, plan.getReplacementTargets().size());
        assertTrue(plan.getReplacementTargets().stream()
                .anyMatch(file -> file.getRepoRelativePath().equals("nested/reports/report.pdf")));
        assertTrue(plan.getReplacementTargets().stream()
                .anyMatch(file -> file.getRepoRelativePath().equals("nested/assets/logo.png")));
    }

    @Test
    void replacementPlanIgnoresTextSkipAndBinaryExtensions(@TempDir Path tempDir) throws Exception {
        Path java = tempDir.resolve("src/App.java");
        Path jar = tempDir.resolve("lib/app.jar");
        Path md = tempDir.resolve("README.md");
        Path unknown = tempDir.resolve("data/file.xyz");
        Files.createDirectories(java.getParent());
        Files.createDirectories(jar.getParent());
        Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(java, "code");
        Files.writeString(jar, "jar");
        Files.writeString(md, "docs");
        Files.writeString(unknown, "unknown");

        RepositoryInventory inventory = new RepositoryInventory(List.of(
                fileInfo(java, "src/App.java"),
                fileInfo(jar, "lib/app.jar"),
                fileInfo(md, "README.md"),
                fileInfo(unknown, "data/file.xyz")));

        ReplacementPlan plan = ReplacementPlan.from(inventory, fileClassifier);

        assertTrue(plan.getReplacementTargets().isEmpty());
    }

    @Test
    void replacesSvgWithDummyImage(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("assets/icon.svg");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "<svg></svg>");

        binaryPlaceholderCopier.replace(fileInfo(target, "assets/icon.svg"));

        assertArrayEquals(placeholderAssetProvider.loadPlaceholder("svg"), Files.readAllBytes(target));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "gif", "bmp", "webp"})
    void loadsImagePlaceholderForAllSupportedExtensions(String extension) {
        byte[] placeholder = placeholderAssetProvider.loadPlaceholder(extension);

        assertTrue(placeholder.length > 0);
        assertEquals("DUMMY_IMAGE.png", placeholderAssetProvider.resolveAssetName(extension));
    }

    private static FileInfo fileInfo(Path absolutePath, String repoRelativePath) throws Exception {
        return new FileInfo(absolutePath, repoRelativePath, Files.size(absolutePath));
    }
}
