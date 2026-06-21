package com.scrambler.report;

import com.scrambler.exception.ReportException;

import java.util.List;
import java.util.Set;

/**
 * Defines the entity report schema and version compatibility rules.
 */
public final class ReportSchema {

    public static final String CURRENT_VERSION = "2.0";
    public static final String LEGACY_CSV_VERSION = "1.0";
    public static final String REPORT_FILENAME = "entity_report.xlsx";
    public static final String LEGACY_REPORT_FILENAME = "entity_report.csv";
    public static final String VERSION_HEADER = "report_version";

    public static final List<String> XLSX_COLUMNS = List.of(
            "entity_type",
            "file_path",
            "original_value",
            "masked_value",
            "start_offset",
            "end_offset");

    public static final List<String> DATA_COLUMNS = List.of(
            "repo_relative_path",
            "entity_type",
            "original_value",
            "masked_value",
            "start_offset",
            "end_offset");

    private static final Set<String> SUPPORTED_VERSIONS = Set.of(CURRENT_VERSION, LEGACY_CSV_VERSION);

    private ReportSchema() {
    }

    /**
     * Validates that the report version is supported by this build.
     *
     * @param reportVersion version read from the report header
     * @throws ReportException when the version is missing or unsupported
     */
    public static void validateVersion(String reportVersion) {
        if (reportVersion == null || reportVersion.isBlank()) {
            throw new ReportException("Report version is missing");
        }
        if (!SUPPORTED_VERSIONS.contains(reportVersion)) {
            throw new ReportException("Unsupported report version: " + reportVersion);
        }
    }

    /**
     * Validates that a parsed CSV header row matches the expected data column order.
     *
     * @param headerColumns column names from the report header row
     * @throws ReportException when the header does not match the schema
     */
    public static void validateDataHeader(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() != DATA_COLUMNS.size()) {
            throw new ReportException("Invalid entity report header");
        }
        for (int index = 0; index < DATA_COLUMNS.size(); index++) {
            if (!DATA_COLUMNS.get(index).equals(headerColumns.get(index))) {
                throw new ReportException("Invalid entity report header column at index " + index
                        + ": expected " + DATA_COLUMNS.get(index) + ", found " + headerColumns.get(index));
            }
        }
    }

    /**
     * Validates that a parsed XLSX header row matches the expected column order.
     *
     * @param headerColumns column names from the report header row
     * @throws ReportException when the header does not match the schema
     */
    public static void validateXlsxHeader(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() != XLSX_COLUMNS.size()) {
            throw new ReportException("Invalid entity report header");
        }
        for (int index = 0; index < XLSX_COLUMNS.size(); index++) {
            if (!XLSX_COLUMNS.get(index).equals(headerColumns.get(index))) {
                throw new ReportException("Invalid entity report header column at index " + index
                        + ": expected " + XLSX_COLUMNS.get(index) + ", found " + headerColumns.get(index));
            }
        }
    }
}
