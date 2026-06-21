package com.scrambler.unmasking;

import com.scrambler.exception.ReportException;
import com.scrambler.report.EntityReportRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Primary lookup index mapping masked tokens to original values.
 */
public final class MappingIndex {

    private final Map<String, String> maskedToOriginal;

    private MappingIndex(Map<String, String> maskedToOriginal) {
        this.maskedToOriginal = maskedToOriginal;
    }

    /**
     * Builds a lookup index from validated entity report rows.
     *
     * @param records entity report rows loaded from CSV
     * @return immutable masked-value lookup index
     * @throws ReportException when rows fail validation or contain duplicate masked values
     */
    public static MappingIndex from(List<EntityReportRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        RestoreValidator validator = new RestoreValidator();
        validator.validate(records);

        Map<String, String> maskedToOriginal = new HashMap<>();
        for (EntityReportRecord record : records) {
            String maskedValue = record.getMaskedValue();
            String originalValue = record.getOriginalValue();

            String existingOriginal = maskedToOriginal.get(maskedValue);
            if (existingOriginal != null) {
                if (!existingOriginal.equals(originalValue)) {
                    throw new ReportException("Duplicate masked value in entity report: " + maskedValue);
                }
                continue;
            }
            maskedToOriginal.put(maskedValue, originalValue);
        }

        return new MappingIndex(Map.copyOf(maskedToOriginal));
    }

    /**
     * Returns the original value for a masked token.
     *
     * @param maskedValue masked token from a file
     * @return original value when mapped
     */
    public String getOriginalValue(String maskedValue) {
        Objects.requireNonNull(maskedValue, "maskedValue must not be null");
        return maskedToOriginal.get(maskedValue);
    }

    /**
     * Returns whether the index contains a mapping for the masked token.
     *
     * @param maskedValue masked token candidate
     * @return true when a mapping exists
     */
    public boolean containsMaskedValue(String maskedValue) {
        Objects.requireNonNull(maskedValue, "maskedValue must not be null");
        return maskedToOriginal.containsKey(maskedValue);
    }

    /**
     * Returns the masked tokens known to this index.
     *
     * @return immutable set of masked values
     */
    public Set<String> getMaskedValues() {
        return Collections.unmodifiableSet(maskedToOriginal.keySet());
    }

    /**
     * Returns the number of mappings in this index.
     *
     * @return mapping count
     */
    public int size() {
        return maskedToOriginal.size();
    }
}
