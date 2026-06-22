package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.FileInfo;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ArchiveExtractorSymlinkTest {

    @TempDir
    Path tempDir;

    private WorkspaceManager workspaceManager;
    private ArchiveExtractor archiveExtractor;
    private FileIterator fileIterator;

    @BeforeEach
    void setUp() {
        workspaceManager = new WorkspaceManager();
        ScramblerConfig config = ScramblerConfig.builder().workspaceBasePath(tempDir).build();
        archiveExtractor = new ArchiveExtractor(workspaceManager, config);
        fileIterator = new FileIterator(workspaceManager);
        assumeSymbolicLinksSupported();
    }

    @Test
    void skipsSymlinkEntriesInZipArchive() throws IOException {
        Path zipPath = tempDir.resolve("repo.zip");
        createZipWithSymlink(zipPath, "App.java", "class App {}", "secret.txt", "/etc/passwd");

        Workspace workspace = workspaceManager.createWorkspace(
                ScramblerConfig.builder().workspaceBasePath(tempDir).build());

        String logOutput = captureStderr(() -> {
            Path extractionRoot = archiveExtractor.extract(zipPath, workspace);
            assertFalse(Files.exists(extractionRoot.resolve("secret.txt")));
            assertTrue(Files.isRegularFile(extractionRoot.resolve("App.java")));

            List<FileInfo> files = fileIterator.collectFiles(extractionRoot);
            assertEquals(1, files.size());
            assertEquals("App.java", files.get(0).getRepoRelativePath());
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: secret.txt"));
    }

    @Test
    void skipsSymlinkEntriesInTarArchive() throws IOException {
        Path tarPath = tempDir.resolve("repo.tar");
        createTarWithSymlink(tarPath);

        Workspace workspace = workspaceManager.createWorkspace(
                ScramblerConfig.builder().workspaceBasePath(tempDir).build());

        String logOutput = captureStderr(() -> {
            Path extractionRoot = archiveExtractor.extract(tarPath, workspace);
            assertFalse(Files.exists(extractionRoot.resolve("secret.txt")));
            assertTrue(Files.isRegularFile(extractionRoot.resolve("Config.yml")));

            List<FileInfo> files = fileIterator.collectFiles(extractionRoot);
            assertEquals(1, files.size());
            assertEquals("Config.yml", files.get(0).getRepoRelativePath());
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: secret.txt"));
    }

    @Test
    void skipsSymlinkInsideNestedZipArchive() throws IOException {
        Path innerZip = tempDir.resolve("inner.zip");
        createZipWithSymlink(innerZip, "nested.txt", "nested content", "link.txt", "/etc/hosts");

        Path outerZip = tempDir.resolve("outer.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(outerZip))) {
            zipOutputStream.putNextEntry(new ZipEntry("bundle.zip"));
            zipOutputStream.write(Files.readAllBytes(innerZip));
            zipOutputStream.closeEntry();
        }

        Workspace workspace = workspaceManager.createWorkspace(
                ScramblerConfig.builder().workspaceBasePath(tempDir).build());

        String logOutput = captureStderr(() -> {
            Path extractionRoot = archiveExtractor.extract(outerZip, workspace);
            new NestedArchiveProcessor(fileIterator, archiveExtractor).expandArchives(extractionRoot, workspace);

            assertFalse(Files.exists(extractionRoot.resolve("bundle/link.txt")));
            assertTrue(Files.isRegularFile(extractionRoot.resolve("bundle/nested.txt")));

            List<FileInfo> files = fileIterator.collectFiles(extractionRoot);
            assertEquals(1, files.size());
            assertEquals("bundle/nested.txt", files.get(0).getRepoRelativePath());
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: link.txt"));
    }

    @Test
    void skipsSymlinksWhenCopyingDirectoryInput() throws IOException {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "class App {}");
        Files.writeString(repoRoot.resolve("Config.yml"), "key: value");
        Files.createSymbolicLink(repoRoot.resolve("secret.txt"), Path.of("/etc/passwd"));

        Workspace workspace = workspaceManager.createWorkspace(
                ScramblerConfig.builder().workspaceBasePath(tempDir).build());

        String logOutput = captureStderr(() -> {
            Path extractionRoot = archiveExtractor.extract(repoRoot, workspace);
            assertFalse(Files.exists(extractionRoot.resolve("secret.txt")));
            assertTrue(Files.isRegularFile(extractionRoot.resolve("App.java")));
            assertTrue(Files.isRegularFile(extractionRoot.resolve("Config.yml")));
        });

        assertTrue(logOutput.contains("WARN Skipping symbolic link: secret.txt"));
    }

    private static void createZipWithSymlink(
            Path zipPath,
            String regularName,
            String regularContent,
            String symlinkName,
            String symlinkTarget) throws IOException {
        try (ZipArchiveOutputStream zipOutputStream =
                     new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            ZipArchiveEntry regularEntry = new ZipArchiveEntry(regularName);
            byte[] regularBytes = regularContent.getBytes(StandardCharsets.UTF_8);
            regularEntry.setSize(regularBytes.length);
            zipOutputStream.putArchiveEntry(regularEntry);
            zipOutputStream.write(regularBytes);
            zipOutputStream.closeArchiveEntry();

            ZipArchiveEntry symlinkEntry = new ZipArchiveEntry(symlinkName);
            symlinkEntry.setUnixMode(org.apache.commons.compress.archivers.zip.UnixStat.LINK_FLAG
                    | org.apache.commons.compress.archivers.zip.UnixStat.DEFAULT_LINK_PERM);
            byte[] linkBytes = symlinkTarget.getBytes(StandardCharsets.UTF_8);
            symlinkEntry.setSize(linkBytes.length);
            zipOutputStream.putArchiveEntry(symlinkEntry);
            zipOutputStream.write(linkBytes);
            zipOutputStream.closeArchiveEntry();
        }
    }

    private static void createTarWithSymlink(Path tarPath) throws IOException {
        try (TarArchiveOutputStream tarOutputStream =
                     new TarArchiveOutputStream(Files.newOutputStream(tarPath))) {
            byte[] configContent = "key: value".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry configEntry = new TarArchiveEntry("Config.yml");
            configEntry.setSize(configContent.length);
            tarOutputStream.putArchiveEntry(configEntry);
            tarOutputStream.write(configContent);
            tarOutputStream.closeArchiveEntry();

            String linkTarget = "/etc/passwd";
            TarArchiveEntry symlinkEntry = new TarArchiveEntry("secret.txt", TarArchiveEntry.LF_SYMLINK);
            symlinkEntry.setLinkName(linkTarget);
            byte[] linkBytes = linkTarget.getBytes(StandardCharsets.UTF_8);
            symlinkEntry.setSize(linkBytes.length);
            tarOutputStream.putArchiveEntry(symlinkEntry);
            tarOutputStream.write(linkBytes);
            tarOutputStream.closeArchiveEntry();
        }
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
