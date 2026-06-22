package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.FileInfo;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveExtractionLimitsTest {

    @TempDir
    Path tempDir;

    private WorkspaceManager workspaceManager;

    @BeforeEach
    void setUp() {
        workspaceManager = new WorkspaceManager();
    }

    @Test
    void rejectsDeepNestedArchiveChain() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxNestedArchiveDepth(5)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);
        NestedArchiveProcessor nestedProcessor = new NestedArchiveProcessor(new FileIterator(workspaceManager), extractor);

        Path rootZip = createNestedZipChain(7);
        Workspace workspace = workspaceManager.createWorkspace(config);

        extractor.extract(rootZip, workspace);

        ArchiveException failure = assertThrows(
                ArchiveException.class,
                () -> nestedProcessor.expandArchives(workspace.getExtractionPath(), workspace));
        assertTrue(failure.getMessage().contains("Nested archive depth exceeded: depth=6"));
    }

    @Test
    void rejectsGlobalExtractionSizeLimit() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxExtractedBytes(1_000L)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);
        NestedArchiveProcessor nestedProcessor = new NestedArchiveProcessor(new FileIterator(workspaceManager), extractor);

        Path outerZip = tempDir.resolve("repo.zip");
        Path innerZip = tempDir.resolve("inner.zip");
        createZip(innerZip, "nested.txt", "x".repeat(600));
        createZipWithNestedArchive(outerZip, "outer.txt", "y".repeat(600), "inner.zip", Files.readAllBytes(innerZip));

        Workspace workspace = workspaceManager.createWorkspace(config);
        extractor.extract(outerZip, workspace);

        ArchiveException failure = assertThrows(
                ArchiveException.class,
                () -> nestedProcessor.expandArchives(workspace.getExtractionPath(), workspace));
        assertEquals("Extraction size limit exceeded", failure.getMessage());
    }

    @Test
    void rejectsExcessiveGlobalFileCount() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxExtractedFiles(3L)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);

        Path zipPath = tempDir.resolve("many-files.zip");
        try (ZipArchiveOutputStream zipOutputStream =
                     new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            for (int index = 0; index < 4; index++) {
                ZipArchiveEntry entry = new ZipArchiveEntry("file-" + index + ".txt");
                byte[] content = ("file-" + index).getBytes(StandardCharsets.UTF_8);
                entry.setSize(content.length);
                zipOutputStream.putArchiveEntry(entry);
                zipOutputStream.write(content);
                zipOutputStream.closeArchiveEntry();
            }
        }

        Workspace workspace = workspaceManager.createWorkspace(config);
        ArchiveException failure = assertThrows(
                ArchiveException.class,
                () -> extractor.extract(zipPath, workspace));
        assertEquals("Extraction file count limit exceeded", failure.getMessage());
    }

    @Test
    void skipsOversizedFileWithoutFailingRepository() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxSingleFileSize(100L)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);
        FileIterator fileIterator = new FileIterator(workspaceManager);

        Path zipPath = tempDir.resolve("mixed.zip");
        createZip(zipPath, "small.txt", "ok");
        appendToZip(zipPath, "large.txt", "x".repeat(200).getBytes(StandardCharsets.UTF_8));

        String logOutput = captureStderr(() -> {
            Workspace workspace = workspaceManager.createWorkspace(config);
            extractor.extract(zipPath, workspace);
            List<FileInfo> files = fileIterator.collectFiles(workspace.getExtractionPath());
            assertEquals(1, files.size());
            assertEquals("small.txt", files.get(0).getRepoRelativePath());
            assertFalse(Files.exists(workspace.getExtractionPath().resolve("large.txt")));
        });

        assertTrue(logOutput.contains("WARN Skipping oversized file: large.txt"));
    }

    @Test
    void rejectsSuspiciousCompressionRatio() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxCompressionRatio(10)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);

        Path zipPath = tempDir.resolve("ratio-bomb.zip");
        try (ZipArchiveOutputStream zipOutputStream =
                     new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            ZipArchiveEntry entry = new ZipArchiveEntry("bomb.txt");
            byte[] payload = "A".repeat(5_000).getBytes(StandardCharsets.UTF_8);
            entry.setSize(payload.length);
            entry.setMethod(ZipArchiveEntry.DEFLATED);
            zipOutputStream.putArchiveEntry(entry);
            zipOutputStream.write(payload);
            zipOutputStream.closeArchiveEntry();
        }

        Workspace workspace = workspaceManager.createWorkspace(config);
        ArchiveException failure = assertThrows(
                ArchiveException.class,
                () -> extractor.extract(zipPath, workspace));
        assertEquals("Suspicious compression ratio detected", failure.getMessage());
    }

    @Test
    void normalRepositoryExtractsSuccessfully() throws IOException {
        ScramblerConfig config = limitsConfig().build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);
        NestedArchiveProcessor nestedProcessor = new NestedArchiveProcessor(new FileIterator(workspaceManager), extractor);

        Path zipPath = tempDir.resolve("repo.zip");
        createZip(zipPath, "App.java", "public class App {}");
        createZip(tempDir.resolve("lib.zip"), "Util.java", "class Util {}");
        appendToZip(zipPath, "lib.zip", Files.readAllBytes(tempDir.resolve("lib.zip")));

        Workspace workspace = workspaceManager.createWorkspace(config);
        extractor.extract(zipPath, workspace);
        int expanded = nestedProcessor.expandArchives(workspace.getExtractionPath(), workspace);

        assertEquals(1, expanded);
        List<FileInfo> files = new FileIterator(workspaceManager).collectFiles(workspace.getExtractionPath());
        assertEquals(2, files.size());
    }

    @Test
    void nestedArchivesWithinDepthAndSizeLimitsSucceed() throws IOException {
        ScramblerConfig config = limitsConfig()
                .maxNestedArchiveDepth(5)
                .maxExtractedBytes(10_000L)
                .build();
        ArchiveExtractor extractor = new ArchiveExtractor(workspaceManager, config);
        NestedArchiveProcessor nestedProcessor = new NestedArchiveProcessor(new FileIterator(workspaceManager), extractor);

        Path rootZip = createNestedZipChain(3);
        Workspace workspace = workspaceManager.createWorkspace(config);
        extractor.extract(rootZip, workspace);
        nestedProcessor.expandArchives(workspace.getExtractionPath(), workspace);

        assertTrue(Files.isRegularFile(workspace.getExtractionPath()
                .resolve("level2")
                .resolve("level3")
                .resolve("leaf.txt")));
    }

    @Test
    void computeNestedDepthUsesExtractionRootRelativePath() {
        Path extractionRoot = Path.of("/workspace/extracted");
        assertEquals(1, ExtractionBudget.computeNestedDepth(
                extractionRoot.resolve("a.zip"), extractionRoot));
        assertEquals(3, ExtractionBudget.computeNestedDepth(
                extractionRoot.resolve("a").resolve("b").resolve("c.zip"), extractionRoot));
    }

    private ScramblerConfig.Builder limitsConfig() {
        return ScramblerConfig.builder().workspaceBasePath(tempDir);
    }

    private Path createNestedZipChain(int depth) throws IOException {
        Path leaf = tempDir.resolve("leaf.txt");
        Files.writeString(leaf, "leaf-content");

        Path currentZip = tempDir.resolve("leaf-only.zip");
        createZip(currentZip, "leaf.txt", Files.readString(leaf));

        for (int level = depth; level >= 1; level--) {
            Path zipPath = tempDir.resolve("level" + level + ".zip");
            if (level == depth) {
                createZip(zipPath, "leaf.txt", Files.readString(leaf));
            } else {
                Path innerZip = tempDir.resolve("level" + (level + 1) + ".zip");
                createZipWithNestedArchive(
                        zipPath,
                        "marker-" + level + ".txt",
                        "level-" + level,
                        "level" + (level + 1) + ".zip",
                        Files.readAllBytes(innerZip));
            }
        }
        return tempDir.resolve("level1.zip");
    }

    private static void createZip(Path zipPath, String entryName, String content) throws IOException {
        createZip(zipPath, entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void createZip(Path zipPath, String entryName, byte[] content) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content);
            zipOutputStream.closeEntry();
        }
    }

    private static void createZipWithNestedArchive(
            Path zipPath,
            String regularName,
            String regularContent,
            String nestedName,
            byte[] nestedBytes) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zipOutputStream.putNextEntry(new ZipEntry(regularName));
            zipOutputStream.write(regularContent.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(nestedName));
            zipOutputStream.write(nestedBytes);
            zipOutputStream.closeEntry();
        }
    }

    private static void appendToZip(Path zipPath, String entryName, byte[] content) throws IOException {
        Path updatedZip = zipPath.resolveSibling(zipPath.getFileName() + ".tmp");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(updatedZip))) {
            if (Files.exists(zipPath)) {
                try (var inputStream = Files.newInputStream(zipPath);
                     var existingZip = new java.util.zip.ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    while ((entry = existingZip.getNextEntry()) != null) {
                        zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                        existingZip.transferTo(zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                }
            }
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content);
            zipOutputStream.closeEntry();
        }
        Files.move(updatedZip, zipPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String captureStderr(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(originalErr);
        }
    }
}
