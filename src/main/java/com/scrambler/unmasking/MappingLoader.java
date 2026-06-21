package com.scrambler.unmasking;

import com.scrambler.exception.ReportException;
import com.scrambler.report.CsvReportReader;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Loads entity report rows from CSV or XLSX entity reports.
 */
public final class MappingLoader {

    private final CsvReportReader csvReportReader;
    private final XlsxReportReader xlsxReportReader;

    /**
     * Creates a mapping loader with the default report readers.
     */
    public MappingLoader() {
        this(new CsvReportReader(), new XlsxReportReader());
    }

    /**
     * Creates a mapping loader with supplied report readers.
     *
     * @param csvReportReader  reader for legacy CSV reports
     * @param xlsxReportReader reader for XLSX reports
     */
    MappingLoader(CsvReportReader csvReportReader, XlsxReportReader xlsxReportReader) {
        this.csvReportReader = Objects.requireNonNull(csvReportReader, "csvReportReader must not be null");
        this.xlsxReportReader = Objects.requireNonNull(xlsxReportReader, "xlsxReportReader must not be null");
    }

    /**
     * Loads mapping rows from an entity report file.
     *
     * @param reportPath path to {@code entity_report.xlsx} or legacy {@code entity_report.csv}
     * @return parsed report rows in file order
     * @throws ReportException when reading or schema validation fails
     */
    public List<EntityReportRecord> load(Path reportPath) {
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        String fileName = reportPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xlsx")) {
            return xlsxReportReader.read(reportPath);
        }
        if (fileName.endsWith(".csv")) {
            return csvReportReader.read(reportPath);
        }
        throw new ReportException("Unsupported entity report format: " + reportPath);
    }
}
