# E2E Smoke Task Metadata Hygiene Audit

## Current Project Status

The previous recorded task, `E2E smoke mock state reset audit`, is complete and archived after human testing and commit. It added the frontend E2E mock reset rule and fixed one current-task context failure by replacing stale `.claude/commands/trellis/*.md` references with existing Codex skill files under `.agents/skills/...`.

The working tree started clean on `main`, with no active Trellis task. This task is the recommended next cleanup from the journal because stale Trellis task context references can break `task.py validate` during handoff, check, and record flows.

## Classification

Simple Task.

The goal and boundaries are clear, and the expected edits are limited to Trellis metadata/spec files. This is not a production frontend/backend behavior change.

## Goal

Audit and clean Trellis task context metadata so Codex/DeepSeek handoff, `check`, `finish-work`, `record-session`, archive, and validate flows do not fail because task context files point at nonexistent `.claude/commands/trellis/...` paths.

## Requirements

- Audit `.trellis/tasks/**/{implement,check,debug}.jsonl` for `file` entries that point to nonexistent files.
- Replace stale Codex-context references to `.claude/commands/trellis/check.md` with `.agents/skills/check/SKILL.md`.
- Replace stale Codex-context references to `.claude/commands/trellis/finish-work.md` with `.agents/skills/finish-work/SKILL.md`.
- Preserve unrelated task context entries and reasons unless a reason is clearly tied to the stale path and should remain semantically equivalent.
- Confirm active/list/archive/record task lifecycle expectations from existing Trellis scripts and previous task PRDs.
- Add or supplement a Trellis task context hygiene guideline that states Codex task contexts should reference `.agents/skills/<skill>/SKILL.md`, not `.claude/commands/trellis/*.md`.
- Validate the new current task and a representative set of affected archived tasks after cleanup.

## Non-Goals

- Do not modify production frontend source, backend source, API clients, DTOs, database migrations, Redis/MQ contracts, auth behavior, infra, or deployment logic.
- Do not rewrite archived PRDs, task descriptions, journal history, or completed business acceptance criteria.
- Do not change `task.py` behavior unless validation reveals a direct bug that cannot be resolved by metadata/spec cleanup; if that happens, stop and return to Codex/user for scope confirmation.
- Do not auto-commit. If archive commands are needed later, use `--no-commit` unless the human explicitly asks otherwise.

## Command / Payload Contract

| Command / Pattern | Purpose | Expected Input | Expected Result |
| --- | --- | --- | --- |
| `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks` | Find stale task context references. | No payload. | Lists every stale JSONL entry that must be reviewed. |
| JSONL context entry | Trellis task context payload. | `{"file": "<repo-relative-existing-path>", "reason": "<why context matters>"}` | `file` resolves from repo root; `reason` remains useful for implement/check/debug agents. |
| `python ./.trellis/scripts/task.py validate <task-dir>` | Validate context file paths and JSONL shape. | Active or archived task directory. | Exits 0 after all context `file` entries resolve. |
| `python ./.trellis/scripts/task.py list` | Verify active task list. | No payload. | Shows current non-archived task state without completed/archive pollution. |
| `python ./.trellis/scripts/task.py list-archive 2026-05` | Verify archive discovery. | Month string. | Lists archived May 2026 tasks, including affected tasks after metadata cleanup. |
| `python ./.trellis/scripts/get_context.py` | Verify current task injection. | No payload. | Shows this task as current after `task.py start`. |

## Validation / Error Matrix

| Scenario | Expected Handling |
| --- | --- |
| Context JSONL has invalid JSON. | Fix only the malformed metadata entry if it is in scope; otherwise record blocker. |
| Context JSONL `file` points to `.claude/commands/trellis/check.md`. | Replace with `.agents/skills/check/SKILL.md`. |
| Context JSONL `file` points to `.claude/commands/trellis/finish-work.md`. | Replace with `.agents/skills/finish-work/SKILL.md`. |
| Context JSONL points to another nonexistent production/spec/task file. | Do not guess. Investigate whether the path moved; if unclear, record the exact task/path and ask for confirmation. |
| `task.py validate` fails for an unrelated archived task context outside the stale `.claude` pattern. | Do not broaden the task silently; document separately and request scope decision. |
| Active/list/archive commands show inconsistent state. | Document observed command output and stop before changing task lifecycle code. |

## Good / Base / Bad Cases

| Case | Definition |
| --- | --- |
| Good | All stale `.claude/commands/trellis/*.md` references in task context JSONL are replaced with existing `.agents/skills/.../SKILL.md` files, new hygiene spec exists, and representative affected tasks validate. |
| Good | Current task context uses existing `.agents/skills/start/SKILL.md`, `.agents/skills/before-dev/SKILL.md`, `.agents/skills/check/SKILL.md`, and `.agents/skills/finish-work/SKILL.md` as appropriate. |
| Base | Some unrelated missing context paths remain, but they are documented with exact files and excluded from this scoped cleanup. |
| Base | `task.py list-archive` and `get_context.py` are verified manually rather than changed. |
| Bad | Production frontend/backend files change. |
| Bad | Archived PRDs or journal entries are rewritten to hide history. |
| Bad | Metadata cleanup changes semantic task context reasons or removes useful spec/code references. |
| Bad | AI runs `task.py archive` without `--no-commit` and creates a commit. |

