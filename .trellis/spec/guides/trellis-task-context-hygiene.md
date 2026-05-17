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

## Full JSONL Audit

To classify all hygiene problems across every task JSONL file, run the following audit script from the repo root:

```python
import json
from pathlib import Path

repo = Path('.')
tasks_dir = repo / '.trellis' / 'tasks'

jsonl_files = sorted(tasks_dir.glob('**/*.jsonl'))

invalid_json = []
missing_path = []
stale_command = []

for f in jsonl_files:
    rel = str(f.relative_to(repo)).replace('\\', '/')
    for i, line in enumerate(f.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        try:
            data = json.loads(line)
        except json.JSONDecodeError:
            invalid_json.append(f'{rel}:{i}')
            continue
        fp = data.get('file', '')
        if not fp:
            missing_path.append(f'{rel}:{i} (no file field)')
            continue
        normalized_fp = fp.replace('\\', '/')
        if '.claude/commands/trellis' in normalized_fp:
            stale_command.append(f'{rel}:{i} -> {fp}')
            continue
        entry_type = data.get('type', 'file')
        full = repo / fp
        if entry_type == 'directory':
            if not full.is_dir():
                missing_path.append(f'{rel}:{i} -> {fp}')
        else:
            if not full.is_file():
                missing_path.append(f'{rel}:{i} -> {fp}')

print(f'INVALID_JSON_COUNT={len(invalid_json)}')
for item in invalid_json:
    print(f'  INVALID_JSON: {item}')
print(f'MISSING_PATH_COUNT={len(missing_path)}')
for item in missing_path:
    print(f'  MISSING_PATH: {item}')
print(f'STALE_COMMAND_COUNT={len(stale_command)}')
for item in stale_command:
    print(f'  STALE_COMMAND: {item}')
```

Also run a targeted stale-command scan:

```bash
rg -n "\.claude/commands/trellis|\.claude\\commands\\trellis" .trellis/tasks -g "*.jsonl"
```

## Good / Base / Bad Cases

### Archived Path Repair

**Good:**
- A JSONL entry in an archived task references `.trellis/tasks/<task>/prd.md`.
- The archived task directory contains `prd.md`.
- The entry is rewritten to `.trellis/tasks/archive/<YYYY-MM>/<task>/prd.md`.
- `task.py validate` passes for the repaired task.
- The `reason` field is preserved exactly.

**Base:**
- Path separators are normalized only in touched entries; untouched valid entries do not need cosmetic rewrites.
- Some historical tasks from a different era or pre-standard format may remain unresolved; the final report lists exact file and line numbers.

**Bad:**
- Creating a placeholder `prd.md` or `research.md` in the archive directory to satisfy validation.
- Repointing a context entry to a similarly named task because the intended target is missing.
- Rewriting a path without first confirming the target file exists on disk.

### Invalid JSON Handling

**Good:**
- An invalid JSON line is a truncated version of a valid entry where the `file` and `reason` values are recoverable from adjacent lines and the target file exists.
- The line is repaired to one valid JSON object per line, preserving the original intent.
- After repair, `task.py validate` passes.

**Base:**
- Invalid JSON lines whose original `file` path and `reason` cannot be recovered safely are left unchanged.
- The final audit report lists each unresolved invalid line with exact `file:line` and explains why it was not changed.
- No lines are deleted merely to make the audit numbers look better.

**Bad:**
- Deleting invalid JSON lines without understanding whether they carried required context.
- Inventing a `file` path or `reason` value to repair a corrupted line when the original intent cannot be proven.
- Replacing a corrupted line with a nearby task's content because the line number looks similar.

### Stale Command Paths

**Good:**
- A `.claude/commands/trellis/check.md` reference is replaced with `.agents/skills/check/SKILL.md` only after confirming that file exists.

**Base:**
- Zero stale command paths found; no action needed.

**Bad:**
- Replacing a stale command path with a skill file that does not exist.
- Leaving stale `.claude/commands/trellis` references in place without documentation when they could have been fixed.

## Evidence-Based Repair Rules

1. **Always verify target existence.** Before rewriting any `file` path in JSONL, confirm the target file or directory exists on disk from the repo root.
2. **Never guess the intended target.** If an archived task's context entry references a path that no longer exists in any form, do not invent a replacement.
3. **Preserve original reason text.** When repairing a path, the `reason` field must remain semantically identical to the original entry.
4. **One JSON object per line.** All JSONL repairs must produce valid, single-line JSON objects conforming to the `{"file": "...", "reason": "..."}` contract.
5. **Forward-slash paths.** Normalize path separators to forward slashes in any touched entries.
6. **List unresolved findings.** Every hygiene sweep must produce a final report of unresolved issues with exact `file:line` locations.
