package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.exception.ReportException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads {@link ReportSchema#REPORT_FILENAME} into {@link EntityReportRecord} rows.
 */
public final class XlsxReportReader {

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * Loads entity report rows from an XLSX file.
     *
     * @param inputPath path to {@code entity_report.xlsx}
     * @return parsed report rows in file order
     * @throws ReportException when reading or schema validation fails
     */
    public List<EntityReportRecord> read(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");

        try (Workbook workbook = WorkbookFactory.create(inputPath.toFile())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new ReportException("Entity report workbook is empty: " + inputPath);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new ReportException("Missing entity report header row");
            }

            List<String> headerColumns = readRow(headerRow);
            ReportSchema.validateXlsxHeader(headerColumns);

            List<EntityReportRecord> records = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                List<String> values = readRow(row);
                if (values.size() != ReportSchema.XLSX_COLUMNS.size()) {
                    throw new ReportException("Invalid entity report row with " + values.size() + " columns");
                }
                records.add(toRecord(values));
            }
            return List.copyOf(records);
        } catch (IOException e) {
            throw new ReportException("Failed to read entity report: " + inputPath, e);
        }
    }

    private List<String> readRow(Row row) {
        List<String> values = new ArrayList<>(ReportSchema.XLSX_COLUMNS.size());
        for (int column = 0; column < ReportSchema.XLSX_COLUMNS.size(); column++) {
            Cell cell = row.getCell(column);
            values.add(cell == null ? "" : dataFormatter.formatCellValue(cell));
        }
        return values;
    }

    private static EntityReportRecord toRecord(List<String> values) {
        try {
            return new EntityReportRecord(
                    ReportSchema.CURRENT_VERSION,
                    values.get(1),
                    EntityType.valueOf(values.get(0)),
                    values.get(2),
                    values.get(3),
                    Integer.parseInt(values.get(4)),
                    Integer.parseInt(values.get(5)));
        } catch (IllegalArgumentException e) {
            throw new ReportException("Invalid entity report row: " + String.join(",", values), e);
        }
    }

    private static boolean isBlankRow(Row row) {
        for (int column = 0; column < ReportSchema.XLSX_COLUMNS.size(); column++) {
            Cell cell = row.getCell(column);
            if (cell != null && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }
}
