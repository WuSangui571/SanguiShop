# 修复管理端履约发货成功后主状态摘要回归

## Task Classification

Complex Task.

This is not a broad feature expansion. The goal is a scoped regression fix for the admin fulfillment shipping flow exposed by smoke testing: after a successful ship action, the admin fulfillment detail/list must show the shipped order lifecycle snapshot and must not regress the order main status to a payment-domain `paid` display.

## Current Project Status

- Previous journal entry records the mall receipt-confirmation smoke task as completed on `main`.
- That previous full smoke run reported one existing blocker outside the mall task boundary: `frontend/e2e/admin-order-payment-smoke.spec.ts`, test `successful ship does not overwrite order main status with payment status`.
- In this planning pass, the focused fulfillment regression command passed once:
  - `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "successful ship does not overwrite order main status with payment status"`
- In this planning pass, the whole admin order/payment smoke file failed at a different test:
  - `shows payment refresh loading state and guards duplicate clicks`
  - Failure signal: timeout waiting for `button:has-text("Refresh payment")`.
- Therefore the implementation pass must first reproduce the current failure mode before editing. Do not silently broaden this task to unrelated payment-refresh fixes unless the same root cause is proven or the user explicitly approves.

## Goal

Ensure admin fulfillment shipping success produces a stable shipped snapshot in both detail and list displays:

- Order main status summary displays shipped main lifecycle state.
- Fulfillment status summary displays shipped fulfillment state.
- Carrier, tracking number, and shipped time reflect the successful ship response.
- Payment-domain status or a payment refresh result must not overwrite the order main lifecycle status after shipping.

## API / Command / Payload Contract

No new backend API, DB schema, MQ, Redis, infra, storage, AI, or permission contract is intended.

Existing frontend admin fulfillment contract:

- Service function: `shipAdminFulfillment(orderId, payload)`
- Route: `POST /api/admin/fulfillments/{orderId}/ship`
- Auth context: `ops`
- Payload:
  - `requestId: string`, generated/preserved by frontend write action and trimmed before send.
  - `carrier: string`, user input trimmed before send.
  - `trackingNo: string`, user input trimmed before send.
- Successful response data type: `AdminFulfillmentResponse`
- Required response fields for this regression:
  - `orderId`
  - `orderNo`
  - `status`
  - `fulfillmentStatus`
  - `carrier`
  - `trackingNo`
  - `shippedAt`
  - `traceId`

Expected success snapshot for this task:

```json
{
  "status": "shipped",
  "fulfillmentStatus": "shipped",
  "carrier": "SF Express",
  "trackingNo": "SF123456789CN",
  "shippedAt": "2026-05-16T09:00:00+08:00"
}
```

## Validation / Error Matrix

| Case | Expected Behavior |
| --- | --- |
| No ops session | Fulfillment workspace does not call admin fulfillment APIs. |
| No fulfillment permission | Fulfillment workspace does not render or call admin fulfillment APIs. |
| Initial `status=paid`, `fulfillmentStatus=unshipped` | Ship form is enabled. |
| Initial `status=created`, `fulfillmentStatus=unshipped` | Ship form remains disabled and no ship API is called. |
| Initial shipped/completed/cancelled/unknown status | Ship form remains disabled and no ship API is called. |
| Successful ship | Detail and matching list item move to `status=shipped`, `fulfillmentStatus=shipped`; logistics fields render from response. |
| Successful ship followed by list/detail refresh | Refreshed data must not revert detail/list to `paid` / `unshipped` unless the mocked or backend response explicitly says so. |
| Payment-domain response with `status=paid` exists elsewhere in the smoke fixture | It must not overwrite fulfillment response `status=shipped`. |
| `ORDER_STATUS_INVALID` or `DOWNSTREAM_TIMEOUT` ship failure | Preserve prior paid/unshipped detail/list snapshot, keep draft where applicable, display backend `code/message/traceId`. |
| Duplicate ship click while pending | Send exactly one POST and show disabled/pending UI. |

## Good / Base / Bad Cases

