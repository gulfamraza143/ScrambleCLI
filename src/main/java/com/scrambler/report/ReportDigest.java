package com.scrambler.report;

import com.scrambler.exception.ReportException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 digest sidecar for {@link ReportSchema#REPORT_FILENAME} tamper detection.
 */
public final class ReportDigest {

    public static final String DIGEST_FILENAME = "entity_report.sha256";

    private ReportDigest() {
    }

    /**
     * Computes the SHA-256 hex digest of a report file's UTF-8 bytes.
     *
     * @param reportPath path to {@code entity_report.csv}
     * @return lowercase hex digest
     */
    public static String computeHexDigest(Path reportPath) {
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        try {
            byte[] content = Files.readAllBytes(reportPath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (IOException e) {
            throw new ReportException("Failed to read entity report for digest: " + reportPath, e);
        } catch (NoSuchAlgorithmException e) {
            throw new ReportException("SHA-256 is not available", e);
        }
    }

    /**
     * Writes the digest sidecar next to the report file.
     *
     * @param reportPath path to {@code entity_report.csv}
     * @param digestPath destination digest file path
     */
    public static void write(Path reportPath, Path digestPath) {
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        Objects.requireNonNull(digestPath, "digestPath must not be null");

        String digest = computeHexDigest(reportPath);
        Path parent = digestPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new ReportException("Failed to create digest directory: " + parent, e);
            }
        }

        try {
            Files.writeString(digestPath, digest + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReportException("Failed to write report digest: " + digestPath, e);
        }
    }

    /**
     * Verifies the report file against an existing digest sidecar.
     *
     * @param reportPath path to {@code entity_report.csv}
     * @param digestPath path to {@code entity_report.sha256}
     * @throws ReportException when the digest file is missing, unreadable, or mismatched
     */
    public static void verify(Path reportPath, Path digestPath) {
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        Objects.requireNonNull(digestPath, "digestPath must not be null");

        if (!Files.isRegularFile(digestPath)) {
            throw new ReportException("Report digest file is missing: " + digestPath);
        }

        String expectedDigest;
        try {
            expectedDigest = Files.readString(digestPath, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new ReportException("Failed to read report digest: " + digestPath, e);
        }

        if (expectedDigest.isBlank()) {
            throw new ReportException("Report digest file is empty: " + digestPath);
        }

        String actualDigest = computeHexDigest(reportPath);
        if (!expectedDigest.equalsIgnoreCase(actualDigest)) {
            throw new ReportException("Entity report digest mismatch — report may have been tampered with");
        }
    }
}
