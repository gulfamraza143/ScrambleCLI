# SCRAMBLE Enterprise — Format-Preserving Masking Engine

Repository sanitization CLI that detects sensitive data in source code, replaces it with **format-preserving synthetic values**, and produces a reversible audit report for full restoration.

---

## Quick Start

### Build

```bash
cd ScrambleCLI
mvn package
```

JARs are written to `target/`:

| JAR | Purpose |
|-----|---------|
| `scramble-mask.jar` | Mask a repository |
| `scramble-unmask.jar` | Restore a masked repository |

Requires **Java 21+**.

### Mask a repository

```bash
java -jar target/scramble-mask.jar <repo.zip|folder>
```

**Outputs** (written next to the input path):

| File | Description |
|------|-------------|
| `masked_code.zip` | Sanitized repository with format-preserving masked text |
| `entity_report.xlsx` | Audit trail of every original → masked mapping |
| `entity_report.sha256` | SHA-256 integrity digest of the report |

### Restore a masked repository

```bash
java -jar target/scramble-unmask.jar <masked_code.zip> <entity_report.xlsx>
```

**Output:** `original_repo.zip` — fully restored repository.

No token placeholders are required at restore time. The engine replaces literal masked values using the XLSX report.

---

## Pipeline Overview

```
Input (ZIP / TAR / TGZ / 7Z / folder)
  │
  ├─ 1. Workspace creation
  ├─ 2. Archive extraction
  ├─ 3. Nested archive expansion
  ├─ 4. Repository inventory
  ├─ 5. File classification
  ├─ 6. Sensitive data detection
  ├─ 7. Format-preserving masking
  ├─ 8. Binary placeholder replacement
  └─ 9. Report + output ZIP generation
```

---

## Phase 1 — Repository Traversal

**Input:** ZIP archive, TAR, TGZ, 7Z, or folder.

The `FileIterator` walks the extracted tree recursively. Nested archives (`.zip`, `.tar`, `.tgz`, `.7z`) inside the repository are expanded automatically before processing.

**Excluded extensions** (skipped entirely):

`.jar` `.war` `.ear` `.exe` `.dll` `.so` `.dylib`

---

## Phase 2 — File Classification

| Category | Extensions |
|----------|------------|
| **TEXT** | `.java` `.xml` `.yml` `.yaml` `.json` `.sql` `.txt` `.properties` `.js` `.ts` and more |
| **IMAGE** | `.png` `.jpg` `.jpeg` `.gif` `.bmp` `.webp` `.svg` |
| **DOCUMENT** | `.pdf` `.docx` `.xlsx` `.pptx` and other office formats |
| **ARCHIVE** | `.zip` `.tar` `.7z` (expanded, not masked in place) |
| **SKIP** | Binaries, compiled artifacts, markdown |

Only **TEXT** files undergo entity detection and format-preserving masking. **DOCUMENT** and **IMAGE** files are replaced with sanitized placeholder assets.

---

## Phase 3 — Brand Masking

Dictionary-driven brand term replacement with case preservation:

| Original | Masked |
|----------|--------|
| ICICI | LOTUS |
| ICICI Bank | LOTUS Bank |
| ICICI Lombard | LOTUS Lombard |
| ICICI Prudential | LOTUS Prudential |

Same source value always maps to the same masked value across the entire repository.

Brand terms are loaded from `src/main/resources/company-dictionary.txt`.

---

## Phase 4 — Entity Masking

A global `Map<String, String>` dictionary drives all replacements:

| Rule | Guarantee |
|------|-----------|
| Same original value | Same masked value everywhere |
| Different original value | Different masked value |
| Collision safety | No duplicate masked values for different originals |

**Supported entity types:**

| Domain | Types |
|--------|-------|
| PII | EMAIL, PHONE, PAN, AADHAAR |
| SPII | IFSC, UPI_ID, CREDIT_CARD, GSTIN, TAN |
| Secrets | PASSWORD, API_KEY, SECRET_KEY, JWT, PRIVATE_KEY |
| Infrastructure | URL, IP_ADDRESS, DATABASE_URL |
| Company | COMPANY_BRAND, CIN |

