package com.scrambler.unmasking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.ReportException;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreValidatorTest {

    private final RestoreValidator validator = new RestoreValidator();

    @Test
    void acceptsValidReportRows() {
        assertDoesNotThrow(() -> validator.validate(List.of(
                record("a.txt", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 0, 15))));
    }

    @Test
    void rejectsDuplicateMaskedValues() {
        List<EntityReportRecord> records = List.of(
                record("a.txt", EntityType.EMAIL, "first@icici.com", "EMAIL_000001", 0, 15),
                record("b.txt", EntityType.EMAIL, "second@icici.com", "EMAIL_000001", 0, 16));

        ReportException exception = assertThrows(ReportException.class, () -> validator.validate(records));
        assertTrue(exception.getMessage().contains("Duplicate masked value"));
    }

    @Test
    void rejectsInconsistentReportVersions() {
        EntityReportRecord first = new EntityReportRecord(
                "1.0", "a.txt", EntityType.EMAIL, "a@b.com", "EMAIL_000001", 0, 7);
        EntityReportRecord second = new EntityReportRecord(
                "9.9", "b.txt", EntityType.EMAIL, "c@d.com", "EMAIL_000002", 0, 7);

        ReportException exception = assertThrows(ReportException.class, () -> validator.validate(List.of(first, second)));
        assertTrue(exception.getMessage().contains("Inconsistent report versions"));
    }

    @Test
    void rejectsBlankOriginalValue() {
        ReportException exception = assertThrows(ReportException.class, () -> validator.validate(List.of(
                record("a.txt", EntityType.EMAIL, "   ", "SCRAMBLE_EMAIL_000001", 0, 15))));
        assertTrue(exception.getMessage().contains("original_value is missing"));
    }

    @Test
    void rejectsUnsupportedReportVersion() {
        EntityReportRecord unsupported = new EntityReportRecord(
                "2.0", "a.txt", EntityType.EMAIL, "a@b.com", "EMAIL_000001", 0, 7);

        ReportException exception = assertThrows(ReportException.class, () -> validator.validate(List.of(unsupported)));
        assertTrue(exception.getMessage().contains("Unsupported report version: 2.0"));
    }

    private static EntityReportRecord record(
            String path,
            EntityType type,
            String original,
            String masked,
            int start,
            int end) {
        return new EntityReportRecord(
                ReportSchema.CURRENT_VERSION,
                path,
                type,
                original,
                masked,
                start,
                end);
    }
}
