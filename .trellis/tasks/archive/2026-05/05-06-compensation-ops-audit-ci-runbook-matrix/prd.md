# Compensation Ops Audit CI Manual Acceptance Troubleshooting Matrix

## Goal

固化 compensation ops audit manual GitHub workflow 在真实 runner 上暴露的失败模式，形成可执行排障矩阵和验收模板。后续看到 GitHub annotation 或 Maven log 时，应能先判断失败发生在哪个 step、是否阻塞、问题归属和修复动作，而不是凭经验猜测。

## Scope

- Docs/spec-only change.
- Do not change business code, Maven tests, PowerShell script behavior, or workflow behavior unless a documented contradiction is discovered.
- Primary docs:
  - `docs/compensation-ops-audit-search.md`
  - `.trellis/spec/backend/quality-guidelines.md`

## Requirements

- Add a failure matrix for:
  - `Node.js 20 actions are deprecated`
  - `/usr/bin/git failed with exit code 128`
  - `No url found for submodule path 'Trellis' in .gitmodules`
  - Maven wrapper permission / `chmod +x ./mvnw`
  - `pwsh` path separator mistakes
  - Surefire target test class not executed
- For each failure class, define:
  - Typical workflow step: Checkout, Setup Java, chmod, pwsh script, or Maven test
  - Whether it blocks the workflow
  - Whether it is runner/platform warning, repo metadata, workflow permission/checkout, shell/script portability, or Maven/test-selector issue
  - Diagnosis points and corrective action
- Document repair strategies:
  - Official action version upgrade strategy
  - `permissions: contents: read`
  - `persist-credentials: false`
  - `submodules: false`
  - `.gitmodules` gitlink contract
  - Maven wrapper/cache/Java 21 checks
  - Confirming actual Surefire test class execution
- Add Good/Base/Bad cases:
  - Good: `service=all` succeeds and has no warning/error annotation
  - Base: workflow succeeds with annotation warning and reviewer classifies whether it is blocking
  - Bad: checkout failure causes unrelated Maven script edits
  - Bad: reviewer trusts script "Expected Maven output should show" without checking actual Maven test output
- Add a GitHub manual acceptance template with:
  - Run URL
  - Branch / commit
  - Service input
  - Runner
  - Step/log assertion checklist
  - Final conclusion

## Acceptance Criteria

- [ ] The runbook can be used directly from a GitHub Actions failure page to identify the owner layer and next action.
- [ ] The backend quality spec keeps the executable CI/manual acceptance contract synchronized with the runbook.
- [ ] No business code, API contract, database, frontend, or workflow behavior changes are included.
- [ ] `$check` finds no missing executable details for this infra/docs change.
- [ ] Finish output includes verification commands and a git sync command in the requested `git add .;git commit -m "...";git push;` form.

## Technical Notes

- The task captures lessons from the final accepted `Compensation Ops Audit` workflow on 2026-05-06.
- The workflow remains manual `workflow_dispatch`, not a required `pull_request` gate.
- Documentation should name exact files, commands, inputs, steps, and assertion points.
