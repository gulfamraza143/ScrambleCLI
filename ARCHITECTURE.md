# SCRAMBLECLI — Architecture

Production-bound V1 architecture for a Java 21 repository sanitization CLI. This document locks all decisions and invariants that must hold before and during implementation.

**Status:** Approved for implementation  
**Technology:** Java 21, Maven, CLI — no Spring Boot, database, REST, OCR, AI, or Apache POI in V1

---

## 1. Product Overview

SCRAMBLECLI sanitizes repository archives by detecting and masking sensitive data in text files, replacing binary assets with placeholders, and producing a reversible mapping report for text restoration.

Two executable JARs:

| JAR | Purpose |
|-----|---------|
| `scramble-mask.jar` | Mask `repo.zip` → `masked_repo.zip` + `entity_report.csv` |
| `scramble-unmask.jar` | Restore `masked_repo.zip` + `entity_report.csv` → `original_repo.zip` |

### Masking Flow

```
repo.zip
  → Extract
  → Inventory Files
  → Classify Files
  → Detect Sensitive Entities (TEXT only)
  → Mask Sensitive Entities (TEXT only)
  → Generate entity_report.csv
  → Replace Documents/Images with Placeholders
  → Create masked_repo.zip
```

### Unmasking Flow

```
masked_repo.zip + entity_report.csv
  → Load Mappings
  → Restore Original Content (TEXT only)
  → Create original_repo.zip
```

---

## 2. Package Structure

```
com.scrambler
├── app
├── pipeline
├── archive
├── workspace
├── inventory
├── classify
├── detection
├── masking
├── replacement
├── unmasking
├── report
├── file
├── config
└── exception
```

### Package Responsibilities

| Package | Responsibility |
|---------|----------------|
| `app` | Entry points (`MaskingApplication`, `UnmaskingApplication`), CLI parsing, exit codes, user-facing errors |
| `pipeline` | Orchestrates execution flow; owns `PipelineContext` and `RunSummary` |
| `archive` | ZIP extraction and ZIP creation (streaming I/O) |
| `workspace` | Temp workspace lifecycle, extraction directories, cleanup, path normalization, zip-slip protection |
| `inventory` | Repository traversal, file discovery, `RepositoryInventory`, `FileInfo` |
| `classify` | File categorization into TEXT, DOCUMENT, IMAGE, SKIP; `ClassificationResult` |
| `detection` | `DetectionEngine`, `Entity`, `EntityType`, `DetectionContext`, `DetectionResult` |
| `masking` | `MaskingEngine`, `EntityReplacer`, `MappingRegistry` |
| `replacement` | `PlaceholderAssetProvider`, `BinaryPlaceholderCopier`, `ReplacementPlan` |
| `unmasking` | `UnmaskingEngine`, `MappingLoader`, `MappingIndex` |
| `report` | `CsvReportWriter`, `CsvReportReader`, `EntityReportRecord`, `ReportSchema` |
| `file` | `TextFileReader`, `TextFileWriter`, `CharsetPolicy` — text I/O and charset only |
| `config` | `ScramblerConfig`, `SupportedExtensions`, `CompanyDictionary`, `ReversibilityPolicy`, `ProcessingMode` |
| `exception` | `ArchiveException`, `FileProcessingException`, `MaskingException`, `ReportException` |

### Correctly Omitted for V1

No `util`, `model`, `audit`, `encryption`, REST, database, OCR, or POI packages.

---

## 3. Dependency Rules

### Dependency Direction (Invariant)

```
app → pipeline → stage packages → file, config
                      ↓
         detection (Entity/EntityType), inventory (FileInfo), report (EntityReportRecord)
                      ↓
                  exception
```

### Boundary Rules

