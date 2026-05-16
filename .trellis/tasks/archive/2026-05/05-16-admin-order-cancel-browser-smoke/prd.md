# Admin Order Cancel Browser Smoke Coverage

## Task Classification

Complex Task.

This is a frontend-focused E2E smoke coverage task for a high-risk admin write path. It touches the admin order workspace behavior, gateway API contract assertions, ops auth headers, request id payload generation, duplicate-submit guarding, error envelope display, and post-write list/detail synchronization. It must be planned and handed off for implementation; Codex must not modify business implementation files in this planning round.

## Goal

Add real Chromium smoke coverage for admin order cancellation in the existing admin order/payment browser smoke suite.

The coverage must prove that an authorized order admin can cancel only a `created` order through the confirmation dialog, the frontend sends the expected gateway request with ops auth and a generated `requestId`, duplicate confirmation clicks do not create duplicate API calls, backend error envelopes preserve `code`, `message`, and `traceId`, and the admin order list/detail state remains correct after success or failure.

## Scope

In scope:

- Extend the existing admin order/payment Playwright smoke fixture and route mock for cancel success/failure.
- Add focused browser smoke tests in the current admin order smoke suite.
- Reuse the existing Vite + Playwright smoke infrastructure and ops session fixture.
- Keep the test hermetic; mock `/api/**` at the browser route layer.
- Validate existing frontend contract behavior without changing backend Java, DB, Redis, MQ, gateway routes, or live service dependencies.

Out of scope:

- Backend service implementation.
- Database migrations.
- Gateway route changes.
- New DTO field design beyond the existing `AdminCancelOrderRequest`.
- Visual redesign of the admin order workspace.
- Live backend or Docker-based smoke tests.
- Broad refactors of order management composables/components.

## API Contract Under Test

Route:

```text
POST /api/admin/orders/{orderId}/cancel
```

Auth:

```text
Authorization: Bearer <ops access token>
```

Auth context:

```text
ops
```

Payload:

```json
{
  "requestId": "generated-and-trimmed-client-request-id"
}
```

Expected success envelope:

```json
{
  "code": "ADMIN_ORDER_CANCELLED",
  "message": "OK",
  "data": {
    "orderId": 1001,
    "orderNo": "ADM-CRT-1001",
    "status": "cancelled",
    "paymentNo": null,
    "traceId": "trace-order-cancel-success"
  },
  "traceId": "trace-admin-cancel-success",
  "timestamp": "2026-05-16T08:00:00+08:00"
}
```

Expected failure envelope shape:

```json
{
  "code": "ORDER_STATUS_INVALID",
  "message": "Only created orders can be cancelled.",
  "data": null,
  "traceId": "trace-order-cancel-invalid",
  "timestamp": "2026-05-16T08:00:00+08:00"
}
```

## Validation And Error Matrix

| Case | Mock HTTP | Error Code | Required UI / Test Behavior |
| --- | --- | --- | --- |
| Missing ops session | N/A | N/A | Order workspace does not load and no admin API calls are made. Existing smoke may already cover this. |
| `OPS_COMPENSATION_ADMIN` only | N/A | N/A | Order workspace is hidden and no cancel API call is possible. Existing smoke may already cover this. |
| Created order cancel click | N/A before confirm | N/A | Click opens confirmation dialog and does not call cancel API before confirm. |
| Created order confirm success | 200 | `ADMIN_ORDER_CANCELLED` or success code | Sends one `POST /api/admin/orders/{orderId}/cancel` with `Authorization: Bearer mock-ops-jwt-token` and JSON body containing non-empty `requestId`; detail and active list row show `Cancelled`; dialog closes; cancel button becomes disabled. |
| Duplicate confirm while pending | Deferred 200 | N/A | Repeated confirm click while pending sends exactly one cancel request; button/dialog shows pending disabled state. |
| Paid order | N/A | N/A | Cancel button is disabled; no confirmation dialog; no cancel API call. |
| Shipped order | N/A | N/A | Cancel button is disabled; no confirmation dialog; no cancel API call. |
| Completed order | N/A | N/A | Cancel button is disabled; no confirmation dialog; no cancel API call. |
| Cancelled order | N/A | N/A | Cancel button is disabled; no confirmation dialog; no cancel API call. |
| Refunding/unknown order | N/A | N/A | Cancel button is disabled; raw status remains visible; no cancel API call. |
| Backend status rejection | 409 | `ORDER_STATUS_INVALID` | Show backend `message`, `code`, and `traceId`; preserve current detail/list snapshot; dialog remains available for retry or closes only if implementation already does so consistently. |
| Backend downstream timeout | 503 | `DOWNSTREAM_TIMEOUT` | Show backend `message`, `code`, and `traceId`; preserve current detail/list snapshot and status; allow later retry. |

## Good / Base / Bad Cases

Good:

