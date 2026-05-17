# Archived Trellis Context JSONL Full Hygiene Sweep

## Goal

Stabilize historical Trellis task context metadata so archived task handoff, validation, record-session, and archive checks are not interrupted by pre-existing JSONL hygiene failures.

This task is limited to Trellis metadata and Trellis documentation. It must not change business implementation, application behavior, API contracts, database schema, frontend source, backend source, runtime configuration, or deployment behavior.

## Task Classification

Complex Task.

Rationale: the task spans many archived task context files, requires full-repo metadata auditing, needs careful classification of invalid JSON versus resolvable missing paths, and updates a shared Trellis guide. It should be planned before execution and implemented with conservative, evidence-based edits.

## Current Project State

- Repository was clean at session start on `main`.
- No active Trellis task existed before this task was created.
- Recent journal sessions show completed Trellis metadata and E2E hygiene work:
  - `E2E pending route lifecycle cleanup` completed and recorded.
  - `E2E smoke mock state reset audit` completed and recorded.
  - `E2E Smoke Task Metadata Hygiene Audit` completed and recorded.
- The previous metadata audit added `.trellis/spec/guides/trellis-task-context-hygiene.md`, registered it in the guides index, removed touched `.claude/commands/trellis/*.md` JSONL references, and fixed archive path drift only for touched archived tasks.
- The previous check explicitly left a follow-up candidate: full historical `.trellis/tasks` scan still had unrelated pre-existing invalid JSON and missing archived path issues.

## Requirements

- Scan every `.trellis/tasks/**/{implement,check,debug}.jsonl` file.
- Classify findings into:
  - invalid JSON lines,
  - missing archived PRD/research context paths,
  - stale command paths such as `.claude/commands/trellis/*.md`,
  - unresolved entries whose intended target cannot be proven.
- Fix only entries whose intended target can be proven from the repository:
  - If an archived task JSONL references `.trellis/tasks/<task>/prd.md` or `.trellis/tasks/<task>/research.md`, rewrite it to `.trellis/tasks/archive/<YYYY-MM>/<task>/prd.md` or `research.md` only when that target file exists.
  - Preserve the original semantic reason text when possible.
  - Normalize path separators to repository-style forward slashes in touched JSONL entries.
- Repair invalid JSON only when the intended `{ "file": "...", "reason": "..." }` entry can be reconstructed without guessing and the referenced file exists.
- For invalid JSON lines that cannot be reconstructed safely, leave them unchanged and list them clearly as unresolved.
- Confirm that no JSONL references remain to missing files for every task that was modified, unless explicitly listed as unresolved because no safe target exists.
- Update `.trellis/spec/guides/trellis-task-context-hygiene.md` with:
  - a full audit command or script pattern for all task JSONL files,
  - Good/Base/Bad cases for archived path repair,
  - Good/Base/Bad cases for invalid JSON handling,
  - guidance that automatic repair must be evidence-based and must not invent missing PRD/research files.

## Command / Payload Contract

No application API, database, frontend DTO, backend DTO, Redis/MQ, auth, or storage contract changes are allowed.

The effective command contract for this task is Trellis context validation:

| Command | Purpose | Required Assertion |
| --- | --- | --- |
| `python ./.trellis/scripts/task.py validate <task-dir>` | Validate a specific task context directory | Must pass for the current task and every modified archived task. |
| Full JSONL audit command/script | Classify invalid JSON, missing file paths, and stale command paths under `.trellis/tasks` | Output must separate counts and file:line findings by category. |
| `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"` | Stale command path check | Must return no matches, or findings must be explained if intentionally unresolved. |

JSONL context entry payload fields:

| Field | Required | Meaning |
| --- | --- | --- |
| `file` | yes | Repo-root relative path to an existing file or directory. |
| `reason` | yes for normal file entries | Human-readable reason for injecting the context. Preserve existing intent. |
| `type` | optional | May be `directory` for directory context entries; otherwise omitted or treated as file. |

## Validation / Error Matrix

| Finding | Expected Handling | Error If |
| --- | --- | --- |
| `.trellis/tasks/<task>/prd.md` in archived task JSONL and `.trellis/tasks/archive/<month>/<task>/prd.md` exists | Rewrite to archived path | Rewrite points to a non-existent file or wrong task. |
| `.trellis/tasks/<task>/research.md` in archived task JSONL and archived research exists | Rewrite to archived path | Rewrite is done without proving target exists. |
| Missing path does not have a provable archived target | Do not guess; list unresolved | PRD/research file is invented or replaced with a nearby unrelated task file. |
| Invalid JSON line is a truncated or wrapped context entry but can be reconstructed from adjacent context and existing path | Repair to one valid JSON object per line | Reason/path intent is guessed or semantic scope changes silently. |
| Invalid JSON cannot be reconstructed safely | Leave unchanged; list unresolved with file and line | Line is deleted or replaced merely to make validation pass. |
| `.claude/commands/trellis/*.md` stale command path appears | Replace with existing Codex `.agents/skills/<skill>/SKILL.md` equivalent when skill exists | Replacement path does not exist or purpose changes. |
| Modified task validates | Accept | `task.py validate` fails for any modified task. |

