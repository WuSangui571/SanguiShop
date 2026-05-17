# Focused Code Research

## Relevant Specs

- `.trellis/workflow.md`: Defines task creation, context JSONL setup, current-task activation, and validation expectations.
- `.trellis/spec/backend/index.md`: Read for SanguiShop backend scope awareness. No backend service checklist item is triggered because this task does not modify backend implementation, APIs, DB, Redis/MQ, auth, or secrets.
- `.trellis/spec/frontend/index.md`: Read for SanguiShop frontend scope awareness. No frontend checklist item is triggered because this task does not modify frontend implementation, API types, state, or UI.
- `.trellis/spec/guides/index.md`: Shared guide index; points to the Trellis context hygiene guide as the relevant concrete guideline.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: Primary concrete guideline for task context JSONL path conventions, validation, full audit, and Good/Base/Bad cases.
- `.agents/skills/start/SKILL.md`: Session/task workflow used to classify this work and create the task.
- `.agents/skills/before-dev/SKILL.md`: Mandatory pre-development guideline discovery workflow used before preparing implementation context.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: Current validation contract reads `data.get("file")`; if absent, validation reports `Missing file field`. It checks `type == "directory"` with `is_dir()`, otherwise `is_file()`.
- `.trellis/scripts/task.py`: CLI exposes `init-context`, `add-context`, `validate`, and `list-context`; context files are `implement.jsonl`, `check.jsonl`, and `debug.jsonl`.
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/{implement,check,debug}.jsonl`: All 28 target entries are valid JSON objects using legacy `path` plus `reason`, with no `file` field.
- Existing current-format task context files use one JSON object per line with `file` and `reason`; `type` appears only when directory context is intended.

## Target Audit Findings

Target files:

- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/implement.jsonl`: 14 legacy `path` entries.
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/check.jsonl`: 12 legacy `path` entries.
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/debug.jsonl`: 2 legacy `path` entries.

Target existence check:

- All 28 legacy `path` values resolve to existing files from the repo root.
- None of the 28 target values resolve to directories.
- No `type: "directory"` is expected for the target 04-29 conversion.

Legacy paths in scope:

- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/messaging-cache-guidelines.md`
- `.trellis/spec/backend/observability-devops.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/guides/architecture-review-checklist.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`

## Full Audit Baseline

The planning full-audit command found:

- `INVALID_JSON_COUNT=5`
- `LEGACY_PATH_COUNT=28`
- `MISSING_PATH_COUNT=32`
- `STALE_COMMAND_COUNT=0`

Important baseline note:

- The 28 intended findings are exactly the 04-29 legacy `path` entries.
- The other 4 missing-path findings are from the current dirty working tree state for `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/{implement,check}.jsonl`, where entries still reference `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md` and `research.md` after that task was archived. Those are not part of this task unless the user expands scope.
- The 5 invalid JSON findings are pre-existing 2026-05 archive issues already documented by the previous task and remain out of scope.

## Files Likely To Modify

- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/implement.jsonl`: rename `path` to `file` for 14 entries.
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/check.jsonl`: rename `path` to `file` for 12 entries.
- `.trellis/tasks/archive/2026-04/04-29-phase-1-foundation/debug.jsonl`: rename `path` to `file` for 2 entries.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: document recognized legacy `path` format and upgrade rules with Good/Base/Bad cases.
- Current task files under `.trellis/tasks/05-17-trellis-legacy-context-format-upgrade/`: PRD, research, context JSONL, and task metadata.

## Risk / Boundary Notes

- The conversion is mechanically safe only because every target `path` exists. The implementer must not generalize this to missing targets.
- `task.py validate` does not treat `path` as a legacy alias; the fix must write the current `file` contract.
- Full audit counts in this dirty working tree may not go directly from 28 to 0 because 4 unrelated missing paths currently exist from the previous archived task state. For this task, success means the 28 04-29 legacy entries disappear and no new missing paths are introduced.
- Do not repair the 4 unrelated 05-17 archive findings or the 5 invalid JSON findings unless the user explicitly expands scope.
- Do not modify `.trellis/scripts/*`; the goal is metadata normalization and guide documentation, not validator behavior.

## Required Tests

- Full JSONL audit before and after edits, with categories for invalid JSON, legacy path fields, missing paths, and stale command paths.
- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-04/04-29-phase-1-foundation`
- `python ./.trellis/scripts/task.py validate .trellis/tasks/05-17-trellis-legacy-context-format-upgrade`
- `rg -n '"path"' .trellis/tasks/archive/2026-04/04-29-phase-1-foundation -g "*.jsonl"`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`

## Suggested Execution Order

1. Parse the three target JSONL files and verify every legacy `path` target still exists.
2. Rewrite only those three files from `path` to `file`, preserving `reason`; do not add `type` because all targets are files.
3. Update `.trellis/spec/guides/trellis-task-context-hygiene.md` with legacy `path` recognition, upgrade rules, and Good/Base/Bad cases.
4. Run target validation and full audit.
5. Report remaining out-of-scope audit findings separately from the 04-29 result.
