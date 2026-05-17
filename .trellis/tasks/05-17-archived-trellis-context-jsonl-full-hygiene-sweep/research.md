# Focused Code Research

## Relevant Specs

- `.trellis/workflow.md`: Defines Trellis task lifecycle, context JSONL injection, validation, and archive workflow.
- `.trellis/spec/guides/index.md`: Registers shared thinking guides, including Trellis task context hygiene.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: Primary task-specific guideline for context file path conventions, forbidden `.claude/commands/trellis` paths, and `task.py validate` expectations.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: Relevant because this task should avoid inventing a new validator or abstraction unless necessary; prefer existing `task.py validate` and simple audit commands.
- `.trellis/spec/backend/quality-guidelines.md`: Relevant only for completion discipline and Good/Base/Bad case framing; no backend code should change.
- `.trellis/spec/frontend/quality-guidelines.md`: Relevant only because recent metadata hygiene work was triggered by frontend E2E task contexts; no frontend code should change.
- `.agents/skills/start/SKILL.md`: Session/task workflow used for creating this task and PRD.
- `.agents/skills/before-dev/SKILL.md`: Mandatory pre-development spec reading process.
- `.agents/skills/check/SKILL.md`: Check workflow expected in task context for Codex review.
- `.agents/skills/finish-work/SKILL.md`: Finish-work checklist expected in task context for final validation.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: `cmd_init_context` creates `implement.jsonl`, `check.jsonl`, and `debug.jsonl`; `cmd_add_context` appends JSON objects with `file` and `reason`; `_validate_jsonl` parses each line as JSON and verifies `file` exists as a repo-root relative file or directory.
- `.trellis/scripts/common/cli_adapter.py`: `get_trellis_command_path()` maps Codex Trellis commands to `.agents/skills/<name>/SKILL.md`, confirming `.claude/commands/trellis/*.md` is stale for this environment.
- `.trellis/tasks/archive/2026-05/*/{implement,check,debug}.jsonl`: Existing context entries are line-oriented JSON objects, not arrays. Edits should preserve JSONL structure and one object per line.
- Existing archive layout: archived tasks live under `.trellis/tasks/archive/YYYY-MM/<task>/`; archived context entries that still point to `.trellis/tasks/<task>/prd.md` or `research.md` should point to the same file under the archive directory when it exists.

## Initial Audit Findings

Planning command scanned every `.trellis/tasks/**/{implement,check,debug}.jsonl` file and found:

- `INVALID_JSON_COUNT=10`
- `MISSING_PATH_COUNT=29`
- `STALE_COMMAND_COUNT=0`

Missing archived PRD/research paths found in:

- `.trellis/tasks/archive/2026-05/05-09-admin-review-image-preview/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-10-admin-fulfillment-failure-permission-component-tests/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-11-admin-seckill-activity-persistence-product-sku-snapshot/{check,debug,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-12-local-one-click-smoke-scripts/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-12-local-runtime-and-manual-task-hygiene/check.jsonl`
- `.trellis/tasks/archive/2026-05/05-12-ops-auth-permission-login-whitelist/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-16-admin-fulfillment-shipping-browser-smoke/{check,debug,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-16-mall-order-status-center-browser-smoke/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-17-e2e-smoke-mock-state-reset-audit/{check,implement}.jsonl`
- `.trellis/tasks/archive/2026-05/05-17-e2e-smoke-task-metadata-hygiene-audit/{check,implement}.jsonl`

Archived target existence check:

| Task | `prd.md` Exists | `research.md` Exists |
| --- | --- | --- |
| `05-09-admin-review-image-preview` | yes | no |
| `05-10-admin-fulfillment-failure-permission-component-tests` | yes | yes |
| `05-11-admin-seckill-activity-persistence-product-sku-snapshot` | yes | yes |
| `05-12-local-one-click-smoke-scripts` | yes | no |
| `05-12-local-runtime-and-manual-task-hygiene` | yes | no |
| `05-12-ops-auth-permission-login-whitelist` | yes | yes |
| `05-16-admin-fulfillment-shipping-browser-smoke` | yes | no |
| `05-16-mall-order-status-center-browser-smoke` | yes | no |
| `05-17-e2e-smoke-mock-state-reset-audit` | yes | yes |
| `05-17-e2e-smoke-task-metadata-hygiene-audit` | yes | no |

Invalid JSON found in:

- `.trellis/tasks/archive/2026-05/05-01-payment-callback-timeout-compensation/implement.jsonl:5`
- `.trellis/tasks/archive/2026-05/05-03-compensation-observability-config-hardening/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-05-compensation-ops-dashboard-auth-session/implement.jsonl:11`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/check.jsonl:3`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/check.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-07-user-order-center-filter-recovery/implement.jsonl:6`
- `.trellis/tasks/archive/2026-05/05-07-user-order-history-pagination/implement.jsonl:5`
- `.trellis/tasks/archive/2026-05/05-07-user-order-lifecycle-feedback/check.jsonl:4`
- `.trellis/tasks/archive/2026-05/05-07-user-order-lifecycle-feedback/implement.jsonl:3`
- `.trellis/tasks/archive/2026-05/05-07-user-order-lifecycle-feedback/implement.jsonl:5`

## Files Likely To Modify

- `.trellis/spec/guides/trellis-task-context-hygiene.md`: add full audit command and Good/Base/Bad cases.
- `.trellis/tasks/archive/2026-05/*/{implement,check,debug}.jsonl`: repair only provable archived PRD/research paths and safely reconstructable invalid JSON entries.
- Current task files under `.trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep/`: PRD, research, task context files, and task metadata.

## Risk / Boundary Notes

- Some invalid JSON lines may be fragments from earlier mojibake or wrapped text. Do not delete them or invent a new reason just to make validation pass.
- `task.py validate` fails fast per invalid/missing entry, but it does not know archive intent; humans must confirm archive target existence before rewriting.
- Current scan shows zero `.claude/commands/trellis` matches. Keep a stale-command check in final verification because future or missed files may surface during edits.
- This is a Trellis metadata-only task. Any change under application source directories is out of scope.
- Do not edit `.trellis/scripts/task.py`, `task_context.py`, or `cli_adapter.py` in this task; use their existing behavior as the contract.

## Required Tests

- Run full JSONL audit before and after edits, reporting invalid JSON, missing paths, and stale command path counts.
- Run `python ./.trellis/scripts/task.py validate .trellis/tasks/05-17-archived-trellis-context-jsonl-full-hygiene-sweep`.
- Run `python ./.trellis/scripts/task.py validate <each modified archived task directory>`.
- Run `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"`.
- Run `git diff --check`.

## Suggested Execution Order

1. Run the full audit command and save the categorized findings in the implementation notes or final report.
2. Fix archived PRD/research path drift for targets proven to exist.
3. Inspect invalid JSON files one by one; repair only lines that are safely reconstructable.
4. Update the Trellis hygiene guide with durable audit and Good/Base/Bad guidance.
5. Validate every modified task, then run the full audit again.
6. Report remaining unresolved findings with exact paths and line numbers.