| Rule | Detail |
|------|--------|
| **Entity ownership** | `Entity` and `EntityType` live in `detection`. Masking, report, and unmasking depend on `detection` for nouns only. Only `detection` defines entity types. Refactor to a separate `domain` package only if dependency cycles appear. |
| **FileInfo ownership** | `FileInfo` lives in `inventory`. Classify, detection, replacement, and pipeline consume it. No other package redefines file descriptors. |
| **Config isolation** | `ScramblerConfig` loads configuration. Stages receive immutable snapshots via context objects (e.g. `DetectionContext`). Stages must not read config files directly. |
| **File scope** | `file` is text I/O and charset only. Binary copy belongs in `archive` and `replacement`. Do not expand `file` into general I/O. |
| **Pipeline hub** | Stages must not depend on `pipeline`. `pipeline` passes `PipelineContext` into stages; stages return results upward. Stages must not call each other. |
| **Replacement vs report** | Placeholder swaps must not produce `entity_report.csv` rows. `replacement` is entirely outside the CSV contract. |
| **Cross-JAR contract** | `EntityReportRecord` and `ReportSchema` in `report` are the only mapping row type and schema definition. Both JARs embed the same schema from the same build artifact. |

---

## 4. V1 Product Policy

Encoded in `ReversibilityPolicy` (`config`):

| Category | Mask Behavior | Report Rows | Unmask Restore |
|----------|---------------|-------------|----------------|
| **TEXT** | Detect + mask with reversible tokens | Yes | Full restore |
| **DOCUMENT** | Replace with placeholder binary | No | No — remains placeholder |
| **IMAGE** | Replace with placeholder binary | No | No — remains placeholder |
| **SKIP** | Pass-through unchanged | No | Pass-through unchanged |

### Supported Extensions (V1 Baseline)

**TEXT** (reversible):

`.java`, `.py`, `.js`, `.ts`, `.yml`, `.yaml`, `.properties`, `.json`, `.xml`, `.sql`, `.csv`, `.txt`, `.md`, `.log`

**TEXT** (secret-bearing — required for V1):

`.env`, `.pem`, `.key`, `.p12`, `.crt`

**DOCUMENT** (placeholder only):

`.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`

**IMAGE** (placeholder only):

`.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.webp`

**SKIP** (pass-through):

`.jar`, `.war`, `.ear`, `.class`, `.exe`, `.dll`, `.so`, `.dylib`, `.bin`, `.7z`, `.rar`, `.tar`, `.gz`

Extension lists are owned by `SupportedExtensions` in `config` as the single source of truth.

### V1 Scope Honesty (Product Copy)

These limitations must appear in CLI help and product documentation:

- `original_repo.zip` restores **text secrets only**; document and image files remain placeholders.
- Password, bank account, and generic secret detection is **best-effort** with expected false positives/negatives.
- No OCR or POI: SCRAMBLECLI does not scan or mask inline content inside PDF/Office binaries.
- SKIP binaries may contain embedded secrets; SCRAMBLECLI does not scan binary content in V1.

---

## 5. Named Architectural Concepts

These are types or policies — not new packages.

| Concept | Owner | Purpose |
|---------|-------|---------|
| `PipelineContext` | `pipeline` | Workspace, config snapshot, run id, counters — passed through all stages. May cache per-file text content for detect+mask. |
| `RepositoryInventory` | `inventory` | Aggregate of discovered files, not just an iteration helper |
| `ClassificationResult` | `classify` | Category + rationale (extension-based in V1; content sniff in V2) |
| `ReversibilityPolicy` | `config` | TEXT=mask+unmask; DOCUMENT/IMAGE=placeholder only; SKIP=pass-through |
| `RunSummary` | `pipeline` or `app` | Files by category, entities masked, placeholders replaced, duration |
| `OverlapResolutionPolicy` | `detection` (internal) | Longest span wins; ties broken by rule priority |
| `TokenFormatSpec` | `report` or `config` | Architectural definition of `masked_value` shape |
| `CompatibilityPolicy` | `report` | `report_version` validation; optional tool version checks |
| `ProcessingMode` | `config` | Strict vs lenient (orphan tokens, missing files). **V1 default: strict.** |

---

## 6. Detection Architecture

### Public Surface

- **One public class:** `DetectionEngine`
- **No public per-entity detectors** (no `EmailDetector`, `PhoneDetector`, etc.)
- Internal rule records grouped by mechanism; package-private

### Scope Gate (Invariant)

Only `FileCategory.TEXT` enters detection. Enforced at `pipeline`, not inside individual rules.

### Internal Mechanisms

| Mechanism | Examples |
|-----------|----------|
| Dictionary | Company terms: ICICI, ICICIBANK, FXTP, WECARE, SCRAMBLE, etc. |
| Validated regex | Aadhaar, credit card, IFSC |
| General regex | Email, phone, PAN, URL, IP, JWT-shaped |
| Keyword / context | Passwords, API keys, tokens, secrets |

