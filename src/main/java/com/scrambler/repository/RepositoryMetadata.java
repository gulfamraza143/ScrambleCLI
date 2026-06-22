package com.scrambler.repository;

import com.scrambler.exception.AlreadyMaskedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repository-level marker written after successful masking to prevent accidental double masking.
 */
public final class RepositoryMetadata {

    public static final String FILENAME = ".scramble_metadata";
    public static final String SCRAMBLE_VERSION = "1.0";
    public static final String SCHEMA_VERSION = "2.0";

    public static final String ERROR_HEADLINE = "Repository already masked by SCRAMBLE.";
    public static final String ERROR_DETAIL = "Double masking is not allowed.";

    private RepositoryMetadata() {
    }

    /**
     * Returns whether the repository root already contains a SCRAMBLE metadata marker.
     *
     * @param repositoryRoot extracted repository root directory
     * @return {@code true} when {@link #FILENAME} exists at the repository root
     */
    public static boolean existsAtRepositoryRoot(Path repositoryRoot) {
        if (repositoryRoot == null) {
            return false;
        }
        return Files.isRegularFile(repositoryRoot.resolve(FILENAME));
    }

    /**
     * Fails fast when the repository root already contains a SCRAMBLE metadata marker.
     *
     * @param repositoryRoot extracted repository root directory
     * @throws AlreadyMaskedException when the repository has already been masked
     */
    public static void ensureNotAlreadyMasked(Path repositoryRoot) {
        if (existsAtRepositoryRoot(repositoryRoot)) {
            throw new AlreadyMaskedException(ERROR_HEADLINE + System.lineSeparator() + ERROR_DETAIL);
        }
    }

    /**
     * Writes the repository metadata marker to the repository root.
     *
     * @param repositoryRoot extracted repository root directory
     * @throws IOException when the marker file cannot be written
     */
    public static void writeMarker(Path repositoryRoot) throws IOException {
        Path markerPath = repositoryRoot.resolve(FILENAME);
        Files.writeString(markerPath, toJson(), StandardCharsets.UTF_8);
    }

    /**
     * Returns the JSON payload written to {@link #FILENAME}.
     *
     * @return marker file contents
     */
    public static String toJson() {
        return """
                {
                "scrambleVersion": "%s",
                "masked": true,
                "schemaVersion": "%s"
                }
                """.formatted(SCRAMBLE_VERSION, SCHEMA_VERSION);
    }
}
