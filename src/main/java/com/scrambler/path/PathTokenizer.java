package com.scrambler.path;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.masking.GlobalValueMapper;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.security.SymbolicLinkGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tokenizes sensitive repository, folder, and file names within an extracted repository tree.
 */
public final class PathTokenizer {

    private static final String PATH_MAPPING_CONTEXT = ".";
    private static final int PATH_MAPPING_OFFSET = 0;

    private final PathSegmentDetector detector;

    /**
     * Creates a path tokenizer with default detection rules.
     */
    public PathTokenizer() {
        this(new PathSegmentDetector());
    }

    /**
     * Creates a path tokenizer with a supplied segment detector.
     *
     * @param detector path segment detector
     */
    public PathTokenizer(PathSegmentDetector detector) {
        this.detector = detector;
    }

    /**
     * Tokenizes sensitive path segments and prepares the repository folder for self-contained packaging.
     *
     * @param extractionRoot workspace extraction root containing repository files
     * @param repositoryName repository name derived from the input path (without archive extension)
     * @param mapper         shared global value mapper for consistent tokens within the run
     * @param registry       mapping registry receiving path-level records
     * @return tokenization result describing the repository folder and output archive name
     */
    public PathTokenizationResult tokenize(
            Path extractionRoot,
            String repositoryName,
            GlobalValueMapper mapper,
            MappingRegistry registry) {
        try {
            Path contentRoot = resolveContentRoot(extractionRoot, repositoryName);
            Map<String, String> segmentMappings = collectSegmentMappings(contentRoot, mapper, registry);
            applySegmentRenames(contentRoot, segmentMappings);

            String repositoryFolder = resolveRepositoryFolderName(repositoryName, mapper, registry);
            Path repositoryRoot = wrapRepositoryFolder(extractionRoot, contentRoot, repositoryFolder);

            String outputZipName = repositoryFolder + ".zip";
            return new PathTokenizationResult(repositoryRoot, repositoryFolder, outputZipName);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to tokenize repository paths", e);
        }
    }