### Execution Order (Invariant)

1. Dictionary / company terms (word boundaries, case variants)
2. Validated regex (Aadhaar, credit card, IFSC)
3. General regex (email, phone, PAN, URL, IP, JWT-shaped)
4. Keyword / context rules (passwords, API keys, tokens)

### Overlap Resolution (Invariant)

Centralized in `DetectionEngine` before `DetectionResult` is returned:

- Longest span wins
- Ties broken by rule priority

### Entity ID (Invariant)

`entity_id` is assigned at **detection time**. Masking and report reference the same IDs. Never regenerate at mask time.

### Validation Mapping

| Entity Type | Validation |
|-------------|------------|
| Aadhaar | Verhoeff checksum |
| Credit card | Luhn |
| IFSC | 4 alpha + 7 alphanumeric structure |
| PAN | Format charset rules |
| JWT | Structural (three segments) — not signature verification |

### Detection Scope

**PII:** Email, phone, PAN, Aadhaar, passport  
**SPII:** Bank account, IFSC, UPI, credit card  
**Secrets:** Passwords, API keys, access tokens, JWT, AWS keys, SSH keys, private keys  
**Infrastructure:** URLs, hostnames, IP addresses  
**Company data:** Brand names, product names, project names, internal domains/URLs

Per-group enable flags exposed in `ScramblerConfig` (all default on in V1).

---

## 7. Masking Architecture

### Components

- `MaskingEngine` — orchestrates masking for a TEXT file
- `EntityReplacer` — applies substitutions
- `MappingRegistry` — in-memory truth before CSV persistence

### Invariants

| Invariant | Detail |
|-----------|--------|
| **Text only** | Masking applies only to TEXT files per `ReversibilityPolicy` |
| **Replacement order** | Apply substitutions end of file → start so offsets remain valid during mutation |
| **Token design** | Tokens must be unique, delimiter-safe, and unlikely to occur naturally. UUID-based. Defined by `TokenFormatSpec`. |
| **Single write path** | `MappingRegistry` → `CsvReportWriter` → `EntityReportRecord`. No parallel mapping structures. |
| **Charset** | Read and write through `file` + `CharsetPolicy`. One charset per file; per-row charset in CSV must be consistent for all rows of the same path. |
| **Zero-entity TEXT files** | Pass through to output zip unchanged. No report rows required. |
| **Single read** | One TEXT file read per file for detect + mask in the same pipeline segment (or cache content in `PipelineContext`). |
| **Idempotency** | Re-mask without fresh detection is **unsupported** in V1. |

### Unmask Lookup Contract

Unmask locates by `masked_value` first. `start_offset` / `end_offset` are secondary validation only.

---

## 8. Unmasking Architecture

### Components

- `UnmaskingEngine` — orchestrates restoration
- `MappingLoader` — reads `entity_report.csv` via `CsvReportReader`
- `MappingIndex` — primary lookup index

### Scope (Invariant)

- Unmask operates on report rows only.
- DOCUMENT / IMAGE / SKIP files copy from masked zip unchanged.
- **Never re-run `DetectionEngine` during unmask.** Trust report + masked files only.

### MappingIndex Keys

| Priority | Key | Purpose |
|----------|-----|---------|
| Primary | `(repo_relative_path, masked_value)` | Restoration lookup |
| Secondary | `entity_id` | Validation |

### Replacement Order (Invariant)

Same end → start rule as masking.

### Pre-Restore Validation (V1 Default: Strict)

| Condition | V1 Behavior |
|-----------|-------------|
| Unknown `report_version` | **Fail** |
| Token in file not in report | **Fail** |
| Report row for missing file | **Fail** |
| Charset mismatch | **Fail** |
| Offset mismatch but token found | **Warn** — restore by token |

---

## 9. CSV Contract

### Schema Owner

`ReportSchema` is the only definition of column order and validation. Both JARs embed the same schema.

### Columns (V1)

| Column | Required | Purpose |
|--------|----------|---------|
| `report_version` | Yes | Schema evolution |
| `entity_id` | Yes | Stable identity |
| `repo_relative_path` | Yes | Must match workspace normalization |
| `entity_type` | Yes | Audit and validation |
| `original_value` | Yes | V1 unmask restore (high security sensitivity) |
| `masked_value` | Yes | Primary unmask lookup key |
| `start_offset` | Yes | Secondary integrity check |
| `end_offset` | Yes | Secondary integrity check |
| `charset` | Yes | Critical for correct restore |

