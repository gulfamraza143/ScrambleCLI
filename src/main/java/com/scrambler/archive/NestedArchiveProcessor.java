package com.scrambler.archive;

import com.scrambler.exception.ArchiveException;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.FileIterator;
import com.scrambler.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recursively expands nested repository archives until only processable files remain.
 */
public final class NestedArchiveProcessor {

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "tar", "tgz", "7z");

    private final FileIterator fileIterator;
    private final ArchiveExtractor archiveExtractor;

    /**
     * Creates a nested archive processor.
     *
     * @param fileIterator      repository file iterator
     * @param archiveExtractor  archive extractor for nested archives
     */
    public NestedArchiveProcessor(FileIterator fileIterator, ArchiveExtractor archiveExtractor) {
        this.fileIterator = fileIterator;
        this.archiveExtractor = archiveExtractor;
    }

    /**
     * Expands nested archives under the extraction root until no further archives are found.
     *
     * @param extractionRoot repository extraction root
     * @param workspace      active workspace
     * @return number of nested archives expanded
     */
    public int expandArchives(Path extractionRoot, Workspace workspace) {
        int expandedCount = 0;
        boolean foundArchive;
        do {
            foundArchive = false;
            List<FileInfo> files = fileIterator.collectFiles(extractionRoot);
            for (FileInfo fileInfo : files) {
                if (isNestedArchive(fileInfo)) {
                    archiveExtractor.extractNestedArchive(fileInfo.getAbsolutePath(), workspace);
                    expandedCount++;
                    foundArchive = true;
                    break;
                }
            }
        } while (foundArchive);
        return expandedCount;
    }

    private static boolean isNestedArchive(FileInfo fileInfo) {
        String extension = extensionOf(fileInfo.getRepoRelativePath());
        return ARCHIVE_EXTENSIONS.contains(extension);
    }

    private static String extensionOf(String repoRelativePath) {
        String fileName = Path.of(repoRelativePath).getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tar.gz")) {
            return "tgz";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