    private Map<String, String> collectSegmentMappings(
            Path contentRoot,
            GlobalValueMapper mapper,
            MappingRegistry registry) throws IOException {
        Map<String, String> segmentMappings = new HashMap<>();
        Set<String> folderSegments = new HashSet<>();
        Set<String> fileNames = new HashSet<>();

        try (Stream<Path> paths = Files.walk(contentRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, contentRoot.relativize(path))) {
                    return;
                }
                Path relative = contentRoot.relativize(path);
                if (relative.getNameCount() == 0) {
                    return;
                }
                for (int index = 0; index < relative.getNameCount() - (Files.isRegularFile(path) ? 1 : 0); index++) {
                    folderSegments.add(relative.getName(index).toString());
                }
                if (Files.isRegularFile(path)) {
                    fileNames.add(relative.getFileName().toString());
                }
            });
        }

        for (String folderSegment : folderSegments) {
            if (detector.isSensitiveFolderSegment(folderSegment)) {
                String masked = mapper.resolve(EntityType.FOLDER_NAME, folderSegment);
                segmentMappings.put(folderSegment, masked);
                registry.register(new MappingRecord(
                        PATH_MAPPING_CONTEXT,
                        EntityType.FOLDER_NAME,
                        folderSegment,
                        masked,
                        PATH_MAPPING_OFFSET,
                        PATH_MAPPING_OFFSET));
            }
        }

        for (String fileName : fileNames) {
            if (detector.isSensitiveFileName(fileName)) {
                String masked = mapper.resolve(EntityType.FILE_NAME, fileName);
                segmentMappings.put(fileName, masked);
                registry.register(new MappingRecord(
                        PATH_MAPPING_CONTEXT,
                        EntityType.FILE_NAME,
                        fileName,
                        masked,
                        PATH_MAPPING_OFFSET,
                        PATH_MAPPING_OFFSET));
            }
        }

        return segmentMappings;
    }

    private void applySegmentRenames(Path contentRoot, Map<String, String> segmentMappings) throws IOException {
        if (segmentMappings.isEmpty()) {
            return;
        }

        List<Path> directories = collectDirectories(contentRoot);
        directories.sort(Comparator.comparingInt((Path path) -> path.getNameCount()).reversed());
        for (Path directory : directories) {
            renamePathSegment(contentRoot, directory, segmentMappings);
        }

        // Re-walk after directory renames; paths collected before renames become stale.
        for (Path file : collectRegularFiles(contentRoot)) {
            renamePathSegment(contentRoot, file, segmentMappings);
        }
    }

    private List<Path> collectDirectories(Path contentRoot) throws IOException {
        List<Path> directories = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(contentRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, contentRoot.relativize(path))) {
                    return;
                }
                if (Files.isDirectory(path) && !path.equals(contentRoot)) {
                    directories.add(path);
                }
            });
        }
        return directories;
    }

    private List<Path> collectRegularFiles(Path contentRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(contentRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, contentRoot.relativize(path))) {
                    return;
                }
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            });
        }
        return files;
    }

    private void renamePathSegment(Path contentRoot, Path path, Map<String, String> segmentMappings)
            throws IOException {
        Path relative = contentRoot.relativize(path);
        String segmentName = relative.getFileName().toString();
        String mappedSegment = segmentMappings.get(segmentName);
        if (mappedSegment == null || mappedSegment.equals(segmentName)) {
            return;
        }

        Path target;
        if (relative.getParent() == null) {
            target = contentRoot.resolve(mappedSegment);
        } else {
            target = contentRoot.resolve(relative.getParent().resolve(mappedSegment));
        }
        if (path.equals(target) || !Files.exists(path)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(path, target);
    }

    private String resolveRepositoryFolderName(
            String repositoryName,
            GlobalValueMapper mapper,
            MappingRegistry registry) {
        if (detector.isSensitiveRepositoryName(repositoryName)) {
            String masked = mapper.resolve(EntityType.REPOSITORY_NAME, repositoryName);
            registry.register(new MappingRecord(
                    PATH_MAPPING_CONTEXT,
                    EntityType.REPOSITORY_NAME,
                    repositoryName,
                    masked,
                    PATH_MAPPING_OFFSET,
                    PATH_MAPPING_OFFSET));
            return masked;
        }
        return repositoryName;
    }

    private Path wrapRepositoryFolder(Path extractionRoot, Path contentRoot, String repositoryFolder)
            throws IOException {
        if (contentRoot.getFileName().toString().equals(repositoryFolder)) {
            return contentRoot;
        }

        if (contentRoot.equals(extractionRoot)) {
            Path wrappedRoot = extractionRoot.resolve(repositoryFolder);
            Files.createDirectories(wrappedRoot);
            try (Stream<Path> children = Files.list(extractionRoot)) {
                for (Path child : children.toList()) {
                    if (child.equals(wrappedRoot)) {
                        continue;
                    }
                    Files.move(child, wrappedRoot.resolve(child.getFileName()));
                }
            }
            return wrappedRoot;
        }

        Path renamedRoot = contentRoot.resolveSibling(repositoryFolder);
        Files.move(contentRoot, renamedRoot);
        return renamedRoot;
    }

    private Path resolveContentRoot(Path extractionRoot, String repositoryName) throws IOException {
        try (Stream<Path> children = Files.list(extractionRoot)) {
            List<Path> entries = children
                    .filter(path -> !SymbolicLinkGuard.skipIfSymbolicLink(path, extractionRoot.relativize(path)))
                    .toList();
            List<Path> directories = entries.stream().filter(Files::isDirectory).toList();
            List<Path> files = entries.stream().filter(Files::isRegularFile).toList();

            if (directories.size() == 1 && files.isEmpty()) {
                Path singleDirectory = directories.get(0);
                if (singleDirectory.getFileName().toString().equals(repositoryName)) {
                    return singleDirectory;
                }
            }
        }
        return extractionRoot;
    }

    /**
     * Derives the repository name from an input archive or folder path.
     *
     * @param inputPath masking input path
     * @return repository name without archive extension
     */
    public static String deriveRepositoryName(Path inputPath) {
        String fileName = inputPath.getFileName().toString();
        return stripArchiveExtension(fileName);
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
