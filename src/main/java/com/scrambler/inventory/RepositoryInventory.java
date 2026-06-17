package com.scrambler.inventory;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate of files discovered in an extracted repository.
 */
public final class RepositoryInventory {

    private final List<FileInfo> files;

    /**
     * Creates an inventory from an immutable file list.
     *
     * @param files discovered repository files
     */
    public RepositoryInventory(List<FileInfo> files) {
        Objects.requireNonNull(files, "files must not be null");
        this.files = List.copyOf(files);
    }

    /**
     * Returns the discovered files in repository-relative path order.
     *
     * @return immutable file list
     */
    public List<FileInfo> getFiles() {
        return files;
    }

    /**
     * Returns the total number of inventoried files.
     *
     * @return file count
     */
    public int getTotalFileCount() {
        return files.size();
    }

    /**
     * Returns an unmodifiable view of repository-relative paths for display and downstream stages.
     *
     * @return sorted repository-relative paths
     */
    public List<String> getRepoRelativePaths() {
        return files.stream()
                .map(FileInfo::getRepoRelativePath)
                .toList();
    }
}
