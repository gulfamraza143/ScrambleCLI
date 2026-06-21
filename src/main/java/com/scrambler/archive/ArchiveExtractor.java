package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts repository archives in ZIP, TAR, TGZ, or 7Z format into a workspace.
 */
public final class ArchiveExtractor {

    private static final int COPY_BUFFER_SIZE = 8192;

    private final WorkspaceManager workspaceManager;
    private final ScramblerConfig config;
    private final ZipExtractor zipExtractor;

    /**
     * Creates an archive extractor bound to workspace safety helpers and configuration limits.
     *
     * @param workspaceManager workspace manager providing zip-slip validation
     * @param config           configuration providing archive safety limits
     */
    public ArchiveExtractor(WorkspaceManager workspaceManager, ScramblerConfig config) {
        this.workspaceManager = workspaceManager;
        this.config = config;
        this.zipExtractor = new ZipExtractor(workspaceManager, config);
    }

    /**
     * Extracts the given archive or folder into the workspace extraction directory.
     *
     * @param inputPath path to a repository archive or folder
     * @param workspace active workspace receiving extracted content
     * @return extraction root directory containing repository files
     * @throws ArchiveException if the archive is missing, corrupt, unsafe, or exceeds configured limits
     */
    public Path extract(Path inputPath, Workspace workspace) {
        if (inputPath == null) {
            throw new ArchiveException("Input path must not be null");
        }
        if (workspace == null) {
            throw new ArchiveException("Workspace must not be null");
        }
        if (Files.isDirectory(inputPath)) {
            return copyDirectory(inputPath, workspace.getExtractionPath());
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

    /**
     * Extracts a nested archive file into its parent directory and removes the archive file.
     *
     * @param archivePath path to nested archive
     * @param workspace   active workspace
     * @throws ArchiveException when extraction fails
     */
    public void extractNestedArchive(Path archivePath, Workspace workspace) {
        Path parentDirectory = archivePath.getParent();
        if (parentDirectory == null) {
            throw new ArchiveException("Nested archive has no parent directory: " + archivePath);
        }
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
            extractZipInto(archivePath, targetRoot, workspace);
            return targetRoot;
        }
        if (fileName.endsWith(".tar") || fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            extractTarInto(archivePath, targetRoot, workspace);
            return targetRoot;
        }
        if (fileName.endsWith(".7z")) {
            extractSevenZInto(archivePath, targetRoot, workspace);
            return targetRoot;
        }
        throw new ArchiveException("Unsupported nested archive format: " + archivePath);
    }

    private Path copyDirectory(Path sourceDirectory, Path targetRoot) {
        try {
            Files.walk(sourceDirectory).forEach(sourcePath -> {
                try {
                    Path relative = sourceDirectory.relativize(sourcePath);
                    Path targetPath = targetRoot.resolve(relative);
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath);
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
        extractTarInto(tarPath, extractionRoot, workspace);
        return extractionRoot;
    }

    private void extractTarInto(Path tarPath, Path extractionRoot, Workspace workspace) {
        long entryCount = 0L;
        long totalUncompressedBytes = 0L;

        try (InputStream fileStream = Files.newInputStream(tarPath);
             InputStream inputStream = openTarStream(tarPath, fileStream);
             ArchiveInputStream<?> archiveInputStream = new TarArchiveInputStream(inputStream)) {

            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                entryCount++;
                validateEntryCount(entryCount);
                if (entry.isDirectory()) {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath);
                } else {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath.getParent());
                    totalUncompressedBytes += copyStream(archiveInputStream, targetPath, totalUncompressedBytes);
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
    }

    private Path extractSevenZ(Path sevenZPath, Workspace workspace) {
        Path extractionRoot = workspace.getExtractionPath();
        extractSevenZInto(sevenZPath, extractionRoot, workspace);
        return extractionRoot;
    }

    private void extractSevenZInto(Path sevenZPath, Path extractionRoot, Workspace workspace) {
        long entryCount = 0L;
        long totalUncompressedBytes = 0L;

        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(sevenZPath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                entryCount++;
                validateEntryCount(entryCount);
                if (entry.isDirectory()) {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath);
                } else {
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    Files.createDirectories(targetPath.getParent());
                    totalUncompressedBytes += copySevenZEntry(sevenZFile, targetPath, totalUncompressedBytes);
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
    }

    private void extractZipInto(Path zipPath, Path extractionRoot, Workspace workspace) {
        long entryCount = 0L;
        long totalUncompressedBytes = 0L;

        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                try {
                    entryCount++;
                    validateEntryCount(entryCount);
                    Path targetPath = workspaceManager.resolveSafeExtractPath(extractionRoot, entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        totalUncompressedBytes += copyStream(zipInputStream, targetPath, totalUncompressedBytes);
                    }
                } finally {
                    zipInputStream.closeEntry();
                }
            }
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract nested ZIP archive: " + zipPath, e);
        }

        if (entryCount == 0L) {
            throw new ArchiveException("ZIP archive contains no entries: " + zipPath);
        }
    }

    private InputStream openTarStream(Path tarPath, InputStream fileStream) throws IOException {
        String fileName = tarPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            return new GzipCompressorInputStream(new BufferedInputStream(fileStream));
        }
        return new BufferedInputStream(fileStream);
    }

    private long copySevenZEntry(SevenZFile sevenZFile, Path targetPath, long totalUncompressedBytes)
            throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long writtenBytes = 0L;
        try (var outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = sevenZFile.read(buffer)) > 0) {
                writtenBytes += read;
                validateUncompressedSize(totalUncompressedBytes + writtenBytes);
                outputStream.write(buffer, 0, read);
            }
        }
        return writtenBytes;
    }

    private long copyStream(InputStream inputStream, Path targetPath, long totalUncompressedBytes)
            throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long writtenBytes = 0L;
        try (var outputStream = Files.newOutputStream(targetPath)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                writtenBytes += read;
                validateUncompressedSize(totalUncompressedBytes + writtenBytes);
                outputStream.write(buffer, 0, read);
            }
        }
        return writtenBytes;
    }

    private void validateEntryCount(long entryCount) {
        if (entryCount > config.getMaxZipEntries()) {
            throw new ArchiveException("Archive entry count exceeds configured limit: " + config.getMaxZipEntries());
        }
    }

    private void validateUncompressedSize(long totalUncompressedBytes) {
        if (totalUncompressedBytes > config.getMaxUncompressedBytes()) {
            throw new ArchiveException(
                    "Uncompressed archive size exceeds configured limit: " + config.getMaxUncompressedBytes());
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