- Good: after clicking ship and receiving `ADMIN_FULFILLMENT_SHIPPED`, `.summary-grid` contains `Shipped`, carrier, tracking number, and no stale `Paid` main-status text.
- Good: `.list-item.active` also shows the shipped fulfillment/order snapshot after the success merge or refresh.
- Good: existing payment refresh tests still prove payment status cannot overwrite shipped/completed/cancelled/unknown admin order main status.
- Base: if focused fulfillment passes but full smoke fails due ordering or mock leakage, fix the fixture/state isolation or waiting semantics inside the admin smoke boundary.
- Base: if a different payment-refresh smoke remains red and is unrelated, document it as a separate blocker instead of modifying payment logic under this task.
- Bad: implementation changes backend Java, DB migrations, gateway auth, Redis/MQ, Docker, dependencies, or API field names.
- Bad: shipping success only updates fulfillmentStatus while leaving order main status displayed as `Paid`.
- Bad: test passes by weakening/removing the assertion that `Paid` must not appear in the shipped summary.
- Bad: payment response `status=paid` is assigned into fulfillment or order main lifecycle state after shipping.

## Acceptance Criteria

- [ ] The current failure mode is reproduced or explicitly classified as non-reproducible with command output noted.
- [ ] Shipping success updates admin fulfillment detail to `status=shipped` and `fulfillmentStatus=shipped`.
- [ ] Shipping success updates the matching fulfillment list row to the same shipped snapshot.
- [ ] The regression test asserts order main status and fulfillment status separately, and verifies stale `Paid` / `Awaiting shipment` summary text is absent after the ship success settles.
- [ ] The fix does not broaden admin payment refresh behavior unless root cause proves shared mock/state merge logic.
- [ ] No backend Java, DB migration, Redis/MQ, gateway, infra, dependency, or unrelated frontend workspace changes are made.
- [ ] Focused fulfillment smoke passes.
- [ ] Full frontend smoke is run; any unrelated remaining failure is reported with exact test name and error.
- [ ] Frontend unit tests, typecheck, lint, and build pass before handing back to Codex check.

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Likely Files To Inspect Or Modify

- `frontend/src/composables/useFulfillmentManagement.ts`
- `frontend/src/views/admin/FulfillmentManagementView.vue`
- `frontend/src/views/admin/fulfillmentManagementModel.ts`
- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
- `frontend/src/views/admin/fulfillmentManagementModel.test.ts`
- `frontend/e2e/admin-order-payment-smoke.spec.ts`
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`
- `frontend/src/types/api/order.ts` only if existing fields/types are insufficient; avoid contract expansion if possible.

## Explicit Non-Goals

- Do not add new admin fulfillment API fields.
- Do not change backend services.
- Do not change payment service behavior.
- Do not alter mall order receipt/review flows.
- Do not remove or weaken existing smoke assertions.
- Do not update visual styling except where required for broken state rendering.
- Do not change permissions, auth storage, route names, or environment configuration.

## Required Tests

Run from `frontend/`:

```powershell
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "successful ship does not overwrite order main status with payment status"
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npm run test:smoke
cmd /c npx vitest run src/views/admin/fulfillmentManagementModel.test.ts src/views/admin/FulfillmentManagementView.spec.ts
cmd /c npm test
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm run build
```

Assertion points:

- Ship POST count is exactly one for a single submit.
- Payload includes nonblank `requestId`, trimmed `carrier`, and trimmed `trackingNo`.
- Detail summary shows shipped order main status and shipped fulfillment status.
- Active list item shows shipped status.
- Summary does not contain stale `Paid` or `Awaiting shipment` after ship success settles.
- Failure responses preserve `code/message/traceId` and do not optimistically show shipped state.

## Planning Notes For Implementer

- The focused regression passed during Codex planning, while the whole admin smoke file failed at a separate payment-refresh loading test. Treat this as a reproduction warning.
- First rerun the focused fulfillment test and the whole admin smoke file in your execution environment.
- If the failure reproduces only in full smoke, inspect shared module-level mock variables, route mocks, pending/deferred route reset, test ordering, and missing waits around async ship success.
- If the implementation is already correct and only the failing smoke assertion reads the DOM before the ship success settles, prefer strengthening the test wait/assertion semantics without weakening the business assertion.
- If a real implementation bug exists, prefer a scoped merge helper in the fulfillment composable/model that updates current detail and matching list row from the ship success response before/after list refresh, without assigning payment response status into order main status.
