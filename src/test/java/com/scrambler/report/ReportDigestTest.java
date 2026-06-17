package com.scrambler.report;

import com.scrambler.exception.ReportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDigestTest {

    @Test
    void writeAndVerifyMatchingDigest(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        Path digestPath = tempDir.resolve(ReportDigest.DIGEST_FILENAME);
        Files.writeString(reportPath, """
                report_version,1.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                a.txt,EMAIL,admin@icici.com,SCRAMBLE_EMAIL_000001,0,15
                """, StandardCharsets.UTF_8);

        ReportDigest.write(reportPath, digestPath);

        assertDoesNotThrow(() -> ReportDigest.verify(reportPath, digestPath));
    }

    @Test
    void rejectsTamperedReport(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve(ReportSchema.REPORT_FILENAME);
        Path digestPath = tempDir.resolve(ReportDigest.DIGEST_FILENAME);
        Files.writeString(reportPath, """
                report_version,1.0
                repo_relative_path,entity_type,original_value,masked_value,start_offset,end_offset
                a.txt,EMAIL,admin@icici.com,SCRAMBLE_EMAIL_000001,0,15
                """, StandardCharsets.UTF_8);
        ReportDigest.write(reportPath, digestPath);
        Files.writeString(reportPath, Files.readString(reportPath).replace("admin@icici.com", "tampered@icici.com"));

        ReportException exception = assertThrows(ReportException.class, () -> ReportDigest.verify(reportPath, digestPath));
        assertTrue(exception.getMessage().contains("digest mismatch"));
    }
}
