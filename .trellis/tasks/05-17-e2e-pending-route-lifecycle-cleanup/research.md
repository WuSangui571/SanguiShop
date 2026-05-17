# Research: E2E Pending Route 生命周期清理规范化

## Relevant Specs

- `.trellis/spec/frontend/quality-guidelines.md`
  - Relevant because the task is a frontend quality/test hardening task.
  - Current guidance already requires typecheck/build, key interaction tests, duplicate-submit protection, and cleanup for polling-like asynchronous work.
  - If a durable deferred-route pattern is established, this is the target spec for adding an E2E deferred route cleanup rule.

- `.trellis/spec/frontend/api-contracts.md`
  - Relevant because the target smoke files validate admin order payment refresh, admin cancel, admin fulfillment ship, mall payment refresh, and mall receipt confirmation API behavior.
  - Admin order rules require payment refresh to use `GET /api/admin/payments/by-order/{orderId}`, preserve order main status, and guard duplicate actions.
  - Mall order rules require payment refresh to use `GET /api/payments/{paymentNo}`, receipt confirmation to use `POST /api/orders/{orderId}/receipt-confirmations`, preserve backend trace details, and guard duplicate pending actions.

- `.trellis/spec/frontend/hook-guidelines.md`
  - Relevant by analogy for explicit async lifecycle cleanup: timers/polling/SSE/WebSocket must be cleaned up on disposal.
  - The same lifecycle ownership principle should apply to intentionally pending Playwright routes in E2E tests.

- `.trellis/spec/frontend/component-guidelines.md`
  - Relevant because duplicate-click guards rely on visible pending/disabled UI state.
  - Current notes warn that pending flags must drive reactive UI state, which these smoke tests assert through "Refreshing", "Cancelling", "Shipping...", and "Confirming" button states.

- `.trellis/spec/frontend/type-safety.md`
  - Relevant if an E2E-only helper is added; helper types should use Playwright `Route` directly and avoid `any`.

- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Relevant because the task explicitly asks to judge local `try/finally` versus a small helper.
  - Guidance supports abstraction only when repeated patterns appear and the abstraction does not hide domain rules.

## Code Patterns Found

- Global mock state reset pattern:
  - `frontend/e2e/admin-order-payment-smoke.spec.ts` defines top-level deferred route refs (`pendingPaymentRoute`, `pendingCancelRoute`, `pendingFulfillmentShipRoute`) and clears them in `resetMockState()`.
  - `frontend/e2e/mall-order-status-smoke.spec.ts` defines top-level deferred route refs (`pendingPaymentRoute`, `pendingReceiptRoute`) and clears them in `resetMockState()`.

- Deferred route capture pattern:
  - Admin payment route handler captures `pendingPaymentRoute = route` when `deferPaymentResponse` is true, then returns without fulfilling.
  - Admin cancel route handler captures `pendingCancelRoute = route` when `deferCancelResponse` is true.
  - Admin fulfillment ship handler captures `pendingFulfillmentShipRoute = route` when `deferFulfillmentShip` is true.
  - Mall payment route handler captures `pendingPaymentRoute = route` when `deferPaymentResponse` is true.
  - Mall receipt confirmation handler captures `pendingReceiptRoute = route` when `deferReceiptConfirmationResponse` is true.

- Manual tail cleanup pattern to replace:
  - Admin payment smoke manually does:
    - assert pending route is non-null,
    - `await paymentRoute.fulfill(...)`,
    - `pendingPaymentRoute = null`,
    - then assert button returns to normal.
  - Admin cancel, admin ship, mall payment, and mall receipt use the same shape.
  - Current risk: assertions before manual fulfill can leave the route pending until page/test teardown.

- Route-specific assertion pattern to preserve:
  - Admin payment waits for automatic detail selection refresh count before enabling deferral, then asserts manual refresh does not duplicate.
  - Mall payment records `paymentCountBaseline` before enabling deferral to prove only the manual click consumes the deferred route.
  - Admin cancel, admin ship, and mall receipt assert the first request count and then dispatch a second click while pending to prove no duplicate request.

## Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - Add explicit cleanup around pending admin payment refresh, cancel confirm, and fulfillment ship deferred routes.
  - Likely implementation: local `try/finally` for each test, or a tiny helper used by all three pending-route scenarios.

- `frontend/e2e/mall-order-status-smoke.spec.ts`
  - Add explicit cleanup around pending mall payment refresh and receipt confirmation deferred routes.
  - Preserve previous payment refresh baseline/counter isolation.

- Optional E2E-only helper under `frontend/e2e/`
  - Only if the same lifecycle code is repeated enough to improve clarity.
  - Helper should not own route matcher, response body creation, counters, or business assertions.

- Optional `.trellis/spec/frontend/quality-guidelines.md`
  - Update only if implementation creates or formalizes a stable pattern.
  - Suggested rule: intentionally deferred Playwright routes must be owned by explicit `try/finally` cleanup; cleanup should clear references and release or abort still-pending routes without relying only on page teardown.

## Risk / Boundary Notes

- Do not change production code under `frontend/src/`.
- Do not change backend/API/DTO/DB/infra contracts.
- Do not weaken smoke tests by removing route-specific counters or duplicate guard assertions.
- Be careful not to double-fulfill a Playwright `Route`; cleanup likely needs a local `released` boolean or a helper that tracks release state.
- On assertion failure, aborting a still-pending route in `finally` is usually safer than fulfilling a success response after the scenario has already failed.
- If cleanup aborts a pending route during a passing path by mistake, the UI may remain in loading/error state and break the final assertion. Passing path should explicitly fulfill before final UI recovery assertion.
- Existing global `resetMockState()` still matters between tests, but it should not be the only cleanup owner for intentionally pending routes inside a test.

## Required Tests

Focused Playwright:

- From `frontend/`: `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- From `frontend/`: `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "shows payment refresh loading state"`

Additional focused tests covering all identified pending route sites:

- From `frontend/`: `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate cancel confirm while pending sends exactly one request"`
- From `frontend/`: `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate ship click while pending sends exactly one request and shows pending state"`
- From `frontend/`: `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "duplicate pending receipt confirmation sends only one POST"`

Broader frontend checks:

- From `frontend/`: `cmd /c npm run test:smoke`
- From `frontend/`: `cmd /c npm run lint`
- From `frontend/`: `cmd /c npm run typecheck`
- From `frontend/`: `cmd /c npm run build`
