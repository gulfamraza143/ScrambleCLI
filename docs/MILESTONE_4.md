# Milestone 4 — Masking Engine

# Overview

Milestone 4 introduces the `masking` package. Given a `DetectionResult`, the masking engine replaces sensitive spans with deterministic, type-prefixed tokens and records every substitution in an in-memory `MappingRegistry` for later CSV persistence.

Masking applies only to TEXT files. Non-TEXT categories are never passed to the engine.

# Objective

Deliver a masking subsystem that:

1. Converts detected entities into masked tokens (`ENTITYTYPE_NNNNNN` format).
2. Applies substitutions end-to-start so offsets remain valid during mutation.
3. Registers one `MappingRecord` per masked occurrence in `MappingRegistry`.
4. Preserves file structure, whitespace, and line breaks.
5. Returns original content unchanged when no entities are detected.
6. Integrates with `MaskingApplication` after detection.
7. Provides automated test coverage for replacement order, token numbering, and edge cases.

# Architecture Impact

Milestone 4 extends the masking flow to:

```
repo.zip → Extract → Inventory → Classify → Detect (TEXT) → Mask (TEXT)
```

**Package introduced:** `masking`

**Dependency direction preserved:**

- `masking` depends on `detection` (`DetectionResult`, `Entity`, `EntityType`) and uses `EntityReplacer` internally.
- `report` (Milestone 5) will consume `MappingRegistry` records; no parallel mapping structures.
- Stages do not call each other; `MaskingApplication` orchestrates detect then mask per file.

**Architectural invariants honored:**

- Text-only masking per `ReversibilityPolicy`.
- Replacement order: end of file → start.
- Single read per TEXT file: content is read once, detected, then masked in the same pass.
- Zero-entity TEXT files pass through unchanged with no registry entries.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `MaskingEngine` | `masking` | Orchestrates token generation and span replacement |
| `EntityReplacer` | `masking` | Applies ordered span substitutions to content |
| `MappingRegistry` | `masking` | In-memory collection of `MappingRecord` entries |
| `MappingRecord` | `masking` | One masked occurrence: path, type, original, token, offsets |

**Modified component:**

| Component | Change |
|-----------|--------|
| `MaskingApplication` | Calls `MaskingEngine.mask` after detection; accumulates `MappingRegistry` across files |

# Token Format

Tokens follow the pattern `{EntityType}_{sequence}` with a six-digit zero-padded sequence per entity type within a run:

```
EMAIL_000001
URL_000001
PASSWORD_000001
```

Sequences are deterministic within a single CLI run. Repeated identical values in the same file receive distinct tokens (one registry row per occurrence).

# Execution Flow

```
DetectionResult + original content
         │
         ▼
   MaskingEngine.mask(content, detectionResult, mappingRegistry)
         │  If no entities → return original content
         │  Sort entities by start offset (ascending)
         │  For each entity:
         │    Generate next token for entity type
         │    Register MappingRecord in registry
         │    Build Replacement(start, end, token)
         │  EntityReplacer.replace (end → start order)
         ▼
   Masked content string
```

**Example:**

```
Input:  admin_email: admin@icici.com
Output: admin_email: EMAIL_000001
```

# Security Considerations

| Control | Detail |
|---------|--------|
| **Delimiter-safe tokens** | Type prefix + numeric suffix unlikely to occur naturally in source |
| **No original values in stdout** | Summary reports counts only, not plaintext secrets |
| **Registry before persistence** | Mappings held in memory until Milestone 5 writes CSV |
| **End-to-start replacement** | Prevents offset corruption during multi-entity masking |

# Testing

**Test classes:**

- `MaskingEngineTest` (`src/test/java/com/scrambler/masking/`)
- `EntityReplacerTest` (`src/test/java/com/scrambler/masking/`)

**Coverage includes:**

| Area | Examples Tested |
|------|-----------------|
| Single and multiple replacements | One and many entities per file |
| Multiple entity types | Distinct token prefixes per type |
| Repeated values | Distinct tokens per occurrence |
| End-to-start order | Overlapping/adjacent span safety |
| Empty detection | Original content returned unchanged |
| Structure preservation | Line breaks and whitespace intact |
| Deterministic numbering | Consistent sequences within a run |

Run tests:

```bash
mvn test
```

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Detected entities replaced with typed tokens | Met |
| End-to-start replacement order | Met |
| MappingRegistry records all occurrences | Met |
| Original content preserved when no entities | Met |
| File structure and line breaks preserved | Met |
| All tests pass | Met |

# Known Limitations

- **No masked ZIP output** — masked content is computed in memory; Milestone 7+ archive packaging for mask flow is not yet wired.
- **No CSV persistence** — `MappingRegistry` is not written to disk until Milestone 5.
- **No document/image placeholder swap** — `replacement` package not implemented.
- **Re-mask unsupported** — re-running mask without fresh detection is not supported in V1.
- **UTF-8 only** — all text I/O uses UTF-8; charset column not yet in schema.

# Next Steps

Proceed to **Milestone 5 — CSV Report Contract** to persist `MappingRegistry` rows to `entity_report.csv` with RFC-style escaping and `report_version` 1.0 compatibility. See `docs/MILESTONE_5.md`.
