# E2E Smoke Mock State Reset 一致性审计

## Goal

Audit and tighten mutable mock-state reset consistency in the two frontend Playwright smoke suites that recently accumulated deferred routes, request counters, payload captures, mock errors, response mocks, and pending route references:

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
- `frontend/e2e/mall-order-status-smoke.spec.ts`

The goal is to preserve the stability gains from recent route deferral and pending-route cleanup work, reducing hidden cross-test contamination when future admin or mall smoke cases are added.

## Scope Classification

Complex Task.

Rationale: the task spans two large E2E smoke files, their shared mutable test fixtures, reset conventions, and potentially the frontend quality spec. It requires a focused audit and implementation plan before code edits, but it must remain tightly scoped to tests and test-spec guidance.

## Requirements

- Audit all global mutable mock state in the two target smoke files.
- Verify that each `beforeEach` / `resetMockState()` path resets every mutable variable used by route handlers and assertions.
- Check route counters, request payload arrays, mock errors, response mocks, defer flags, and pending route refs for omissions, stale values, or naming inconsistencies.
- Fix only clearly identified reset omissions, misleading names, or inconsistent cleanup that can create cross-test contamination.
- Do not introduce a large helper abstraction or broad refactor.
- If the audit reveals a stable reusable rule, update `.trellis/spec/frontend/quality-guidelines.md` with an E2E mock state reset rule.
- Preserve existing deferred Playwright `Route` lifecycle rules: intentional pending routes must be fulfilled and nulled in the success path, and aborted plus nulled in `finally` cleanup.

## Non-Goals / Forbidden Scope

- Do not modify production frontend source files under `frontend/src`.
- Do not modify backend code, API DTOs, database migrations, Redis/MQ, infrastructure, auth, or storage contracts.
- Do not change public API routes, payload field names, validation behavior, or error semantics.
- Do not rewrite the smoke suites into a shared fixture system unless a small local cleanup is required to fix a concrete inconsistency.
- Do not broaden the task into new business test coverage beyond reset-state consistency.

## Contract / API Depth Check

This task is frontend E2E test maintenance only.

- API / command / payload fields: no new or changed application API, command, DTO, or payload contract is expected.
- Validation / error matrix: no application validation or error handling behavior should change.
- DB / storage / infra / AI / permissions: not in scope.
- Frontend types: no production type contract change expected.

If implementation discovers a need to change API fields, DTOs, backend behavior, persistent storage, permissions, or production frontend behavior, stop and request confirmation before expanding scope.

## Good / Base / Bad Cases

- Good: each route handler reads only state initialized by the current test or reset by `resetMockState()`.
- Good: every request counter and payload capture array starts from a deterministic baseline before assertions that depend on exact counts or payload contents.
- Good: each mock error or mock response override is reset to its default before the next test.
- Good: each defer flag and pending route reference is cleared after the test path, including assertion-failure paths.
- Base: small local naming or reset-order fixes are acceptable when they make existing behavior explicit.
- Base: a frontend quality guideline update is added only if the audit produces a reusable rule.
- Bad: a test passes only because a previous test left a mock response, error, counter, payload array, defer flag, or pending route reference in a favorable state.
- Bad: `resetMockState()` omits a newly added mutable global, making future tests order-dependent.
- Bad: implementation hides reset gaps behind a broad helper refactor without proving the concrete issue.

## Acceptance Criteria

- [ ] All mutable global mock state in `admin-order-payment-smoke.spec.ts` is enumerated and mapped to reset behavior.
- [ ] All mutable global mock state in `mall-order-status-smoke.spec.ts` is enumerated and mapped to reset behavior.
- [ ] Any definite missing reset, stale baseline, or misleading mutable-state naming is fixed within the two smoke files.
- [ ] No production implementation files are changed.
- [ ] `.trellis/spec/frontend/quality-guidelines.md` is updated only if a stable E2E reset rule is confirmed.
- [ ] Focused Playwright tests covering the touched smoke areas pass.
- [ ] `cmd /c npm run test:smoke` passes.
- [ ] `cmd /c npm run lint` passes.
- [ ] `cmd /c npm run typecheck` passes.
- [ ] `cmd /c npm run build` passes.

## Required Tests and Assertion Points

- Focused admin smoke tests around payment refresh, cancel pending guard, and ship pending guard.
- Focused mall smoke tests around payment refresh and receipt confirmation pending guard.
- Full smoke suite: `cmd /c npm run test:smoke`.
- Static checks: `cmd /c npm run lint`, `cmd /c npm run typecheck`, `cmd /c npm run build`.

Assertion points for implementation review:

- Exact request count assertions use a baseline when automatic refreshes can occur before manual actions.
- Payload capture arrays are empty at test start unless the test explicitly seeds them.
- Mock error variables and mock response variables return to default success/empty values in `resetMockState()`.
- Deferred route flags default to `false`.
- Pending route references default to `null` and are not relied on for cross-test cleanup.

## Handoff Notes

Codex prepares PRD, context, focused research, and test plan only. DeepSeek will perform implementation. Codex should later run check / finish-work after DeepSeek completes code edits.
