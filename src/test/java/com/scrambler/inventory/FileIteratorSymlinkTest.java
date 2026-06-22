package com.scrambler.inventory;

import com.scrambler.workspace.WorkspaceManager;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileIteratorSymlinkTest {

    @TempDir
    Path tempDir;

    private FileIterator fileIterator;

    @BeforeEach
    void setUp() {
        fileIterator = new FileIterator(new WorkspaceManager());
        assumeSymbolicLinksSupported();
    }

    @Test
    void skipsSymlinkedFileAndLogsWarning() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "class App {}");
        Files.createSymbolicLink(repoRoot.resolve("secret.txt"), Path.of("/etc/passwd"));

        String logOutput = captureStderr(() -> {
            List<FileInfo> files = fileIterator.collectFiles(repoRoot);
            assertEquals(1, files.size());
            assertEquals("App.java", files.get(0).getRepoRelativePath());
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: secret.txt"));
    }

    @Test
    void skipsSymlinkedDirectoryAndDoesNotTraverseTarget() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Path realDir = repoRoot.resolve("real");
        Files.createDirectories(realDir);
        Files.writeString(realDir.resolve("inside.txt"), "content");
        Files.createSymbolicLink(repoRoot.resolve("linkdir"), realDir);
        Files.writeString(repoRoot.resolve("App.java"), "class App {}");

        String logOutput = captureStderr(() -> {
            List<FileInfo> files = fileIterator.collectFiles(repoRoot);
            List<String> paths = files.stream().map(FileInfo::getRepoRelativePath).collect(Collectors.toList());
            assertEquals(2, files.size());
            assertTrue(paths.contains("App.java"));
            assertTrue(paths.contains("real/inside.txt"));
            assertFalse(paths.contains("linkdir/inside.txt"));
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: linkdir"));
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

    private static void assumeSymbolicLinksSupported() {
        try {
            Path probe = Files.createTempDirectory("symlink-probe");
            Path target = probe.resolve("target.txt");
            Files.writeString(target, "probe");
            Path link = probe.resolve("link.txt");
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(probe);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "Symbolic links not supported in this environment");
        }
    }
}
