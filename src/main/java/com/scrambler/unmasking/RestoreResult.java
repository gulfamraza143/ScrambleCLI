package com.scrambler.unmasking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated outcome of an unmask restore run.
 */
public final class RestoreResult {

    private int filesProcessed;
    private int tokensRestored;
    private final List<String> warnings;

    /**
     * Creates an empty restore result.
     */
    public RestoreResult() {
        this.warnings = new ArrayList<>();
    }

    /**
     * Returns the number of files processed during restore.
     *
     * @return files processed count
     */
    public int getFilesProcessed() {
        return filesProcessed;
    }

    /**
     * Returns the number of masked tokens restored to original values.
     *
     * @return tokens restored count
     */
    public int getTokensRestored() {
        return tokensRestored;
    }

    /**
     * Returns warnings collected during restore, such as offset mismatches.
     *
     * @return immutable warning messages
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Records that one file was processed.
     */
    public void incrementFilesProcessed() {
        filesProcessed++;
    }

    /**
     * Adds to the restored token count.
     *
     * @param count number of tokens restored in the latest operation
     */
    public void addTokensRestored(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        tokensRestored += count;
    }

    /**
     * Appends a warning message.
     *
     * @param warning non-blank warning detail
     */
    public void addWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            throw new IllegalArgumentException("warning must not be blank");
        }
        warnings.add(warning);
    }
}
