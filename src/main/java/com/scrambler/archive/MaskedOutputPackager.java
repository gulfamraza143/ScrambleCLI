package com.scrambler.archive;

import com.scrambler.exception.ArchiveException;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages a masked repository folder into a ZIP archive.
 */
public final class MaskedOutputPackager {

    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String STAGING_ZIP_NAME = "output.zip.tmp";

    private final WorkspaceManager workspaceManager;

    /**
     * Creates a packager bound to workspace path normalization helpers.
     *
     * @param workspaceManager workspace manager providing repository-relative path normalization
     */
    public MaskedOutputPackager(WorkspaceManager workspaceManager) {
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager must not be null");
    }

    /**
     * Creates a masked repository output archive.
     *
     * @param repositoryRoot   directory containing masked repository contents
     * @param repositoryFolder repository folder name inside the ZIP
     * @param outputZip        final ZIP output path
     * @param workspace        active workspace used for atomic staging
     */
    public void create(
            Path repositoryRoot,
            String repositoryFolder,
            Path outputZip,
            Workspace workspace) {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null");
        Objects.requireNonNull(repositoryFolder, "repositoryFolder must not be null");
        Objects.requireNonNull(outputZip, "outputZip must not be null");
        Objects.requireNonNull(workspace, "workspace must not be null");

        if (!Files.isDirectory(repositoryRoot)) {
            throw new ArchiveException("Repository root does not exist or is not a directory: " + repositoryRoot);
        }

        List<Path> repositoryFiles = collectRegularFiles(repositoryRoot);
        if (repositoryFiles.isEmpty()) {
            throw new ArchiveException("Repository root contains no files: " + repositoryRoot);
        }

        Path stagingZip = workspace.getRootPath().resolve(STAGING_ZIP_NAME);
        try {
            writeZip(repositoryRoot, repositoryFolder, stagingZip, repositoryFiles);
            Path parent = outputZip.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            moveToOutput(stagingZip, outputZip);
        } catch (ArchiveException e) {
            deleteQuietly(stagingZip);
            throw e;
        } catch (IOException e) {
            deleteQuietly(stagingZip);
            throw new ArchiveException("Failed to create masked repository ZIP archive: " + outputZip, e);
        }
    }

    private List<Path> collectRegularFiles(Path repositoryRoot) {
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new ArchiveException("Failed to inventory repository root: " + repositoryRoot, e);
        }
    }

    private void writeZip(
            Path repositoryRoot,
            String repositoryFolder,
            Path stagingZip,
            List<Path> repositoryFiles) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(stagingZip))) {
            for (Path file : repositoryFiles) {
                String relativePath = workspaceManager.toRepoRelativePath(repositoryRoot, file);
                String entryName = repositoryFolder + "/" + relativePath;
                writeFileEntry(zipOutputStream, entryName, file, buffer);
            }
        }
    }

    private static void writeFileEntry(
            ZipOutputStream zipOutputStream,
            String entryName,
            Path file,
            byte[] buffer) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(entry);
        try (InputStream inputStream = Files.newInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, read);
            }
        }
        zipOutputStream.closeEntry();
    }

    private static void moveToOutput(Path stagingZip, Path outputZip) throws IOException {
        try {
            Files.move(stagingZip, outputZip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(stagingZip, outputZip, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup must not mask the original archive failure.
        }
    }
}
