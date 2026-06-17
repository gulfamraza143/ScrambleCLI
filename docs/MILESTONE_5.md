# Milestone 5 — CSV Report Contract

# Overview

Milestone 5 introduces the `report` package and persists masking mappings to `entity_report.csv`. This file is the cross-JAR contract between `scramble-mask.jar` and `scramble-unmask.jar`: it holds the reversible mapping required to restore TEXT content.

`MaskingApplication` now writes the report alongside masking and prints a summary of masked files and entity counts.

# Objective

Deliver a report subsystem that:

1. Defines `ReportSchema` as the single source of truth for column order and version rules.
2. Writes `entity_report.csv` from `MappingRegistry` using RFC-style CSV escaping.
3. Reads report rows back via `CsvReportReader` into `EntityReportRecord`.
4. Validates `report_version` 1.0 compatibility on read.
5. Integrates report persistence into `MaskingApplication`.
6. Provides automated test coverage for roundtrip fidelity, Unicode, and edge cases.

# Architecture Impact

Milestone 5 extends the masking flow to:

```
repo.zip → Extract → Inventory → Classify → Detect → Mask → Generate entity_report.csv
```

**Package introduced:** `report`

**Exception introduced:** `ReportException` for CSV read/write and schema validation failures.

**Dependency direction preserved:**

- `report` depends on `detection` (`EntityType`) and `masking` (`MappingRecord`, `MappingRegistry`).
- Both mask and unmask JARs embed the same schema from the same build artifact.
- No parallel mapping row types; `EntityReportRecord` is the only report row model.

**Architectural invariants honored:**

- `MappingRegistry` → `CsvReportWriter` → `EntityReportRecord` is the single write path.
- No CSV rows for DOCUMENT / IMAGE placeholder swaps or SKIP files.
- Unknown `report_version` fails hard on read.
- RFC-style escaping for commas, quotes, newlines, and Unicode.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `ReportSchema` | `report` | Version constant, column order, header validation |
| `EntityReportRecord` | `report` | Immutable mapping row; `fromMappingRecord` converter |
| `CsvFormat` | `report` | RFC-style CSV field escaping and record parsing |
| `CsvReportWriter` | `report` | Streams registry rows to `entity_report.csv` |
| `CsvReportReader` | `report` | Parses CSV into `EntityReportRecord` list |
| `ReportException` | `exception` | Report I/O and schema failures |

**Modified component:**

| Component | Change |
|-----------|--------|
| `MaskingApplication` | Writes `entity_report.csv` next to input ZIP; prints masked file and entity counts |

# CSV Schema (report_version 1.0)

**Version header row:**

```
report_version,1.0
```

**Data columns:**

| Column | Purpose |
|--------|---------|
| `repo_relative_path` | Repository-relative path (forward slashes) |
| `entity_type` | `EntityType` enum name |
| `original_value` | Matched plaintext (high sensitivity) |
| `masked_value` | Replacement token (primary unmask lookup key) |
| `start_offset` | Inclusive offset in original content |
| `end_offset` | Exclusive offset in original content |

**Note:** Architecture defines optional `entity_id` and `charset` columns for future versions. V1.0 implementation uses the six data columns above without `entity_id` or `charset`.

# Execution Flow

```
MappingRegistry (in-memory, post-mask)
         │
         ▼
   CsvReportWriter.write(registry, reportPath)
         │  Sort records deterministically
         │  Write version header + column header
         │  Write one RFC-escaped row per mapping
         ▼
   entity_report.csv (beside input repo.zip)
         │
         ▼
   MaskingApplication summary:
     Masked files: N
     Entities masked: M
     Report: <absolute path>
```

**Report output location:** Same directory as the input `repo.zip` argument.

# Security Considerations

| Risk | Mitigation |
|------|------------|
| **Plaintext secrets in CSV** | `entity_report.csv` is as sensitive as the source repo; restrict file permissions |
| **CSV corruption** | RFC-style escaping prevents silent field corruption on roundtrip |
| **Version mismatch** | `ReportSchema.validateVersion` rejects unsupported versions on read |
| **No secrets in stdout** | Summary prints paths and counts only, never `original_value` |

# Testing

**Test class:** `CsvReportTest` (`src/test/java/com/scrambler/report/`)

**Coverage includes:**

| Area | Examples Tested |
|------|-----------------|
| Write format | Version header + data rows |
| Read parsing | Records reconstructed from CSV |
| Roundtrip | All fields preserved write → read |
| Unicode | Non-ASCII values preserved |
| Escaping | Commas, quotes, embedded newlines |
| Empty report | Headers only when no entities masked |
| Version rejection | Unsupported `report_version` fails |
| Deterministic order | Stable row ordering across writes |
| Overwrite | Existing report file replaced |

Run tests:

```bash
mvn test
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| `entity_report.csv` written after masking | Met |
| Schema version 1.0 with correct column order | Met |
| RFC-style CSV escaping | Met |
| Reader validates version and header | Met |
| Roundtrip preserves all fields | Met |
| All tests pass | Met |

# Known Limitations

- **No masked ZIP output** — report is written but `masked_repo.zip` packaging is not yet implemented in `MaskingApplication`.
- **No `entity_id` column** — deferred to a future schema version bump.
- **No `charset` column** — UTF-8 assumed; charset mismatch validation deferred.
- **No unmasking** — report is produced; restoration engine is Milestones 6–7.
- **No document/image rows** — placeholder swaps produce no report entries (by design).

# Next Steps

Proceed to **Milestone 6 — Unmasking Engine** to load the report, build a lookup index, validate restore compatibility, and restore masked tokens in TEXT content. See `docs/MILESTONE_6.md`.
