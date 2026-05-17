# Trellis Legacy Context Format Upgrade

## Goal

Upgrade the archived 2026-04 task context files for `04-29-phase-1-foundation` from the legacy `path` JSONL field format to the current `file` field format, so full Trellis JSONL audit noise from this known legacy format is eliminated without changing business code or Trellis runtime behavior.

This is a Trellis metadata-only task. It must preserve the original context intent and avoid touching application source, backend/frontend implementation, API contracts, database schema, Redis/MQ, auth, storage, infra runtime configuration, or Trellis validation scripts.

## Task Classification

Complex Task.

Rationale: the change is mechanically small but affects archived task metadata, shared Trellis guidance, and full-repo validation signals. It requires audit evidence, strict scope boundaries, and clear handoff between Codex planning/check and DeepSeek implementation.

## Current Project State

- Current branch: `main`.
- No active Trellis task existed before this task was created.
- Working tree already contains prior uncommitted Trellis archive/workspace changes from the completed `05-17-archived-trellis-context-jsonl-full-hygiene-sweep` record/archive state. Do not revert or overwrite those changes.
- Previous journal entry records the completed full hygiene sweep:
  - archived 2026-05 JSONL path drift was repaired where provable,
  - stale `.claude/commands/trellis` JSONL references were reduced to zero,
  - `.trellis/spec/guides/trellis-task-context-hygiene.md` was updated with full audit guidance,
  - remaining expected audit noise was `INVALID_JSON_COUNT=5`, `MISSING_PATH_COUNT=28`, `STALE_COMMAND_COUNT=0`,
  - the 28 missing path findings were intentionally left for this follow-up because they all came from `04-29-phase-1-foundation` legacy `path` entries.

## Requirements

- Audit only these legacy context files:
  - `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/implement.jsonl`
  - `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/check.jsonl`
  - `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/debug.jsonl`
- Convert each safely upgradable JSON object from:
  - `{"path":"<repo-relative-path>","reason":"<reason>"}`
  - to `{"file":"<repo-relative-path>","reason":"<reason>"}`
- Preserve each `reason` value exactly unless JSON escaping requires an equivalent representation.
- Confirm every converted target exists from the repo root before editing.
- Add `type: "directory"` only if a converted target is a directory. Do not add it for normal file targets.
- Keep each JSONL entry as one valid JSON object per line.
- Do not edit any archived task JSONL outside `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/`.
- Update `.trellis/spec/guides/trellis-task-context-hygiene.md` with recognized legacy `path` format guidance, including upgrade rules and Good/Base/Bad cases.
- Run full JSONL audit after implementation. Target: reduce `MISSING_PATH_COUNT` from `28` to `0`, assuming no new unrelated working-tree changes introduce missing paths. If any item cannot be safely upgraded, list it exactly with file and line.

## Command / Payload Contract

No application API, command API, backend DTO, frontend DTO, database, Redis/MQ, auth, storage, or external integration contract changes are allowed.

The relevant Trellis JSONL payload contract is:

| Field | Required | Meaning |
| --- | --- | --- |
| `file` | yes | Repo-root relative path to an existing file or directory. |
| `reason` | yes | Human-readable reason for injecting the context. Preserve original intent. |
| `type` | optional | Use `directory` only when the target is an existing directory; otherwise omit. |
| `path` | legacy only | Recognized historical field in old JSONL. Must be upgraded to `file` when the target exists. |

The relevant validation commands are:

| Command | Purpose | Required Assertion |
| --- | --- | --- |
| `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-04/04-29-phase-1-foundation` | Validate the modified archived task context | Must pass after the legacy field conversion. |
| Full JSONL audit command/script | Classify invalid JSON, missing file paths, legacy path fields, and stale command paths under `.trellis/tasks` | Must show no missing-path findings from the 04-29 legacy `path` entries after conversion. |
| `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"` | Stale command path check | Must remain zero matches. |

## Validation / Error Matrix

