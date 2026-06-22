package com.scrambler.workspace;

import com.scrambler.archive.ExtractionBudget;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Manages workspace lifecycle, path normalization, and zip-slip validation helpers.
 */
public final class WorkspaceManager {

    private static final String EXTRACTION_DIR_NAME = "extracted";

    /**
     * Creates a unique temporary workspace with an extraction subdirectory.
     *
     * @param config configuration providing the workspace base path
     * @return newly created workspace
     * @throws ArchiveException if workspace directories cannot be created
     */
    public Workspace createWorkspace(ScramblerConfig config) {
        Path rootPath = null;
        try {
            Path basePath = config.getWorkspaceBasePath();
            Files.createDirectories(basePath);

            String runId = Workspace.newRunId();
            rootPath = createRestrictedDirectory(basePath.resolve("scramble-" + runId));
            Path extractionPath = createRestrictedDirectory(rootPath.resolve(EXTRACTION_DIR_NAME));

            return new Workspace(runId, rootPath, extractionPath, new ExtractionBudget(config));
        } catch (IOException e) {
            deleteRecursively(rootPath);
            throw new ArchiveException("Failed to create workspace", e);
        }
    }

    /**
     * Deletes the workspace directory tree. Failures are swallowed so cleanup
     * in {@code finally} blocks does not mask the original error.
     *
     * @param workspace workspace to remove
     */
    public void cleanup(Workspace workspace) {
        if (workspace == null) {
            return;
        }
        deleteRecursively(workspace.getRootPath());
    }

    /**
     * Validates that a resolved extraction target remains inside the extraction root.
     *
     * @param extractionRoot canonical extraction root directory
     * @param entryName      raw ZIP entry name
     * @return normalized target path safe for extraction
     * @throws ArchiveException if the entry attempts a zip-slip traversal
     */
    public Path resolveSafeExtractPath(Path extractionRoot, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new ArchiveException("ZIP entry name must not be blank");
        }

        String normalizedEntryName = normalizeEntryName(entryName);
        Path targetPath = extractionRoot.resolve(normalizedEntryName).normalize();
        Path normalizedRoot = extractionRoot.toAbsolutePath().normalize();

        if (!targetPath.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
            throw new ArchiveException("Zip-slip detected for entry: " + entryName);
        }

        return targetPath;
    }

    /**
     * Converts an absolute file path under the extraction root into a repository-relative path.
     * Repository-relative paths always use forward slashes and never include a leading {@code ./}.
     *
     * @param extractionRoot extraction root directory
     * @param absolutePath   absolute or workspace-relative file path
     * @return normalized repository-relative path
     * @throws ArchiveException if the path escapes the extraction root or contains traversal segments
     */
    public String toRepoRelativePath(Path extractionRoot, Path absolutePath) {
        Path normalizedRoot = extractionRoot.toAbsolutePath().normalize();
        Path normalizedFile = absolutePath.toAbsolutePath().normalize();

        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new ArchiveException("Path is outside extraction root: " + absolutePath);
        }

        Path relativePath = normalizedRoot.relativize(normalizedFile);
        String repoRelativePath = relativePath.toString().replace(FileSystems.getDefault().getSeparator(), "/");

        if (repoRelativePath.isEmpty()) {
            throw new ArchiveException("Cannot derive repository-relative path for extraction root");
        }

        validateNoTraversalSegments(repoRelativePath);
        return stripLeadingDotSlash(repoRelativePath);
    }

    private static String normalizeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        validateNoTraversalSegments(normalized);
        return stripLeadingDotSlash(normalized);
    }

    private static void validateNoTraversalSegments(String path) {
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                throw new ArchiveException("Path traversal is not permitted: " + path);
            }
        }
    }

    private static String stripLeadingDotSlash(String path) {
        if (path.startsWith("./")) {
            return path.substring(2);
        }
        return path;
    }

    private static Path createRestrictedDirectory(Path directory) throws IOException {
        if (isPosix()) {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            FileAttribute<?> attributes = PosixFilePermissions.asFileAttribute(permissions);
            return Files.createDirectories(directory, attributes);
        }
        return Files.createDirectories(directory);
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static void deleteRecursively(Path rootPath) {
        if (rootPath == null || !Files.exists(rootPath)) {
            return;
        }
        try {
            try (var paths = Files.walk(rootPath)) {
                paths.sorted((left, right) -> right.compareTo(left))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // Best-effort cleanup must not throw from finally blocks.
                            }
                        });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup must not throw from finally blocks.
        }
    }
}
