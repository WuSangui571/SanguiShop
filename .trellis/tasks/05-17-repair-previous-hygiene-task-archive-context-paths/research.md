# Research: Repair Previous Hygiene Task Archive Context Paths

## Relevant Specs

- `.trellis/spec/guides/index.md`: identifies Trellis Task Context Hygiene as the relevant guide for creating or updating task JSONL context and handling validation failures.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: defines context path conventions, full JSONL audit, archived path repair cases, invalid JSON boundaries, stale command scan, and evidence-based repair rules.
- `.trellis/spec/backend/index.md`: read for project context; no backend service owns this metadata-only change.
- `.trellis/spec/frontend/index.md`: read for project context; no frontend surface owns this metadata-only change.

## Code Patterns Found

- Archived path repair pattern: `.trellis/spec/guides/trellis-task-context-hygiene.md` says archived task entries may be rewritten from `.trellis/tasks/<task>/prd.md` to `.trellis/tasks/archive/<YYYY-MM>/<task>/prd.md` only when the archived target exists and the `reason` field is preserved.
- Full audit pattern: the same guide's Python audit script parses every `.trellis/tasks/**/*.jsonl` file, tracks invalid JSON separately, treats missing `file` targets as missing paths, reports legacy `path` fields separately, and normalizes backslashes before stale command detection.
- Current target entries:
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:2`
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:3`
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:3`
  - `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:4`

## Files Likely To Modify

- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl`: update two `file` values from active task paths to archived task paths.
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl`: update two `file` values from active task paths to archived task paths.

## Verified Existing Targets

Both archived target files exist:

- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`

## Current Full Audit Findings

Current audit output before implementation:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

Target in-scope missing paths:

- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:3 -> .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/check.jsonl:4 -> .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:2 -> .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/implement.jsonl:3 -> .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/research.md`

Out-of-scope missing paths discovered in the same audit:

- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:3 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/check.jsonl:4 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:4 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-trellis-legacy-context-format-upgrade/implement.jsonl:5 -> .trellis/tasks/05-17-trellis-legacy-context-format-upgrade/research.md`

Pre-existing invalid JSON findings to leave unchanged:

- `.trellis/tasks/archive/2026-05/05-01-payment-callback-timeout-compensation/implement.jsonl:5`
- `.trellis/tasks/archive/2026-05/05-03-compensation-observability-config-hardening/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-05-compensation-ops-dashboard-auth-session/implement.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-07-user-order-history-pagination/implement.jsonl:5`

## Risk / Boundary Notes

- The user requested only the previous hygiene archive task files be modified. Current audit has additional missing paths outside that boundary, so global `MISSING_PATH_COUNT=0` is not reachable without expanding scope.
- Do not create placeholder files to satisfy validation.
- Do not delete or repair invalid JSON fragments because their original intent is not safely recoverable in this task.
- Do not rewrite unrelated context entries for formatting.
- Preserve reasons exactly in the four touched JSONL entries.

## Required Tests

- `python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-archived-trellis-context-jsonl-full-hygiene-sweep`
- Full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md`
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`
- `git diff --check`
