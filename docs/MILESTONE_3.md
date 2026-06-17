# Milestone 3 — Sensitive Entity Detection

# Overview

Milestone 3 introduces the `detection` package and a single public `DetectionEngine` that scans TEXT file content for sensitive entities. Detection is rule-driven, overlap-aware, and produces stable offsets consumed by later masking and reporting stages.

Classification (Milestone 2) gates which files enter detection: only `FileCategory.TEXT` files are scanned. The engine itself does not enforce the scope gate; callers in `MaskingApplication` skip non-TEXT files.

# Objective

Deliver a detection subsystem that:

1. Exposes one public class: `DetectionEngine`.
2. Detects PII, SPII, secrets, infrastructure identifiers, and company dictionary terms.
3. Resolves overlapping matches centrally (longest span wins; ties broken by rule priority).
4. Returns `DetectionResult` with mandatory `start_offset` / `end_offset` for every entity.
5. Integrates with `MaskingApplication` for TEXT-only scanning.
6. Provides automated test coverage for entity types, overlap resolution, and secret patterns.

# Architecture Impact

Milestone 3 extends the masking flow to:

```
repo.zip → Extract → Inventory → Classify → Detect Sensitive Entities (TEXT only)
```

**Package introduced:** `detection`

**Supporting config:** `CompanyDictionary` in `config` supplies brand/product terms for dictionary matching.

**Dependency direction preserved:**

- `detection` depends on `inventory` (`FileInfo` via `DetectionContext`) and `config` (`CompanyDictionary`).
- `masking` and `report` depend on `detection` for `Entity` and `EntityType` nouns only.
- No public per-entity detector classes; internal `DetectionRule` records are package-private.

**Architectural invariants honored:**

- Single public `DetectionEngine` with internal rule mechanisms grouped by detection strategy.
- Overlap resolution is centralized before `DetectionResult` is returned.
- Detection does not read or write files; callers supply content via `DetectionContext`.
- Entity offsets are assigned at detection time and never regenerated at mask time.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `DetectionEngine` | `detection` | Rule catalog execution, overlap resolution, `DetectionResult` assembly |
| `DetectionContext` | `detection` | Immutable file metadata + text content input |
| `DetectionResult` | `detection` | Resolved entity list for one file |
| `Entity` | `detection` | Detected span: type, original value, start/end offsets |
| `EntityType` | `detection` | Enum of supported entity categories |
| `EntityDomain` | `detection` | High-level grouping (PII, SPII, secrets, etc.) |
| `DetectionRule` | `detection` | Internal rule record: pattern, type, priority |
| `CompanyDictionary` | `config` | Configurable company/brand term list |

**Modified component:**

| Component | Change |
|-----------|--------|
| `MaskingApplication` | Reads TEXT files, calls `DetectionEngine.detect`, passes `DetectionResult` to masking (Milestone 4) |

# Detection Strategy

| Mechanism | Entity Types | Examples |
|-----------|--------------|----------|
| Dictionary | `COMPANY_NAME` | ICICI, ICICIBANK, FXTP, WECARE, SCRAMBLE |
| Validated regex | `IFSC`, `PAN` | Bank branch codes, PAN format |
| General regex | `EMAIL`, `PHONE`, `URL`, `IP_ADDRESS` | Contact and infrastructure identifiers |
| Keyword / context | `PASSWORD`, `API_KEY`, `SECRET` | Assignment lines (`password:`, `api_key:`) |
| Structural | `JWT`, `PRIVATE_KEY`, `DATABASE_URL` | Three-segment JWT, PEM blocks, JDBC URLs |

**Execution order (invariant):**

1. Dictionary / company terms
2. Validated regex (IFSC, PAN)
3. General regex (email, phone, URL, IP)
4. Keyword / context rules (passwords, API keys, secrets)

**Overlap resolution:**

- Longest matching span wins.
- Ties broken by rule priority index.

# Execution Flow

```
For each FileInfo in inventory:
         │
         ▼
   FileClassifier.classify(fileInfo)
         │  Skip if not TEXT
         ▼
   TextFileReader.readUtf8(absolutePath)
         │
         ▼
   DetectionEngine.detect(DetectionContext(fileInfo, content))
         │  Run rule catalog; collect candidates
         │  Resolve overlaps; sort by offset
         ▼
   DetectionResult(entities)
         │
         ▼
   [Milestone 4: MaskingEngine.mask(content, detectionResult, registry)]
```

# Security Considerations

| Control | Detail |
|---------|--------|
| **Scope gate** | Only TEXT files are passed to detection by `MaskingApplication` |
| **No content logging** | Detection does not log matched `original_value` text |
| **Best-effort detection** | Regex and keyword rules may produce false positives/negatives; documented in architecture |
| **Private key blocks** | Multi-line PEM content is detected as a single span |
| **Comment avoidance** | Password/API-key rules target assignment lines, not commented examples |

Milestone 1 archive and workspace security controls remain unchanged.

# Testing

**Test class:** `DetectionEngineTest` (`src/test/java/com/scrambler/detection/`)

**Coverage includes:**

| Area | Examples Tested |
|------|-----------------|
| Core PII/SPII | Email, phone, PAN, IFSC, IP address |
| Company dictionary | Configured brand terms |
| Secrets | Password, API key, secret assignments; JWT; private key blocks; JDBC URLs |
| Overlap resolution | Longest span wins; API key vs embedded JWT |
| Offsets | Mandatory start/end offsets preserved |
| Edge cases | Null context rejection; empty content; commented passwords |

Run tests:

```bash
mvn test
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Single public `DetectionEngine` | Met |
| TEXT-only scope gate at caller | Met |
| Overlap resolution centralized | Met |
| Entity offsets mandatory | Met |
| Secret and infrastructure patterns detected | Met |
| All tests pass | Met |

# Known Limitations

- **No Aadhaar / credit card validators yet** — architecture defines them; full validated-regex coverage may expand in later work.
- **No `entity_id` column in report** — deferred; offsets and masked tokens are the V1 contract.
- **UTF-8 only** — charset policy and per-row charset in CSV are not implemented.
- **No masking output** — Milestone 3 detects only; tokens are produced in Milestone 4.
- **No CSV report** — Milestone 5 persists mappings.
- **Inline orchestration** — no `pipeline` package yet; `MaskingApplication` coordinates stages directly.

# Next Steps

Proceed to **Milestone 4 — Masking Engine** to replace detected entities with reversible tokens, register mappings in `MappingRegistry`, and apply end-to-start substitutions. See `docs/MILESTONE_4.md`.
