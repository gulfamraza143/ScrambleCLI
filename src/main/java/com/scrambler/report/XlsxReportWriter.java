package com.scrambler.report;

import com.scrambler.exception.ReportException;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes {@link ReportSchema#REPORT_FILENAME} from an in-memory {@link MappingRegistry}.
 */
public final class XlsxReportWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(XlsxReportWriter.class);

    private static final Comparator<MappingRecord> DETERMINISTIC_ORDER = Comparator
            .comparing(MappingRecord::getRepoRelativePath)
            .thenComparingInt(MappingRecord::getStartOffset)
            .thenComparingInt(MappingRecord::getEndOffset)
            .thenComparing(record -> record.getEntityType().name())
            .thenComparing(MappingRecord::getMaskedValue);

    /**
     * Persists registry mappings to {@code entity_report.xlsx}.
     *
     * @param mappingRegistry in-memory mappings produced by masking
     * @param outputPath      destination XLSX path; existing files are overwritten
     * @throws ReportException when writing fails
     */
    public void write(MappingRegistry mappingRegistry, Path outputPath) {
        Objects.requireNonNull(mappingRegistry, "mappingRegistry must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        List<MappingRecord> records = new ArrayList<>(mappingRegistry.getRecords());
        records.sort(DETERMINISTIC_ORDER);
        LOGGER.info("Writing entity report with {} mapping records to {}", records.size(), outputPath);

        Path parent = outputPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new ReportException("Failed to create report directory: " + parent, e);
            }
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("entity_report");
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < ReportSchema.XLSX_COLUMNS.size(); column++) {
                headerRow.createCell(column).setCellValue(ReportSchema.XLSX_COLUMNS.get(column));
            }

            int rowIndex = 1;
            for (MappingRecord record : records) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(record.getEntityType().name());
                row.createCell(1).setCellValue(record.getRepoRelativePath());
                row.createCell(2).setCellValue(record.getOriginalValue());
                row.createCell(3).setCellValue(record.getMaskedValue());
                row.createCell(4).setCellValue(record.getStartOffset());
                row.createCell(5).setCellValue(record.getEndOffset());
            }

            for (int column = 0; column < ReportSchema.XLSX_COLUMNS.size(); column++) {
                sheet.autoSizeColumn(column);
            }

            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                workbook.write(outputStream);
            }
            LOGGER.info("Entity report written successfully to {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write entity report to {}", outputPath, e);
            throw new ReportException("Failed to write entity report: " + outputPath, e);
        }
    }
}
