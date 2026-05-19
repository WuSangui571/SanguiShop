# Repair Legacy Context Format Upgrade Archive Context Paths

## Task Classification

Simple Task.

The target change is a narrow Trellis metadata hygiene repair in archived task context JSONL files. It does not modify backend, frontend, API, database, Redis, MQ, auth, storage, infra, or runtime business code.

## Goal

Repair stale active-task context paths in the archived task:

`.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/`

The intended fix is to replace references to the no-longer-existing active task directory:

`.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/`

with the existing archived task directory:

`.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/`

Only the four known stale path entries are in scope.

## Current Baseline

The user-provided expected baseline is:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=4`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

Codex pre-implementation audit on 2026-05-18 found a workspace-state discrepancy:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

The extra four missing paths are from an uncommitted/visible archived previous task:

- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/check.jsonl:10`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/check.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:9`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:10`

DeepSeek must not silently expand this task to repair those extra four paths. If the full audit still reports 8 missing paths at implementation time, stop and ask for confirmation or confirm the workspace baseline has been cleaned before claiming `MISSING_PATH_COUNT=0`.

## In-Scope Target Findings

Repair exactly these four entries if the archived targets still exist:

- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:3`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:4`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:9`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:10`

Expected replacements:

- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
  -> `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
  -> `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`

## Requirements

- Verify both archived target files exist before editing:
  - `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
  - `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
- Preserve each existing `reason` value exactly.
- Keep JSONL format as one valid JSON object per line.
- Use forward-slash repo-relative paths in touched JSON objects.
- Touch only the target archived task's `check.jsonl` and `implement.jsonl`, unless the user explicitly expands scope.
- Do not create placeholder files to satisfy validation.
- Do not delete historical invalid JSON lines.
- Do not repair unrelated archived task findings without approval.

## Out Of Scope

- Backend code.
- Frontend code.
- API, command, payload, DTO, DB, Redis, MQ, auth, storage, infra, or AI changes.
- Modifying `.trellis/scripts/` behavior.
- Repairing the five historical invalid JSON lines.
- Repairing any missing path outside:
  - `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl`
  - `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl`

## Contract / API / Payload Notes

No application API, command signature, request payload, response payload, database schema, or frontend DTO changes are involved.

The only data contract in scope is the Trellis context JSONL entry contract:

```json
{"file": "<repo-relative existing path>", "reason": "<existing reason text>"}
```

`type: "directory"` is not expected because all four target entries point to files.

## Validation / Error Matrix

| Case | Condition | Expected Action |
| --- | --- | --- |
| Good | Stale active-task path points to a task now present under `.trellis/tasks/archive/2026-05/` | Replace only the `file` value with the archived path and preserve `reason`. |
| Good | Archived `prd.md` and `research.md` exist | Proceed with the four path replacements. |
| Base | Full audit still reports five invalid JSON lines | Leave them unchanged and list them in the final report. |
| Base | Full audit reports extra missing paths outside this target | Do not repair them without explicit approval; report the exact discrepancy. |
| Bad | Archived target file does not exist | Do not invent a path or create placeholders; stop and report. |
| Bad | A line is invalid JSON | Do not delete or rewrite it unless the original intent is provable and in scope. |
| Bad | A stale path belongs to another archived task | Do not change it in this task. |

## Good / Base / Bad Cases

Good:

- Four target stale paths are rewritten to existing archived `prd.md` / `research.md` paths.
- `task.py validate` passes for the target archived task.
- `reason` text is unchanged.
- Full audit shows `LEGACY_PATH_COUNT=0` and `STALE_COMMAND_COUNT=0`.

Base:

- The five historical invalid JSON lines remain and are reported exactly.
- If unrelated workspace-state missing paths are present, they are reported as a blocker to claiming global `MISSING_PATH_COUNT=0`.

Bad:

- Creating replacement `prd.md` or `research.md` files.
- Touching business implementation files.
- Broadly rewriting archived context files for cosmetic consistency.
- Claiming full `MISSING_PATH_COUNT=0` when unrelated missing paths still exist.

## Acceptance Criteria

- [ ] Target archived `prd.md` and `research.md` existence is verified before editing.
- [ ] Exactly four in-scope stale paths are repaired.
- [ ] `reason` text on all four touched entries is preserved exactly.
- [ ] No backend/frontend/runtime implementation files are modified.
- [ ] `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths` passes.
- [ ] `python ./.trellis/scripts/task.py validate .trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths` passes after implementation context remains valid.
- [ ] Full JSONL audit reports no in-scope missing paths for the target archived task.
- [ ] Final full audit result is reported exactly. If unrelated missing paths remain, list them and do not claim `MISSING_PATH_COUNT=0`.
- [ ] `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"` reports no stale command matches.
- [ ] `git diff --check` passes, allowing only known Windows line-ending warnings if present.

## Required Tests

Run these commands from repo root:

```powershell
python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths
python ./.trellis/scripts/task.py validate .trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths
rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"
git diff --check
```

Also run the full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md`.

## Implementation Notes For DeepSeek

- Use a structured JSONL-preserving edit or carefully replace only the four `file` string values.
- Do not change key order unless the local edit mechanism requires it.
- Do not normalize unrelated lines.
- Re-run audit after editing and compare against the starting baseline.
