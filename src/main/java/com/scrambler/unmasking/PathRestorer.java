package com.scrambler.unmasking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.security.SymbolicLinkGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Restores original repository, folder, and file names using path-level entity report mappings.
 */
public final class PathRestorer {

    /**
     * Result of path restoration including optional repository entry prefix for packaging.
     *
     * @param repositoryRoot restored repository content root
     * @param entryPrefix    ZIP entry prefix when the repository was packaged in a named folder
     */
    public record PathRestoreResult(Path repositoryRoot, String entryPrefix) {
    }

    /**
     * Restores original path names and returns packaging metadata.
     *
     * @param extractionRoot root directory produced by archive extraction
     * @param records        validated entity report rows
     * @return restoration result
     */
    public PathRestoreResult restore(Path extractionRoot, List<EntityReportRecord> records) {
        Map<String, String> folderMappings = new HashMap<>();
        Map<String, String> fileMappings = new HashMap<>();
        String repositoryToken = null;
        String originalRepositoryName = null;

        for (EntityReportRecord record : records) {
            switch (record.getEntityType()) {
                case REPOSITORY_NAME -> {
                    repositoryToken = record.getMaskedValue();
                    originalRepositoryName = record.getOriginalValue();
                }
                case FOLDER_NAME -> folderMappings.put(record.getMaskedValue(), record.getOriginalValue());
                case FILE_NAME -> fileMappings.put(record.getMaskedValue(), record.getOriginalValue());
                default -> {
                    // Content mappings are handled separately.
                }
            }
        }

        try {
            Path repositoryRoot = locateRepositoryRoot(extractionRoot, repositoryToken);
            applySegmentRestores(repositoryRoot, folderMappings, fileMappings);
            Path restoredRoot = restoreRepositoryFolderName(
                    repositoryRoot, extractionRoot, repositoryToken, originalRepositoryName);
            return new PathRestoreResult(restoredRoot, originalRepositoryName);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to restore repository paths", e);
        }
    }

    private Path locateRepositoryRoot(Path extractionRoot, String repositoryToken) throws IOException {
        if (repositoryToken != null) {
            Path tokenRoot = extractionRoot.resolve(repositoryToken);
            if (Files.isDirectory(tokenRoot)) {
                return tokenRoot;
            }
        }

        return extractionRoot;
    }

    private void applySegmentRestores(
            Path repositoryRoot,
            Map<String, String> folderMappings,
            Map<String, String> fileMappings) throws IOException {
        if (folderMappings.isEmpty() && fileMappings.isEmpty()) {
            return;
        }

        List<Path> directories = collectDirectories(repositoryRoot);
        directories.sort(Comparator.comparingInt((Path path) -> path.getNameCount()).reversed());
        for (Path directory : directories) {
            restorePathSegment(repositoryRoot, directory, folderMappings);
        }

        // Re-walk after directory restores; paths collected before restores become stale.
        for (Path file : collectRegularFiles(repositoryRoot)) {
            restorePathSegment(repositoryRoot, file, fileMappings);
        }
    }

    private static List<Path> collectDirectories(Path repositoryRoot) throws IOException {
        List<Path> directories = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, repositoryRoot.relativize(path))) {
                    return;
                }
                if (Files.isDirectory(path) && !path.equals(repositoryRoot)) {
                    directories.add(path);
                }
            });
        }
        return directories;
    }

    private static List<Path> collectRegularFiles(Path repositoryRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, repositoryRoot.relativize(path))) {
                    return;
                }
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            });
        }
        return files;
    }

    private void restorePathSegment(Path repositoryRoot, Path path, Map<String, String> maskedToOriginal)
            throws IOException {
        Path relative = repositoryRoot.relativize(path);
        String segmentName = relative.getFileName().toString();
        String restoredSegment = maskedToOriginal.get(segmentName);
        if (restoredSegment == null || restoredSegment.equals(segmentName)) {
            return;
        }

        Path target;
        if (relative.getParent() == null) {
            target = repositoryRoot.resolve(restoredSegment);
        } else {
            target = repositoryRoot.resolve(relative.getParent().resolve(restoredSegment));
        }
        if (path.equals(target) || !Files.exists(path)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(path, target);
    }

    private Path restoreRepositoryFolderName(
            Path repositoryRoot,
            Path extractionRoot,
            String repositoryToken,
            String originalRepositoryName) throws IOException {
        if (repositoryToken == null || originalRepositoryName == null) {
            return repositoryRoot;
        }
        if (!repositoryRoot.getFileName().toString().equals(repositoryToken)) {
            return repositoryRoot;
        }

        Path restoredRoot = extractionRoot.resolve(originalRepositoryName);
        Files.createDirectories(extractionRoot);
        Files.move(repositoryRoot, restoredRoot);
        return restoredRoot;
    }
}
