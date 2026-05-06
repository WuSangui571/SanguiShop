# Compensation Ops Audit Cross Platform Manual CI

## Goal

Make the compensation ops audit controller test entry reusable from both local PowerShell and GitHub manual CI without turning the tests into a default pull request gate.

## Requirements

- Update `scripts/verify-compensation-ops-audit.ps1` to choose `.\mvnw.cmd` on Windows and `./mvnw` on non-Windows `pwsh`.
- Preserve `-Service all|order|payment` and `-MavenRepoLocal`.
- Add `-PrintCommandOnly` so CI/debug can inspect the expanded Maven reactor command without executing tests.
- Print the actual Maven executable, module selector, test selector, and command line.
- Add `.github/workflows/compensation-ops-audit.yml` with `workflow_dispatch`, `service` input, `ubuntu-latest`, and `pwsh`.
- Update `docs/compensation-ops-audit-search.md` with manual CI workflow usage, local PowerShell usage, raw Maven fallback, and non-PR-gate positioning.
- Update `.trellis/spec/backend/quality-guidelines.md` so the manual `workflow_dispatch` entry is a Good case while target test class execution remains mandatory.

## Acceptance Criteria

- [ ] Windows command path remains compatible with existing local acceptance.
- [ ] Ubuntu `pwsh` path uses `./mvnw`.
- [ ] `-PrintCommandOnly` prints the resolved command and exits successfully without running Maven.
- [ ] Manual GitHub workflow can run all/order/payment variants on demand.
- [ ] Documentation makes clear this is manual acceptance/on-demand regression, not a default PR gate.
- [ ] Backend quality spec records the executable manual CI pattern and target-test confirmation rule.

## Technical Notes

- Scope is scripting, GitHub Actions, documentation, and backend quality spec only.
- No Java source, API contract, database, Redis, or MQ behavior should change.
- The manual workflow should not add `pull_request`, `push`, or required-check semantics.
