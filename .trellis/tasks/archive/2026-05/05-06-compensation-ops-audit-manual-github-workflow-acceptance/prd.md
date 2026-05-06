# Compensation Ops Audit Manual GitHub Workflow Acceptance

## Goal

Verify the existing manual GitHub Actions workflow for compensation ops audit tests on the real `ubuntu-latest` runner using `pwsh` and `./mvnw`, then document the run result. Fix only CI compatibility issues found during the real run.

## Scope

- Trigger `.github/workflows/compensation-ops-audit.yml` manually through `workflow_dispatch`.
- Run `service=all` first.
- If needed for diagnosis, run `service=order` and/or `service=payment`.
- Confirm Linux runner behavior, especially `pwsh`, `chmod +x ./mvnw`, and `./mvnw` execution.
- Inspect GitHub Actions logs for the expected script transparency lines and Maven test execution.
- Record the GitHub run URL and acceptance result in the workspace journal after verification.
- Update runbook/spec only if the real run reveals missing guidance, unclear logs, or CI-only fixes.

## Out of Scope

- No new business feature work.
- No controller, service, database, MQ, or API contract changes unless a test exposes an existing build/test wiring issue.
- No broad CI redesign beyond making this workflow runnable and auditable.

## Acceptance Criteria

- [ ] GitHub Actions workflow is manually triggered for `service=all`.
- [ ] Logs show `Maven executable: ./mvnw`.
- [ ] Logs show the module selector expected for the selected service input.
- [ ] Logs show the test selector expected for the selected service input.
- [ ] Maven output confirms target controller test class execution:
  - `InternalOrderCompensationControllerTest` for order coverage.
  - `InternalPaymentCompensationControllerTest` for payment coverage.
- [ ] If `service=all` fails, diagnosis identifies whether the failure is workflow compatibility, wrapper execution/download/cache, Java 21 setup, Maven cache/repo, PowerShell path handling, or a real test failure.
- [ ] Any CI-only fix is verified by a follow-up GitHub run.
- [ ] Final session record includes the GitHub run URL and whether `service=all` passed.

## Technical Notes

- Primary files likely involved:
  - `.github/workflows/compensation-ops-audit.yml`
  - `scripts/verify-compensation-ops-audit.ps1`
  - `docs/compensation-ops-audit-search.md`
  - `.trellis/spec/backend/quality-guidelines.md`
- Relevant commands:
  - GitHub UI: Actions -> Compensation Ops Audit -> Run workflow -> `service=all`
  - Local dry check: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-compensation-ops-audit.ps1 -PrintCommandOnly`
  - Linux expected executable: `./mvnw`

## Plan

1. Read relevant Trellis backend and guide specs before implementation.
2. Inspect current workflow, script, and runbook to confirm intended assertions.
3. Check whether GitHub CLI is available and authenticated for triggering/listing workflow runs.
4. If local tooling can trigger the workflow, push as needed and run `service=all`; otherwise provide exact manual trigger instructions and wait for the run URL/log result.
5. If the run fails due to CI compatibility, patch the workflow/script/runbook/spec minimally and re-run the targeted verification path.
6. Run `$check` quality review against changed files.
7. Run `$finish-work` checklist and provide exact test and git sync commands.
