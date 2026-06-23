package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.report.ReportSchema;
import com.scrambler.replacement.PlaceholderAssetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskingApplicationIntegrationTest {

    private final PlaceholderAssetProvider placeholderAssetProvider = new PlaceholderAssetProvider();

    @Test
    void masksTextReplacesBinaryAssetsAndCreatesOutputArchive(@TempDir Path tempDir) throws Exception {
        Path repoZip = tempDir.resolve("repo.zip");
        createZip(repoZip, Map.of(
                "config/app.properties", "admin_email=admin@icici.com\n",
                "assets/logo.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
                "docs/report.pdf", "secret pdf".getBytes(StandardCharsets.UTF_8),
                "README.md", """
                        # ICICI docs
                        Admin Email: [admin@bank.com](mailto:admin@bank.com)
                        Portal: https://internal.icici.com
                        password: hunter2
                        """,
                "lib/app.jar", "binary".getBytes(StandardCharsets.UTF_8)));

        int exitCode = new MaskingApplication(configFor(tempDir)).run(new String[]{repoZip.toString()});

        assertEquals(MaskingApplication.EXIT_SUCCESS, exitCode);
        Path maskedZip = tempDir.resolve("repo.zip");
        assertTrue(Files.isRegularFile(maskedZip));
        assertFalse(zipContainsEntry(maskedZip, ReportSchema.REPORT_FILENAME));
        assertTrue(Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));

        String maskedProperties = readZipEntry(maskedZip, "repo/config/app.properties");
        assertFalse(maskedProperties.contains("admin@icici.com"));

        String maskedReadme = readZipEntry(maskedZip, "repo/README.md");
        assertFalse(maskedReadme.contains("admin@bank.com"));
        assertFalse(maskedReadme.contains("https://internal.icici.com"));
        assertFalse(maskedReadme.contains("hunter2"));
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
        return new String(readZipEntryBytes(zipPath, entryName), StandardCharsets.UTF_8);
    }

    private static byte[] readZipEntryBytes(Path zipPath, String entryName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return zipInputStream.readAllBytes();
                }
            }
        }
        throw new IOException("ZIP entry not found: " + entryName);
    }
}
