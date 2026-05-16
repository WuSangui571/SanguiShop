# 管理端订单/支付状态真实浏览器冒烟覆盖

## Goal

Add real Chromium smoke coverage for the admin order management workspace so regressions in order main status display, payment status refresh, ops session/auth, backend error envelopes, and deep-link restore are caught through the actual Vite/Vue/Playwright browser path.

## Classification

Complex Task.

Rationale: this touches the browser runtime, admin session bootstrap, workspace permissions, URL query restore, gateway-style mock envelopes, order/payment API contracts, and smoke command scope. Implementation should be planned first and executed separately.

## Current Project Status

Previous completed work added customer-side mall order status browser smoke coverage:

- `frontend/e2e/mall-order-status-smoke.spec.ts` starts a Vite dev server through the Vite API and runs Playwright Chromium tests.
- `frontend/package.json` already has `test:smoke` as `playwright test --project=chromium`.
- Existing mall smoke covers login/session, order list/detail, lifecycle rendering, payment refresh, traceId errors, deep link, reload restore, and protected API auth headers.
- This task extends the same browser-level regression approach to the admin order workspace.

## Scope

In scope:

- Add admin order/payment smoke coverage using Playwright Chromium.
- Reuse the existing Vite/Playwright smoke infrastructure and gateway-envelope mocking style.
- Seed or mock an ops/admin session with `ADMIN` role or `ORDER_MANAGEMENT_ADMIN` permission.
- Exercise the real `/admin?workspace=order` UI path.
- Verify list loading, detail selection, deep link restore, payment refresh, terminal/unknown status preservation, and error display.
- Keep the smoke tests hermetic: no live backend, DB, Redis, MQ, Nacos, or payment service.

Out of scope / forbidden:

- No backend Java changes.
- No database migration, Redis/MQ, Docker, CI, Nacos, or infra changes.
- No new API routes or DTO field changes unless a test compile failure proves the existing typed frontend contract is incomplete.
- No implementation changes to order/payment business logic unless the smoke test exposes a real defect and the user explicitly authorizes expanding scope.
- No real secrets, real JWTs, real payment credentials, or live service calls.
- Do not weaken existing unit/component tests or remove existing assertions.

## API / Command / Payload Contract

### Commands

Required smoke command behavior:

- Keep `npm run test:smoke` working from `frontend/`.
- Preferred implementation: include the new admin smoke spec under `frontend/e2e/` so the existing command runs both mall and admin smoke specs.
- Optional only if runtime becomes too high: add `test:smoke:admin` while keeping `test:smoke` as the broad smoke suite.

Expected commands after implementation:

