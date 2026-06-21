package com.scrambler.unmasking;

import com.scrambler.exception.ReportException;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates entity report rows before building an unmask lookup index.
 */
public final class RestoreValidator {

    /**
     * Validates report rows for restore compatibility.
     *
     * @param records entity report rows loaded from CSV
     * @throws ReportException when validation fails
     */
    public void validate(List<EntityReportRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        String reportVersion = null;
        Map<String, String> maskedToOriginal = new HashMap<>();

        for (EntityReportRecord record : records) {
            validateRecord(record);

            if (reportVersion == null) {
                reportVersion = record.getReportVersion();
            } else if (!reportVersion.equals(record.getReportVersion())) {
                throw new ReportException("Inconsistent report versions in entity report");
            }

            ReportSchema.validateVersion(record.getReportVersion());

            String existingOriginal = maskedToOriginal.get(record.getMaskedValue());
            if (existingOriginal != null && !existingOriginal.equals(record.getOriginalValue())) {
                throw new ReportException("Duplicate masked value in entity report: " + record.getMaskedValue());
            }
            maskedToOriginal.put(record.getMaskedValue(), record.getOriginalValue());
        }
    }

    private static void validateRecord(EntityReportRecord record) {
        if (record == null) {
            throw new ReportException("Corrupt entity report row: record is null");
        }
        if (record.getRepoRelativePath() == null || record.getRepoRelativePath().isBlank()) {
            throw new ReportException("Corrupt entity report row: repo_relative_path is missing");
        }
        if (record.getEntityType() == null) {
            throw new ReportException("Corrupt entity report row: entity_type is missing");
        }
        if (record.getOriginalValue() == null || record.getOriginalValue().isBlank()) {
            throw new ReportException("Corrupt entity report row: original_value is missing");
        }
        if (record.getMaskedValue() == null || record.getMaskedValue().isBlank()) {
            throw new ReportException("Corrupt entity report row: masked_value is missing");
        }
        if (record.getEndOffset() < record.getStartOffset()) {
            throw new ReportException("Corrupt entity report row: end_offset precedes start_offset");
        }
    }
}