### Exclusions (Invariant)

- No rows for DOCUMENT / IMAGE placeholder swaps
- No rows for SKIP files

### CSV Handling (Invariant)

- Reader and writer must handle RFC-style CSV escaping (commas, quotes, newlines, Unicode)
- Silent CSV corruption is a top unmask failure mode — must not occur
- Unknown `report_version` → hard fail in unmask with actionable error

### V1 Optional Addition

`rule_id` — append as new column with version bump when needed. Helps debug false positives without breaking contract.

### Redundancy

`charset` per row is acceptable if identical for all rows of the same path.

---

## 10. Workspace Lifecycle

### Responsibilities (`workspace`)

- Creation and teardown of temp directories
- Extraction root management
- Path normalization
- Zip-slip protection
- Atomic output staging

### Lifecycle Phases (Invariant)

```
CREATE → EXTRACT → PROCESS → PACK → CLEANUP
```

`CLEANUP` always runs (in `finally`). Do not rely on `deleteOnExit` alone.

`pipeline` orchestrates; `workspace` implements directory semantics and safety.

### Path Normalization Rules

| Rule | Detail |
|------|--------|
| Separators | `repo_relative_path` uses forward slashes on all OSes |
| Prefix | No leading `./` |
| Traversal | Reject `..` and absolute paths inside archives |
| Casing | Case-sensitive as stored in zip |

### Temp Directory

- Configurable base path in `ScramblerConfig`
- Unique subdirectory per run (CI-safe)
- User-only readable permissions where OS permits

### Atomic Outputs (Invariant)

Write `masked_repo.zip` / `original_repo.zip` to a temp file in workspace, then move/rename. Avoids partial artifacts on failure.

### Unmask Workspace

Same rules: extract masked zip → restore TEXT in workspace → pack `original_repo.zip` → cleanup.

---

## 11. Failure Handling

### Exception Types (`exception`)

| Type | Subsystem |
|------|-----------|
| `ArchiveException` | ZIP extract/create, zip-slip, zip bomb limits |
| `FileProcessingException` | Text file read/write, charset errors |
| `MaskingException` | Mask/unmask substitution failures |
| `ReportException` | CSV read/write, schema validation |

Keep the set small. Optional fifth type `DetectionException` only if detection failures need distinct handling.

Optional base type `ScrambleException` with error codes for app-level exit code mapping.

### Exit Code Contract (`app`)

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Invalid CLI usage / missing inputs |
| `2` | Processing failure (archive, schema, mask/unmask) |

No partial-success exit codes in V1.

### Fail-Fast Defaults

| Stage / Condition | Behavior |
|-------------------|----------|
| Zip-slip / corrupt zip | Fail immediately |
| Report write failure after mask | **Fail** — masked zip without report is dangerous |
| Single TEXT file read failure | Fail (strict) |
| Unmask orphan token | Fail (strict) |
| Unknown report version | Fail |
| Charset mismatch on unmask | Fail |

### User-Facing Errors

- `app` formats stderr messages
- Suppress stack traces in default mode

---

## 12. Security

| Risk | Severity | Mitigation |
|------|----------|------------|
| `entity_report.csv` holds plaintext secrets | Critical | Treat as equally sensitive as source repo; restrict permissions; warn in CLI |
| Zip-slip | Critical | Mandatory in `workspace` |
| Zip bomb | High | Max entry count / uncompressed size in `ScramblerConfig` |
| Secrets in unlisted extensions | High | `.env`, `.pem`, `.key`, `.p12`, `.crt` in TEXT list |
| SKIP binaries with embedded secrets | Medium | Document limitation — no binary scanning in V1 |
| Predictable tokens | Medium | UUID-based tokens per `TokenFormatSpec` |
| Sensitive data in logs | Medium | Never log `original_value` |
| Temp dir permissions | Medium | User-only readable workspace |
| Regulatory data (Aadhaar/PAN) | Compliance | Product/legal documentation beyond this architecture |

---

## 13. Performance (V1 Expectations)

