# Milestone 2 — File Classification

# Overview

Milestone 2 adds extension-based file classification to the SCRAMBLECLI inventory pipeline. After extraction and file discovery (Milestone 1), each `FileInfo` is assigned a `FileCategory` that determines how the file will be handled in later processing stages per `ReversibilityPolicy` in [ARCHITECTURE.md](ARCHITECTURE.md).

Classification is purely extension-based: no content inspection, no MIME detection, no magic-byte sniffing.

# Objective

Deliver a `classify` package that:

1. Assigns every inventoried file one of four categories: TEXT, DOCUMENT, IMAGE, or SKIP.
2. Integrates with `MaskingApplication` so inventory output includes the category label.
3. Preserves all Milestone 1 behavior (extraction, inventory, cleanup, exit codes, security controls).
4. Provides automated test coverage for classification rules and edge cases.

# Architecture Impact

Milestone 2 extends the masking flow to:

```
repo.zip → Extract → Inventory Files → Classify Files
```

**Package introduced:** `classify`

**New dependency:** `app` and future pipeline stages depend on `classify`; `classify` depends on `inventory` (`FileInfo`) only. `classify` does not depend on `pipeline`, `detection`, or `masking`, preserving the dependency direction in [ARCHITECTURE.md](ARCHITECTURE.md).

**Ownership rules preserved:**

- `FileInfo` remains owned by `inventory`; `ClassificationResult` wraps the same `FileInfo` instance.
- Extension lists are embedded in `FileClassifier` at this milestone. The architecture designates `SupportedExtensions` in `config` as the long-term single source of truth; consolidation into `config` is a future refactor, not a Milestone 2 requirement.
- Classification does not invoke detection. The scope gate (TEXT-only detection) will be enforced at the `pipeline` layer in a later milestone.

**Inventory output change:** Each line now includes a left-aligned category label before the repository-relative path.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `FileCategory` | `classify` | Enum: `TEXT`, `DOCUMENT`, `IMAGE`, `SKIP` |
| `ClassificationResult` | `classify` | Immutable pair of `FileInfo` and assigned `FileCategory` |
| `FileClassifier` | `classify` | Extension-based classification using repository-relative path |

**Modified component:**

| Component | Change |
|-----------|--------|
| `MaskingApplication` | Calls `FileClassifier.classify` for each `FileInfo`; prints category-padded label in inventory output |

# Execution Flow

Milestone 2 reuses the Milestone 1 flow through inventory construction, then adds classification at output time:

```
java -jar scramble-mask.jar <repo.zip>
         │
         ▼
   [Milestone 1: extract → collect files → build RepositoryInventory]
         │
         ▼
   For each FileInfo in inventory:
         │
         ▼
   FileClassifier.classify(fileInfo)
         │  Extract extension from repoRelativePath (case-insensitive)
         │  Lookup in extension map; unknown/no extension → TEXT
         ▼
   ClassificationResult(fileInfo, category)
         │
         ▼
   Print: "<CATEGORY>  <repoRelativePath>"
         │
         ▼
   Print: "Total Files: N"
```

**Example output:**

```
===== INVENTORY =====

TEXT      src/App.java
TEXT      config/application.yml
DOCUMENT  report.pdf
IMAGE     logo.png
SKIP      app.jar

Total Files: 5
```

# Classification Strategy

| Rule | Behavior |
|------|----------|
| **Mechanism** | Extension-based only; derived from the last `.` in `repoRelativePath` |
| **Case sensitivity** | Extension comparison is case-insensitive (normalized to lowercase) |
| **Unknown extension** | Defaults to `TEXT` |
| **No extension** | Defaults to `TEXT` (e.g., `Makefile`, `Dockerfile`) |
| **Trailing dot** | Treated as no extension → `TEXT` (e.g., `archive/file.`) |
| **Content inspection** | None |
| **MIME detection** | None |

**Categories and representative extensions:**

| Category | Purpose (per architecture) | Example Extensions |
|----------|---------------------------|-------------------|
| `TEXT` | Reversible detect-and-mask target | `.java`, `.yml`, `.json`, `.env`, `.pem`, … |
| `DOCUMENT` | Placeholder replacement only | `.pdf`, `.docx`, `.xlsx`, `.pptx`, … |
| `IMAGE` | Placeholder replacement only | `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, … |
| `SKIP` | Pass-through unchanged | `.jar`, `.class`, `.exe`, `.tar`, `.gz`, … |

Compound extensions such as `.tar.gz` classify by the final segment (`gz` → SKIP).

# Security Considerations

Milestone 2 introduces no new security surface. All Milestone 1 controls remain in effect:

- Zip-slip and zip bomb protection during extraction.
- Workspace isolation and guaranteed cleanup.
- Fail-fast exit code `2` on archive and processing failures.

Classification operates on repository-relative path strings already validated by `WorkspaceManager`; it does not read file contents or expand the attack surface.

# Testing

**Test class:** `FileClassifierTest` (`src/test/java/com/scrambler/classify/`)

**Result:** 22 passing tests (JUnit 5, parameterized and unit tests).

**Coverage includes:**

| Area | Examples Tested |
|------|-----------------|
| TEXT classification | `.java`, `.yml`, `.md`, `.env`, `.pem` |
| DOCUMENT classification | `.pdf`, `.docx`, `.xlsx` |
| IMAGE classification | `.png`, `.jpeg`, `.webp` |
| SKIP classification | `.jar`, `.war`, `.class`, `.tar.gz`, `.7z` |
| Case-insensitive extensions | `.YAML`, `.DOCX`, `.JPEG`, `.CLASS` |
| Unknown extensions | `data/unknown` → TEXT |
| No extensions | `Makefile` → TEXT |
| Trailing-dot filenames | `archive/file.` → TEXT |
| Null input validation | `classify(null)` → `NullPointerException` |
| FileInfo identity | `ClassificationResult` returns the same `FileInfo` instance |

Run tests:

```bash
mvn test
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Files classified correctly by extension | Met |
| Inventory output includes category labels | Met |
| Existing Milestone 1 functionality preserved | Met |
| All tests pass | Met |

# Known Limitations

- **Extension-only classification** — files mislabeled by extension (e.g., a `.txt` file containing binary data) are not detected.
- **No content sniffing** — magic bytes, MIME types, and charset analysis are not used.
- **No detection** — sensitive entity scanning is not implemented; classification does not inspect file contents.
- **No masking** — file contents are never read or modified.
- **No reporting** — no `entity_report.csv` is produced.
- **No unmasking** — the unmask JAR and flow are not implemented.
- **Extension list not yet centralized** — `FileClassifier` owns the extension map; migration to `SupportedExtensions` in `config` is deferred.

# Next Steps

Proceed to entity **detection** for `FileCategory.TEXT` files. Detection will be gated at the pipeline layer so only TEXT files enter the detection engine, per [ARCHITECTURE.md](ARCHITECTURE.md). Classification output from this milestone feeds that stage directly.
