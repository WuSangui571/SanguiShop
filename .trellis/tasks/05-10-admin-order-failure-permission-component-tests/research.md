# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`
  - Contains Admin Order Management contract: `listAdminOrders`, `getAdminOrder`, `cancelAdminOrder`, `getAdminPaymentByOrderId`, ops auth context, required UI handling, permission boundary, query omission, datetime normalization, deep-link URL params, sessionStorage key, payment refresh merge, backend error preservation, and duplicate cancel guard.
- `.trellis/spec/frontend/component-guidelines.md`
  - Async UI must handle loading, success, empty, error, retry, and pending states.
- `.trellis/spec/frontend/hook-guidelines.md`
  - API work goes through `services/*Api.ts`; composables own loading/error/retry and cleanup behavior.
- `.trellis/spec/frontend/state-management.md`
  - Server facts such as order/payment status stay backend-owned; backend `code/message/traceId` remain raw business/debug data.
- `.trellis/spec/frontend/type-safety.md`
  - API fixtures should follow DTO types, money remains integer cents, time values are API strings, enum/status values require unknown fallback.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Core interactions need component tests; critical buttons need loading/disabled duplicate-submit behavior; errors must include trace IDs.
- `.trellis/spec/frontend/directory-structure.md`
  - Admin page tests belong beside page components under `frontend/src/views/admin`.
- `.trellis/spec/backend/microservice-contracts.md`
  - Confirms response envelope, `requestId` idempotency for writes, and external API error matrix.
- `.trellis/spec/backend/gateway-security.md`
  - Confirms admin routes require JWT/RBAC and unauthorized access maps to `AUTH_FORBIDDEN`.
- `.trellis/spec/backend/error-handling.md`
  - Confirms external responses return `traceId` and distinguish business/system errors.
- `.trellis/spec/backend/order-create-contracts.md`
  - Admin Order Management addendum defines list/detail/cancel fields, order permission requirement, nullable `paymentNo`, and cancel validation matrix.
- `.trellis/spec/backend/payment-pay-contracts.md`
  - Admin Payment Status addendum defines `GET /api/admin/payments/by-order/{orderId}`, `PAYMENT_NOT_FOUND`, and order-management permission requirement.
- `.trellis/spec/backend/quality-guidelines.md`
  - Useful for later Codex check if backend contracts are suspected, though this task should remain frontend-test-only.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - Relevant risk checklist for transaction-chain UI: retry, idempotency, frontend loading/error/retry states, and order/payment boundaries.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Encourages reusing existing component-test patterns rather than extracting new shared abstractions prematurely.

## Code Patterns Found

- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
  - Best current template for admin workspace component tests:
    - `// @vitest-environment happy-dom`
    - `mount` from `@vue/test-utils`
    - service-module mocks
    - `HttpClientError` for backend `code/message/traceId`
    - `flushPromises` plus `nextTick`
    - no-access prop gate
    - list failure / retry / empty tests
    - filter query / reset tests
    - write failure recovery and pending duplicate tests
    - deterministic `crypto.randomUUID` stub for request payload assertions.
- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Broader write-action pattern for backend error preservation, retry after rejection, pending guards, dialog/draft preservation, and avoiding untyped mocks.
- `frontend/src/App.spec.ts`
  - Existing shallow App permission pattern:
    - typed `PersistedOpsSession`
    - mocked `useOpsAuthSession`
    - mocked `useAppPreferences`
    - `window.history.replaceState` to choose `/admin?workspace=...`
    - wrapper teardown in `afterEach`
    - review and fulfillment workspace tests already cover allowed role/permission and deny `OPS_COMPENSATION_ADMIN` alone.
- `frontend/src/App.vue`
  - Defines `ORDER_MANAGEMENT_ADMIN_PERMISSION = 'ORDER_MANAGEMENT_ADMIN'`.
  - `canAccessOrderWorkspace` is true for `ADMIN` role or `ORDER_MANAGEMENT_ADMIN` permission.
  - `OrderManagementView` receives `session`, `canAccessOrderWorkspace`, and `initialOrderId`.
  - Admin query `workspace=order&orderId=...` is parsed and passed into order workspace.
- `frontend/src/views/admin/OrderManagementView.vue`
  - Props: `session`, `canAccessOrderWorkspace`, optional `initialOrderId`.
  - Uses `useOrderManagement(sessionRef, canAccessRef, { initialFilters, initialOrderId })`.
  - Initial filters are read from URL search, then sessionStorage, then defaults.
  - Watches `props.session` with `immediate: true` and calls `bootstrap()`.
  - Error banners render backend `message`, `code`, and optional `traceId`.
  - Empty banner appears only in `v-else-if` after `listError` and loading checks.
  - Cancel button opens `.confirm-backdrop` dialog; API call happens in `confirmCancelOrder()`, not on initial cancel button click.
