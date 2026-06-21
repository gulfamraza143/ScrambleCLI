package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;

import java.nio.file.Path;
import java.util.List;

/**
 * Test helper for writing entity reports in XLSX or CSV format.
 */
public final class TestReportWriter {

    private TestReportWriter() {
    }

    /**
     * Writes an XLSX entity report from mapping records.
     *
     * @param mappingRegistry registry containing mappings
     * @param outputPath      destination path ending in {@code .xlsx}
     */
    public static void writeXlsx(MappingRegistry mappingRegistry, Path outputPath) {
        new XlsxReportWriter().write(mappingRegistry, outputPath);
    }

    /**
     * Writes an XLSX entity report from explicit rows.
     *
     * @param outputPath destination path ending in {@code .xlsx}
     * @param rows       mapping rows to persist
     */
    public static void writeXlsx(Path outputPath, List<MappingRecord> rows) {
        MappingRegistry registry = new MappingRegistry();
        rows.forEach(registry::register);
        writeXlsx(registry, outputPath);
    }

    /**
     * Writes a legacy CSV entity report from explicit rows.
     *
     * @param outputPath destination path ending in {@code .csv}
     * @param rows       mapping rows to persist
     */
    public static void writeCsv(Path outputPath, List<MappingRecord> rows) {
        MappingRegistry registry = new MappingRegistry();
        rows.forEach(registry::register);
        new CsvReportWriter().write(registry, outputPath);
    }

    /**
     * Creates a mapping record for tests.
     */
    public static MappingRecord record(
            String path,
            EntityType type,
            String original,
            String masked,
            int start,
            int end) {
        return new MappingRecord(path, type, original, masked, start, end);
    }
}
