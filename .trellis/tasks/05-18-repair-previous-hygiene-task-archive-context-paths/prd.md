# Repair Previous Hygiene Task Archive Context Paths

## Goal

Repair the archived Trellis context path drift in:

- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/implement.jsonl`
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/check.jsonl`

The four in-scope entries currently point at the old active task directory. They must point at the archived task's existing `prd.md` and `research.md` files instead.

## Classification

Simple Task.

This is Trellis metadata-only context hygiene. No backend service, frontend page, API, database schema, Redis/MQ integration, auth/permission behavior, storage contract, AI/RAG behavior, deployment file, or Trellis runtime script is in scope.

## Current Project State

The previous recorded sessions completed these related hygiene tasks:

- `05-17-repair-previous-hygiene-task-archive-context-paths`: repaired four paths in the older `05-17-archived-trellis-context-jsonl-full-hygiene-sweep` archive task.
- `05-17-repair-legacy-context-format-upgrade-archive-paths`: repaired four paths in `05-17-trellis-legacy-context-format-upgrade`.

After those tasks were archived, their own context files now contain stale active-task paths. The current full JSONL audit reports:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

This task is scoped to the four missing paths in `05-17-repair-previous-hygiene-task-archive-context-paths` only. The four missing paths in `05-17-repair-legacy-context-format-upgrade-archive-paths` and the five historical invalid JSON lines remain out of scope unless separately approved.

## Requirements

- Update only these archived task context files:
  - `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/implement.jsonl`
  - `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/check.jsonl`
- Replace only these active-task path targets:
  - `.trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/prd.md`
  - `.trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/research.md`
- Use these archived path targets:
  - `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/prd.md`
  - `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/research.md`
- Confirm both archived target files exist before editing.
- Preserve every touched entry's `reason` text exactly.
- Keep JSONL as one valid JSON object per line.
- Use forward-slash repo-relative paths in touched entries.
- Do not create placeholder files.
- Do not delete, repair, or rewrite the five historical invalid JSON fragments.
- Do not touch `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/` in this task.

## Acceptance Criteria

- [ ] The target archived task validates:
  - `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths`
- [ ] The target archived task no longer contributes missing-path findings in the full JSONL audit.
- [ ] The full audit still reports `LEGACY_PATH_COUNT=0`.
- [ ] The full audit still reports `STALE_COMMAND_COUNT=0`.
- [ ] The stale command scan returns no matches:
  - `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- [ ] `git diff --check` passes, aside from known Windows line-ending warnings if present.
- [ ] Final report lists the five unresolved historical invalid JSON findings exactly.
- [ ] Final report lists any out-of-scope missing paths exactly, including the four current findings in `05-17-repair-legacy-context-format-upgrade-archive-paths` if they remain.

## Contract / Command Surface

No application API, DTO, frontend type, database schema, cache key, MQ event, permission rule, or runtime command contract is changed.

Verification command contracts:

| Command | Purpose | Expected Assertion |
| --- | --- | --- |
| `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths` | Validate the repaired archived target task context | Must pass |
| Full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md` | Classify invalid JSON, missing paths, legacy `path` entries, and stale command references | Target task contributes zero missing-path findings; out-of-scope findings are listed |
| `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"` | Detect stale Claude-only Trellis command references | Must return no matches |
| `git diff --check` | Detect whitespace errors | Must pass, except known line-ending warnings if any |

## Validation / Error Matrix

| Case | Expected Result |
| --- | --- |
| Archived `prd.md` exists before rewrite | Rewrite target entries to the archived `prd.md` path |
| Archived `research.md` exists before rewrite | Rewrite target entries to the archived `research.md` path |
| Archived target file is missing | Stop; do not invent, create, or redirect to another file |
| JSONL entry has existing `reason` text | Preserve the exact original value |
| Full audit still reports five invalid JSON lines | Leave unchanged and list as historical out-of-scope findings |
| Full audit reports missing paths outside this target archive task | Leave unchanged and list as out of scope |

## Good / Base / Bad Cases

Good:

- The four target entries point at existing archived `prd.md` and `research.md`.
- Reasons are preserved exactly.
- `task.py validate` passes for the target archived task.

Base:

- Full audit still reports five historical invalid JSON findings.
- Full audit may still report four out-of-scope missing paths from `05-17-repair-legacy-context-format-upgrade-archive-paths`.

Bad:

- Creating placeholder `prd.md` or `research.md`.
- Deleting invalid JSON lines to improve audit counts.
- Editing the later `05-17-repair-legacy-context-format-upgrade-archive-paths` archive task in this task.
- Reformatting unrelated JSONL entries.

## Required Tests

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths`
- Full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`

## Out Of Scope

- Backend and frontend code.
- Trellis scripts or hook behavior.
- Application runtime behavior.
- API, DB, Redis, MQ, storage, deployment, permission, or AI/RAG contracts.
- The five historical invalid JSON lines.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/`.
