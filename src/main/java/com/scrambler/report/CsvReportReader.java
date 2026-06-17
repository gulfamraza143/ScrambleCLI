package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.ReportException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads {@link ReportSchema#REPORT_FILENAME} into {@link EntityReportRecord} rows.
 */
public final class CsvReportReader {

    /**
     * Loads entity report rows from a CSV file using UTF-8 encoding.
     *
     * @param inputPath path to {@code entity_report.csv}
     * @return parsed report rows in file order
     * @throws ReportException when reading or schema validation fails
     */
    public List<EntityReportRecord> read(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");

        try (PushbackReader reader = new PushbackReader(
                new InputStreamReader(Files.newInputStream(inputPath), StandardCharsets.UTF_8))) {
            List<String> versionRow = CsvFormat.readRecord(reader);
            if (versionRow == null || versionRow.size() != 2) {
                throw new ReportException("Invalid entity report version header");
            }
            if (!ReportSchema.VERSION_HEADER.equals(versionRow.get(0))) {
                throw new ReportException("Invalid entity report version header key: " + versionRow.get(0));
            }

            String reportVersion = versionRow.get(1);
            ReportSchema.validateVersion(reportVersion);

            List<String> headerRow = CsvFormat.readRecord(reader);
            if (headerRow == null) {
                throw new ReportException("Missing entity report data header");
            }
            ReportSchema.validateDataHeader(headerRow);

            List<EntityReportRecord> records = new ArrayList<>();
            List<String> dataRow;
            while ((dataRow = CsvFormat.readRecord(reader)) != null) {
                if (dataRow.isEmpty() || isBlankRow(dataRow)) {
                    continue;
                }
                if (dataRow.size() != ReportSchema.DATA_COLUMNS.size()) {
                    throw new ReportException("Invalid entity report row with " + dataRow.size() + " columns");
                }
                records.add(toRecord(reportVersion, dataRow));
            }
            return List.copyOf(records);
        } catch (IOException e) {
            throw new ReportException("Failed to read entity report: " + inputPath, e);
        }
    }

    private static EntityReportRecord toRecord(String reportVersion, List<String> dataRow) {
        try {
            return new EntityReportRecord(
                    reportVersion,
                    dataRow.get(0),
                    EntityType.valueOf(dataRow.get(1)),
                    dataRow.get(2),
                    dataRow.get(3),
                    Integer.parseInt(dataRow.get(4)),
                    Integer.parseInt(dataRow.get(5)));
        } catch (IllegalArgumentException e) {
            throw new ReportException("Invalid entity report row: " + String.join(",", dataRow), e);
        }
    }

    private static boolean isBlankRow(List<String> dataRow) {
        for (String value : dataRow) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
