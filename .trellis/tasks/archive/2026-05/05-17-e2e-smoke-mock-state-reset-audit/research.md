# Research: E2E Smoke Mock State Reset 一致性审计

## Relevant Specs

- `.trellis/spec/frontend/quality-guidelines.md`
  - Relevant for E2E testing DoD, duplicate-submit protection, polling cleanup, and the existing "E2E Deferred Route Lifecycle" rule.
  - Current rule already says intentional delayed Playwright `Route` objects must be managed with explicit `try/finally` cleanup and must not rely only on page teardown or `resetMockState()`.
- `.trellis/spec/frontend/api-contracts.md`
  - Relevant because both smoke files mock admin order/payment/fulfillment and mall order/payment/receipt API routes. Existing route paths, payload fields, traceId preservation, duplicate guards, and non-overwrite payment merge behavior must not change.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Relevant because this task explicitly forbids broad abstraction. Use small local fixes only when they address concrete reset-state inconsistency.
- `.trellis/spec/guides/index.md`
  - Relevant as shared guide index; no cross-layer guide is required because no API/DB/infra/storage/auth contract changes are in scope.

## Code Patterns Found

- Central per-file mock reset:
  - `frontend/e2e/admin-order-payment-smoke.spec.ts`: `resetMockState()` near the top resets request counters, auth header arrays, query captures, response mocks, error mocks, defer flags, pending route refs, and session refresh mock.
  - `frontend/e2e/mall-order-status-smoke.spec.ts`: `resetMockState()` near the top resets payment/order counters, auth header captures, order mocks, payment error, receipt payload captures, receipt error, defer flags, and pending route refs.
- Per-test setup:
  - Both files call `resetMockState()` in `test.beforeEach(...)`, then install route handlers with `setupDefaultApiRoutes(page)`, then set locale.
- Deferred route lifecycle:
  - Admin payment refresh, admin cancel, admin fulfillment ship, mall payment refresh, and mall receipt confirmation tests set a defer flag, assert pending UI and duplicate-click guard, fulfill the captured `Route`, null the pending ref, and abort/null it in `finally` if still pending.
- Exact count isolation:
  - Mall payment refresh captures `paymentCountBaseline` before enabling deferral so automatic future payment refreshes do not consume the deferred route intended for manual-click assertions.
  - Admin payment refresh currently waits for the automatic selected-order payment refresh to reach `adminPaymentStatusCallCount === 1` before setting `deferPaymentResponse = true`.
- Route payload capture:
  - Admin cancel and ship handlers push serialized payloads to arrays.
  - Mall receipt handler pushes parsed body, headers, and path to `receiptPayloads`.

## Mutable State Map

### `frontend/e2e/admin-order-payment-smoke.spec.ts`

Per-test mutable state covered by `resetMockState()`:

- Pending routes: `pendingPaymentRoute`, `pendingCancelRoute`, `pendingFulfillmentShipRoute`.
- Counters: `adminApiCallCount`, `adminPaymentStatusCallCount`, `cancelApiCallCount`, `shipApiCallCount`.
- Header/query/payload captures: `adminApiAuthHeaders`, `adminOrderListQueries`, `cancelRequestAuthHeaders`, `cancelRequestPayloads`, `shipApiAuthHeaders`, `shipApiPayloads`.
- Order/payment mocks: `mockOrderSummaries`, `mockOrderById`, `mockPaymentStatus`, `mockPaymentError`, `mockListError`, `mockDetailError`.
- Cancel mocks: `mockCancelSuccess`, `mockCancelError`, `deferCancelResponse`.
- Fulfillment mocks: `mockFulfillments`, `mockFulfillmentById`, `mockFulfillmentShipSuccess`, `mockFulfillmentShipError`, `mockFulfillmentListError`, `mockFulfillmentDetailError`, `deferFulfillmentShip`.
- Payment defer flag: `deferPaymentResponse`.
- Session mock: `mockSessionRefresh`.

Suite lifecycle state:

- `viteServer` is not per-test mock state. It is initialized in `beforeAll`, closed and nulled in `afterAll`.

Initial audit note:

- No obvious top-level mutable mock state was found outside `resetMockState()` except suite lifecycle state.
- Manual empty-list tests assign `mockOrderSummaries/mockOrderById` or `mockFulfillments/mockFulfillmentById` directly; these are still reset before each test.
- Deferred route tests follow the current frontend quality spec lifecycle pattern.

### `frontend/e2e/mall-order-status-smoke.spec.ts`

Per-test mutable state covered by `resetMockState()`:

- Pending routes: `pendingPaymentRoute`, `pendingReceiptRoute`.
- Counters: `paymentRequestCount`, `orderRouteRequestCount`, `receiptConfirmationRequestCount`.
- Header/payload captures: `protectedApiAuthHeaders`, `receiptPayloads`.
- Order/payment mocks: `mockOrders`, `mockOrderById`, `mockPaymentError`.
- Receipt mocks: `mockReceiptConfirmationError`.
- Defer flags: `deferPaymentResponse`, `deferReceiptConfirmationResponse`.

Suite lifecycle state:

- `viteServer` is initialized in `beforeAll`, closed and nulled in `afterAll`.

Initial audit note:

- No obvious top-level mutable mock state was found outside `resetMockState()` except suite lifecycle state.
- The non-shipped receipt test resets `receiptConfirmationRequestCount` inside a loop to keep each status case independent within one Playwright test.
- Mall payment refresh already uses a baseline before enabling deferral.
- Deferred route tests follow the current frontend quality spec lifecycle pattern.

## Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - Only if the final audit finds a definite reset omission, stale exact-count assumption, or misleading mutable-state name/comment.
- `frontend/e2e/mall-order-status-smoke.spec.ts`
  - Only if the final audit finds a definite reset omission, stale exact-count assumption, or misleading mutable-state name/comment.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Add a narrow E2E mock-state reset rule only if implementation confirms a stable convention worth preserving.

## Risk / Boundary Notes

- Do not touch `frontend/src/**`, backend code, DB migrations, infra, auth, Redis/MQ, or production API contracts.
- Do not change route paths, payload field names, response envelope shapes, error codes, or traceId assertions.
- Do not replace the two smoke files with a broad shared fixture abstraction.
- Keep existing duplicate-click and deferred route behavior intact.
- If a pending route is intentionally deferred, the success path should fulfill and null the route ref; `finally` should abort and null only if still pending.
- `resetMockState()` should initialize mock errors/responses, counters, arrays, defer flags, and pending refs to deterministic defaults before route setup.

## Required Tests

Focused tests:

- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate cancel confirm while pending sends exactly one request"`
- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "duplicate ship click while pending sends exactly one request and shows pending state"`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "shows payment refresh loading state"`
- `cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "duplicate pending receipt confirmation sends only one POST"`

Full required checks:

- `cmd /c npm run test:smoke`
- `cmd /c npm run lint`
- `cmd /c npm run typecheck`
- `cmd /c npm run build`

## Planning Self-Check

- Acceptance criteria are explicit in `prd.md`.
- Forbidden scope is explicit: no production frontend/backend/API/DB/infra/auth/storage changes.
- Expected modification files are limited to two smoke files plus optional frontend quality guideline.
- Required focused and full test commands are listed.
- Concrete guideline files were read: frontend quality, frontend API contracts, shared code reuse guide, and shared guide index.
- No user clarification is currently required because the user already defined the collaboration split and scope boundaries.
- No API / DB / frontend type / DTO field changes are expected; if discovered, implementation must stop for confirmation.
