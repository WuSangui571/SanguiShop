# Trellis Task Context Hygiene

## Use This When

- Creating or updating Trellis task context files (`implement.jsonl`, `check.jsonl`, `debug.jsonl`).
- Validating task context with `task.py validate`.
- Debugging `task.py validate` failures caused by missing context file paths.
- Setting up a new Codex/DeepSeek/Kilo/Claude task handoff.

## Context Path Convention

Trellis task context `file` entries must reference existing, platform-appropriate paths.

### Codex Tasks

Codex tasks use skills-based commands under `.agents/skills/`:

| Purpose | Correct Path |
|---------|-------------|
| Start workflow | `.agents/skills/start/SKILL.md` |
| Before-dev workflow | `.agents/skills/before-dev/SKILL.md` |
| Check workflow | `.agents/skills/check/SKILL.md` |
| Finish-work workflow | `.agents/skills/finish-work/SKILL.md` |

### Forbidden Pattern

**Do not** reference `.claude/commands/trellis/*.md` paths in task context JSONL files. These paths are Claude-specific and do not exist in Codex, Kilo, Kiro, or other non-Claude environments.

### Platform-Aware Path Resolution

`task.py init-context` uses `cli_adapter.py` to auto-detect the platform and generate correct paths. The `get_trellis_command_path` method maps commands to platform-specific locations:

- Codex: `.agents/skills/<name>/SKILL.md`
- Kilo: `.kilocode/workflows/<name>.md`
- Kiro: `.kiro/skills/<name>/SKILL.md`
- Claude: `.claude/commands/trellis/<name>.md`

If you manually add context entries via `task.py add-context`, always verify the path exists from the repo root.

## Validation Rules

1. Every `file` entry in task context JSONL must resolve to an existing file or directory from the repo root.
2. `task.py validate <task-dir>` checks all three context files (`implement.jsonl`, `check.jsonl`, `debug.jsonl`).
3. Non-existent paths block validation and must be fixed before task handoff.

## Hygiene Checklist

- [ ] All `file` entries in task context JSONL point to existing paths.
- [ ] Codex task context files use `.agents/skills/<skill>/SKILL.md`, not `.claude/commands/trellis/*.md`.
- [ ] `task.py validate` passes for the current task.
- [ ] Archived tasks affected by stale references are cleaned up if in scope.
- [ ] Context reasons remain semantically equivalent after path replacement.