```powershell
cd frontend
cmd /c npm run test:smoke
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

### Admin Session

Use the existing ops session storage and `httpClient` auth context rules. The browser smoke should either:

- Seed `sessionStorage` before navigation with the existing ops session key and a valid future `expiresAt`, or
- Mock the ops login/session bootstrap route if that is the established app path.

Session shape must match `PersistedOpsSession`:

```json
{
  "userId": 9001,
  "shopId": 1,
  "username": "ops-order-admin",
  "accessToken": "mock-ops-jwt-token",
  "tokenType": "Bearer",
  "expiresAt": "2099-12-31T23:59:59+08:00",
  "roles": [],
  "permissions": ["ORDER_MANAGEMENT_ADMIN"]
}
```

Also include a negative permission scenario with `permissions: ["OPS_COMPENSATION_ADMIN"]` and no `ADMIN` role. That session must not show the order workspace and must not call admin order/payment APIs.

### Gateway Mock Envelope

All mocked API responses must use the shared `ApiResult<T>` envelope shape:

```json
{
  "code": "ADMIN_ORDER_LIST",
  "message": "OK",
  "data": {},
  "traceId": "trace-admin-smoke",
  "timestamp": "2026-05-16T08:00:00+08:00"
}
```

Mocked backend errors must preserve:

- `code`
- `message`
- `traceId`
- HTTP status

### Admin Order Routes

Use existing frontend service contracts:

| Function | Route | Method | Auth Context |
| --- | --- | --- | --- |
| `listAdminOrders` | `/api/admin/orders` | `GET` | `ops` |
| `getAdminOrder` | `/api/admin/orders/{orderId}` | `GET` | `ops` |
| `getAdminPaymentByOrderId` | `/api/admin/payments/by-order/{orderId}` | `GET` | `ops` |

List query payload assertions:

- `status=all` must be omitted.
- Blank `orderNo` and `userId` must be omitted.
- `page` and `size` must be present and normalized.
- If filters are exercised, datetime-local values must be normalized to ISO-8601 values.

Order list item fields:

- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `status`
- `totalAmountCent`
- nullable `paymentNo`
- `itemCount`
- nullable `traceId`
- nullable `createdAt`
- nullable `updatedAt`

Order detail fields:

- base order fields from `AdminOrderDetailResponse`
- `reservationNo`
- nullable `paymentNo`
- `items[]`
- `statusTimeline[]`

Payment response fields:

- `paymentId`
- `paymentNo`
- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `channel`
- `status`
- `amountCent`

## Requirements

- The browser smoke must open `/admin?workspace=order` and render the real admin order workspace for an authorized ops session.
- The smoke must verify protected admin order/payment API calls include `Authorization: Bearer mock-ops-jwt-token`.
- The smoke must verify no protected admin order/payment API call happens before a valid ops/admin session is available.
- The smoke must load an admin order list and select order detail through real DOM interaction.
- The smoke must cover visible main order status labels for:
  - `created`
  - `paid`
  - `shipped`
  - `completed`
  - `cancelled`
  - an unknown raw status such as `refunding`
- The smoke must verify timeline nodes preserve known statuses and unknown statuses without dropping a node.
- Payment refresh must update/display payment-specific fields such as `paymentNo` while preserving order main `status`.
- Payment refresh with payment `status=paid` must not overwrite terminal order main statuses:
  - `shipped`
  - `completed`
  - `cancelled`
- Payment refresh with payment `status=paid` must not overwrite unknown order main status such as `refunding`.
- Automatic detail payment load may suppress `PAYMENT_NOT_FOUND`, but an explicit operator refresh must display `PAYMENT_NOT_FOUND`, backend `message`, and `traceId`.
- Backend error display must preserve arbitrary backend `code/message/traceId`, for example `DOWNSTREAM_TIMEOUT` or `PAYMENT_REFRESH_FAILED`.
- Deep link `/admin?workspace=order&orderId=...` must restore detail without requiring a list click.
- Reloading the same deep link must keep detail restoration working.
- A session with only `OPS_COMPENSATION_ADMIN` must not render the order workspace and must not call admin order/payment APIs.
- The new smoke tests must not introduce live external dependencies.

## Acceptance Criteria

- [ ] `frontend/e2e/admin-order-payment-smoke.spec.ts` or equivalent exists and runs in Chromium through Playwright.
- [ ] `npm run test:smoke` includes admin order/payment smoke coverage, or a documented `test:smoke:admin` is added and `test:smoke` remains the broad smoke command.
- [ ] The authorized ops session path reaches `/admin?workspace=order`, renders the order workspace, and loads list data.
- [ ] Unauthorized `OPS_COMPENSATION_ADMIN`-only session cannot access the order workspace and makes no admin order/payment API calls.
- [ ] List and detail display assertions cover `created`, `paid`, `shipped`, `completed`, `cancelled`, and unknown raw status.
- [ ] Payment refresh assertions prove returned payment status does not overwrite terminal or unknown order main status.
- [ ] `PAYMENT_NOT_FOUND` explicit refresh displays backend `code/message/traceId`.
- [ ] Generic backend payment/list/detail error envelope displays backend `code/message/traceId`.
- [ ] Deep link `/admin?workspace=order&orderId=...` restores detail and still works after reload.
- [ ] Protected admin API requests carry the seeded ops `Authorization` header.
- [ ] The smoke suite is hermetic and starts/closes its Vite server without leaving local artifacts.

## Validation / Error Matrix

| Case | Mock Response | Expected Browser Behavior |
| --- | --- | --- |
| Missing ops session | No admin API mocks should be hit | Login/boot/forbidden surface, no order workspace API calls. |
| `OPS_COMPENSATION_ADMIN` only | No admin order/payment API mocks should be hit | Order workspace tab/view is unavailable. |
| Authorized `ORDER_MANAGEMENT_ADMIN` | 200 `ADMIN_ORDER_LIST` | Order workspace list renders and protected requests carry Bearer token. |
| Empty list | 200 `ADMIN_ORDER_LIST` with `items=[]` | Empty state appears, no crash. |
| Detail selection | 200 `ADMIN_ORDER_DETAIL` | Detail facts, items, and timeline render. |
| Detail deep link | 200 `ADMIN_ORDER_DETAIL` for URL `orderId` | Detail renders without list click and survives reload. |
| Automatic detail payment missing | 404 `PAYMENT_NOT_FOUND` | Error may be suppressed during automatic detail load; order detail remains visible. |
| Explicit payment refresh missing | 404 `PAYMENT_NOT_FOUND` | Backend code/message/traceId visible; order detail remains unchanged. |
| Payment backend failure | 503 `DOWNSTREAM_TIMEOUT` or equivalent | Backend code/message/traceId visible; order detail remains unchanged. |
| Payment refresh for `created` | 200 `ADMIN_PAYMENT_STATUS` with `status=paid` | Payment fields display; order main status should not be derived blindly from payment status in admin smoke. |
| Payment refresh for `shipped` | 200 payment `status=paid` | Main status remains `shipped`; not shown as `paid`. |
| Payment refresh for `completed` | 200 payment `status=paid` | Main status remains `completed`; not shown as `paid`. |
| Payment refresh for `cancelled` | 200 payment `status=paid` | Main status remains `cancelled`; not shown as `paid`. |
| Payment refresh for unknown order status | 200 payment `status=paid` | Main status remains raw unknown status, e.g. `refunding`. |

## Good / Base / Bad Cases

Good:

- Admin with `ORDER_MANAGEMENT_ADMIN` can load the order workspace, list, detail, timeline, and payment status in a real browser.
- Protected admin API calls include the ops Bearer token from persisted session.
- Terminal and unknown order main statuses survive payment refresh without regression to payment-domain `paid`.
- Backend error envelopes preserve `code/message/traceId` in visible UI.
- Deep-linked admin order detail restores from URL and after reload.

Base:

- Smoke tests mock gateway envelopes locally and do not require live backend services.
- Existing model/component tests remain the deeper branch coverage for filters, request trimming, cancel duplicate guards, and URL serialization.
- It is acceptable for automatic detail payment lookup to suppress `PAYMENT_NOT_FOUND`; explicit refresh must display it.

Bad:

- Smoke test uses live backend, real credentials, or real JWT.
- Payment response `status` overwrites admin order main `status`.
- Unknown backend order status crashes the page or disappears from timeline.
- `OPS_COMPENSATION_ADMIN` alone can open order management.
- API errors display only generic text and drop `traceId`.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`: Admin Order Management APIs, Admin Payment Status behavior, error preservation, deep links, payment non-overwrite contract.
- `.trellis/spec/frontend/type-safety.md`: `ApiResult<T>`, integer cents, unknown enum fallback.
- `.trellis/spec/frontend/state-management.md`: ops token/session storage, backend error details are business data and must not be translated away.
- `.trellis/spec/frontend/component-guidelines.md`: loading/error/empty/retry states and reactive pending flags.
- `.trellis/spec/frontend/hook-guidelines.md`: HTTP calls through services/composables and cleanup for async behavior.
- `.trellis/spec/frontend/directory-structure.md`: smoke files under `frontend/e2e`, services under `frontend/src/services`, DTOs under `frontend/src/types/api`.
- `.trellis/spec/frontend/quality-guidelines.md`: smoke/e2e coverage for core interactions and traceId errors.
- `.trellis/spec/backend/microservice-contracts.md`: gateway envelope, traceId, admin API contract thinking.
- `.trellis/spec/backend/gateway-security.md`: `/admin/**` JWT/RBAC expectations; `OPS_COMPENSATION_ADMIN` boundaries.
- `.trellis/spec/backend/order-create-contracts.md`: Admin Order Management Addendum and Status Non-Overwrite Contract.
- `.trellis/spec/backend/payment-pay-contracts.md`: Admin Payment Status Addendum and `PAYMENT_NOT_FOUND`.
- `.trellis/spec/backend/error-handling.md`: code/message/traceId error response rules.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: order/payment status and frontend/API boundary risk checklist.