---

## Phase 5 — Format Preservation

Masked values preserve length, character classes, separators, and overall shape:

| Type | Example Original | Example Masked |
|------|------------------|----------------|
| EMAIL | `john@gmail.com` | `lotus001@example.com` |
| PHONE | `9876543210` | `8123456780` |
| PAN | `ABCPA1234F` | `RIVER1234X` |
| AADHAAR | `123412341232` | `987654321098` |
| GSTIN | `27AAPFU0939F1ZV` | `27RIVER1234X1ZA` |
| IFSC | `ICIC0001234` | `LOTS0001234` |
| UPI | `john@okicici` | `lotus001@demo` |

Assignment syntax is preserved for secrets (e.g. `password=hunter2` → `password=forest042`).

---

## Phase 6 — Noun-Based Generation

A bundled dictionary of **5,000+ nouns** (`noun-dictionary.txt`) seeds deterministic synthetic values:

```
lotus, river, forest, mountain, falcon, ocean, nebula, planet, ...
```

Generation is deterministic within a run — the same original always produces the same masked value.

---

## Phase 7 — Collision Check

Before committing a replacement, the engine checks whether the generated masked value already maps to a different original. If so, it generates the next candidate (up to 10,000 attempts).

This guarantees a **bijective** mapping:

- One original → one masked value
- One masked value → one original

---

## Phase 8 — Report

The entity report is written as **`entity_report.xlsx`** (schema version 2.0).

| Column | Description |
|--------|-------------|
| `entity_type` | Detected entity type (e.g. EMAIL, PAN) |
| `file_path` | Repository-relative path |
| `original_value` | Matched sensitive text |
| `masked_value` | Format-preserving replacement |
| `start_offset` | Inclusive start offset in original file |
| `end_offset` | Exclusive end offset in original file |

A SHA-256 sidecar (`entity_report.sha256`) is written for integrity verification during restore.

Legacy CSV reports (`entity_report.csv`, version 1.0) with `SCRAMBLE_*` token placeholders are still supported for restore.

---

## Phase 9 — Restore

```bash
java -jar scramble-unmask.jar masked_code.zip entity_report.xlsx
```

The unmasking engine:

1. Loads and validates the XLSX report (and digest, if present)
2. Builds a masked-value → original-value lookup index
3. Replaces every masked literal in TEXT files (longest match first)
4. Re-packages the repository as `original_repo.zip`

Same value is restored everywhere it appeared. Binary placeholder files (PDF, images, etc.) are not restored — they remain as sanitized stand-ins.

---

## Project Structure

```
ScrambleCLI/
├── src/main/java/com/scrambler/
│   ├── app/           MaskingApplication, UnmaskingApplication
│   ├── archive/       ArchiveExtractor, NestedArchiveProcessor, ZipCreator
│   ├── classify/      FileClassifier, FileCategory
│   ├── config/        ScramblerConfig, CompanyDictionary, SupportedExtensions
│   ├── detection/     DetectionEngine, EntityType, validators
│   ├── file/          TextFileReader, TextFileWriter
│   ├── inventory/     FileIterator, RepositoryInventory
│   ├── masking/       MaskingEngine, FormatPreservingGenerator, GlobalValueMapper
│   ├── replacement/   BinaryPlaceholderCopier
│   ├── report/        XlsxReportWriter, XlsxReportReader, ReportDigest
│   ├── unmasking/     UnmaskingEngine, MappingIndex, MappingLoader
│   └── workspace/     WorkspaceManager
├── src/main/resources/
│   ├── company-dictionary.txt
│   └── noun-dictionary.txt
├── src/test/java/     Unit and integration tests
└── docs/              Architecture and milestone documentation
```

---

## Testing

```bash
mvn test
```

Integration tests include full mask → report → unmask roundtrips against synthetic bank repository fixtures.

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Invalid usage (wrong number of arguments) |
| `2` | Processing failure (archive, detection, report, or restore error) |

---

## License

Internal / enterprise use. See project maintainers for licensing terms.