- A created order displays the cancel action, opens a confirmation dialog, sends the exact admin cancel route only after confirmation, and includes a generated `requestId`.
- The cancel request uses the persisted ops session token as `Authorization: Bearer mock-ops-jwt-token`.
- Duplicate confirm clicks while the route is deferred produce one network request.
- Successful cancellation synchronizes detail and active list state to `cancelled` without assigning a payment response status into the order main status.
- Backend `ORDER_STATUS_INVALID` and `DOWNSTREAM_TIMEOUT` envelopes render `code`, `message`, and `traceId` and preserve the current order snapshot.

Base:

- The smoke suite may continue to use hermetic browser route mocks instead of live backend services.
- Existing component/model unit tests remain the primary coverage for lower-level helpers such as `buildAdminCancelOrderRequest` and `canCancelAdminOrder`; browser smoke should focus on end-to-end UI/network behavior.
- The route mock may be added to `frontend/e2e/admin-order-payment-smoke.spec.ts` and reuse `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts` instead of creating a new suite.

Bad:

- The cancel API is called before the confirmation dialog is accepted.
- Paid, shipped, completed, cancelled, or unknown-status orders can send a cancel request.
- Duplicate clicks send multiple cancel requests with different `requestId` values while the first request is pending.
- Error UI drops the backend `code`, `message`, or `traceId`.
- Cancel failure clears the selected detail or mutates the status to `cancelled`.
- The smoke test relies on live backend, DB, Redis, MQ, Nacos, payment service, or Docker.

## Expected Implementation Files

Likely files to modify:

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - Add cancel route mock state, request capture, deferred route handling, and browser smoke tests.
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`
  - Add or reuse created/paid/shipped/completed/cancelled/unknown order fixture helpers and a cancelled success helper if needed.

Only modify implementation files if DeepSeek discovers a real behavioral bug that prevents the PRD from passing:

- `frontend/src/views/admin/OrderManagementView.vue`
- `frontend/src/composables/useOrderManagement.ts`
- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/services/orderApi.ts`
- `frontend/src/types/api/order.ts`

Do not modify:

- Backend Java services.
- Database migrations.
- Redis/MQ contracts.
- Gateway routing/security config.
- Unrelated admin product, seckill, review, fulfillment, mall order, or payment implementation.
- Package manager metadata unless a new dependency is truly required. No new dependency is expected.

## Required Tests And Assertion Points

Focused smoke:

```powershell
cd frontend
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
```

Required assertion points:

- Created order cancel button is visible/enabled.
- First cancel click opens confirmation dialog.
- No `POST /api/admin/orders/{orderId}/cancel` occurs before confirm.
- Confirm sends `POST /api/admin/orders/{orderId}/cancel`.
- Request has `Authorization: Bearer mock-ops-jwt-token`.
- Request JSON has a non-empty string `requestId`.
- Duplicate confirm while pending sends one request.
- Paid, shipped, completed, cancelled, and unknown/refunding orders keep cancel disabled and send zero cancel requests.
- `ORDER_STATUS_INVALID` failure displays backend `message`, `code`, `traceId`, and preserves selected detail.
- `DOWNSTREAM_TIMEOUT` failure displays backend `message`, `code`, `traceId`, and preserves selected detail.
- Success changes current detail and active list row to `Cancelled` and disables further cancel.

Full frontend validation after implementation:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npm run test:smoke
cmd /c npm test
cmd /c npm run build
```

## Acceptance Criteria

- [ ] PRD and Trellis task context exist for implementation and check phases.
- [ ] Admin cancel browser smoke proves confirmation-before-request behavior.
- [ ] Admin cancel browser smoke proves route, auth header, and `requestId` payload.
- [ ] Admin cancel browser smoke proves duplicate pending confirm guard.
- [ ] Admin cancel browser smoke proves created-only cancel availability and non-created negative matrix.
- [ ] Admin cancel browser smoke proves `ORDER_STATUS_INVALID` and `DOWNSTREAM_TIMEOUT` error envelope preservation.
- [ ] Admin cancel browser smoke proves current detail/list snapshot preservation after failure.
- [ ] Admin cancel browser smoke proves list/detail status synchronization after success.
- [ ] Focused Playwright command passes.
- [ ] Full smoke, unit tests, typecheck, lint, and build pass before finish-work.

## Planning Notes For Handoff

- Existing unit tests already cover many lower-level cancel behaviors, including confirmation, request id, duplicate guard, failed retry, and successful cancelled status merge. The gap is real browser/network coverage.
- The current smoke fixture already covers ops session setup, auth header capture for admin API calls, list/detail/payment route mocks, and created/paid/shipped/completed/cancelled/unknown order fixtures.
- Add cancel-specific route state carefully so it does not disturb existing payment refresh tests.
- The smoke route handler should parse `request.postDataJSON()` or equivalent to assert the payload shape in the test, not in production code.
- If the implementation already passes all browser assertions with test-only changes, do not change business files.
