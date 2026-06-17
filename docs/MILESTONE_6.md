# Milestone 6 — Unmasking Engine

# Overview

Milestone 6 introduces the `unmasking` package. Given masked TEXT content and a validated entity report, the unmasking engine restores original values by replacing masked tokens using a pre-built `MappingIndex`.

This milestone delivers the core restoration logic. It does not yet package output archives or expose a standalone CLI — that integration is Milestone 7.

# Objective

Deliver an unmasking subsystem that:

1. Loads `entity_report.csv` via `MappingLoader` and `CsvReportReader`.
2. Validates report rows with `RestoreValidator` before index construction.
3. Builds a `MappingIndex` keyed by `masked_value` → `original_value`.
4. Restores masked tokens in TEXT content using `UnmaskingEngine`.
5. Fails fast on orphan tokens, duplicate masked values, and unsupported report versions.
6. Applies end-to-start replacement (same rule as masking).
7. Never re-runs `DetectionEngine` during restore.
8. Provides automated unit test coverage including mask→unmask roundtrip at the content level.

# Architecture Impact

Milestone 6 implements the core of the unmasking flow:

```
entity_report.csv → Load Mappings → Validate → Build Index → Restore TEXT content
```

**Package introduced:** `unmasking`

**Exception introduced:** `MaskingException` for mask/unmask substitution failures.

**Dependency direction preserved:**

- `unmasking` depends on `report` (`EntityReportRecord`, `CsvReportReader`) and `masking` (`EntityReplacer` for span substitution).
- `unmasking` does not depend on `detection` at runtime — entity types in tokens are matched by pattern only for orphan detection.
- Stages do not call each other; callers orchestrate load → validate → index → unmask.

**Architectural invariants honored:**

- Primary lookup by `masked_value`; offsets are secondary (audit only in V1).
- Strict mode: orphan token in file → fail.
- Unknown `report_version` → fail.
- Never log `original_value`.
- DOCUMENT / IMAGE / SKIP files are out of scope for the engine (handled at app layer in Milestone 7).

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `MappingLoader` | `unmasking` | Loads report rows from CSV via shared reader |
| `RestoreValidator` | `unmasking` | Pre-restore validation: version, duplicates, corrupt rows |
| `MappingIndex` | `unmasking` | Immutable `masked_value` → `original_value` lookup |
| `UnmaskingEngine` | `unmasking` | Token scan, orphan detection, end-to-start restoration |
| `RestoreResult` | `unmasking` | Aggregate counters: files processed, tokens restored, warnings |
| `MaskingException` | `exception` | Unmask substitution and orphan token failures |

# Restoration Strategy

| Step | Behavior |
|------|----------|
| **Load** | `MappingLoader.load(reportPath)` → `List<EntityReportRecord>` |
| **Validate** | `RestoreValidator.validate(records)` — version, duplicates, required fields |
| **Index** | `MappingIndex.from(records)` — builds immutable lookup map |
| **Scan** | Regex detects token-shaped identifiers (`ENTITYTYPE_NNNNNN`) |
| **Orphan check** | Any token in content without index entry → `MaskingException` |
| **Replace** | Collect all occurrences per mapped token; apply end-to-start via `EntityReplacer` |

**Example:**

```
Input:  admin_email: EMAIL_000001
Output: admin_email: admin@icici.com
```

# Execution Flow

```
entity_report.csv
         │
         ▼
   MappingLoader.load(path)
         │
         ▼
   RestoreValidator.validate(records)
         │
         ▼
   MappingIndex.from(records)
         │
         ▼
   UnmaskingEngine.unmask(maskedContent, index, restoreResult)
         │  Detect orphan tokens → fail
         │  Build replacements for known tokens
         │  EntityReplacer.replace (end → start)
         ▼
   Restored content string
```

# Security Considerations

| Control | Detail |
|---------|--------|
| **Strict orphan policy** | Token in file not in report causes immediate failure |
| **No detection on unmask** | Trust report + masked files only; no re-scanning |
| **No original values in logs** | Engine and tests avoid printing restored secrets |
| **Report sensitivity** | CSV holds plaintext originals; treat as confidential |
| **Duplicate token rejection** | Prevents ambiguous restore mappings |

# Testing

**Test classes:**

- `UnmaskingEngineTest` (`src/test/java/com/scrambler/unmasking/`)
- `MappingIndexTest` (`src/test/java/com/scrambler/unmasking/`)
- `RestoreValidatorTest` (`src/test/java/com/scrambler/unmasking/`)

**Coverage includes:**

| Area | Examples Tested |
|------|-----------------|
| Single and multiple token restore | One and many tokens per content |
| Repeated occurrences | Same token restored at every offset |
| Orphan tokens | Missing mapping fails fast |
| Duplicate mappings | Rejected at index build |
| Unsupported version | Rejected on load/validate |
| Roundtrip | Mask → report → unmask restores original content |
| Structure | Line breaks preserved |
| False positives | Non-token identifiers ignored |

Run tests:

```bash
mvn test
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Report loaded and validated before restore | Met |
| MappingIndex built from validated rows | Met |
| Tokens restored by `masked_value` lookup | Met |
| Orphan tokens fail (strict mode) | Met |
| End-to-start replacement order | Met |
| Mask→unmask content roundtrip passes | Met |
| All tests pass | Met |

# Known Limitations

- **No CLI workflow** — engine is library-only until Milestone 7.
- **No archive I/O** — does not extract masked zip or create `original_repo.zip`.
- **No charset validation** — UTF-8 assumed; charset column not in V1.0 schema.
- **No offset mismatch warnings** — offsets stored but not validated during restore in V1.
- **No `entity_id` lookup** — index keyed by `masked_value` only.
- **TEXT scope only** — non-TEXT file pass-through is the caller's responsibility.

# Next Steps

Proceed to **Milestone 7 — Repository Restoration Workflow** to integrate unmasking into `UnmaskingApplication`, add `ZipCreator`, and deliver the full `masked_repo.zip` + `entity_report.csv` → `original_repo.zip` CLI. See `docs/MILESTONE_7.md`.
