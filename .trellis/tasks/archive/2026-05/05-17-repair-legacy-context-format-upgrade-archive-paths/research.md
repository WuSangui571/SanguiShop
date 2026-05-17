# Focused Code Research

## Relevant Specs

- `.trellis/workflow.md`: Defines task creation, context setup, current-task activation, validation, and finish workflow.
- `.agents/skills/start/SKILL.md`: Defines session start, task classification, PRD, research, context setup, and task activation workflow.
- `.agents/skills/before-dev/SKILL.md`: Requires package/spec discovery, relevant spec index reads, concrete guideline reads, and shared guide reads before development.
- `.trellis/spec/backend/index.md`: Read for backend scope awareness. No backend checklist item is triggered because this task does not modify backend services, APIs, DB, Redis/MQ, auth, or secrets.
- `.trellis/spec/frontend/index.md`: Read for frontend scope awareness. No frontend checklist item is triggered because this task does not modify frontend source, API types, state, UI, or token handling.
- `.trellis/spec/guides/index.md`: Shared guide index; identifies Trellis Task Context Hygiene as the relevant concrete guide.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: Primary guideline for JSONL path conventions, full audit classification, archived path repair Good/Base/Bad cases, and evidence-based repair rules.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: `task.py validate` reads each JSONL line as JSON, requires `file`, checks `type == "directory"` with `is_dir()`, otherwise checks `is_file()`. It does not resolve archived active-task paths automatically.
- `.trellis/scripts/task.py`: Exposes `init-context`, `add-context`, `validate`, `list-context`, and `start`; no script behavior change is needed for this task.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: Existing archived path repair rule says active task paths in archived JSONL should be rewritten to `.trellis/tasks/archive/<YYYY-MM>/<task>/...` only after confirming the archived target exists.
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl`: Lines 3 and 4 point to missing active task `prd.md` and `research.md`.
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl`: Lines 4 and 5 point to missing active task `prd.md` and `research.md`.

## Target Audit Findings

Target missing paths:

- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:3 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:4 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:4 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:5 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`

Verified replacement targets:

- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/prd.md` exists.
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/research.md` exists.

## Full Audit Baseline

Current baseline full JSONL audit:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

Baseline details:

- Four target findings are in `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/{check,implement}.jsonl`.
- Four additional missing-path findings are in `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/{check,implement}.jsonl`, caused by the current uncommitted archive state. They are not part of this task unless the user expands the scope.
- Five invalid JSON findings are historical archive issues and remain out of scope.

## Files Likely To Modify

- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl`: rewrite lines 3 and 4 from active task paths to archived task paths.
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl`: rewrite lines 4 and 5 from active task paths to archived task paths.

Current task setup files already created or expected:

- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/{implement,check,debug}.jsonl`
- `.trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/task.json`

## Risk / Boundary Notes

- The intended repair is evidence-based because the archived `prd.md` and `research.md` targets exist.
- The `reason` fields must be preserved exactly; this is a path-only rewrite.
- Do not create missing active-task files to satisfy validation.
- Do not repair unrelated missing paths in `05-17-repair-previous-hygiene-task-archive-context-paths` during this task.
- Do not repair the 5 historical invalid JSON lines during this task.
- Do not edit Trellis scripts; the validator already has the desired behavior.
- Do not change backend/frontend business code, API/DTO contracts, DB, Redis/MQ, auth, storage, infra, Maven, Vite, Docker, or runtime config.

## Required Tests

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade`
- Full JSONL audit from `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`

Expected audit interpretation after DeepSeek edits:

- No `MISSING_PATH` entries should remain for `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/`.
- `LEGACY_PATH_COUNT` should remain `0`.
- `STALE_COMMAND_COUNT` should remain `0`.
- `INVALID_JSON_COUNT=5` may remain as historical out-of-scope.
- In the current dirty working tree, up to 4 unrelated `MISSING_PATH` entries may remain from `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/`; report them separately unless scope is expanded.

## Suggested Execution Order For DeepSeek

1. Reconfirm the archived `prd.md` and `research.md` files exist.
2. Edit only the four target JSONL lines in `check.jsonl` and `implement.jsonl`.
3. Preserve each `reason` value exactly.
4. Run the target `task.py validate` command.
5. Run full JSONL audit and confirm the target task no longer appears in `MISSING_PATH`.
6. Run stale command scan and `git diff --check`.
7. Report remaining out-of-scope findings separately.
