package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.security.SymbolicLinkGuard;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Extracts repository archives in ZIP, TAR, TGZ, or 7Z format into a workspace.
 */
public final class ArchiveExtractor {

    private static final int COPY_BUFFER_SIZE = 8192;

    private final WorkspaceManager workspaceManager;
    private final ScramblerConfig config;
    private final ZipExtractor zipExtractor;

    public ArchiveExtractor(WorkspaceManager workspaceManager, ScramblerConfig config) {
        this.workspaceManager = workspaceManager;
        this.config = config;
        this.zipExtractor = new ZipExtractor(workspaceManager, config);
    }

    public Path extract(Path inputPath, Workspace workspace) {
        if (inputPath == null) {
            throw new ArchiveException("Input path must not be null");
        }
        if (workspace == null) {
            throw new ArchiveException("Workspace must not be null");
        }
        if (Files.isDirectory(inputPath)) {
            return copyDirectory(inputPath, workspace.getExtractionPath(), workspace.getExtractionBudget());
        }
        if (!Files.isRegularFile(inputPath)) {
            throw new ArchiveException("Input does not exist or is not a regular file: " + inputPath);
        }

        String fileName = inputPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            return zipExtractor.extract(inputPath, workspace);
        }
        if (fileName.endsWith(".tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            return extractTar(inputPath, workspace);
        }
        if (fileName.endsWith(".7z")) {
            return extractSevenZ(inputPath, workspace);
        }
        throw new ArchiveException("Unsupported archive format: " + inputPath);
    }

    public void extractNestedArchive(Path archivePath, Workspace workspace) {
        Path parentDirectory = archivePath.getParent();
        if (parentDirectory == null) {
            throw new ArchiveException("Nested archive has no parent directory: " + archivePath);
        }

        ExtractionBudget budget = workspace.getExtractionBudget();
        int depth = ExtractionBudget.computeNestedDepth(archivePath, workspace.getExtractionPath());
        budget.validateNestedDepth(depth);

        Path stagingDirectory = parentDirectory.resolve(stripArchiveExtension(archivePath.getFileName().toString()));
        try {
            Files.createDirectories(stagingDirectory);
            extractIntoDirectory(archivePath, stagingDirectory, workspace);
            Files.delete(archivePath);
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract nested archive: " + archivePath, e);
        }
    }

    private Path extractIntoDirectory(Path archivePath, Path targetRoot, Workspace workspace) {
        String fileName = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            zipExtractor.extractZipFileInto(archivePath, targetRoot, workspace.getExtractionBudget());
            return targetRoot;
        }
        if (fileName.endsWith(".tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            extractTarInto(archivePath, targetRoot, workspace.getExtractionBudget());
            return targetRoot;
        }
        if (fileName.endsWith(".7z")) {
            extractSevenZInto(archivePath, targetRoot, workspace.getExtractionBudget());
            return targetRoot;
        }
        throw new ArchiveException("Unsupported nested archive format: " + archivePath);
    }

