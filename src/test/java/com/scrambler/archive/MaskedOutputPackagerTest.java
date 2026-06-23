package com.scrambler.archive;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportWriter;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MaskedOutputPackagerTest {

    @Test
    void createsZipWithRepositoryOnly(@TempDir Path tempDir) throws Exception {
        Path repositoryRoot = tempDir.resolve("A81D9F22");
        Path nestedFile = repositoryRoot.resolve("nested/app.txt");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(nestedFile, "masked content\n");
        Files.writeString(repositoryRoot.resolve(".scramble_metadata"), "masked\n");

        WorkspaceManager workspaceManager = new WorkspaceManager();
        Workspace workspace = workspaceManager.createWorkspace(ScramblerConfig.builder()
                .workspaceBasePath(tempDir.resolve("workspace"))
                .build());
        Path outputZip = tempDir.resolve("A81D9F22.zip");

        try {
            new MaskedOutputPackager(workspaceManager).create(
                    repositoryRoot,
                    "A81D9F22",
                    outputZip,
                    workspace);
        } finally {
            workspaceManager.cleanup(workspace);
        }

        List<String> entries = listZipEntries(outputZip);
        assertEquals(List.of(
                "A81D9F22/.scramble_metadata",
                "A81D9F22/nested/app.txt"), entries);
        assertFalse(entries.contains(ReportSchema.REPORT_FILENAME));
        assertEquals("masked content\n", readZipEntry(outputZip, "A81D9F22/nested/app.txt"));
    }

    private static List<String> listZipEntries(Path zipPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
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
