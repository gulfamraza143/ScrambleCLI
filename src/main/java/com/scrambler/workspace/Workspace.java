package com.scrambler.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a unique temporary workspace for a single SCRAMBLECLI run.
 */
public final class Workspace {

    private final String runId;
    private final Path rootPath;
    private final Path extractionPath;

    /**
     * Creates a workspace bound to a run identifier and directory layout.
     *
     * @param runId         unique identifier for this run
     * @param rootPath      root temporary directory for the run
     * @param extractionPath directory where repository archives are extracted
     */
    public Workspace(String runId, Path rootPath, Path extractionPath) {
        this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath must not be null");
        this.extractionPath = Objects.requireNonNull(extractionPath, "extractionPath must not be null");
    }

    /**
     * Generates a new run identifier suitable for CI-safe unique workspaces.
     *
     * @return new run identifier
     */
    public static String newRunId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the unique run identifier.
     *
     * @return run identifier
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Returns the workspace root directory.
     *
     * @return workspace root path
     */
    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Returns the extraction directory inside the workspace.
     *
     * @return extraction root path
     */
    public Path getExtractionPath() {
        return extractionPath;
    }
}
