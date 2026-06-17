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
 * Creates repository ZIP archives from an extracted workspace directory using streaming I/O.
 */
public final class ZipCreator {

    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String STAGING_ZIP_NAME = "output.zip.tmp";

    private final WorkspaceManager workspaceManager;

    /**
     * Creates a ZIP creator bound to workspace path normalization helpers.
     *
     * @param workspaceManager workspace manager providing repository-relative path normalization
     */
    public ZipCreator(WorkspaceManager workspaceManager) {
        this.workspaceManager = Objects.requireNonNull(workspaceManager, "workspaceManager must not be null");
    }

    /**
     * Packages the extracted repository tree into a ZIP archive.
     * The output is staged inside the workspace and atomically moved into place.
     *
     * @param sourceDirectory root directory containing repository files
     * @param outputZip       final ZIP output path; existing files are overwritten
     * @param workspace       active workspace used for atomic staging
     * @throws ArchiveException when packing fails or the source tree is empty
     */
    public void create(Path sourceDirectory, Path outputZip, Workspace workspace) {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
        Objects.requireNonNull(outputZip, "outputZip must not be null");
        Objects.requireNonNull(workspace, "workspace must not be null");

        if (!Files.isDirectory(sourceDirectory)) {
            throw new ArchiveException("Source directory does not exist or is not a directory: " + sourceDirectory);
        }

        List<Path> files = collectRegularFiles(sourceDirectory);
        if (files.isEmpty()) {
            throw new ArchiveException("Source directory contains no files: " + sourceDirectory);
        }

        Path stagingZip = workspace.getRootPath().resolve(STAGING_ZIP_NAME);
        try {
            writeZip(sourceDirectory, stagingZip, files);
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
            throw new ArchiveException("Failed to create ZIP archive: " + outputZip, e);
        }
    }

    private List<Path> collectRegularFiles(Path sourceDirectory) {
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new ArchiveException("Failed to inventory source directory: " + sourceDirectory, e);
        }
    }

    private void writeZip(Path sourceDirectory, Path stagingZip, List<Path> files) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(stagingZip))) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            for (Path file : files) {
                String entryName = workspaceManager.toRepoRelativePath(sourceDirectory, file);
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
        } catch (IOException e) {
            throw new ArchiveException("Failed to write staged ZIP archive: " + stagingZip, e);
        } catch (ArchiveException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArchiveException("Failed to write staged ZIP archive: " + stagingZip, e);
        }
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
