package com.scrambler.path;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.report.ReportDigest;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTokenizationIntegrationTest {

    @Test
    void tokenizesRepositoryFolderAndFileNamesConsistently(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/ICICI_CODE_BANK/notes.txt", "support@icici.com\n",
                "ICICI_CODE_BANK/ICICI_CODE_BANK.txt", "plain\n",
                "ICICI_CODE_BANK/ICICI_Config/app.properties", "service=icici\n"));

        int exitCode = new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        assertEquals(0, exitCode);

        Path outputZip = findSingleZipOutput(tempDir, "ICICI_CODE_BANK.zip");
        assertFalse(Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));
        assertTrue(zipContainsEntry(outputZip, ReportSchema.REPORT_FILENAME));
        assertTrue(zipContainsEntry(outputZip, ReportDigest.DIGEST_FILENAME));

        String repoFolder = outputZip.getFileName().toString().replace(".zip", "");
        assertTrue(repoFolder.matches("[0-9A-F]{8}"));
        assertTrue(zipContainsEntry(outputZip, repoFolder + "/.scramble_metadata"));
        assertTrue(zipContainsEntry(outputZip, repoFolder + "/" + repoFolder + "/notes.txt"));
        assertTrue(listZipEntries(outputZip).stream().anyMatch(
                entry -> entry.startsWith(repoFolder + "/") && entry.matches(".+/[0-9A-F]{8}\\.txt")));
        assertFalse(listZipEntries(outputZip).stream().anyMatch(entry -> entry.contains("ICICI")));
    }

    @Test
    void nestedIdenticalSegmentsShareTokenAndRenameFile(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/ICICI_CODE_BANK/ICICI_CODE_BANK.txt", "hello\n"));

        int exitCode = new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        assertEquals(0, exitCode);

        Path outputZip = findSingleZipOutput(tempDir, "ICICI_CODE_BANK.zip");
        String repoToken = outputZip.getFileName().toString().replace(".zip", "");

        assertTrue(repoToken.matches("[0-9A-F]{8}"));
        assertTrue(zipContainsEntry(outputZip, repoToken + "/" + repoToken + "/" + repoToken + ".txt"));
        assertFalse(listZipEntries(outputZip).stream().anyMatch(entry -> entry.contains("ICICI_CODE_BANK")));
    }

    @Test
    void sameOriginalValueMapsToSameTokenWithinRun(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of(
                "ICICI_CODE_BANK/a.txt", "one\n",
                "ICICI_CODE_BANK/b.txt", "two\n"));

        new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        Path outputZip = findSingleZipOutput(tempDir, "ICICI_CODE_BANK.zip");

        Set<String> repoFolderNames = new HashSet<>();
        for (String entry : listZipEntries(outputZip)) {
            if (entry.endsWith("/a.txt") || entry.endsWith("/b.txt")) {
                repoFolderNames.add(entry.substring(0, entry.indexOf('/')));
            }
        }
        assertEquals(1, repoFolderNames.size());
    }

    @Test
    void genericPathsRemainUnchanged(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("myproject.zip");
        createZip(repoZip, Map.of(
                "src/main/App.java", "public class App {}\n",
                "README.md", "# docs\n",
                "pom.xml", "<project/>\n"));

        new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});
        Path outputZip = tempDir.resolve("myproject.zip");

        assertTrue(Files.isRegularFile(outputZip));
        assertTrue(zipContainsEntry(outputZip, "myproject/src/main/App.java"));
        assertTrue(zipContainsEntry(outputZip, "myproject/README.md"));
        assertTrue(zipContainsEntry(outputZip, "myproject/pom.xml"));
    }

    @Test
    void roundtripRestoresOriginalPathsAndContent(@TempDir Path tempDir) throws Exception {
        Map<String, String> originalFiles = Map.of(
                "ICICI_CODE_BANK/ICICI_Config/secrets.txt", "admin@icici.com\n",
                "ICICI_CODE_BANK/README.md", "# ICICI portal\n");

        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(originalZip, originalFiles);

        new com.scrambler.app.MaskingApplication(configFor(tempDir)).run(new String[]{originalZip.toString()});
        Path maskedZip = findSingleZipOutput(tempDir, "ICICI_CODE_BANK.zip");

        int exitCode = new com.scrambler.app.UnmaskingApplication(configFor(tempDir)).run(new String[]{
                maskedZip.toString()
        });
        assertEquals(0, exitCode);

        Path restoredZip = tempDir.resolve(com.scrambler.app.UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            assertEquals(entry.getValue(), readZipEntry(restoredZip, entry.getKey()), "Mismatch for " + entry.getKey());
        }
    }

    @Test
    void differentRunsMayProduceDifferentTokens(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        createZip(repoZip, Map.of("ICICI_CODE_BANK/app.txt", "data\n"));

        Path runOneDir = tempDir.resolve("run-one");
        Path runTwoDir = tempDir.resolve("run-two");
        Files.createDirectories(runOneDir);
        Files.createDirectories(runTwoDir);
        Files.copy(repoZip, runOneDir.resolve("ICICI_CODE_BANK.zip"));
        Files.copy(repoZip, runTwoDir.resolve("ICICI_CODE_BANK.zip"));

        new com.scrambler.app.MaskingApplication(configFor(runOneDir)).run(new String[]{
                runOneDir.resolve("ICICI_CODE_BANK.zip").toString()
        });
        new com.scrambler.app.MaskingApplication(configFor(runTwoDir)).run(new String[]{
                runTwoDir.resolve("ICICI_CODE_BANK.zip").toString()
        });

        String tokenOne = findSingleZipOutput(runOneDir, "ICICI_CODE_BANK.zip").getFileName().toString();
        String tokenTwo = findSingleZipOutput(runTwoDir, "ICICI_CODE_BANK.zip").getFileName().toString();
        assertNotEquals(tokenOne, tokenTwo);
    }

    private static ScramblerConfig configFor(Path workspaceBase) {
        return ScramblerConfig.builder()
                .workspaceBasePath(workspaceBase)
                .build();
    }

    private static Path findSingleZipOutput(Path directory, String inputZipName) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".zip")
                            && !path.getFileName().toString().equals(inputZipName))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Masked output ZIP not found in " + directory));
        }
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
        return listZipEntries(zipPath).contains(entryName);
    }

    private static java.util.List<String> listZipEntries(Path zipPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.List<String> entries = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
            return entries;
        }
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
