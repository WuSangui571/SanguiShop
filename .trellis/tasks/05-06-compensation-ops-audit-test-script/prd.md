# Compensation Ops Audit Test Script

## Goal

Provide a repo-local reusable command entry for compensation ops audit controller regression tests so manual verification and future CI wiring do not depend on copying a long Maven reactor command.

## Requirements

- Add `scripts/verify-compensation-ops-audit.ps1`.
- Default to running both order and payment compensation ops audit controller tests.
- Support running only order or only payment tests.
- Support a repo-local Maven cache override.
- Print clear expected test class names so humans can confirm Maven executed the intended tests.
- Update `docs/compensation-ops-audit-search.md` to recommend the script and retain the raw Maven command as troubleshooting fallback.
- Update `.trellis/spec/backend/quality-guidelines.md` so future compensation ops audit changes use the script as the Good case while preserving direct Maven reactor rules.
- Do not wire the script into mandatory CI in this task.

## Acceptance Criteria

- [x] `.\scripts\verify-compensation-ops-audit.ps1` runs both controller tests.
- [x] `.\scripts\verify-compensation-ops-audit.ps1 -Service order` runs only `InternalOrderCompensationControllerTest`.
- [x] `.\scripts\verify-compensation-ops-audit.ps1 -Service payment` runs only `InternalPaymentCompensationControllerTest`.
- [x] The script uses `.\mvnw.cmd`, explicit `-pl`, `-am`, and `-Dsurefire.failIfNoSpecifiedTests=false`.
- [x] Docs/spec describe the script as the recommended entry and keep direct Maven fallback rules.

## Technical Notes

- Use PowerShell because existing repo verification scripts are PowerShell.
- Use `MAVEN_USER_HOME` and default repo-local `.m2` behavior consistently with `scripts/verify.ps1`.
- Keep output human-readable and CI-friendly.