- `frontend/src/composables/useOrderManagement.ts`
  - `bootstrap()` returns early when `!canAccessWorkspace.value || !session.value`.
  - `refreshList()` uses a list gate and calls `listAdminOrders(buildAdminOrderQuery(filters.value))`.
  - List failure sets `listError`, clears `items`, and sets `total=0`.
  - `selectOrder()` calls `getAdminOrder(orderId)` and then `refreshPaymentStatus(false)`.
  - `refreshPaymentStatus(false)` suppresses `PAYMENT_NOT_FOUND` during automatic detail load.
  - Manual `refreshPaymentStatus()` defaults to `showMissingError=true`.
  - Payment refresh success applies `applyAdminPaymentToDetail` and `applyAdminPaymentToSummaries`.
  - `cancelSelectedOrder()` uses `actionGate`, generates `requestId`, calls `cancelAdminOrder`, and clears action pending in `finally`.
- `frontend/src/views/admin/orderManagementModel.ts`
  - Existing pure helpers already cover:
    - default filters
    - `buildAdminOrderQuery` with `status=all` and blank filter omission
    - datetime-local normalization to `:00+08:00`
    - URL read/write helpers for `workspace=order`, `orderId`, filters, page, size
    - sessionStorage serialization with version guard
    - backend error conversion preserving `code/message/traceId`
    - payment snapshot merge into detail/list
    - duplicate submission gate
    - cancel request `requestId` trimming.
- `frontend/src/views/admin/orderManagementModel.test.ts`
  - Pure model tests already cover filter payload, pagination clamp, status fallback, deep-link parser, URL params, storage restore, timeline labels, payment merge, error preservation, duplicate gate, and cancel request. Component tests should prove DOM interactions and composable behavior, not duplicate only helper logic.
- `frontend/src/services/orderApi.ts`
  - `listAdminOrders`, `getAdminOrder`, and `cancelAdminOrder` use gateway `/api/admin/orders` routes with `authContext: 'ops'`.
- `frontend/src/services/paymentApi.ts`
  - `getAdminPaymentByOrderId` uses `/api/admin/payments/by-order/{orderId}` with `authContext: 'ops'`.
- `frontend/src/types/api/order.ts`
  - Relevant types: `AdminOrderQueryParams`, `AdminOrderSummaryResponse`, `AdminOrderDetailResponse`, `AdminCancelOrderRequest`, `AdminOrderPageResponse`, and `OrderStatus`.
- `frontend/src/types/api/payment.ts`
  - Relevant type: `PaymentResponse`, with `status: 'created' | 'paid' | 'failed' | string`.

## Files Likely To Modify

- `frontend/src/views/admin/OrderManagementView.spec.ts`
  - Likely new test file. Add component tests for prop/session gate, list failure/retry/empty, filter query/reset, deep link detail loading, cancel confirmation/failure/retry/duplicate guard, and payment refresh boundaries.
- `frontend/src/App.spec.ts`
  - Extend existing App permission tests with an order workspace describe block.

## Files To Read But Not Modify Unless Explicitly Authorized Later

- `frontend/src/views/admin/OrderManagementView.vue`
  - Use selectors and behavior ordering for tests.
- `frontend/src/composables/useOrderManagement.ts`
  - Understand access gates, list/action gates, payment refresh behavior, and failure recovery.
- `frontend/src/views/admin/orderManagementModel.ts`
  - Existing query/deep-link/error/payment helpers; should not be changed unless tests expose a real authorized implementation gap.
- `frontend/src/services/orderApi.ts`
  - Existing API client contract; no route or payload changes planned.
- `frontend/src/services/paymentApi.ts`
  - Existing admin payment status client; no route changes planned.
- `frontend/src/types/api/order.ts`
- `frontend/src/types/api/payment.ts`

## Risk / Boundary Notes

- The task is test-only for implementation. Codex must not edit production business code in this planning turn.
- If DeepSeek finds an implementation gap, it should keep changes minimal and only within the user's authorized task; if the gap implies API/permission/route contract changes, stop and ask.
- `OrderManagementView` bootstrap through an immediate watcher can trigger async list/detail/payment calls. Component tests should clear mocks after initial mount before asserting explicit search/reset/manual refresh behavior.
- Deep-link tests may involve both `App.spec.ts` and `OrderManagementView.spec.ts`:
  - App-level test can prove `workspace=order` is visible for allowed roles.
  - Component-level test can pass `initialOrderId` and prove `getAdminOrder` is called.