### Code Patterns Found

- `frontend/e2e/mall-order-status-smoke.spec.ts`: Vite server lifecycle, Playwright routes, gateway envelope mocks, auth header assertions, deep link/reload smoke style.
- `frontend/e2e/fixtures/mallOrderStatusSmoke.ts`: typed fixture factory pattern for `ApiResult<T>` and frontend DTO compatibility.
- `frontend/src/App.vue`: admin workspace routing, ops session bootstrap, permission gates, `/admin?workspace=order&orderId=...` initial order parsing.
- `frontend/src/composables/useOrderManagement.ts`: admin list/detail/payment refresh orchestration, `PAYMENT_NOT_FOUND` suppression on automatic detail load, refresh pending guard.
- `frontend/src/views/admin/orderManagementModel.ts`: status labels, timeline descriptions, URL/search/filter helpers, payment-to-detail/list merge that only writes `paymentNo`.
- `frontend/src/views/admin/OrderManagementView.vue`: DOM classes/text surfaces for filters, list, detail, payment errors, action buttons, and timeline.
- `frontend/src/services/httpClient.ts`: auth token resolution, `Authorization` header, `X-Trace-Id`, envelope parsing, `HttpClientError`.
- `frontend/src/services/opsSessionStorage.ts`: existing ops session storage key and read/write behavior.
- `frontend/src/services/orderApi.ts`: admin order routes use `authContext: 'ops'`.
- `frontend/src/services/paymentApi.ts`: admin payment route uses `authContext: 'ops'`.

### Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`: new browser smoke spec for admin order/payment status paths.
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`: new typed fixture factories for ops session, admin orders, admin order pages/details, payments, and envelopes.
- `frontend/package.json`: only if a separate `test:smoke:admin` script is needed; otherwise no change required because `test:smoke` already runs all `frontend/e2e` specs.
- `frontend/package-lock.json`: only if package script changes cause npm metadata churn, which should be avoided unless necessary.

Files to read but avoid modifying unless the smoke exposes an authorized defect:

- `frontend/src/App.vue`
- `frontend/src/composables/useOrderManagement.ts`
- `frontend/src/views/admin/OrderManagementView.vue`
- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/services/orderApi.ts`
- `frontend/src/services/paymentApi.ts`
- `frontend/src/services/httpClient.ts`
- `frontend/src/services/opsSessionStorage.ts`
- `frontend/src/types/api/order.ts`
- `frontend/src/types/api/payment.ts`
- `frontend/src/types/api/auth.ts`

## Required Tests And Assertion Points

Minimum targeted command:

```powershell
cd frontend
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
```

Full frontend verification before completion:

```powershell
cd frontend
cmd /c npm run test:smoke
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

Assertion points:

- Vite server starts and closes inside Playwright lifecycle.
- `/admin?workspace=order` renders order workspace only for valid admin/order session.
- No admin order/payment API call occurs before valid ops session.
- Admin API calls include `Authorization: Bearer mock-ops-jwt-token`.
- List route receives normalized query with no `status=all` or blank filters.
- Detail route is called for clicked order and URL `orderId`.
- Main status labels render for `created`, `paid`, `shipped`, `completed`, `cancelled`, and raw unknown status.
- Timeline contains known and unknown nodes.
- Payment refresh button shows disabled/loading state and ignores duplicate clicks.
- Payment refresh updates `paymentNo`/payment display but not order main status for terminal/unknown orders.
- `PAYMENT_NOT_FOUND` explicit refresh displays backend `code/message/traceId`.
- Generic backend error envelope displays backend `code/message/traceId`.
- Deep link and reload restore selected detail.

## Planning Self-Check

- Acceptance criteria are explicit.
- Forbidden modification boundaries are explicit.
- Expected modified files are listed.
- Required test commands are listed.
- Specific guideline files have been read, not only spec indexes.
- No user clarification is currently required for planning.
- API/DTO/frontend type fields are aligned with existing `AdminOrder*`, `PaymentResponse`, `PersistedOpsSession`, and `ApiResult<T>` contracts.

