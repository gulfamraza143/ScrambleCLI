package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts repository ZIP archives into a workspace using streaming I/O.
 */
public final class ZipExtractor {

    private static final int COPY_BUFFER_SIZE = 8192;

    private final WorkspaceManager workspaceManager;
    private final ScramblerConfig config;

    /**
     * Creates a ZIP extractor bound to workspace safety helpers and configuration limits.
     *
     * @param workspaceManager workspace manager providing zip-slip validation
     * @param config           configuration providing archive safety limits
     */
    public ZipExtractor(WorkspaceManager workspaceManager, ScramblerConfig config) {
        this.workspaceManager = workspaceManager;
        this.config = config;
    }

    /**
     * Extracts the given ZIP archive into the workspace extraction directory.
     *
     * @param zipFile   path to the repository ZIP archive
     * @param workspace active workspace receiving extracted content
     * @return extraction root directory containing repository files
     * @throws ArchiveException if the archive is missing, corrupt, unsafe, or exceeds configured limits
     */
    public Path extract(Path zipFile, Workspace workspace) {
        if (zipFile == null) {
            throw new ArchiveException("ZIP file path must not be null");
        }
        if (workspace == null) {
            throw new ArchiveException("Workspace must not be null");
        }
        if (!Files.isRegularFile(zipFile)) {
            throw new ArchiveException("ZIP file does not exist or is not a regular file: " + zipFile);
        }

        Path extractionRoot = workspace.getExtractionPath();
        long entryCount = 0L;
        long totalUncompressedBytes = 0L;

        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                try {
                    entryCount++;
                    if (entryCount > config.getMaxZipEntries()) {
                        throw new ArchiveException(
                                "ZIP entry count exceeds configured limit: " + config.getMaxZipEntries()
                        );
                    }

                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        createParentDirectories(targetPath, entry.getName());
                        long writtenBytes = copyEntry(zipInputStream, targetPath);
                        totalUncompressedBytes += writtenBytes;
                        if (totalUncompressedBytes > config.getMaxUncompressedBytes()) {
                            throw new ArchiveException(
                                    "Uncompressed ZIP size exceeds configured limit: "
                                            + config.getMaxUncompressedBytes()
                            );
                        }
                    }
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract ZIP archive: " + zipFile, e);
        }

        if (entryCount == 0L) {
            throw new ArchiveException("ZIP archive contains no entries: " + zipFile);
        }

        return extractionRoot;
    }

    private void createParentDirectories(Path targetPath, String entryName) throws IOException {
        Path parentDirectory = targetPath.getParent();
        if (parentDirectory == null) {
            throw new ArchiveException(
                    "ZIP entry resolves to a path without a parent directory: " + entryName
            );
        }
        Files.createDirectories(parentDirectory);
    }

    private long copyEntry(ZipInputStream zipInputStream, Path targetPath) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (var outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = zipInputStream.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > config.getMaxUncompressedBytes()) {
                    throw new ArchiveException(
                            "Uncompressed ZIP size exceeds configured limit: " + config.getMaxUncompressedBytes()
                    );
                }
                outputStream.write(buffer, 0, read);
            }
        }

        return totalBytes;
    }
}
