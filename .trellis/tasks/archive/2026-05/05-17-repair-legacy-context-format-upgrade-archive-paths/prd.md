# Repair Legacy Context Format Upgrade Archive Paths

## Goal

Repair the archived Trellis context paths in `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/` so its own `implement.jsonl` and `check.jsonl` no longer point at the now-missing active task directory.

This is a Trellis metadata-only task. It must preserve the original context intent, keep each JSONL entry as one valid JSON object per line, and avoid any backend/frontend business implementation changes.

## Task Classification

Complex Task.

Rationale: the edit surface is small, but the work affects archived task context hygiene, full-repo audit signals, and a Codex/DeepSeek handoff. The task must be planned with explicit boundaries before another agent edits files.

## Current Project State

- Branch: `main`.
- No active Trellis task existed before this task was created.
- The working tree already contains uncommitted Trellis archive/workspace changes from the prior hygiene task archive/record-session flow. Do not revert or overwrite them.
- Journal summary:
  - Session 27 completed the legacy `path` to `file` format upgrade for `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/`.
  - Session 28 completed repair of four archived context paths in `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/`.
  - Session 28 explicitly left four missing paths in `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/` and five historical invalid JSON lines out of scope.
- Current baseline full JSONL audit reports:
  - `INVALID_JSON_COUNT=5`.
  - `MISSING_PATH_COUNT=8`.
  - `LEGACY_PATH_COUNT=0`.
  - `STALE_COMMAND_COUNT=0`.
- Important baseline note: the 8 missing paths include the 4 target findings for this task and 4 additional findings in `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/` caused by the current uncommitted archive state. This task must not silently expand to those additional 4 findings.

## Requirements

- Confirm `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/prd.md` exists.
- Confirm `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/research.md` exists.
- Rewrite only these four JSONL entries:
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:3`
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:4`
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:4`
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:5`
- Replace active-task paths:
  - `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
  - `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`
- With archived paths:
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/prd.md`
  - `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/research.md`
- Preserve each `reason` text exactly.
- Keep each JSONL line as a single valid JSON object.
- Use forward-slash repo-relative paths in touched entries.
- Do not create placeholder files.
- Do not modify `.trellis/scripts/*`.
- Do not modify backend/frontend source, API contracts, database schema, Redis/MQ, auth, storage, infra, Maven, Vite, Docker, or runtime config.
- Do not fix the 5 historical invalid JSON lines in this task.
- Do not fix the 4 additional missing paths in `05-17-repair-previous-hygiene-task-archive-context-paths` unless the user explicitly expands the scope.

## Command / Payload Contract

No application API, command API, backend DTO, frontend DTO, database, Redis/MQ, auth, storage, or external integration contract changes are allowed.

The relevant Trellis JSONL payload contract is:

| Field | Required | Meaning |
| --- | --- | --- |
| `file` | yes | Repo-root relative path to an existing file or directory. |
| `reason` | yes | Human-readable reason for injecting the context; must preserve original intent. |
| `type` | optional | Use `directory` only when the target is an existing directory. Not expected for this task. |

## Validation / Error Matrix

| Case | Expected Handling | Error If |
| --- | --- | --- |
| Active task PRD path is missing but archived PRD exists | Rewrite only the `file` value to the archived PRD path | Reason text changes, line becomes multi-line JSON, or a placeholder file is created |
| Active task research path is missing but archived research exists | Rewrite only the `file` value to the archived research path | Target existence is not verified first |
| A target archived file does not exist | Stop and report the blocker | A guessed or nearby path is used |
| Full audit still reports 5 invalid JSON lines | Leave unchanged and report as historical boundary | Invalid JSON lines are deleted or invented |
| Full audit still reports unrelated missing paths outside target task | Report separately as out of scope | Implementation edits unrelated archive tasks without approval |

## Good / Base / Bad Cases

Good:

- `{"file": ".trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md", "reason": "..."}` is rewritten to `{"file": ".trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/prd.md", "reason": "..."}` after confirming the archived file exists.
- The same rule is applied to `research.md`.
- `task.py validate` passes for `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade`.
- The full audit shows the 4 target `05-17-trellis-legacy-context-format-upgrade` missing paths are gone.

Base:

- The full audit may still show 5 historical invalid JSON findings.
- In the current dirty working tree, the full audit may still show 4 additional missing paths from `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/`; those are outside this task unless separately approved.
- No `type: "directory"` entries are needed because both targets are files.

Bad:

- Editing backend/frontend business code.
- Editing `.trellis/scripts/task.py` or validator behavior.
- Touching archived task JSONL outside `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/`.
- Changing reason text while repairing paths.
- Deleting invalid JSON lines to improve audit counts.

## Acceptance Criteria

- [ ] The four target JSONL entries point to archived `prd.md` and `research.md` paths.
- [ ] The four target JSONL entries preserve original `reason` values.
- [ ] `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/prd.md` and `research.md` exist.
- [ ] `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade` passes.
- [ ] Full JSONL audit no longer lists missing paths for `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/{check,implement}.jsonl`.
- [ ] Full JSONL audit reports `LEGACY_PATH_COUNT=0` and `STALE_COMMAND_COUNT=0`.
- [ ] Any remaining `INVALID_JSON_COUNT=5` findings are reported as historical out-of-scope items.
- [ ] Any remaining missing paths outside the target archived task are reported separately and not repaired without approval.
- [ ] No business implementation files are modified.

## Expected Files To Modify

- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl`

Current task preparation files created by Codex:

- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/{implement,check,debug}.jsonl`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/task.json`

## Forbidden Scope

- Do not modify application business code.
- Do not modify API, DTO, DB, Redis, MQ, auth, storage, frontend types, UI, infra, or runtime config.
- Do not modify Trellis scripts or validation behavior.
- Do not edit unrelated archived task JSONL files.
- Do not resolve historical invalid JSON findings in this task.
- Do not finish, archive, commit, or record-session during the DeepSeek implementation pass unless the user explicitly asks.

## Required Tests And Assertion Points

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade`
  - Assert the target archived task validates.
- Full JSONL audit from `.trellis/spec/guides/trellis-task-context-hygiene.md`
  - Assert the four target missing paths are gone.
  - Assert `LEGACY_PATH_COUNT=0`.
  - Assert `STALE_COMMAND_COUNT=0`.
  - Report remaining invalid JSON and unrelated missing-path findings separately.
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
  - Assert stale command references remain zero.
- `git diff --check`
  - Assert no whitespace errors in touched files.

## Codex / DeepSeek Collaboration Boundary

Codex prepares the PRD, research, task context, and final check handoff. DeepSeek performs the JSONL path rewrite. Codex must not implement the archived JSONL repair in this planning pass.