    private Path copyDirectory(Path sourceDirectory, Path targetRoot, ExtractionBudget budget) {
        try {
            Files.walk(sourceDirectory).forEach(sourcePath -> {
                try {
                    Path relative = sourceDirectory.relativize(sourcePath);
                    if (SymbolicLinkGuard.skipIfSymbolicLink(sourcePath, relative)) {
                        return;
                    }
                    Path targetPath = targetRoot.resolve(relative);
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        long fileSize = Files.size(sourcePath);
                        if (budget.skipIfOversized(relative.toString(), fileSize)) {
                            return;
                        }
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath);
                        budget.recordExtractedFile(relative.toString(), fileSize);
                    }
                } catch (IOException e) {
                    throw new ArchiveException("Failed to copy repository folder: " + sourcePath, e);
                }
            });
            return targetRoot;
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveException("Failed to copy repository folder: " + sourceDirectory, e);
        }
    }

    private Path extractTar(Path tarPath, Workspace workspace) {
        Path extractionRoot = workspace.getExtractionPath();
        extractTarInto(tarPath, extractionRoot, workspace.getExtractionBudget());
        return extractionRoot;
    }

    private void extractTarInto(Path tarPath, Path extractionRoot, ExtractionBudget budget) {
        long entryCount = 0L;
        long archiveUncompressedBytes = 0L;
        long compressedArchiveSize;

        try {
            compressedArchiveSize = Files.size(tarPath);
        } catch (IOException e) {
            throw new ArchiveException("Failed to read TAR archive size: " + tarPath, e);
        }

        try (InputStream fileStream = Files.newInputStream(tarPath);
             InputStream inputStream = openTarStream(tarPath, fileStream);
             ArchiveInputStream<?> archiveInputStream = new TarArchiveInputStream(inputStream)) {

            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                entryCount++;
                validateEntryCount(entryCount);
                if (SymbolicLinkGuard.isTarSymlink(entry)) {
                    SymbolicLinkGuard.logSkippedArchiveSymlink(entry.getName());
                    skipArchiveEntry(archiveInputStream, entry);
                    continue;
                }
                if (entry.isDirectory()) {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath);
                } else {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath.getParent());
                    if (budget.skipIfOversized(entry.getName(), entry.getSize())) {
                        skipArchiveEntry(archiveInputStream, entry);
                        continue;
                    }
                    archiveUncompressedBytes += copyStream(
                            archiveInputStream, targetPath, entry.getName(), budget);
                }
            }
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract TAR archive: " + tarPath, e);
        }

        if (entryCount == 0L) {
            throw new ArchiveException("TAR archive contains no entries: " + tarPath);
        }

        budget.validateArchiveCompressionRatio(compressedArchiveSize, archiveUncompressedBytes);
    }

    private Path extractSevenZ(Path sevenZPath, Workspace workspace) {
        Path extractionRoot = workspace.getExtractionPath();
        extractSevenZInto(sevenZPath, extractionRoot, workspace.getExtractionBudget());
        return extractionRoot;
    }

    private void extractSevenZInto(Path sevenZPath, Path extractionRoot, ExtractionBudget budget) {
        long entryCount = 0L;
        long archiveUncompressedBytes = 0L;
        long compressedArchiveSize;

        try {
            compressedArchiveSize = Files.size(sevenZPath);
        } catch (IOException e) {
            throw new ArchiveException("Failed to read 7Z archive size: " + sevenZPath, e);
        }

        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(sevenZPath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                entryCount++;
                validateEntryCount(entryCount);
                if (SymbolicLinkGuard.isSevenZSymlink(entry)) {
                    SymbolicLinkGuard.logSkippedArchiveSymlink(entry.getName());
                    continue;
                }
                if (entry.isDirectory()) {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath);
                } else {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath.getParent());
                    if (budget.skipIfOversized(entry.getName(), entry.getSize())) {
                        continue;
                    }
                    archiveUncompressedBytes += copySevenZEntry(
                            sevenZFile, targetPath, entry.getName(), budget);
                }
            }
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract 7Z archive: " + sevenZPath, e);
        }

        if (entryCount == 0L) {
            throw new ArchiveException("7Z archive contains no entries: " + sevenZPath);
        }

        budget.validateArchiveCompressionRatio(compressedArchiveSize, archiveUncompressedBytes);
    }

    private InputStream openTarStream(Path tarPath, InputStream fileStream) throws IOException {
        String fileName = tarPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            return new GzipCompressorInputStream(new BufferedInputStream(fileStream));
        }
        return new BufferedInputStream(fileStream);
    }

    private long copySevenZEntry(
            SevenZFile sevenZFile,
            Path targetPath,
            String entryName,
            ExtractionBudget budget) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long writtenBytes = 0L;
        try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = sevenZFile.read(buffer)) > 0) {
                writtenBytes += read;
                if (writtenBytes > config.getMaxSingleFileSize()) {
                    Files.deleteIfExists(targetPath);
                    budget.skipIfOversized(entryName, writtenBytes);
                    return 0L;
                }
                budget.ensureAdditionalBytesAllowed(read);
                outputStream.write(buffer, 0, read);
            }
        }
        budget.recordExtractedFile(entryName, writtenBytes);
        return writtenBytes;
    }

    private long copyStream(
            InputStream inputStream,
            Path targetPath,
            String entryName,
            ExtractionBudget budget) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long writtenBytes = 0L;
        try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                writtenBytes += read;
                if (writtenBytes > config.getMaxSingleFileSize()) {
                    Files.deleteIfExists(targetPath);
                    budget.skipIfOversized(entryName, writtenBytes);
                    skipArchiveEntry(inputStream, null);
                    return 0L;
                }
                budget.ensureAdditionalBytesAllowed(read);
                outputStream.write(buffer, 0, read);
            }
        }
        budget.recordExtractedFile(entryName, writtenBytes);
        return writtenBytes;
    }

    private void skipArchiveEntry(InputStream inputStream, ArchiveEntry entry) throws IOException {
        if (entry != null && entry.getSize() > 0L) {
            long remaining = entry.getSize();
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            while (remaining > 0L) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                remaining -= read;
            }
            return;
        }
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        while (inputStream.read(buffer) != -1) {
            // Discard remaining entry bytes.
        }
    }

    private void validateEntryCount(long entryCount) {
        if (entryCount > config.getMaxZipEntries()) {
            throw new ArchiveException("Archive entry count exceeds configured limit: " + config.getMaxZipEntries());
        }
    }

    private static String stripArchiveExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz")) {
            return fileName.substring(0, fileName.length() - 7);
        }
        if (lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".zip") || lower.endsWith(".7z")) {
            int lastDot = fileName.lastIndexOf('.');
            return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        }
        return fileName;
    }
}
