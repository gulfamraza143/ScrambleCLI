package com.scrambler.masking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory store of masking mappings for a run.
 */
public final class MappingRegistry {

    private final List<MappingRecord> records = new ArrayList<>();

    /**
     * Records one masked entity mapping.
     *
     * @param record mapping to store
     */
    public void register(MappingRecord record) {
        if (record == null) {
            throw new NullPointerException("record must not be null");
        }
        records.add(record);
    }

    /**
     * Returns all registered mappings in insertion order.
     *
     * @return unmodifiable view of mapping records
     */
    public List<MappingRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }
}
