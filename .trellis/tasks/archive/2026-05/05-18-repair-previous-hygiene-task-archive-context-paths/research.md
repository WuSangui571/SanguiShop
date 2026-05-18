# Research: Repair Previous Hygiene Task Archive Context Paths

## Relevant Specs

- `.trellis/spec/backend/index.md`: read for project positioning; no backend service owns this metadata-only change.
- `.trellis/spec/frontend/index.md`: read for project positioning; no frontend surface owns this metadata-only change.
- `.trellis/spec/guides/index.md`: identifies Trellis Task Context Hygiene as the relevant guide for task JSONL context and validation failures.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: primary guideline for context path conventions, full JSONL audit, archived path repair, invalid JSON boundaries, stale command scans, and evidence-based repair rules.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: `task.py validate` reads `implement.jsonl`, `check.jsonl`, and `debug.jsonl`; every JSONL object needs a `file` field whose target exists from repo root unless `type: "directory"` is used.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: archived path repair is valid when an entry points at `.trellis/tasks/<task>/prd.md`, the archived task contains `prd.md`, and the entry is rewritten to `.trellis/tasks/archive/<YYYY-MM>/<task>/prd.md` while preserving `reason`.
- Full audit script from the same guide classifies invalid JSON, missing `file` targets, legacy `path` fields, and stale `.claude/commands/trellis` references separately.

## Current Audit Snapshot

Before implementation, the full JSONL audit reports:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

In-scope missing paths:

- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/check.jsonl:7 -> .trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/check.jsonl:8 -> .trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/research.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:8 -> .trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:9 -> .trellis/tasks/05-17-repair-previous-hygiene-task-archive-context-paths/research.md`

Out-of-scope missing paths in the same audit:

- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:3 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:4 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:9 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:10 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`

Pre-existing invalid JSON findings to leave unchanged:

- `.trellis/tasks/archive/2026-05/05-01-payment-callback-timeout-compensation/implement.jsonl:5`
- `.trellis/tasks/archive/2026-05/05-03-compensation-observability-config-hardening/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-05-compensation-ops-dashboard-auth-session/implement.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-07-user-order-history-pagination/implement.jsonl:5`

## Verified Existing Targets

The intended archived target files exist:

- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/research.md`

## Files Likely To Modify

- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/implement.jsonl`: update line 8 and line 9 `file` values only.
- `.trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths/check.jsonl`: update line 7 and line 8 `file` values only.

## Risk / Boundary Notes

- The user's initial count expected four missing paths; current audit shows eight because a later archived hygiene task now has the same self-archive path drift. That later task is outside this scope.
- Global `MISSING_PATH_COUNT=0` is not expected after this task unless the scope is explicitly expanded.
- Do not create placeholder files, delete invalid JSON fragments, or repoint to similarly named tasks.
- Preserve the exact `reason` values on the four touched entries.
- Keep edits scoped to the two target JSONL files.

## Required Tests

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-previous-hygiene-task-archive-context-paths`
- Full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`