## Acceptance Criteria

- [ ] `rg` over `.trellis/tasks` finds no stale `.claude/commands/trellis/check.md` or `.claude/commands/trellis/finish-work.md` entries in `{implement,check,debug}.jsonl`.
- [ ] Any remaining nonexistent context file references are either fixed within scope or documented as explicit follow-up blockers.
- [ ] Trellis context hygiene guidance exists in `.trellis/spec/` and clearly states Codex context should use `.agents/skills/<skill>/SKILL.md`.
- [ ] This task's `implement.jsonl`, `check.jsonl`, and `debug.jsonl` validate.
- [ ] Representative affected archived tasks validate after cleanup, including at least one May 2026 frontend/E2E smoke task and one older archived task if stale references are found there.
- [ ] `task.py list`, `task.py list-archive 2026-05`, and `get_context.py` show consistent active/archive/current-task state.
- [ ] No production frontend/backend behavior files are modified.

## Required Tests And Assertion Points

- `python ./.trellis/scripts/task.py validate .trellis/tasks/05-17-e2e-smoke-task-metadata-hygiene-audit`
  - Assert the current task context JSONL files are valid and every `file` path exists.
- `rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks`
  - Assert no stale Trellis command references remain in task context JSONL files, or only documented out-of-scope historical text remains outside context files.
- `python ./.trellis/scripts/task.py validate <affected-archive-task-dir>`
  - Assert representative cleaned archived task contexts validate.
- `python ./.trellis/scripts/task.py list`
  - Assert only active tasks appear and this task is active after start.
- `python ./.trellis/scripts/task.py list-archive 2026-05`
  - Assert archived task discovery still works.
- `python ./.trellis/scripts/get_context.py`
  - Assert current task injection points to this task.
- `git diff --check`
  - Assert metadata/spec changes do not introduce whitespace errors.

## Relevant Specs

- `.trellis/workflow.md`: Trellis task lifecycle, current task, archive, record, and no AI commit rule.
- `.trellis/spec/frontend/quality-guidelines.md`: Existing E2E smoke quality rules and recent E2E metadata context.
- `.trellis/spec/backend/quality-guidelines.md`: General review/test discipline and Good/Base/Bad acceptance shape.
- `.trellis/spec/guides/index.md`: Shared thinking guide index.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: Search-first and avoid needless abstraction.
- `.trellis/spec/guides/architecture-review-checklist.md`: Contract-first review order and Good/Base/Bad risk framing.

## Code Patterns Found

- `.trellis/scripts/common/task_context.py`: `task.py validate` reads JSONL context files and verifies referenced `file` paths exist from repo root.
- `.trellis/scripts/common/task_store.py`: `task.py archive` moves completed tasks and can auto-commit unless `--no-commit` is used.
- `.trellis/scripts/common/tasks.py`: active task iteration skips `.trellis/tasks/archive`.
- `.trellis/scripts/common/cli_adapter.py`: Codex command/skill path convention resolves to `.agents/skills/<name>/SKILL.md`.
- `.trellis/tasks/archive/2026-05/05-12-local-runtime-and-manual-task-hygiene/prd.md`: Existing task-hygiene PRD pattern for archive/list/validate flow and no AI auto-commit boundary.
- `.trellis/tasks/archive/2026-05/05-17-e2e-smoke-mock-state-reset-audit/check.jsonl`: Recent corrected Codex context path pattern.

## Files Likely To Modify

- `.trellis/spec/guides/index.md`: Add a link to the task context hygiene guide if a new guide file is created.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: New guideline for Codex task context paths and validation rules.
- `.trellis/tasks/**/{implement,check,debug}.jsonl`: Replace stale `.claude/commands/trellis/*.md` context paths with `.agents/skills/.../SKILL.md` where found.
- `.trellis/tasks/05-17-e2e-smoke-task-metadata-hygiene-audit/{implement,check,debug}.jsonl`: Current task context generated by `init-context` and supplemented by Codex.

## Risk / Boundary Notes

- Archived task context cleanup is metadata-only but touches historical task directories; preserve task intent and reason text.
- Because `.claude/commands/trellis/*.md` may be valid in a Claude-oriented environment but absent here, this cleanup targets Codex task context stability in this repository.
- If future multi-agent environments need dual-path support, that is a separate `task.py`/adapter design task, not part of this audit.
- Any missing non-`.claude` context path discovered during validate may indicate an old moved file; do not invent a replacement without evidence.

