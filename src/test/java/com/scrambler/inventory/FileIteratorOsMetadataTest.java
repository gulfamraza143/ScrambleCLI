package com.scrambler.inventory;

import com.scrambler.workspace.WorkspaceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIteratorOsMetadataTest {

    @TempDir
    Path tempDir;

    private FileIterator fileIterator;

    @BeforeEach
    void setUp() {
        fileIterator = new FileIterator(new WorkspaceManager());
    }

    @Test
    void skipsOsMetadataFilesDuringInventory() throws Exception {
        Path repoRoot = tempDir.resolve("fxtp-develop");
        Files.createDirectories(repoRoot.resolve("__MACOSX/fxtp-develop"));
        Files.writeString(repoRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(repoRoot.resolve("README.md"), "# docs");
        Files.writeString(repoRoot.resolve(".pre-commit-config.yaml"), "repos: []");
        Files.write(repoRoot.resolve("__MACOSX/fxtp-develop/._.pre-commit-config.yaml"), new byte[]{(byte) 0xFF});
        Files.writeString(repoRoot.resolve("._ignored.yaml"), "ignored");
        Files.writeString(repoRoot.resolve(".DS_Store"), "metadata");
        Files.writeString(repoRoot.resolve("Thumbs.db"), "metadata");

        String logOutput = captureStderr(() -> {
            List<FileInfo> files = fileIterator.collectFiles(repoRoot);
            List<String> paths = files.stream().map(FileInfo::getRepoRelativePath).collect(Collectors.toList());
            assertEquals(3, files.size());
            assertTrue(paths.contains("pom.xml"));
            assertTrue(paths.contains("README.md"));
            assertTrue(paths.contains(".pre-commit-config.yaml"));
            assertFalse(paths.stream().anyMatch(path -> path.contains("__MACOSX")));
            assertFalse(paths.stream().anyMatch(path -> path.startsWith("._")));
        });

        assertTrue(logOutput.contains("WARN Skipping OS metadata: __MACOSX/fxtp-develop/._.pre-commit-config.yaml"));
        assertTrue(logOutput.contains("WARN Skipping OS metadata: ._ignored.yaml"));
        assertTrue(logOutput.contains("WARN Skipping OS metadata: .DS_Store"));
        assertTrue(logOutput.contains("WARN Skipping OS metadata: Thumbs.db"));
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
