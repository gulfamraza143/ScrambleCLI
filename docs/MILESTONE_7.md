# Milestone 7 — Repository Restoration Workflow

# Overview

Milestone 7 delivers the first complete repository restoration workflow. It integrates Milestone 6 unmasking components with archive, workspace, inventory, and classification layers into `UnmaskingApplication` — the CLI entry point for `scramble-unmask.jar`.

Input: `masked_repo.zip` + `entity_report.csv`  
Output: `original_repo.zip` with all TEXT content restored.

# Objective

Deliver a working unmask CLI that:

1. Accepts two arguments: masked repository ZIP and entity report CSV.
2. Extracts the masked archive into an isolated workspace.
3. Loads, validates, and indexes entity report mappings.
4. Restores TEXT files in the workspace; leaves DOCUMENT, IMAGE, and SKIP files unchanged.
5. Packages the restored tree into `original_repo.zip` with preserved structure.
6. Cleans up the workspace on success or failure.
7. Prints a restore summary without exposing original values.
8. Returns standardized exit codes.
9. Provides integration test coverage including end-to-end mask→unmask roundtrip.

# Architecture Impact

Milestone 7 completes the unmasking flow:

```
masked_repo.zip + entity_report.csv
  → Workspace
  → Extract
  → Load Mappings
  → Validate
  → Build Index
  → Restore TEXT files
  → Create original_repo.zip
  → Cleanup
```

**New application:** `UnmaskingApplication` in `app`

**New archive component:** `ZipCreator` in `archive`

**New file component:** `TextFileWriter` in `file`

**Packages reused (not redesigned):**

`WorkspaceManager`, `Workspace`, `ZipExtractor`, `MappingLoader`, `RestoreValidator`, `MappingIndex`, `UnmaskingEngine`, `FileIterator`, `FileClassifier`, `TextFileReader`

**Dependency direction preserved:**

- `app` orchestrates; stage packages do not call each other.
- No service layers, factories, or duplicate CSV/archive logic introduced.
- Same `report_version` 1.0 contract as mask JAR.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `UnmaskingApplication` | `app` | CLI entry point, workflow orchestration, summary, exit codes |
| `ZipCreator` | `archive` | Streaming ZIP creation with workspace staging and atomic move |
| `TextFileWriter` | `file` | UTF-8 text file write for restored content |
| `RestoreResult` (extended) | `unmasking` | Added `filesRestored` counter for CLI summary |

# Execution Flow

```
java -jar scramble-unmask.jar <masked_repo.zip> <entity_report.csv>
         │
         ▼
   Validate CLI args (exactly two paths)
         │
         ▼
   WorkspaceManager.createWorkspace(config)
         │
         ▼
   ZipExtractor.extract(maskedZip, workspace)
         │
         ▼
   MappingLoader.load(reportPath)
         │
         ▼
   RestoreValidator.validate(records)
         │
         ▼
   MappingIndex.from(records)
         │
         ▼
   For each FileInfo in inventory:
         │  Increment files processed
         │  If TEXT:
         │    Read masked content
         │    UnmaskingEngine.unmask → write restored content
         │    Track files/tokens restored
         │  Else (DOCUMENT/IMAGE/SKIP):
         │    Leave file unchanged on disk
         ▼
   ZipCreator.create(extractionRoot, original_repo.zip, workspace)
         │  Stage zip in workspace; atomic move to output directory
         ▼
   Print restore summary → stdout
         │
         ▼
   WorkspaceManager.cleanup(workspace) → finally block
         │
         ▼
   Exit 0 (success) | 1 (usage) | 2 (processing failure)
```

# CLI Contract

**Usage:**

```bash
java -jar scramble-unmask.jar masked_repo.zip entity_report.csv
```

**Exit codes:**

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Invalid usage |
| `2` | Processing failure |

**Summary output:**

```
Files Processed: X
Files Restored: Y
Tokens Restored: Z
Output Archive:
/path/to/original_repo.zip
```

**Output location:** `original_repo.zip` is written beside the input `masked_repo.zip`. Existing output files are overwritten.

# Restore Scope

| Category | Behavior |
|----------|----------|
| **TEXT** | Read → unmask → write restored content |
| **DOCUMENT** | Unchanged (remains placeholder if masked) |
| **IMAGE** | Unchanged |
| **SKIP** | Unchanged |

# Security Considerations

| Control | Detail |
|---------|--------|
| **Zip-slip / zip bomb** | Same limits as mask flow via `ZipExtractor` + `ScramblerConfig` |
| **Workspace cleanup** | Guaranteed in `finally`; no reliance on `deleteOnExit` |
| **Atomic output** | ZIP staged in workspace, moved to final path; cross-filesystem fallback |
| **Fail-fast** | Orphan tokens, missing report, bad version abort before output zip |
| **No secrets in stdout** | Summary shows counts and paths only |
| **Strict restore** | Orphan token in any TEXT file → exit `2` |

# Testing

**Test class:** `UnmaskingApplicationIntegrationTest` (`src/test/java/com/scrambler/app/`)

**Scenarios covered:**

| # | Scenario |
|---|----------|
| 1 | Masked zip + CSV → original zip |
| 2 | Multiple restored TEXT files |
| 3 | Non-TEXT files remain unchanged |
| 4 | Missing report file → failure |
| 5 | Unsupported report version → failure |
| 6 | Orphan token → failure |
| 7 | Repository with no masked tokens |
| 8 | Workspace cleanup after run |
| 9 | Output zip preserves structure and paths |
| 10 | End-to-end roundtrip: mask → report → unmask → restored repo |

Run tests:

```bash
mvn test
```

Build JARs:

```bash
mvn package
# target/scramble-mask.jar
# target/scramble-unmask.jar
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| `UnmaskingApplication` CLI with two-argument contract | Met |
| TEXT content restored from report mappings | Met |
| Non-TEXT files pass through unchanged | Met |
| `original_repo.zip` created with preserved paths | Met |
| Workspace cleanup on success and failure | Met |
| Exit codes 0 / 1 / 2 | Met |
| Restore summary without original values | Met |
| Integration tests pass (10 scenarios) | Met |
| Mask→unmask roundtrip restores original TEXT | Met |

# Known Limitations

- **TEXT-only restoration** — document and image placeholders are not restored to originals.
- **No masked ZIP in mask CLI** — `MaskingApplication` writes the report but does not yet emit `masked_repo.zip`; roundtrip tests construct masked archives programmatically.
- **UTF-8 only** — no charset policy or per-row charset validation.
- **No `entity_id` in report** — lookup by `masked_value` only.
- **No `pipeline` package** — orchestration remains in `app` entry points.
- **No `replacement` package** — document/image placeholder swap not implemented in mask flow.
- **No lenient mode** — strict orphan and version failures only.

# Next Steps

See `docs/ROADMAP.md` for post–Milestone 7 work: masked ZIP output in mask CLI, binary placeholder replacement, `pipeline` orchestration, charset support, and V2 evolution items from `docs/ARCHITECTURE.md`.
