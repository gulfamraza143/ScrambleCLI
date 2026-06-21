package com.scrambler.report;

import com.scrambler.exception.ReportException;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes {@link ReportSchema#REPORT_FILENAME} from an in-memory {@link MappingRegistry}.
 */
public final class CsvReportWriter {

    private static final Comparator<MappingRecord> DETERMINISTIC_ORDER = Comparator
            .comparing(MappingRecord::getRepoRelativePath)
            .thenComparingInt(MappingRecord::getStartOffset)
            .thenComparingInt(MappingRecord::getEndOffset)
            .thenComparing(record -> record.getEntityType().name())
            .thenComparing(MappingRecord::getMaskedValue);

    /**
     * Persists registry mappings to {@code entity_report.csv} using UTF-8 encoding.
     *
     * @param mappingRegistry in-memory mappings produced by masking
     * @param outputPath      destination CSV path; existing files are overwritten
     * @throws ReportException when writing fails
     */
    public void write(MappingRegistry mappingRegistry, Path outputPath) {
        Objects.requireNonNull(mappingRegistry, "mappingRegistry must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        List<MappingRecord> records = new ArrayList<>(mappingRegistry.getRecords());
        records.sort(DETERMINISTIC_ORDER);

        Path parent = outputPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new ReportException("Failed to create report directory: " + parent, e);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(ReportSchema.VERSION_HEADER);
            writer.write(',');
            writer.write(ReportSchema.LEGACY_CSV_VERSION);
            writer.newLine();

            writer.write(String.join(",", ReportSchema.DATA_COLUMNS));
            writer.newLine();

            for (MappingRecord record : records) {
                writer.write(formatDataRow(record));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new ReportException("Failed to write entity report: " + outputPath, e);
        }
    }

    private static String formatDataRow(MappingRecord record) {
        return ReportSchema.DATA_COLUMNS.stream()
                .map(column -> valueForColumn(column, record))
                .map(CsvFormat::escapeField)
                .collect(Collectors.joining(","));
    }

    private static String valueForColumn(String column, MappingRecord record) {
        return switch (column) {
            case "repo_relative_path" -> record.getRepoRelativePath();
            case "entity_type" -> record.getEntityType().name();
            case "original_value" -> record.getOriginalValue();
            case "masked_value" -> record.getMaskedValue();
            case "start_offset" -> Integer.toString(record.getStartOffset());
            case "end_offset" -> Integer.toString(record.getEndOffset());
            default -> throw new IllegalStateException("Unknown report column: " + column);
        };
    }
}
