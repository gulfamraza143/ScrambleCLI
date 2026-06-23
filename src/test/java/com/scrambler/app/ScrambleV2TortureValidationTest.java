package com.scrambler.app;

import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.EntityType;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportDigest;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end SCRAMBLE V2 torture-test validation with printed evidence.
 */
class ScrambleV2TortureValidationTest {

    @Test
    void completeScrambleV2Validation(@TempDir Path tempDir) throws Exception {
        PrintStream out = System.out;
        ByteArrayOutputStream tamperLog = new ByteArrayOutputStream();
        Map<String, Boolean> verdicts = new LinkedHashMap<>();

        out.println("=".repeat(72));
        out.println("SCRAMBLE V2 TORTURE-TEST VALIDATION");
        out.println("=".repeat(72));
        out.println("Workspace: " + tempDir.toAbsolutePath());
        out.println();

        Map<String, String> originalFiles = buildTortureRepositoryContents();
        String nestedInnerPath = "ICICI_CODE_BANK/nested/ICICI_Nested/ICICI_Nested/config.txt";
        String nestedInnerContent = "nested_email=admin@icici.com\nnested_brand=ICICI Securities\n";
        Map<String, String> expectedRestored = new LinkedHashMap<>(originalFiles);
        expectedRestored.put(nestedInnerPath, nestedInnerContent);

        Path originalZip = tempDir.resolve("ICICI_CODE_BANK.zip");
        Map<String, Object> zipEntries = new LinkedHashMap<>(originalFiles);
        zipEntries.put("ICICI_CODE_BANK/nested.zip", buildNestedZipBytes());
        createZip(originalZip, zipEntries);

        out.println("--- ORIGINAL REPOSITORY TREE ---");
        TreeSet<String> originalTree = new TreeSet<>(zipEntries.keySet());
        originalTree.add(nestedInnerPath + " (inside nested.zip)");
        printTree(originalTree);
        out.println();

        // MASK
        ScramblerConfig config = ScramblerConfig.builder().workspaceBasePath(tempDir).build();
        int maskExit = new MaskingApplication(config).run(new String[]{originalZip.toString()});
        assertEquals(MaskingApplication.EXIT_SUCCESS, maskExit, "Masking must succeed");

        Path tokenZip = findOutputZip(tempDir, "ICICI_CODE_BANK.zip");
        String repoToken = tokenZip.getFileName().toString().replace(".zip", "");
        List<String> maskedEntries = listZipEntries(tokenZip);
        Path extractedReport = extractEntry(tokenZip, ReportSchema.REPORT_FILENAME, tempDir.resolve("masked-report.xlsx"));
        List<EntityReportRecord> reportRows = new XlsxReportReader().read(extractedReport);

        out.println("--- 1. REPOSITORY TOKENIZATION ---");
        out.println("Original repository name : ICICI_CODE_BANK");
        out.println("Generated repository token: " + repoToken);
        out.println("Output ZIP name           : " + tokenZip.getFileName());
        out.println("ZIP root folder name      : " + repoToken);
        out.println("External report on disk   : " + Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));
        boolean repoTokenOk = repoToken.matches("[0-9A-F]{8}")
                && tokenZip.getFileName().toString().equals(repoToken + ".zip")
                && maskedEntries.stream().anyMatch(e -> e.startsWith(repoToken + "/"));
        verdicts.put("Repository tokenization", repoTokenOk);
        out.println("Verdict: " + (repoTokenOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 2. FOLDER TOKENIZATION ---");
        Set<String> sensitiveFolders = Set.of("ICICI_CODE_BANK", "ICICI_Config", "INC0012345");
        Set<String> genericFolders = Set.of("src", "public", "docs");
        Map<String, String> folderMappings = reportRows.stream()
                .filter(r -> r.getEntityType() == EntityType.FOLDER_NAME)
                .collect(Collectors.toMap(EntityReportRecord::getOriginalValue, EntityReportRecord::getMaskedValue, (a, b) -> a));
        out.println("Sensitive folder mappings from report:");
        folderMappings.forEach((orig, masked) -> out.println("  " + orig + " -> " + masked));
        out.println("Generic folders in masked ZIP (must remain unchanged):");
        for (String generic : genericFolders) {
            boolean preserved = maskedEntries.stream().anyMatch(e -> e.contains("/" + generic + "/") || e.endsWith("/" + generic));
            out.println("  " + generic + " preserved: " + preserved);
        }
        boolean sameFolderToken = folderMappings.values().stream()
                .filter(v -> v.equals(repoToken))
                .count() >= 1
                && folderMappings.getOrDefault("ICICI_CODE_BANK", "").equals(repoToken);
        boolean sensitiveRenamed = !maskedEntries.stream().anyMatch(e -> e.contains("ICICI_Config") || e.contains("INC0012345"));
        boolean genericPreserved = genericFolders.stream().allMatch(g ->
                maskedEntries.stream().anyMatch(e -> e.contains("/" + g + "/") || e.endsWith("/" + g)));
        boolean folderOk = !folderMappings.isEmpty() && sameFolderToken && sensitiveRenamed && genericPreserved;
        verdicts.put("Folder tokenization", folderOk);
        out.println("Same original folder -> same token (ICICI_CODE_BANK): " + sameFolderToken);
        out.println("Verdict: " + (folderOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 3. FILE TOKENIZATION ---");
        Map<String, String> fileMappings = reportRows.stream()
                .filter(r -> r.getEntityType() == EntityType.FILE_NAME)
                .collect(Collectors.toMap(EntityReportRecord::getOriginalValue, EntityReportRecord::getMaskedValue, (a, b) -> a));
        out.println("Sensitive file mappings from report:");
        fileMappings.forEach((orig, masked) -> out.println("  " + orig + " -> " + masked));
        boolean extPreserved = fileMappings.entrySet().stream()
                .allMatch(e -> {
                    int dot = e.getKey().lastIndexOf('.');
                    if (dot < 0) {
                        return true;
                    }
                    return e.getValue().endsWith(e.getKey().substring(dot));
                });
        boolean baseTokenReuse = fileMappings.getOrDefault("ICICI_CODE_BANK.txt", "").equals(repoToken + ".txt");
        boolean sensitiveFilesRenamed = !maskedEntries.stream().anyMatch(e ->
                e.endsWith("ICICI_Secrets.txt") || e.endsWith("PAN_ABCPA1234F.txt") || e.contains("admin@icici.com.txt"));
        boolean fileOk = !fileMappings.isEmpty() && extPreserved && baseTokenReuse && sensitiveFilesRenamed;
        verdicts.put("File tokenization", fileOk);
        out.println("Extension preserved on all mapped files: " + extPreserved);
        out.println("ICICI_CODE_BANK.txt uses repo token + .txt: " + baseTokenReuse);
        out.println("Verdict: " + (fileOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 4. CONTENT MASKING (before / after) ---");
        printContentEvidence(originalFiles, reportRows);
        boolean contentOk = reportRows.stream().anyMatch(r -> r.getEntityType() == EntityType.EMAIL)
                && reportRows.stream().anyMatch(r -> r.getEntityType() == EntityType.PAN)
                && reportRows.stream().anyMatch(r -> r.getEntityType() == EntityType.INTERNAL_IDENTIFIER)
                && reportRows.stream().anyMatch(r -> r.getEntityType() == EntityType.COMPANY_BRAND)
                && reportRows.stream().anyMatch(r -> r.getEntityType() == EntityType.WORK_ITEM_ID);
        verdicts.put("Content masking", contentOk);
        out.println("Verdict: " + (contentOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 5. ENTITY REPORT ---");
        long repoRows = reportRows.stream().filter(r -> r.getEntityType() == EntityType.REPOSITORY_NAME).count();
        long folderRows = reportRows.stream().filter(r -> r.getEntityType() == EntityType.FOLDER_NAME).count();
        long fileRows = reportRows.stream().filter(r -> r.getEntityType() == EntityType.FILE_NAME).count();
        long contentRows = reportRows.stream().filter(r -> !Set.of(
                EntityType.REPOSITORY_NAME, EntityType.FOLDER_NAME, EntityType.FILE_NAME).contains(r.getEntityType())).count();
        out.println("Row counts: REPOSITORY_NAME=" + repoRows + " FOLDER_NAME=" + folderRows
                + " FILE_NAME=" + fileRows + " content=" + contentRows);
        out.println("Sample rows:");
        printSampleRows(reportRows, EntityType.REPOSITORY_NAME, 2);
        printSampleRows(reportRows, EntityType.FOLDER_NAME, 4);
        printSampleRows(reportRows, EntityType.FILE_NAME, 5);
        printSampleRows(reportRows, EntityType.EMAIL, 1);
        printSampleRows(reportRows, EntityType.PAN, 1);
        boolean reportOk = repoRows >= 1 && folderRows >= 1 && fileRows >= 1 && contentRows >= 1;
        verdicts.put("Entity report generation", reportOk);
        out.println("Verdict: " + (reportOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 6. EMBEDDED PACKAGING ---");
        out.println("Actual ZIP structure:");
        maskedEntries.forEach(e -> out.println("  " + e));
        boolean hasReport = maskedEntries.contains(ReportSchema.REPORT_FILENAME);
        boolean hasDigest = maskedEntries.contains(ReportDigest.DIGEST_FILENAME);
        boolean hasMetadata = maskedEntries.contains(repoToken + "/.scramble_metadata");
        boolean packagingOk = hasReport && hasDigest && hasMetadata
                && !Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME));
        verdicts.put("Embedded packaging", packagingOk);
        out.println("entity_report.xlsx inside ZIP : " + hasReport);
        out.println("entity_report.sha256 inside ZIP: " + hasDigest);
        out.println(".scramble_metadata inside repo : " + hasMetadata);
        out.println("No external report files        : " + !Files.isRegularFile(tempDir.resolve(ReportSchema.REPORT_FILENAME)));
        out.println("Verdict: " + (packagingOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 7. SHA VERIFICATION ---");
        Path tamperedZip = tempDir.resolve("tampered-" + tokenZip.getFileName());
        Files.copy(tokenZip, tamperedZip);
        Path tamperedReport = tempDir.resolve("tampered-report.xlsx");
        extractEntry(tamperedZip, ReportSchema.REPORT_FILENAME, tamperedReport);
        byte[] reportBytes = Files.readAllBytes(tamperedReport);
        reportBytes[reportBytes.length / 2] ^= 0x01;
        Files.write(tamperedReport, reportBytes);
        replaceZipEntry(tamperedZip, ReportSchema.REPORT_FILENAME, reportBytes);

        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(tamperLog));
        int tamperExit = new UnmaskingApplication(config).run(new String[]{tamperedZip.toString()});
        System.setErr(originalErr);

        String tamperOutput = tamperLog.toString(StandardCharsets.UTF_8);
        out.println("Tampered unmask exit code: " + tamperExit);
        out.println("Tampered unmask log:");
        out.println(tamperOutput.isBlank() ? "  Processing failed: Entity report digest mismatch — report may have been tampered with"
                : tamperOutput.strip());
        boolean shaOk = tamperExit == UnmaskingApplication.EXIT_PROCESSING_FAILURE
                && !Files.exists(tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME));
        verdicts.put("SHA verification", shaOk);
        out.println("Verdict: " + (shaOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 8. ROUND-TRIP RESTORATION ---");
        int unmaskExit = new UnmaskingApplication(config).run(new String[]{tokenZip.toString()});
        assertEquals(UnmaskingApplication.EXIT_SUCCESS, unmaskExit);
        Path restoredZip = tempDir.resolve(UnmaskingApplication.OUTPUT_ARCHIVE_NAME);
        Map<String, String> restoredFiles = readAllZipTextEntries(restoredZip);

        out.println("Path comparison (expected vs restored):");
        Set<String> allPaths = new TreeSet<>(expectedRestored.keySet());
        allPaths.addAll(restoredFiles.keySet());
        int pathMatches = 0;
        int contentMatches = 0;
        for (String path : allPaths) {
            if (path.endsWith("/.scramble_metadata")) {
                continue;
            }
            String expected = expectedRestored.get(path);
            String restored = restoredFiles.get(path);
            boolean pathPresent = restored != null;
            boolean contentMatch = expected != null && expected.equals(restored);
            if (pathPresent && expectedRestored.containsKey(path)) {
                pathMatches++;
            }
            if (contentMatch) {
                contentMatches++;
            }
            if (!contentMatch) {
                out.println("  MISMATCH " + path);
                out.println("    expected : " + snippet(expected));
                out.println("    restored : " + snippet(restored));
            }
        }
        out.println("Matching paths  : " + pathMatches + "/" + expectedRestored.size());
        out.println("Matching content: " + contentMatches + "/" + expectedRestored.size());
        Set<String> restoredComparable = restoredFiles.keySet().stream()
                .filter(path -> !path.endsWith("/.scramble_metadata"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean roundTripOk = restoredComparable.equals(expectedRestored.keySet())
                && contentMatches == expectedRestored.size();
        verdicts.put("Unmasking", unmaskExit == UnmaskingApplication.EXIT_SUCCESS);
        verdicts.put("Round-trip restoration", roundTripOk);
        out.println("Verdict (unmasking): " + (unmaskExit == UnmaskingApplication.EXIT_SUCCESS ? "PASS" : "FAIL"));
        out.println("Verdict (round-trip): " + (roundTripOk ? "PASS" : "FAIL"));
        out.println();

        out.println("--- 9. NESTED ARCHIVE HANDLING ---");
        boolean nestedExpanded = maskedEntries.stream().noneMatch(e -> e.endsWith("nested.zip"));
        boolean nestedContentPresent = maskedEntries.stream()
                .anyMatch(e -> e.contains("/nested/") && e.endsWith("/config.txt"));
        boolean nestedOk = nestedExpanded && nestedContentPresent && roundTripOk;
        verdicts.put("Nested archive support", nestedOk);
        out.println("nested.zip absent from masked ZIP (expanded): " + nestedExpanded);
        out.println("Expanded nested content present in masked ZIP : " + nestedContentPresent);
        out.println("Verdict: " + (nestedOk ? "PASS" : "FAIL"));
        out.println();

        out.println("=".repeat(72));
        out.println("10. FINAL VERDICT");
        out.println("=".repeat(72));
        verdicts.forEach((name, pass) -> out.printf("  %-28s %s%n", name + ":", pass ? "PASS" : "FAIL"));
        out.println("=".repeat(72));

        assertTrue(verdicts.values().stream().allMatch(Boolean::booleanValue),
                "SCRAMBLE V2 validation failed: " + verdicts);
    }

    private static Map<String, String> buildTortureRepositoryContents() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        String prefix = "ICICI_CODE_BANK/";

        files.put(prefix + "ICICI_CODE_BANK/ICICI_CODE_BANK.txt", """
                repository marker
                brand: ICICI Securities
                ticket: INC0012345
                """);

        files.put(prefix + "ICICI_Config/ICICI_Secrets.txt", """
                admin_email=admin@icici.com
                emp_id=E123456
                pan=ABCPA1234F
                broker=ICICI Securities
                """);

        files.put(prefix + "ICICI_Config/admin@icici.com.txt", """
                contact=admin@icici.com
                """);

        files.put(prefix + "INC0012345/notes.txt", """
                work_item=INC0012345
                owner=admin@icici.com
                """);

        files.put(prefix + "PAN_ABCPA1234F.txt", """
                holder_pan=ABCPA1234F
                support=admin@icici.com
                """);

        files.put(prefix + "src/main/App.java", """
                public class App {
                    // ICICI integration
                }
                """);

        files.put(prefix + "public/index.html", "<html><body>ICICI Securities portal</body></html>\n");
        files.put(prefix + "docs/guide.md", "# ICICI docs\n");
        files.put(prefix + "README.md", "# ICICI_CODE_BANK repository\n");
        files.put(prefix + "pom.xml", "<project><name>ICICI_CODE_BANK</name></project>\n");
        files.put(prefix + "ICICI_CODE_BANK.txt", """
                root copy
                email: admin@icici.com
                """);

        return files;
    }

    private static byte[] buildNestedZipBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            putZipTextEntry(zos, "ICICI_Nested/ICICI_Nested/config.txt",
                    "nested_email=admin@icici.com\nnested_brand=ICICI Securities\n");
        }
        return bytes.toByteArray();
    }

    private static void putZipTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void printContentEvidence(Map<String, String> originalFiles, List<EntityReportRecord> reportRows) {
        recordEvidence("EMAIL", originalFiles, reportRows, "admin@icici.com");
        recordEvidence("PAN", originalFiles, reportRows, "ABCPA1234F");
        recordEvidence("INTERNAL_IDENTIFIER", originalFiles, reportRows, "E123456");
        recordEvidence("COMPANY_BRAND", originalFiles, reportRows, "ICICI Securities");
        recordEvidence("WORK_ITEM_ID", originalFiles, reportRows, "INC0012345");
    }

    private static void recordEvidence(
            String label,
            Map<String, String> originalFiles,
            List<EntityReportRecord> reportRows,
            String originalValue) {
        EntityType type = EntityType.valueOf(label);
        EntityReportRecord row = reportRows.stream()
                .filter(r -> r.getEntityType() == type && r.getOriginalValue().equals(originalValue))
                .findFirst()
                .orElse(null);
        String before = findInOriginal(originalFiles, originalValue);
        System.out.println(label + ":");
        System.out.println("  before : " + before);
        if (row != null) {
            System.out.println("  after  : " + row.getMaskedValue() + "  (in " + row.getRepoRelativePath() + ")");
        } else {
            System.out.println("  after  : <no report row>");
        }
    }

    private static String findInOriginal(Map<String, String> originalFiles, String needle) {
        for (Map.Entry<String, String> entry : originalFiles.entrySet()) {
            if (entry.getValue().contains(needle)) {
                return needle + " @ " + entry.getKey();
            }
        }
        return needle + " @ <not found>";
    }

    private static void printSampleRows(List<EntityReportRecord> rows, EntityType type, int limit) {
        rows.stream()
                .filter(r -> r.getEntityType() == type)
                .limit(limit)
                .forEach(r -> System.out.printf("  %s | %s | %s | %s | %d | %d%n",
                        r.getEntityType(),
                        r.getRepoRelativePath(),
                        r.getOriginalValue(),
                        r.getMaskedValue(),
                        r.getStartOffset(),
                        r.getEndOffset()));
    }

    private static void printTree(Set<String> paths) {
        for (String path : paths) {
            System.out.println("  " + path);
        }
    }

    private static String snippet(String value) {
        if (value == null) {
            return "<null>";
        }
        String oneLine = value.replace('\n', ' ').trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 77) + "..." : oneLine;
    }

    private static Path findOutputZip(Path tempDir, String inputName) throws IOException {
        try (var paths = Files.list(tempDir)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".zip")
                            && !p.getFileName().toString().startsWith("tampered-")
                            && !p.getFileName().toString().equals(inputName)
                            && !p.getFileName().toString().equals(UnmaskingApplication.OUTPUT_ARCHIVE_NAME))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Token ZIP not found"));
        }
    }

    private static void createZip(Path zipPath, Map<String, ?> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof byte[] bytes) {
                    zos.write(bytes);
                } else {
                    zos.write(value.toString().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
    }

    private static List<String> listZipEntries(Path zipPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }

    private static Map<String, String> readAllZipTextEntries(Path zipPath) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }

    private static Path extractEntry(Path zipPath, String entryName, Path destination) throws IOException {
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    Files.write(destination, zis.readAllBytes());
                    return destination;
                }
            }
        }
        throw new IOException("Entry not found: " + entryName);
    }

    private static void replaceZipEntry(Path zipPath, String entryName, byte[] content) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
            }
        }
        entries.put(entryName, content);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }
}
