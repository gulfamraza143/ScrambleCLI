package com.scrambler.unmasking;

import com.scrambler.exception.ReportException;
import com.scrambler.report.CsvReportReader;
import com.scrambler.report.EntityReportRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads entity report rows from {@code entity_report.csv} using the shared CSV reader.
 */
public final class MappingLoader {

    private final CsvReportReader csvReportReader;

    /**
     * Creates a mapping loader with the default CSV reader.
     */
    public MappingLoader() {
        this(new CsvReportReader());
    }

    /**
     * Creates a mapping loader with a supplied CSV reader.
     *
     * @param csvReportReader reader that parses entity report rows
     */
    MappingLoader(CsvReportReader csvReportReader) {
        this.csvReportReader = Objects.requireNonNull(csvReportReader, "csvReportReader must not be null");
    }

    /**
     * Loads mapping rows from an entity report CSV file.
     *
     * @param reportPath path to {@code entity_report.csv}
     * @return parsed report rows in file order
     * @throws ReportException when reading or schema validation fails
     */
    public List<EntityReportRecord> load(Path reportPath) {
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        return csvReportReader.read(reportPath);
    }
}
