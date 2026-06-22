package com.scrambler.inventory;

import com.scrambler.exception.FileProcessingException;
import com.scrambler.security.SymbolicLinkGuard;
import com.scrambler.workspace.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Traverses an extracted repository tree and collects file metadata.
 */
public final class FileIterator {

    private final WorkspaceManager workspaceManager;

    /**
     * Creates a file iterator using workspace path normalization rules.
     *
     * @param workspaceManager workspace manager providing repository-relative path normalization
     */
    public FileIterator(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    /**
     * Walks the extraction root and returns metadata for every regular file.
     * Directories are ignored.
     *
     * @param extractionRoot root directory of the extracted repository
     * @return discovered files sorted by repository-relative path
     * @throws FileProcessingException if traversal or metadata collection fails
     */
    public List<FileInfo> collectFiles(Path extractionRoot) {
        if (extractionRoot == null) {
            throw new FileProcessingException("Extraction root must not be null");
        }
        if (!Files.isDirectory(extractionRoot)) {
            throw new FileProcessingException("Extraction root is not a directory: " + extractionRoot);
        }

        List<FileInfo> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(extractionRoot)) {
            paths.forEach(path -> {
                if (SymbolicLinkGuard.skipIfSymbolicLink(path, extractionRoot.relativize(path))) {
                    return;
                }
                if (Files.isRegularFile(path)) {
                    files.add(toFileInfo(extractionRoot, path));
                }
            });
            files.sort(Comparator.comparing(FileInfo::getRepoRelativePath));
        } catch (IOException e) {
            throw new FileProcessingException("Failed to traverse repository: " + extractionRoot, e);
        } catch (RuntimeException e) {
            throw toInventoryFailure("Failed to traverse repository: " + extractionRoot, e);
        }

        return List.copyOf(files);
    }

    private FileInfo toFileInfo(Path extractionRoot, Path absolutePath) {
        try {
            String repoRelativePath = workspaceManager.toRepoRelativePath(extractionRoot, absolutePath);
            long sizeBytes = Files.size(absolutePath);
            return new FileInfo(absolutePath, repoRelativePath, sizeBytes);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to read file metadata: " + absolutePath, e);
        } catch (RuntimeException e) {
            throw toInventoryFailure("Failed to read file metadata: " + absolutePath, e);
        }
    }

    private static FileProcessingException toInventoryFailure(String message, RuntimeException cause) {
        if (cause instanceof FileProcessingException fileProcessingException) {
            return fileProcessingException;
        }
        return new FileProcessingException(message, cause);
    }
}