- `PAYMENT_NOT_FOUND` is special only for automatic detail load when `showMissingError=false`; manual refresh should still display backend error details by default.
- Cancel confirmation is part of the UI contract. Tests must prove initial cancel button click does not call `cancelAdminOrder`.
- Pending duplicate cancel should be tested with a controlled promise. Trigger confirm twice while pending and assert only one API call before resolving/rejecting.
- `cancelSelectedOrder()` refreshes payment/list only after success. Failure tests should not expect list refresh.
- Use deterministic `requestId` by stubbing `globalThis.crypto.randomUUID`, matching fulfillment/review test style.
- Typed fixtures must include required DTO fields; do not use `any` to bypass type checks.
- Avoid over-asserting exact translated copy. Existing preference mock returns keys, so stable assertions can use keys such as `orderAdmin.listEmpty`, raw order numbers, raw backend errors, and CSS selectors.
- `sessionStorage` and URL search can leak across tests; clear `window.sessionStorage` and use `window.history.replaceState` in setup/teardown where URL behavior is tested.

## Required Tests

Targeted:

```powershell
cd frontend; cmd /c npm run test -- orderManagement
cd frontend; cmd /c npm run test -- App
```

Quality:

```powershell
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

Optional broader regression:

```powershell
cd frontend; cmd /c npm run test
```

## Suggested Component Test Structure

- New `OrderManagementView.spec.ts`:
  - Mock `../../services/orderApi` with `listAdminOrders`, `getAdminOrder`, `cancelAdminOrder`.
  - Mock `../../services/paymentApi` with `getAdminPaymentByOrderId`.
  - Mock `useAppPreferences` with `t: (key, params?) => key` or existing minimal key renderer.
  - Define `mockSession: PersistedOpsSession`.
  - Define `mockMeta: ApiResponseMeta`.
  - Define `createOrderSummary(patch = {})`.
  - Define `createOrderDetail(patch = {})`.
  - Define `createPayment(patch = {})`.
  - Define `flushPromises()`.
  - Define `createControlledApiResponse()`.
  - Define `mountView({ session, canAccessOrderWorkspace, initialOrderId } = {})`.
  - In `afterEach`: unmount wrapper, clear mocks, restore crypto mocks, clear sessionStorage, reset URL.
- Extend `App.spec.ts`:
  - Add `describe('App order workspace permission gating', ...)`.
  - Use `/admin?workspace=order`.
  - Reuse `adminSession()`.
  - Assert `admin.orderWorkspace` appears for `ADMIN` and `ORDER_MANAGEMENT_ADMIN`, and does not appear for `OPS_COMPENSATION_ADMIN` alone.

## Expected Assertions By Area

- Permission:
  - no-access component gate: `listAdminOrders` not called.
  - missing session: `listAdminOrders` not called.
  - App `ADMIN`: text contains `admin.orderWorkspace`.
  - App `ORDER_MANAGEMENT_ADMIN`: text contains `admin.orderWorkspace`.
  - App `OPS_COMPENSATION_ADMIN`: text does not contain `admin.orderWorkspace`.
- List failure/retry/empty:
  - failure banner includes backend message, code, traceId.
  - failure state has no `section.banner.empty`.
  - retry button in `.banner.error` increments list API call count and clears error on success.
  - success with `items: []` renders `.banner.empty` and no error banner.
- Filter query:
  - after clearing mount calls, set status to `all`, blank order/user filters, datetime values, click search, and assert call args omit optional blanks/status and include normalized `fromTime`/`toTime`.
  - reset after dirty inputs calls default query `{ page: 1, size: 20 }`.
- Deep link:
  - Mount with `initialOrderId=101`; mock list success and detail success; assert `getAdminOrder(101)` and detail order number/reservation/payment fields render.
- Cancel:
  - Click cancel button; assert confirmation dialog appears and `cancelAdminOrder` has not been called.
  - Confirm failure with `HttpClientError`; assert action banner contains backend message/code/traceId and confirm button is not pending after settle.
  - Trigger confirm again after failure; assert second `cancelAdminOrder` call.
  - Controlled pending promise: trigger confirm twice while pending; assert one API call.
- Payment refresh:
  - Automatic detail load with `PAYMENT_NOT_FOUND` from `getAdminPaymentByOrderId` should not render a payment error banner.
  - Manual refresh failure after a loaded detail should display backend error and keep existing order status/orderNo/payment display.
  - Manual refresh success returns paid `PaymentResponse`; assert matching detail and list item display `PAY-...` and paid label/key.
