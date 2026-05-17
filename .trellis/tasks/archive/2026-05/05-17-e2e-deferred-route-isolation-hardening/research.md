# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/quality-guidelines.md`: frontend DoD requires e2e coverage for key interactions, duplicate-submit guards, polling/cleanup review, and no hardcoded backend/secrets.
- `.trellis/spec/frontend/api-contracts.md`: defines admin order, admin fulfillment, mall order/payment route contracts and required duplicate guard / trace preservation tests.
- `.trellis/spec/frontend/type-safety.md`: route mocks must preserve existing DTO field shapes and unknown fallback behavior; no `any` escape hatch for new helper APIs.
- `.trellis/spec/frontend/directory-structure.md`: if a helper is added, keep it test-scoped under `frontend/e2e` rather than production `src`.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: add an abstraction only if repeated deferred route lifecycle code appears at a real reuse boundary.

## Code Patterns Found

- `frontend/e2e/admin-order-payment-smoke.spec.ts`: top-level mutable mock state includes `pendingPaymentRoute`, `deferPaymentResponse`, `pendingCancelRoute`, `deferCancelResponse`, `pendingFulfillmentShipRoute`, and `deferFulfillmentShip`. `resetMockState()` clears these before each test.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: `setupDefaultApiRoutes()` matches all admin routes through one `page.route('**/api/**')`. It increments broad `adminApiCallCount` for list/detail/payment/cancel/fulfillment routes and dedicated counters for payment status, cancel, and ship.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: admin payment refresh already contains the previous fix pattern. It waits for `selectOrder()` automatic payment refresh via `expect.poll(() => adminPaymentStatusCallCount).toBe(1)` before enabling `deferPaymentResponse`, preventing auto-refresh from consuming the manual deferred route.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: cancel duplicate-pending test enables `deferCancelResponse` before navigation. This is probably safe because only the POST cancel route can capture `pendingCancelRoute`, but it should still be reviewed for explicit pending cleanup on failure and helper consistency.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: ship duplicate-pending test enables `deferFulfillmentShip` before navigation. It is route-specific to POST ship, but shares the same pending route lifecycle pattern.
- `frontend/e2e/mall-order-status-smoke.spec.ts`: top-level mutable mock state includes `pendingPaymentRoute`, `deferPaymentResponse`, `pendingReceiptRoute`, and `deferReceiptConfirmationResponse`; `resetMockState()` clears these before each test.
- `frontend/e2e/mall-order-status-smoke.spec.ts`: mall payment refresh duplicate-pending test enables `deferPaymentResponse` after the deep-linked detail is visible. It currently lacks a separate “baseline payment auto-refresh already happened” guard if future app behavior introduces an automatic payment refresh.
- `frontend/e2e/mall-order-status-smoke.spec.ts`: receipt duplicate-pending test uses dedicated `receiptConfirmationRequestCount` and `pendingReceiptRoute`; route is POST-only and less likely to collide with list/detail requests.

## Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`: consolidate or harden admin payment/cancel/ship deferred route lifecycle; consider helper only if it improves clarity.
- `frontend/e2e/mall-order-status-smoke.spec.ts`: harden mall payment/receipt deferred lifecycle and request counters; payment refresh is the highest-risk adjacent pattern.
- Optional `frontend/e2e/<test-helper>.ts`: only if duplicated lifecycle handling is clearer as a small test helper with explicit capture/complete/cleanup semantics.
- Optional `.trellis/spec/frontend/quality-guidelines.md`: add E2E deferred route guidance only if a stable pattern is introduced.

## Risk / Boundary Notes

- The risk is test flake from delayed route flags, not production behavior. Do not modify `frontend/src/**` to make tests pass.
- Broad counters like `adminApiCallCount` include list/detail/payment/cancel/fulfillment calls and should not be used as duplicate-pending proof for route-specific assertions.
- Deferred flags should be enabled as late as possible unless the route is write-only and cannot be triggered by page load.
- A pending `Route` object left unresolved can leak into later behavior or cause test timeout noise. Implementation should make cleanup explicit.
- Avoid sleeps/timeouts. Use route capture, `expect.poll`, visible loading state, disabled button state, and explicit route fulfillment.
- If adding a helper, it must not hide the route matcher intent or weaken route-specific request count assertions.

## Required Tests

Focused:

- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate cancel confirm while pending sends exactly one request"`
- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate ship click while pending sends exactly one request and shows pending state"`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "duplicate pending receipt confirmation sends only one POST"`

Related files:

- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium`

Full smoke / frontend:

- `cmd /c npm run test:smoke`
- `cmd /c npm run lint`
- `cmd /c npm run typecheck`
- `cmd /c npm run build`
