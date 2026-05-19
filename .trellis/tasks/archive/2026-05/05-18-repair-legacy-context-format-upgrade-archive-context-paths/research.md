# Focused Research

## Relevant Specs

- `.trellis/workflow.md`: Trellis task lifecycle, task directory structure, and validation expectations.
- `.agents/skills/start/SKILL.md`: Session start, task classification, and task workflow expectations.
- `.agents/skills/before-dev/SKILL.md`: Mandatory guideline discovery before implementation.
- `.trellis/spec/backend/index.md`: Read for scope awareness; no backend service checklist is triggered because no backend code is in scope.
- `.trellis/spec/frontend/index.md`: Read for scope awareness; no frontend checklist is triggered because no frontend code is in scope.
- `.trellis/spec/guides/index.md`: Identifies Trellis Task Context Hygiene as the relevant concrete guide.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: Primary spec for context path repair, full audit, stale command scan, and Good/Base/Bad cases.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: `task.py validate` expects each JSONL object to have a `file` field and validates it as an existing repo-relative file unless `type` is `directory`.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl`: Current archived task context contains two stale active-task references at lines 3 and 4.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl`: Current archived task context contains two stale active-task references at lines 9 and 10.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`: Existing archived target file for stale `prd.md` references.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`: Existing archived target file for stale `research.md` references.

## Audit Baseline

Codex ran the full JSONL audit from `.trellis/spec/guides/trellis-task-context-hygiene.md` before implementation planning.

Result in current workspace:

- `INVALID_JSON_COUNT=5`
- `MISSING_PATH_COUNT=8`
- `LEGACY_PATH_COUNT=0`
- `STALE_COMMAND_COUNT=0`

The five historical invalid JSON lines are:

- `.trellis/tasks/archive/2026-05/05-01-payment-callback-timeout-compensation/implement.jsonl:5`
- `.trellis/tasks/archive/2026-05/05-03-compensation-observability-config-hardening/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-05-compensation-ops-dashboard-auth-session/implement.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-07-user-order-history-pagination/implement.jsonl:5`

The four in-scope missing paths are:

- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:3 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl:4 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:9 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/prd.md`
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl:10 -> .trellis/tasks/05-17-repair-legacy-context-format-upgrade-archive-paths/research.md`

The four extra current-workspace missing paths are outside this task's requested target:

- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/check.jsonl:10`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/check.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:9`
- `.trellis/tasks/archive/2026-05/05-18-repair-previous-hygiene-task-archive-context-paths/implement.jsonl:10`

DeepSeek must not repair the four extra paths without explicit user confirmation.

## Files Likely To Modify

Expected implementation files:

- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/check.jsonl`: Replace lines 3 and 4 `file` values with archived paths.
- `.trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths/implement.jsonl`: Replace lines 9 and 10 `file` values with archived paths.

Expected task metadata/context files already prepared by Codex:

- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/prd.md`
- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/research.md`
- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/task.json`
- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/implement.jsonl`
- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/check.jsonl`
- `.trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths/debug.jsonl`

## Risk / Boundary Notes

- Current workspace audit baseline differs from the user's stated baseline because an uncommitted/visible previous archive contributes four extra missing paths.
- This task should not modify business code or Trellis scripts.
- Do not create placeholder target files; the correct archived target files already exist.
- Do not touch historical invalid JSON lines unless a separate task explicitly scopes them.
- Do not broadly rewrite JSONL formatting.
- If the global audit cannot reach `MISSING_PATH_COUNT=0` because of unrelated findings, report the blocker instead of expanding scope.

## Required Tests

Run:

```powershell
python ./.trellis/scripts/task.py validate .trellis/tasks/archive/2026-05/05-17-repair-legacy-context-format-upgrade-archive-paths
python ./.trellis/scripts/task.py validate .trellis/tasks/05-18-repair-legacy-context-format-upgrade-archive-context-paths
rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"
git diff --check
```

Also run the full JSONL audit script from `.trellis/spec/guides/trellis-task-context-hygiene.md` and report all remaining counts and unresolved line locations.
