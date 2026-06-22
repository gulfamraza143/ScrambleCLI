package com.scrambler.repository;

import com.scrambler.exception.AlreadyMaskedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryMetadataTest {

    @Test
    void detectsMarkerOnlyAtRepositoryRoot(@TempDir Path tempDir) throws Exception {
        Path repositoryRoot = tempDir.resolve("repo");
        Files.createDirectories(repositoryRoot);
        Files.createDirectories(repositoryRoot.resolve("vendor_assets"));
        Files.writeString(
                repositoryRoot.resolve("vendor_assets").resolve(RepositoryMetadata.FILENAME),
                RepositoryMetadata.toJson());

        assertFalse(RepositoryMetadata.existsAtRepositoryRoot(repositoryRoot));
        RepositoryMetadata.ensureNotAlreadyMasked(repositoryRoot);
    }

    @Test
    void rejectsRepositoryRootMarker(@TempDir Path tempDir) throws Exception {
        Path repositoryRoot = tempDir.resolve("repo");
        Files.createDirectories(repositoryRoot);
        Files.writeString(repositoryRoot.resolve(RepositoryMetadata.FILENAME), RepositoryMetadata.toJson());

        assertTrue(RepositoryMetadata.existsAtRepositoryRoot(repositoryRoot));
        AlreadyMaskedException exception = assertThrows(
                AlreadyMaskedException.class,
                () -> RepositoryMetadata.ensureNotAlreadyMasked(repositoryRoot));
        assertEquals(
                RepositoryMetadata.ERROR_HEADLINE + System.lineSeparator() + RepositoryMetadata.ERROR_DETAIL,
                exception.getMessage());
    }

    @Test
    void writesExpectedMarkerPayload(@TempDir Path tempDir) throws Exception {
        Path repositoryRoot = tempDir.resolve("repo");
        Files.createDirectories(repositoryRoot);

        RepositoryMetadata.writeMarker(repositoryRoot);

        String written = Files.readString(repositoryRoot.resolve(RepositoryMetadata.FILENAME), StandardCharsets.UTF_8);
        assertEquals(RepositoryMetadata.toJson(), written);
        assertTrue(written.contains("\"masked\": true"));
        assertTrue(written.contains("\"scrambleVersion\": \"1.0\""));
        assertTrue(written.contains("\"schemaVersion\": \"2.0\""));
    }
}
