package com.scrambler.report;

import com.scrambler.detection.EntityType;
import com.scrambler.masking.MappingRecord;

import java.util.Objects;

/**
 * One row in {@link ReportSchema#REPORT_FILENAME} representing a masked entity mapping.
 */
public final class EntityReportRecord {

    private final String reportVersion;
    private final String repoRelativePath;
    private final EntityType entityType;
    private final String originalValue;
    private final String maskedValue;
    private final int startOffset;
    private final int endOffset;

    /**
     * Creates an entity report record.
     *
     * @param reportVersion    schema version for this report
     * @param repoRelativePath repository-relative path using forward slashes
     * @param entityType       detected entity type
     * @param originalValue    matched text
     * @param maskedValue      replacement token
     * @param startOffset      inclusive start offset in the original content
     * @param endOffset        exclusive end offset in the original content
     */
    public EntityReportRecord(
            String reportVersion,
            String repoRelativePath,
            EntityType entityType,
            String originalValue,
            String maskedValue,
            int startOffset,
            int endOffset) {
        this.reportVersion = Objects.requireNonNull(reportVersion, "reportVersion must not be null");
        this.repoRelativePath = Objects.requireNonNull(repoRelativePath, "repoRelativePath must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.originalValue = Objects.requireNonNull(originalValue, "originalValue must not be null");
        this.maskedValue = Objects.requireNonNull(maskedValue, "maskedValue must not be null");
        if (repoRelativePath.isBlank()) {
            throw new IllegalArgumentException("repoRelativePath must not be blank");
        }
        if (maskedValue.isBlank()) {
            throw new IllegalArgumentException("maskedValue must not be blank");
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must not be negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must not be before startOffset");
        }
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    /**
     * Converts an in-memory mapping record into a report row for the current schema version.
     *
     * @param mappingRecord registry record produced during masking
     * @return report row for CSV persistence
     */
    public static EntityReportRecord fromMappingRecord(MappingRecord mappingRecord) {
        Objects.requireNonNull(mappingRecord, "mappingRecord must not be null");
        return new EntityReportRecord(
                ReportSchema.CURRENT_VERSION,
                mappingRecord.getRepoRelativePath(),
                mappingRecord.getEntityType(),
                mappingRecord.getOriginalValue(),
                mappingRecord.getMaskedValue(),
                mappingRecord.getStartOffset(),
                mappingRecord.getEndOffset());
    }

    public String getReportVersion() {
        return reportVersion;
    }

    public String getRepoRelativePath() {
        return repoRelativePath;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EntityReportRecord that)) {
            return false;
        }
        return startOffset == that.startOffset
                && endOffset == that.endOffset
                && reportVersion.equals(that.reportVersion)
                && repoRelativePath.equals(that.repoRelativePath)
                && entityType == that.entityType
                && originalValue.equals(that.originalValue)
                && maskedValue.equals(that.maskedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                reportVersion,
                repoRelativePath,
                entityType,
                originalValue,
                maskedValue,
                startOffset,
                endOffset);
    }
}
