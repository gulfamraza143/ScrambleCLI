package com.scrambler.archive;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.security.OsMetadataGuard;
import com.scrambler.security.SymbolicLinkGuard;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * Extracts repository ZIP archives into a workspace using random-access I/O so
 * central-directory metadata (including Unix symlink attributes) is available.
 */
public final class ZipExtractor {

    private static final int COPY_BUFFER_SIZE = 8192;

    private final WorkspaceManager workspaceManager;
    private final ScramblerConfig config;

    public ZipExtractor(WorkspaceManager workspaceManager, ScramblerConfig config) {
        this.workspaceManager = workspaceManager;
        this.config = config;
    }

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

        extractZipFileInto(zipFile, workspace.getExtractionPath(), workspace.getExtractionBudget());
        return workspace.getExtractionPath();
    }

    void extractZipFileInto(Path zipFile, Path extractionRoot, ExtractionBudget budget) {
        long entryCount = 0L;
        long archiveUncompressedBytes = 0L;
        long compressedArchiveSize;

        try {
            compressedArchiveSize = Files.size(zipFile);
        } catch (IOException e) {
            throw new ArchiveException("Failed to read ZIP archive size: " + zipFile, e);
        }

        try (ZipFile zipArchive = ZipFile.builder().setPath(zipFile).get()) {
            Enumeration<ZipArchiveEntry> entries = zipArchive.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                validateEntryCount(entryCount);

                if (SymbolicLinkGuard.isZipSymlink(entry)) {
                    SymbolicLinkGuard.logSkippedArchiveSymlink(entry.getName());
                    continue;
                }

                if (OsMetadataGuard.isOsMetadataEntryName(entry.getName())) {
                    OsMetadataGuard.logSkippedOsMetadataEntry(entry.getName());
                    continue;
                }

                budget.validateEntryCompressionRatio(entry.getCompressedSize(), entry.getSize());

                Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    createParentDirectories(targetPath, entry.getName());
                    if (budget.skipIfOversized(entry.getName(), entry.getSize())) {
                        continue;
                    }
                    try (InputStream inputStream = zipArchive.getInputStream(entry)) {
                        long writtenBytes = copyEntry(inputStream, targetPath, entry.getName(), budget);
                        archiveUncompressedBytes += writtenBytes;
                    }
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

        budget.validateArchiveCompressionRatio(compressedArchiveSize, archiveUncompressedBytes);
    }

    private void validateEntryCount(long entryCount) {
        if (entryCount > config.getMaxZipEntries()) {
            throw new ArchiveException("Archive entry count exceeds configured limit: " + config.getMaxZipEntries());
        }
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

    private long copyEntry(
            InputStream inputStream,
            Path targetPath,
            String entryName,
            ExtractionBudget budget) throws IOException {
        long totalBytes = 0L;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > config.getMaxSingleFileSize()) {
                    outputStream.close();
                    Files.deleteIfExists(targetPath);
                    budget.skipIfOversized(entryName, totalBytes);
                    drainStream(inputStream);
                    return 0L;
                }
                budget.ensureAdditionalBytesAllowed(read);
                outputStream.write(buffer, 0, read);
            }
        }

        budget.recordExtractedFile(entryName, totalBytes);
        return totalBytes;
    }

    private static void drainStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        while (inputStream.read(buffer) != -1) {
            // Discard remaining entry bytes.
        }
    }
}