| Finding | Expected Handling | Error If |
| --- | --- | --- |
| Legacy JSONL object has `path` and no `file`, and `path` points to an existing file | Rename `path` to `file`; preserve `reason`; omit `type` | Converted path is not verified or reason changes unnecessarily. |
| Legacy JSONL object has `path` and no `file`, and `path` points to an existing directory | Rename `path` to `file`; preserve `reason`; add `type: "directory"` | Directory is treated as a file and `task.py validate` fails. |
| Legacy JSONL object has both `path` and `file` | Prefer current `file` only after manual inspection; do not silently merge | One field is dropped without proving the intended target. |
| Legacy `path` target does not exist | Do not guess; leave unchanged or document as unresolved based on implementation judgment | A placeholder file is created or a similar path is invented. |
| Invalid JSON appears while editing | Stop and repair only if the original entry is recoverable | Line is deleted to make counts pass. |
| Any JSONL outside the 04-29 target directory is implicated | Report as out of scope unless user expands scope | Implementation edits unrelated archived task JSONL files. |

## Good / Base / Bad Cases

Good:

- `{"path":".trellis/spec/backend/index.md","reason":"..."}` is converted to `{"file":".trellis/spec/backend/index.md","reason":"..."}` after confirming the spec file exists.
- A legacy entry pointing to an existing directory is converted to `{"file":"<dir>","reason":"...","type":"directory"}`.
- Full audit reports `MISSING_PATH_COUNT=0` for the previously unresolved 04-29 legacy entries.
- `.trellis/spec/guides/trellis-task-context-hygiene.md` clearly documents legacy `path` recognition and safe conversion rules.

Base:

- All 28 known legacy 04-29 entries point to existing files, so no `type: "directory"` is needed.
- Existing unrelated invalid JSON findings from previous tasks may remain outside this task scope; final report lists them separately if the full audit still shows them.
- Path separators and JSON key order are normalized only in touched lines.

Bad:

- Editing archived JSONL outside `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/`.
- Changing backend/frontend source, Maven files, Docker files, Trellis scripts, or runtime config.
- Replacing `path` with `file` without checking target existence.
- Adding placeholder files so validation passes.
- Treating all missing `file` findings as legacy path entries without parsing the JSON object.

## Acceptance Criteria

- [ ] The three target JSONL files have no remaining `path` fields.
- [ ] The three target JSONL files use current `file` fields with preserved `reason` values.
- [ ] Every converted `file` target exists from the repo root.
- [ ] `type: "directory"` is used only if any converted target is a directory.
- [ ] `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-04/04-29-phase-1-foundation` passes.
- [ ] Full JSONL audit shows the previous 28 legacy missing-path findings are resolved, with target `MISSING_PATH_COUNT=0` unless a documented non-upgradable item exists.
- [ ] Stale command path scan remains zero.
- [ ] `.trellis/spec/guides/trellis-task-context-hygiene.md` documents legacy `path` upgrade rules and Good/Base/Bad cases.
- [ ] No business implementation files are modified.

## Expected Files To Modify

- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/implement.jsonl`
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/check.jsonl`
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/debug.jsonl`
- `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
- `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`
- `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/{implement,check,debug}.jsonl`
- `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/task.json`

## Forbidden Scope

- Do not modify frontend or backend business source.
- Do not modify Maven, Vite, Playwright, Docker, Kubernetes, deployment, environment, or runtime config files.
- Do not modify `.trellis/scripts/task.py`, `.trellis/scripts/common/task_context.py`, `.trellis/scripts/common/cli_adapter.py`, or audit/validation behavior.
- Do not edit archived JSONL outside `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/`.
- Do not fix unrelated invalid JSON findings unless the user explicitly expands scope.
- Do not finish, archive, commit, or record-session during the DeepSeek implementation pass unless the user explicitly asks.

## Required Tests And Assertion Points

- Full JSONL audit before and after edits:
  - assert legacy 04-29 `path` findings are gone,
  - assert missing path count drops from 28 to 0 or all remaining findings are explicitly documented,
  - assert stale command count remains 0,
  - list any unrelated invalid JSON findings separately.
- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-04/04-29-phase-1-foundation`
  - assert the upgraded archived task validates.
- `python ./.trellis/scripts/task.py validate .trellis/tasks/05-17-trellis-legacy-context-format-upgrade`
  - assert this planning/check task context is valid.
- `rg -n '"path"' .trellis/tasks/archive/2026-04/04-29-phase-1-foundation -g "*.jsonl"`
  - assert no legacy `path` fields remain in target JSONL.
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
  - assert stale command path findings remain zero.
- `git diff --check`
  - assert no whitespace errors in touched files.

## Codex / DeepSeek Collaboration Boundary

Codex prepares the task, PRD, context, research, and final check handoff. DeepSeek performs the metadata edits. Codex must not implement the JSONL/spec guide changes in this planning pass.
