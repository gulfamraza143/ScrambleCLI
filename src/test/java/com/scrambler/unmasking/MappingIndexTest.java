package com.scrambler.unmasking;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.ReportException;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingIndexTest {

    @Test
    void buildsMaskedValueToOriginalValueLookup() {
        MappingIndex index = MappingIndex.from(List.of(
                record("a.txt", EntityType.EMAIL, "admin@icici.com", "EMAIL_000001", 0, 15),
                record("b.txt", EntityType.URL, "https://example.com", "URL_000001", 10, 29)));

        assertEquals("admin@icici.com", index.getOriginalValue("EMAIL_000001"));
        assertEquals("https://example.com", index.getOriginalValue("URL_000001"));
        assertEquals(2, index.size());
    }

    @Test
    void rejectsDuplicateMaskedValues() {
        List<EntityReportRecord> records = List.of(
                record("a.txt", EntityType.EMAIL, "first@icici.com", "EMAIL_000001", 0, 15),
                record("b.txt", EntityType.EMAIL, "second@icici.com", "EMAIL_000001", 0, 16));

        ReportException exception = assertThrows(ReportException.class, () -> MappingIndex.from(records));
        assertTrue(exception.getMessage().contains("Duplicate masked value"));
    }

    @Test
    void rejectsCorruptReportRow() {
        RestoreValidator validator = new RestoreValidator();
        List<EntityReportRecord> records = new ArrayList<>();
        records.add(null);

        ReportException exception = assertThrows(ReportException.class, () -> validator.validate(records));
        assertTrue(exception.getMessage().contains("Corrupt entity report row"));
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