| Technique | V1 | Notes |
|-----------|-----|-------|
| Streaming zip I/O | Yes | Never load entire archive in memory |
| Streaming CSV write | Yes | Row-by-row from registry |
| Single TEXT file read | Yes | Detect + mask same content |
| Full-file content for regex | Acceptable | Multi-line secrets require it |
| Max file size limit | Yes | Skip or fail per config |
| Parallel processing | No (V2) | Per-file immutability prerequisite |
| Inventory walk | Single traversal | Classify during or immediately after walk |
| Dictionary matching | Linear | Upgrade to Aho-Corasick in V2 |

Hot path: `inventory` → `classify` → `detect` → `mask` for TEXT.

---

## 14. Technical Debt Mitigations

| Risk | Mitigation |
|------|------------|
| `DetectionEngine` becomes god class | Internal rule catalog by mechanism from day one |
| Offset-only restore | Tokens primary; offsets audit-only |
| Charset inconsistencies | Strict `CharsetPolicy`; charset mismatch fails unmask |
| `ScramblerConfig` naming drift | Rename to `ScrambleConfig` before public docs if desired |
| Missing roundtrip tests | Mask→unmask integration tests are the real contract |
| Extension-only classification | Secret-bearing extensions in TEXT list |
| Two JAR version skew | Embed/check tool version against report (V2) |
| Implicit stage ordering | Pipeline stage order documented here and enforced in `pipeline` |

---

## 15. Architecture Invariants (Checklist)

These must never be violated in V1:

- [ ] **TEXT** → detect → mask → report → unmask restores
- [ ] **DOCUMENT / IMAGE** → placeholder only → no report rows → no unmask restore
- [ ] **SKIP** → pass-through, no detect, no replace
- [ ] `EntityReportRecord` is the only mapping row type
- [ ] `ReportSchema` is the cross-JAR contract
- [ ] `workspace` normalizes paths and blocks zip-slip
- [ ] Single public `DetectionEngine` with internal rule mechanisms
- [ ] Unmask locates by `masked_value`; offsets are secondary
- [ ] `pipeline` orchestrates — stages do not call each other
- [ ] Workspace cleanup always runs
- [ ] `entity_id` assigned at detection time, never at mask time
- [ ] Replacement order: end → start (mask and unmask)
- [ ] One TEXT read per file for detect + mask
- [ ] Atomic zip output: temp file then rename
- [ ] No CSV rows for binary placeholder replacement
- [ ] Strict unmask by default — orphan tokens fail
- [ ] Never re-run detection during unmask
- [ ] Re-mask without fresh detection is unsupported
- [ ] RFC-style CSV escaping in reader and writer
- [ ] Never log `original_value`

---

## 16. Implementation Order

Build in contract order:

1. `exception` + `config` — policies, extensions, dictionary
2. `report` — `ReportSchema`, `EntityReportRecord`, CSV reader/writer
3. `workspace` + `archive` + `file` — extract, zip-slip, charset
4. `inventory` + `classify` — discovery and categorization
5. `detection` — `DetectionEngine`, overlap resolution, entity IDs
6. `masking` — registry, end-to-start replacement
7. `replacement` — placeholder swap (no CSV)
8. `pipeline` — `PipelineContext`, orchestration, `RunSummary`
9. `unmasking` — loader, index, strict validation
10. `app` — CLI, exit codes, user messages
11. Integration tests — mask→unmask roundtrip as contract gate

---

## 17. V2 Evolution (Out of V1 Scope)

Grow `DetectionEngine` internals and `ReportSchema` — not package count.

| Area | Direction |
|------|-----------|
| Detection | External YAML/JSON rule packs into same engine |
| Performance | Parallel TEXT file pipeline |
| Report | v2 columns: `rule_id`, `scramble_tool_version`; optional compression |
| Security | Encrypted mapping sidecar; hash-only originals |
| Audit | New `audit` package — run metadata, checksums |
| CLI | `--json`, `--dry-run`, `--strict` / `--lenient` |
| Dictionary | Multiple dictionaries, environment overlays |
| Matching | Aho-Corasick for company terms |
| Build | Multi-module: shared core + mask-cli + unmask-cli |

**Avoid in V2 unless product changes:** public per-entity detectors, Spring, database, REST.

Binary restore requires a new product mode with sidecar blob store — not the default unmask path.
