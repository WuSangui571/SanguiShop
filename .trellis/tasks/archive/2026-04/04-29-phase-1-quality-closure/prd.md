# Phase 1 Quality Closure

## Goal

Add the first stable quality loop for the SanguiShop phase 1 scaffold so later business modules can rely on repeatable tests, build verification, CI entrypoints, Docker Compose validation, and documented configuration conventions.

## Requirements

- Add minimal unit tests for common contracts:
  - `ApiResult` JSON envelope fields: `code`, `message`, `data`, `traceId`, `timestamp`.
  - `RedisKeyBuilder` key shape: `sangui:{env}:{service}:{domain}:{identifier}`.
  - `EventEnvelope` JSON fields matching the MQ event envelope contract.
  - `CommonErrorCode` baseline error codes.
- Add lightweight smoke tests for Gateway and one business service that verify startup classes exist and Spring context does not fail from basic configuration.
- Add CI/verification entrypoints:
  - `.github/workflows/ci.yml`
  - `scripts/verify.ps1`
  - Maven test and package commands should use the project Maven Wrapper.
- Add Docker Compose configuration validation using:
  - `docker compose -f deploy/docker-compose.yml config`
- Document service configuration conventions and phase 1 build/verify contract.
- Add a lightweight Maven Enforcer rule to pin Java 21 and Maven 3.9.9+ without introducing heavy Checkstyle/PMD.

## Acceptance Criteria

- [ ] Common contract tests cover Good/Base/Bad-oriented assertions where relevant.
- [ ] Gateway and user service smoke tests are present and scoped to basic context/startup validation.
- [ ] CI runs Maven tests, Maven package, and Docker Compose config validation.
- [ ] `scripts/verify.ps1` runs the same local verification sequence.
- [ ] Backend DevOps/Quality specs document the executable verify commands and Enforcer expectations.
- [ ] No secrets or direct secret values are added.

## Technical Notes

- This task changes build/test/CI infrastructure and code-spec documentation. It does not introduce new API endpoints, database migrations, Redis keys beyond contract tests, or MQ runtime consumers.
- Use TDD for new testable behavior: add tests first, observe the expected failure where possible, then add the minimal build/test support needed for green runs.
