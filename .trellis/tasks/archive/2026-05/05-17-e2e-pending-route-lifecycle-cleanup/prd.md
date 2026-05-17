# E2E Pending Route 生命周期清理规范化

## Goal

Normalize pending Playwright route lifecycle handling in the frontend E2E smoke tests for admin order payment and mall order status flows.

The previous deferred route isolation work removed a known mall payment refresh mis-consumption risk, but several smoke tests still keep pending route state alive until the end of a test and then manually `fulfill` plus reset variables. If an assertion fails before that manual cleanup, pending route cleanup depends mostly on page teardown rather than explicit test code ownership.

This task should make each intentionally deferred route have an explicit lifecycle: create, observe, release or abort in `finally`, and clear references without hiding the route-specific matcher, counter, and duplicate guard assertions that make these smoke tests readable.

## Scope

In scope:

- Audit pending route lifecycle in:
  - `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - `frontend/e2e/mall-order-status-smoke.spec.ts`
- Replace fragile "test tail manual fulfill + set null" patterns with one of:
  - local `try/finally` blocks around the assertion region that owns the pending route, or
  - a small E2E-only helper if the same lifecycle pattern appears enough times to reduce duplication without obscuring route-specific assertions.
- Preserve existing route matchers, counters, and duplicate guard expectations as first-class readable assertions.
- Preserve existing payment refresh deferred-route isolation behavior from the previous task.
- If a stable reusable pattern is created, update `.trellis/spec/frontend/quality-guidelines.md` with an E2E deferred route cleanup rule.

Out of scope:

- No production frontend implementation changes.
- No backend changes.
- No API, DTO, route contract, validation, error-code, DB, Redis, MQ, auth, storage, infra, deployment, or AI changes.
- No broad E2E refactor unrelated to pending route lifecycle cleanup.
- No conversion of smoke tests into a generic route DSL that hides scenario-specific intent.
- No weakening or removal of duplicate-click guard assertions, route-specific counters, trace preservation checks, or status non-overwrite checks.

## Task Classification

Complex Task.

Reasons:

- Multiple E2E files and multiple deferred route sites are involved.
- The implementation requires judgment between local `try/finally` and a small helper.
- The task may update frontend quality guidelines if a durable test pattern is established.
- The desired behavior is mostly test lifecycle semantics, so readability and assertion preservation are as important as passing tests.

## Contract Impact

No application contract changes are expected.

API / command / payload fields:

- No API routes are added or changed.
- No request or response payload fields are added or changed.
- No frontend API types or DTOs are added or changed.
- Existing Playwright commands remain the project npm scripts listed in Required Tests.

Validation / error matrix:

| Case | Expected behavior |
| --- | --- |
| Deferred route is observed and test assertions pass | The test explicitly releases the pending route with the intended mocked response and clears local pending state. |
| Assertion fails while route remains pending | `finally` cleanup releases or aborts the route and clears local references before test teardown. |
| Deferred route was already released by the test path | Cleanup is idempotent and does not double-fulfill a Playwright route. |
| Deferred route was never captured | Cleanup is a no-op and the test failure remains focused on the missing route/counter assertion. |
| Duplicate click occurs while first request is pending | Existing counter assertions still prove no second request is sent. |
| Automatic refresh occurs before manual deferred route is enabled | Existing route-specific counter baseline and matcher isolation remain intact. |

Good / Base / Bad cases:

- Good: each intentionally pending route is scoped by explicit cleanup, and route-specific assertions remain easy to read near the scenario they validate.
- Good: a helper is introduced only if it removes repeated lifecycle boilerplate while keeping matcher, counter, response body, and duplicate guard assertions local.
- Base: local `try/finally` is acceptable when only one or two sites use a pattern.
- Bad: a failed test leaves an intentionally pending route solely for page close or test teardown to clean up.
- Bad: a helper abstracts away which route is pending, which response is fulfilled, or which counter proves duplicate-guard behavior.
- Bad: payment response `status` is allowed to overwrite order main lifecycle status in either admin or mall smoke coverage.

## Acceptance Criteria

- [ ] All intentional pending route sites in the two target E2E files are identified.
- [ ] Each intentional pending route has explicit cleanup through local `try/finally` or a narrowly scoped E2E-only helper.
- [ ] Cleanup safely handles the route being absent, already fulfilled, or needing abort on failure.
- [ ] Existing route-specific matchers and counters remain readable in the smoke files.
- [ ] Existing duplicate guard assertions remain present and meaningful.
- [ ] Previous deferred route isolation behavior for mall payment refresh remains covered.
- [ ] No production source files are changed unless a spec update is justified; implementation changes should stay in E2E test files and optional E2E helper files.
- [ ] `.trellis/spec/frontend/quality-guidelines.md` is updated only if the implementation establishes a stable deferred-route lifecycle pattern.
- [ ] Required focused and suite-level frontend checks pass, or any inability to run them is recorded with the reason.

## Expected Files

Likely implementation files:

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
- `frontend/e2e/mall-order-status-smoke.spec.ts`

Possible helper file if justified by repeated pattern:

- An E2E-only helper under `frontend/e2e/` or an existing local E2E support/helper location if one already exists.

Possible spec file if a stable pattern is established:

- `.trellis/spec/frontend/quality-guidelines.md`

Do not modify production app files under `frontend/src/` for this task unless research discovers that the pending-route lifecycle problem is not actually test-owned. If that happens, stop and ask for confirmation before expanding scope.

## Required Tests

Focused checks:

- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "shows payment refresh loading state"`

Broader required frontend checks:

- `cmd /c npm run test:smoke`
- `cmd /c npm run lint`
- `cmd /c npm run typecheck`
- `cmd /c npm run build`

Additional focused Playwright tests should be run if research identifies other pending-route scenarios in the two target files.

## Notes for Implementation Agent

- Keep route lifecycle ownership close to the scenario assertion block.
- Prefer `try/finally` when it is clearer than a helper.
- If a helper is added, it must be E2E-only, small, typed, and must not own route matcher or business assertion semantics.
- Be careful with Playwright `Route`: do not attempt to fulfill an already fulfilled route. Track release state locally if needed.
- On cleanup after failure, aborting a still-pending route can be safer than fulfilling a success response that allows later UI work to continue after the primary assertion already failed.
- Do not change API fixtures or response semantics except where needed to move existing cleanup into `finally`.
