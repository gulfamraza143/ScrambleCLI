# Milestone 1 — Repository Inventory Foundation

# Overview

Milestone 1 establishes the foundational pipeline for SCRAMBLECLI: accept a repository ZIP archive, extract it into an isolated temporary workspace, build a file inventory, and print a human-readable summary to stdout. No classification, detection, masking, or reporting occurs at this stage.

This milestone validates the archive and workspace layers defined in `ARCHITECTURE.md` and provides the entry point (`MaskingApplication`) that later milestones extend.

# Objective

Deliver a working CLI that:

1. Accepts a single argument: the path to `repo.zip`.
2. Safely extracts the archive into a per-run workspace.
3. Discovers all regular files under the extraction root.
4. Prints each file's repository-relative path and a total file count.
5. Cleans up the workspace on success or failure.
6. Returns standardized exit codes for automation and CI integration.

# Architecture Impact

Milestone 1 implements the first segment of the masking flow defined in `ARCHITECTURE.md`:

```
repo.zip → Extract → Inventory Files
```

**Packages introduced:** `app`, `workspace`, `archive`, `inventory`, `config`, `exception`

**Packages not yet implemented:** `pipeline`, `classify`, `detection`, `masking`, `replacement`, `report`, `file`, `unmasking`

**Dependency direction preserved:** `app` depends on `archive`, `workspace`, `inventory`, and `config`. All processing exceptions flow through `exception`. No stage-to-stage coupling exists yet because orchestration lives directly in `MaskingApplication` rather than in a `pipeline` package.

**Architectural invariants honored:**

- `FileInfo` is owned by `inventory` and is the sole file descriptor type.
- Zip-slip protection and path normalization live in `workspace`, not in `archive`.
- `ScramblerConfig` provides immutable run configuration; stages do not read external config files.
- Workspace cleanup runs in a `finally` block and does not rely on `deleteOnExit`.

# Components Implemented

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `MaskingApplication` | `app` | CLI entry point, argument validation, orchestration, inventory output, exit codes |
| `ScramblerConfig` | `config` | Workspace base path, zip bomb limits (`maxZipEntries`, `maxUncompressedBytes`) |
| `Workspace` | `workspace` | Per-run identity (`runId`), root path, extraction path |
| `WorkspaceManager` | `workspace` | Workspace creation, cleanup, zip-slip validation, repository-relative path derivation |
| `ZipExtractor` | `archive` | Streaming ZIP extraction with safety limit enforcement |
| `FileInfo` | `inventory` | Immutable file descriptor: absolute path, repository-relative path, size |
| `FileIterator` | `inventory` | Walks the extraction tree and collects `FileInfo` instances |
| `RepositoryInventory` | `inventory` | Aggregate of discovered files with total count |
| `ArchiveException` | `exception` | Archive extraction, zip-slip, zip bomb, and workspace failures |
| `FileProcessingException` | `exception` | File processing failures (reserved for later milestones) |

# Execution Flow

```
java -jar scramble-mask.jar <repo.zip>
         │
         ▼
   Validate CLI args (exactly one path)
         │
         ▼
   WorkspaceManager.createWorkspace(config)
         │  Creates scramble-<runId>/extracted/ under configured base path
         ▼
   ZipExtractor.extract(zipPath, workspace)
         │  Streaming read; per-entry zip-slip check
         │  Enforces max entry count and max uncompressed bytes
         ▼
   FileIterator.collectFiles(extractionRoot)
         │  Walks tree; builds FileInfo with repo-relative paths
         ▼
   RepositoryInventory(file list)
         │
         ▼
   printInventory(inventory)  →  stdout
         │
         ▼
   WorkspaceManager.cleanup(workspace)  →  finally block
         │
         ▼
   Exit 0 (success) | 1 (usage) | 2 (processing failure)
```

**Inventory output format (Milestone 1):**

```
===== INVENTORY =====

src/App.java
config/application.yml
...

Total Files: N
```

Repository-relative paths use forward slashes, have no leading `./`, and reflect paths as stored in the archive (case-sensitive).

# Security Considerations

| Control | Implementation |
|---------|----------------|
| **Zip-slip protection** | `WorkspaceManager.resolveSafeExtractPath` normalizes each entry name, rejects `..` segments, and verifies the resolved path remains under the extraction root |
| **Zip bomb protection** | `ZipExtractor` enforces `ScramblerConfig.getMaxZipEntries()` (default 50,000) and `getMaxUncompressedBytes()` (default 2 GB) during streaming extraction |
| **Workspace isolation** | Each run creates a unique `scramble-<runId>` directory; extraction is confined to its `extracted/` subdirectory |
| **Workspace cleanup** | `WorkspaceManager.cleanup` recursively deletes the workspace tree in a `finally` block; cleanup failures are swallowed so they do not mask the original error |
| **Directory permissions** | On POSIX systems, workspace directories are created with owner-only read/write/execute permissions |
| **Fail-fast on invalid archives** | Missing files, corrupt ZIPs, empty archives, zip-slip entries, and limit violations throw `ArchiveException` and exit with code `2` |
| **Exit code handling** | Predictable exit codes (`0`, `1`, `2`) enable safe use in scripts and CI pipelines |

# Testing

Milestone 1 does not include a dedicated automated test suite. Verification is manual or integration-level:

- Run the CLI against a valid repository ZIP and confirm inventory output.
- Confirm repository-relative paths and file counts match the archive contents.
- Confirm workspace directories are removed after the run completes.
- Confirm invalid usage (wrong argument count) exits with code `1`.
- Confirm corrupt, empty, zip-slip, or oversized archives exit with code `2`.

# Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Extract ZIP archive into workspace | Met |
| Build inventory of all regular files | Met |
| Print repository files to stdout | Met |
| Correct repository-relative paths (forward slashes, no `./` prefix) | Met |
| Correct file counts in summary | Met |
| Workspace cleanup on success and failure | Met |
| Fail fast on invalid or unsafe archives | Met |

# Known Limitations

- **No file classification** — all files are listed without category labels.
- **No detection** — sensitive entity scanning is not implemented.
- **No masking** — file contents are never read or modified.
- **No reporting** — no `entity_report.csv` is produced.
- **No unmasking** — the unmask JAR and flow are not implemented.
- **No pipeline package** — orchestration is inline in `MaskingApplication`; a `pipeline` stage will be introduced when later milestones add processing steps.
- **No automated unit tests** — coverage for extraction and inventory is manual at this milestone.

# Next Steps

Proceed to **Milestone 2 — File Classification** to assign each inventoried file a `FileCategory` (TEXT, DOCUMENT, IMAGE, SKIP) and update inventory output accordingly. See `docs/MILESTONE_2.md`.
