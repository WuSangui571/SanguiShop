# Compensation Ops Audit Backend Test Runbook

## Goal

Solidify the verified Maven reactor command for compensation ops audit backend tests so future audit-log regression work does not fail because of incorrect module selection or missing reactor dependencies.

## Requirements

- Document the correct targeted backend test command for order and payment compensation controller audit tests.
- Explain why running the same `-Dtest` selector from the repository root can fail in `common` modules with `No tests matching pattern`.
- Explain why running only the target service module without `-am` can fail when local SNAPSHOT dependencies are not installed.
- Keep the guidance executable with Good/Base/Bad cases and explicit expected assertions.
- Update backend quality guidance so the lesson is part of future review and finish-work checks.

## Acceptance Criteria

- [ ] `docs/compensation-ops-audit-search.md` includes a backend regression test runbook with the verified Maven command.
- [ ] `.trellis/spec/backend/quality-guidelines.md` includes the Maven reactor rule and Good/Base/Bad cases for targeted service tests.
- [ ] The documented command covers both `InternalOrderCompensationControllerTest` and `InternalPaymentCompensationControllerTest`.
- [ ] The command uses the Maven wrapper and selects service modules with `-pl ... -am`.
- [ ] Quality check passes for documentation-only changes.

## Technical Notes

- This task changes docs/spec only; it does not alter Java, frontend, API, DB, MQ, or runtime contracts.
- The command must remain Windows-friendly because the observed failure happened in PowerShell/Maven usage.
