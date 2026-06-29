package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.repository.RepositoryMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingIdempotencyTest {

    @Test
    void freshRepositoryMasksSuccessfully(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(tempDir.resolve("repo.zip")));
    }

    @Test
    void alreadyMaskedFolderFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");
        Files.writeString(repoRoot.resolve(RepositoryMetadata.FILENAME), RepositoryMetadata.toJson());

        int exitCode = captureStderrAndRun(tempDir, repoRoot.toString());

        assertEquals(MaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve("repo.zip")));
    }

    @Test
    void unmaskedZipWithWrapperFolderMasksSuccessfully(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("fxtp-develop.zip");
        createZip(repoZip, Map.of(
                "fxtp-develop/pom.xml", "<project/>",
                "fxtp-develop/App.java", "public class App { String email = \"admin@icici.com\"; }"));

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(tempDir.resolve("fxtp-develop.zip")));
    }

    @Test
    void alreadyMaskedZipWithWrapperFolderFails(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("fxtp-develop.zip");
        createZip(repoZip, Map.of(
                "fxtp-develop/" + RepositoryMetadata.FILENAME, RepositoryMetadata.toJson(),
                "fxtp-develop/App.java", "public class App { String email = \"admin@icici.com\"; }"));

        int exitCode = captureStderrAndRun(tempDir, repoZip.toString());

        assertEquals(MaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        assertFalse(Files.exists(tempDir.resolve("entity_report.xlsx")));
    }

    @Test
    void alreadyMaskedFlatZipFails(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("source.zip");
        createZip(repoZip, Map.of(
                RepositoryMetadata.FILENAME, RepositoryMetadata.toJson(),
                "App.java", "public class App { String email = \"admin@icici.com\"; }"));

        int exitCode = captureStderrAndRun(tempDir, repoZip.toString());

        assertEquals(MaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
    }

    @Test
    void alreadyMaskedTokenizedOutputZipFails(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/notes.txt", "support@icici.com\n"));

        ScramblerConfig config = configFor(tempDir);
        assertEquals(MaskingApplication.EXIT_SUCCESS, new MaskingApplication(config).run(new String[]{repoZip.toString()}));

        Path tokenZip = findTokenizedOutputZip(tempDir, "ICICI_CODE_BANK.zip");
        int exitCode = captureStderrAndRun(tempDir, tokenZip.toString());

        assertEquals(MaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
    }

    @Test
    void markerIsWrittenAfterSuccessfulMasking(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");
        assertFalse(Files.exists(repoRoot.resolve(RepositoryMetadata.FILENAME)));

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        Path maskedZip = tempDir.resolve("repo.zip");
        assertTrue(zipContainsEntry(maskedZip, "repo/" + RepositoryMetadata.FILENAME));
        assertEquals(RepositoryMetadata.toJson(), readZipEntry(maskedZip, "repo/" + RepositoryMetadata.FILENAME));
    }

    @Test
    void markerExistsInsideMaskedCodeZip(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        Path maskedZip = tempDir.resolve("repo.zip");
        assertTrue(zipContainsEntry(maskedZip, "repo/" + RepositoryMetadata.FILENAME));
        assertEquals(RepositoryMetadata.toJson(), readZipEntry(maskedZip, "repo/" + RepositoryMetadata.FILENAME));
    }

    @Test
    void nestedArchiveMarkerDoesNotTriggerFailure(@TempDir Path tempDir) throws Exception {
        Path nestedZip = tempDir.resolve("vendor_assets.zip");
        createZip(nestedZip, Map.of(
                RepositoryMetadata.FILENAME, RepositoryMetadata.toJson(),
                "logo.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));

        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("App.java"), "public class App { String email = \"admin@icici.com\"; }");
        Files.copy(nestedZip, repoRoot.resolve("vendor_assets.zip"));

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        Path maskedZip = tempDir.resolve("repo.zip");
        assertTrue(zipContainsEntry(maskedZip, "repo/" + RepositoryMetadata.FILENAME));
    }

    @Test
    void reportsExpectedErrorMessageAndExitCode(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve(RepositoryMetadata.FILENAME), RepositoryMetadata.toJson());

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoRoot.toString()});
        } finally {
            System.setErr(originalErr);
        }

        assertEquals(MaskingApplication.EXIT_PROCESSING_FAILURE, exitCode);
        String errorOutput = stderr.toString(StandardCharsets.UTF_8);
        assertTrue(errorOutput.contains("ERROR " + RepositoryMetadata.ERROR_HEADLINE));
        assertTrue(errorOutput.contains(RepositoryMetadata.ERROR_DETAIL));
    }

    private static Path findTokenizedOutputZip(Path directory, String inputZipName) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".zip")
                            && !path.getFileName().toString().equals(inputZipName))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Tokenized output ZIP not found in " + directory));
        }
    }

    private static int captureStderrAndRun(Path tempDir, String inputPath) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            return new MaskingApplication(configFor(tempDir)).run(new String[]{inputPath});
        } finally {
            System.setErr(originalErr);
        }
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static void createZip(Path zipPath, Map<String, ?> entries) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof byte[] bytes) {
                    zipOutputStream.write(bytes);
                } else {
                    zipOutputStream.write(value.toString().getBytes(StandardCharsets.UTF_8));
                }
                zipOutputStream.closeEntry();
            }
        }
    }

    private static boolean zipContainsEntry(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String readZipEntry(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("ZIP entry not found: " + entryName);
    }
}
