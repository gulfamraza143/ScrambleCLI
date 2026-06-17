# SCRAMBLECLI — Roadmap

This roadmap tracks milestone delivery for V1 and outlines post-V1 evolution. Architecture decisions are locked in `docs/ARCHITECTURE.md`. Per-milestone delivery notes live in `docs/MILESTONE_*.md`.

**Technology:** Java 21, Maven, CLI  
**Build artifacts:** `scramble-mask.jar`, `scramble-unmask.jar`

---

## V1 Milestones

| Milestone | Title | Status | Tag |
|-----------|-------|--------|-----|
| 1 | Repository Inventory Foundation | Complete | `milestone-1` |
| 2 | File Classification | Complete | `milestone-2` |
| 3 | Sensitive Entity Detection | Complete | `milestone-3` |
| 4 | Masking Engine | Complete | `milestone-4` |
| 5 | CSV Report Contract | Complete | `milestone-5` |
| 6 | Unmasking Engine | Complete | `milestone-6` |
| 7 | Repository Restoration Workflow | Complete | — |

### Milestone Summary

```
M1  Extract + inventory + workspace safety
M2  Extension-based classification (TEXT / DOCUMENT / IMAGE / SKIP)
M3  DetectionEngine — PII, secrets, company terms, overlap resolution
M4  MaskingEngine — tokens, MappingRegistry, end-to-start replacement
M5  entity_report.csv — ReportSchema v1.0, RFC CSV read/write
M6  UnmaskingEngine — MappingIndex, strict validation, content restore
M7  UnmaskingApplication — full masked_repo.zip → original_repo.zip workflow
```

### Current V1 Capability

| Flow | Input | Output | CLI |
|------|-------|--------|-----|
| **Mask (partial)** | `repo.zip` | `entity_report.csv` | `scramble-mask.jar` |
| **Unmask (complete)** | `masked_repo.zip` + `entity_report.csv` | `original_repo.zip` | `scramble-unmask.jar` |

The mask CLI detects, masks, and writes the entity report. It does not yet emit `masked_repo.zip` or swap document/image placeholders. The unmask CLI is end-to-end for TEXT restoration.

---

## V1 Remaining Work

These items are defined in `docs/ARCHITECTURE.md` but not yet implemented:

| Priority | Item | Package / Component |
|----------|------|---------------------|
| High | Emit `masked_repo.zip` from mask CLI | `archive.ZipCreator`, `MaskingApplication` |
| High | Write masked TEXT content back to workspace before pack | `file.TextFileWriter`, `MaskingApplication` |
| High | Document/image placeholder replacement | `replacement` |
| Medium | `pipeline` orchestration (`PipelineContext`, `RunSummary`) | `pipeline` |
| Medium | Centralize extensions in `SupportedExtensions` | `config` |
| Medium | `CharsetPolicy` and charset column in report | `file`, `report` |
| Low | `entity_id` column in report schema v1.1+ | `report`, `detection` |
| Low | Offset mismatch warnings on unmask | `unmasking` |

### Target V1 Mask Flow (not yet complete)

```
repo.zip
  → Extract
  → Inventory + Classify
  → Detect + Mask (TEXT)
  → Write entity_report.csv
  → Replace Documents/Images with Placeholders
  → Create masked_repo.zip
```

### Target V1 Unmask Flow (complete at M7)

```
masked_repo.zip + entity_report.csv
  → Extract
  → Load + Validate + Index
  → Restore TEXT
  → Create original_repo.zip
```

---

## Verification

```bash
# Unit and integration tests
mvn test

# Build both JARs
mvn package

# Mask (report only today)
java -jar target/scramble-mask.jar repo.zip

# Unmask (full restore)
java -jar target/scramble-unmask.jar masked_repo.zip entity_report.csv
```

**Contract gate:** mask→unmask roundtrip integration test in `UnmaskingApplicationIntegrationTest`.

---

## V2 Evolution (Out of V1 Scope)

Grow internals and schema — not package count. See `docs/ARCHITECTURE.md` §17.

| Area | Direction |
|------|-----------|
| Detection | External YAML/JSON rule packs into same `DetectionEngine` |
| Performance | Parallel TEXT file processing |
| Report | `rule_id`, `scramble_tool_version`; optional compression |
| Security | Encrypted mapping sidecar; hash-only originals |
| Audit | New `audit` package — run metadata, checksums |
| CLI | `--json`, `--dry-run`, `--strict` / `--lenient` |
| Dictionary | Multiple dictionaries, environment overlays |
| Matching | Aho-Corasick for company terms |
| Build | Multi-module: shared core + mask-cli + unmask-cli |

**Explicitly out of scope for default unmask:** binary blob restore (requires new product mode with sidecar store).

---

## Documentation Index

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Frozen V1 architecture, invariants, contracts |
| [MILESTONE_1.md](MILESTONE_1.md) | Inventory and archive foundation |
| [MILESTONE_2.md](MILESTONE_2.md) | File classification |
| [MILESTONE_3.md](MILESTONE_3.md) | Entity detection |
| [MILESTONE_4.md](MILESTONE_4.md) | Masking engine |
| [MILESTONE_5.md](MILESTONE_5.md) | CSV report contract |
| [MILESTONE_6.md](MILESTONE_6.md) | Unmasking engine |
| [MILESTONE_7.md](MILESTONE_7.md) | Repository restoration workflow |
| [ROADMAP.md](ROADMAP.md) | This file |

---

## Git Milestone Tags

```bash
git tag -l 'milestone-*'
# milestone-1 … milestone-6
```

Tag `milestone-7` when the restoration workflow release is cut.