## Good / Base / Bad Cases

Good:

- A JSONL entry in `.trellis/tasks/archive/2026-05/05-09-example/implement.jsonl` references `.trellis/tasks/05-09-example/prd.md`; the archived `.trellis/tasks/archive/2026-05/05-09-example/prd.md` exists; the entry is rewritten to the archived path and `task.py validate` passes for that task.
- A stale command reference to `.claude/commands/trellis/check.md` is replaced with `.agents/skills/check/SKILL.md` only after confirming that file exists.
- An invalid JSON line is repaired only when the original `file` and `reason` are clear and the target file exists.
- The final audit reports zero stale command paths and zero missing paths for all modified tasks.

Base:

- Some historical invalid JSON remains unresolved because the original path or reason cannot be recovered safely; the final report lists exact file and line numbers.
- Full `.trellis/tasks` validation may still fail only for explicitly unresolved pre-existing invalid JSON entries, and the final report explains why they were not changed.
- Path separators are normalized only in touched entries; untouched valid entries do not need cosmetic rewrites.

Bad:

- Deleting invalid JSON lines without understanding whether they carried required context.
- Creating placeholder `prd.md` or `research.md` files to satisfy validation.
- Repointing a context entry to a similarly named task because the intended target is missing.
- Changing frontend/backend/business code while performing Trellis metadata hygiene.
- Expanding scope into task archive mechanics, hook behavior, or validation script behavior unless the current guide update demonstrably requires it.

## Acceptance Criteria

- [ ] Full JSONL audit command has been run before edits and after edits.
- [ ] Every modified JSONL file is valid JSONL.
- [ ] Every modified task directory passes `python ./.trellis/scripts/task.py validate <task-dir>`.
- [ ] Every archived PRD/research path repair points to an existing archived file.
- [ ] Stale `.claude/commands/trellis` JSONL references remain at zero, or any non-zero finding is explicitly justified.
- [ ] Unresolved invalid JSON or missing paths are listed with exact file and line numbers.
- [ ] `.trellis/spec/guides/trellis-task-context-hygiene.md` includes full-audit guidance and Good/Base/Bad cases for archive path and invalid JSON handling.
- [ ] No business implementation files are modified.

## Expected Files To Modify

- `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `.trellis/tasks/archive/2026-05/*/{implement,check,debug}.jsonl` for archived tasks with provable stale PRD/research paths or safely repairable invalid JSON.
- `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
- `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`
- `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/{implement,check,debug}.jsonl`
- `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/task.json`

## Forbidden Scope

- Do not modify `frontend/`, `services/`, `common/`, `gateway`, `scripts/verify*`, Maven, Vite, Playwright, Docker, Kubernetes, or application runtime config.
- Do not modify `.trellis/scripts/task.py`, `task_context.py`, or `cli_adapter.py` unless the user explicitly expands scope.
- Do not create missing archived PRD/research files as a shortcut.
- Do not archive, finish, commit, or record-session in the DeepSeek implementation pass unless the user explicitly asks.

## Required Tests And Assertion Points

- `python ./.trellis/scripts/task.py validate .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep`
  - Asserts current task context is valid.
- `python ./.trellis/scripts/task.py validate <each-modified-archived-task-dir>`
  - Asserts every touched archived task validates after repair.
- Full JSONL audit command/script over `.trellis/tasks/**/{implement,check,debug}.jsonl`
  - Asserts counts for invalid JSON, missing paths, and stale command paths after edits.
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
  - Asserts stale command path findings are zero.
- `git diff --check`
  - Asserts no whitespace errors in touched text files.

## Initial Planning Audit Snapshot

The planning pass found:

- `INVALID_JSON_COUNT=10`
- `MISSING_PATH_COUNT=29`
- `STALE_COMMAND_COUNT=0`

The missing path findings are concentrated in archived 2026-05 task JSONL files and mostly reference pre-archive `.trellis/tasks/<task>/prd.md` or `research.md` paths.

The invalid JSON findings are concentrated in archived task JSONL files from 2026-05-01 through 2026-05-07. These require conservative handling: repair only if the context entry can be reconstructed from surrounding content and existing targets; otherwise report as unresolved.
