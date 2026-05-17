# Repair Previous Hygiene Task Archive Context Paths

## Goal

Repair the archived Trellis context path drift in the previous hygiene task archive so its `implement.jsonl` and `check.jsonl` entries point to the archived `prd.md` and `research.md` files that actually exist.

## Classification

Simple Task.

This is metadata-only Trellis context hygiene. The intended implementation is limited to two JSONL files in one archived task directory. No backend, frontend, API, database, storage, AI, permission, runtime script, or business implementation change is in scope.

## Current Audit Snapshot

The current full JSONL audit reports:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

The requested target directory accounts for 4 of the 8 missing paths:

- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:3`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:4`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:2`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:3`

The remaining 4 missing paths are from `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/{implement,check}.jsonl`. They are not in the user-approved edit scope for this task.

## Requirements

- Update only the archived previous hygiene task context files:
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl`
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl`
- Replace the active-task paths:
  - `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
  - `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`
- With the archived paths:
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`
- Preserve each entry's `reason` value exactly.
- Keep one valid JSON object per line.
- Use forward-slash repo-relative paths.
- Do not create placeholder files.
- Do not repair the 5 invalid JSON lines in older archived tasks.
- Do not modify `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/` unless the user explicitly expands scope.

## Acceptance Criteria

- [ ] The target archived previous hygiene task validates successfully:
  - `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep`
- [ ] The target task no longer contributes any missing-path findings in the full JSONL audit.
- [ ] `LEGACY_PATH_COUNT=0`.
- [ ] `STALE_COMMAND_COUNT=0`.
- [ ] The stale command scan returns no matches:
  - `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- [ ] `git diff --check` passes, aside from pre-existing Windows line-ending warnings if any.
- [ ] The final report lists the unresolved pre-existing invalid JSON lines exactly.
- [ ] If the implementation scope remains limited to the requested archived previous hygiene task, the final report also lists the 4 out-of-scope missing paths in `05-17-trellis-legacy-context-format-upgrade` and states that global `MISSING_PATH_COUNT=0` is not achievable without expanding scope.

## Contract / Command Surface

No application API, DTO, database schema, frontend type, or runtime command contract is changed.

Verification command contract:

- `task.py validate <task-dir>` must pass for the target archived task directory.
- The full JSONL audit must classify invalid JSON, missing paths, legacy `path` entries, and stale `.claude/commands/trellis` references.
- The stale command scan must search both forward-slash and backslash command path forms.

## Validation / Error Matrix

| Case | Expected Result |
| --- | --- |
| Target `prd.md` archived file exists before rewrite | Rewrite JSONL entries to archived `prd.md` path |
| Target `research.md` archived file exists before rewrite | Rewrite JSONL entries to archived `research.md` path |
| Target archived file does not exist | Stop; do not invent or create placeholder context files |
| JSONL line has existing `reason` text | Preserve it exactly |
| Full audit still reports 5 invalid JSON lines | Leave unchanged and list as pre-existing |
| Full audit reports missing paths outside approved scope | Leave unchanged and list as out of scope unless user expands scope |

## Good / Base / Bad Cases

Good:

- The four target entries are rewritten to the existing archived `prd.md` and `research.md` paths.
- Reasons are preserved.
- Target task validation passes.

Base:

- The full audit may still report the 5 known invalid JSON lines.
- The full audit may still report the 4 newly observed out-of-scope missing paths in `05-17-trellis-legacy-context-format-upgrade` if scope is not expanded.

Bad:

- Creating placeholder `prd.md` or `research.md` files.
- Deleting invalid JSON lines to reduce audit counts.
- Editing other archived tasks without explicit scope expansion.
- Rewriting all paths cosmetically instead of touching only the four requested entries.

## Required Tests

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep`
- Full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`

## Out Of Scope

- Backend or frontend code.
- Trellis scripts.
- Application runtime behavior.
- Database, Redis, MQ, deployment, permissions, AI/RAG, or API contracts.
- Repairing historical invalid JSON fragments.
- Repairing `05-17-trellis-legacy-context-format-upgrade` paths unless user explicitly expands scope.
