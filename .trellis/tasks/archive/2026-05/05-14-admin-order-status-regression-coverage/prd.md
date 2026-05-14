# 管理端订单状态全链路回归覆盖

## Goal

Prevent admin order main-status contract drift across backend projection, frontend API types, display model, component rendering, timeline rendering, and i18n when order states are added or adjusted.

This task follows the recent `completed` admin order display fix. The next change should convert that fix into durable regression coverage and executable spec guidance.

## Classification

Complex Task.

Reasons:

- Cross-layer scope: backend order-service tests, frontend Vue/model tests, and `.trellis/spec` contract documentation.
- Contract-oriented risk: the persisted order main status must stay distinct from derived payment, fulfillment, and review statuses.
- No runtime behavior, API route, DTO field, database, MQ, Redis, auth, or business workflow change is intended.

## Scope

In scope:

- Frontend admin order status matrix test coverage for:
  - `created`
  - `paid`
  - `shipped`
  - `completed`
  - `cancelled`
  - unknown raw status such as `refunding`
- Frontend assertions must cover list display, selected detail display, and timeline behavior.
- Backend parameterized coverage for admin order list/detail projection over every `OrderStatus.values()` main status.
- Backend assertions must prove admin list/detail expose `OrderRecord.status().value()` as the main `status` and do not substitute payment, fulfillment, or review-derived statuses.
- Spec update that captures: admin order main `status` is the persisted order lifecycle status and must not be overwritten by derived states.

Out of scope:

- Do not change business implementation unless an existing test helper blocks the regression tests.
- Do not change database schema, migrations, API routes, request/response field names, auth rules, Redis, MQ, Nacos, Docker, or CI.
- Do not introduce new order statuses.
- Do not modify payment, fulfillment, review, inventory, gateway runtime behavior, or customer mall order flows.
- Do not loosen unknown-status fallback.

## Existing Contract

### Backend APIs

`GET /api/admin/orders`

Response code: `ADMIN_ORDER_LIST`.

Relevant response item fields:

- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `status`
- `totalAmountCent`
- nullable `paymentNo`
- `itemCount`
- nullable `traceId`
- `createdAt`
- `updatedAt`

`GET /api/admin/orders/{orderId}`

Response code: `ADMIN_ORDER_DETAIL`.

Relevant response fields:

- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `requestId`
- nullable `reservationNo`
- nullable `paymentNo`
- `status`
- `totalAmountCent`
- nullable `traceId`
- `createdAt`
- `updatedAt`
- `items[]`
- `statusTimeline[]`

`statusTimeline[]` fields:

- `status`
- nullable `occurredAt`
- nullable `traceId`

### Contract Rule To Preserve

The admin order `status` field in list and detail is the persisted order main lifecycle status from `OrderStatus`.

Derived states must not replace it:

- `paymentNo` / payment status may enrich display snapshots but must not override `completed`, `shipped`, `cancelled`, or unknown main statuses.
- `fulfillmentStatus` is derived from order lifecycle or fulfillment snapshot, but admin order list/detail `status` remains the main order status.
- `reviewed` / review snapshots are separate and must not change admin order main `status`.
- Timeline nodes must keep backend-provided status values; unknown status values use localized unknown description without dropping the node.

## Validation / Error Matrix

This task should not introduce new validation or error behavior.

| Case | Expected Behavior |
| --- | --- |
| Known main status in `OrderStatus.values()` | Backend admin list/detail emit the same lower-case `status` value; frontend displays localized label where supported. |
| Unknown backend status in frontend payload | Frontend displays raw status in list/detail and uses localized unknown timeline description. |
| `status=all` filter | Frontend omits `status` from list request payload. Existing behavior remains. |
| Invalid backend admin status filter | Existing backend behavior remains `ORDER_STATUS_INVALID`; no new behavior in this task. |
| Payment refresh returns `paid` for current order | Existing behavior may merge `paid` only for payment success; tests must not let this obscure persisted terminal statuses. |

## Good / Base / Bad Cases

Good:

- Backend parameterized test loops through `OrderStatus.values()` and asserts list item `status == status.value()`.
- Backend parameterized test loops through `OrderStatus.values()` and asserts detail `status == status.value()`.
- Backend detail timeline includes only `created` for `created`, and `created -> <current status>` for every non-created main status.
- Frontend model matrix asserts localized labels for `created`, `paid`, `shipped`, `completed`, `cancelled`, and raw fallback for unknown.
- Frontend component matrix renders each status in the admin list, selected detail, and timeline.
- Unknown frontend timeline node is preserved and described with `orderAdmin.timelineUnknownDescription`.
- Spec documents the non-overwrite rule as an executable assertion point.

Base:

- Existing single `completed` tests may remain, but broader matrix tests should reduce duplicated single-status cases where practical.
- Component tests may keep mocked i18n keys and assert keys, not rendered natural-language translations.
- Backend tests may use the existing in-memory repository and fixture helpers.

Bad:

- A test only verifies `completed` again and misses the rest of `OrderStatus.values()`.
- Frontend drops unknown timeline nodes or maps unknown main statuses to a generic label instead of raw status.
- Backend admin projection emits `fulfillmentStatus`, payment status, or review state as `status`.
- Spec says status handling is required but does not name concrete assertion points.

## Acceptance Criteria

- [ ] Frontend admin order status matrix covers list display, detail display, and timeline behavior for `created`, `paid`, `shipped`, `completed`, `cancelled`, and unknown.
- [ ] Frontend unknown status behavior preserves raw list/detail status and localized unknown timeline description.
- [ ] Backend admin service parameterized tests cover every `OrderStatus.values()` value for list and detail projection.
- [ ] Backend tests assert the derived timeline contract for every main status.
- [ ] Backend/controller coverage confirms serialized admin detail/list responses preserve current main statuses where applicable.
- [ ] `.trellis/spec` captures "admin order main status must not be overwritten by payment/fulfillment/review-derived statuses" and lists the regression assertion points.
- [ ] No production business behavior, API schema, DB schema, auth, MQ, Redis, gateway, or unrelated UI changes are included.
- [ ] Required verification commands pass.

## Files Likely To Modify

Frontend tests and possibly test helpers:

- `frontend/src/views/admin/orderManagementModel.test.ts`
- `frontend/src/views/admin/OrderManagementView.spec.ts`

Frontend implementation files should only be touched if tests expose an existing coverage gap that cannot be tested otherwise:

- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/views/admin/OrderManagementView.vue`
- `frontend/src/types/api/order.ts`
- `frontend/src/composables/useAppPreferences.ts`

Backend tests:

- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/AdminOrderManagementServiceTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/api/AdminOrderControllerTest.java`

Spec:

- Prefer `.trellis/spec/backend/order-create-contracts.md` under `Admin Order Management Addendum`.
- Optionally add a cross-layer note in `.trellis/spec/guides/cross-layer-thinking-guide.md` only if the backend contract alone is insufficient.
- Avoid frontend spec churn unless frontend-only assertion wording must be tightened.

## Required Tests

Frontend:

```powershell
cmd /c npm test
```

Targeted frontend option while iterating:

```powershell
cmd /c npx vitest run --reporter=verbose src/views/admin/orderManagementModel.test.ts src/views/admin/OrderManagementView.spec.ts
```

Backend:

```powershell
.\mvnw.cmd -q -pl services/sangui-order-service -am "-Dtest=AdminOrderManagementServiceTest,AdminOrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Recommended final hygiene:

```powershell
git diff --check
```

## Implementation Notes For DeepSeek

- Favor parameterized tests over repeated one-off status tests.
- Backend test data should seed orders with each `OrderStatus` and assert `status.value()` is returned unchanged.
- Frontend component tests can generate table-driven cases; mocked `useAppPreferences().t()` returns keys, so expected labels are i18n keys such as `orderAdmin.statusCompleted`.
- Unknown frontend test should use a raw status such as `refunding` and a timeline entry with that status.
- Do not make `OrderStatus` a closed TypeScript union that rejects unknown strings; the current `| string` compatibility is intentional.
- If production code is changed, explain why the regression test could not be added against existing behavior.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden modification scope: defined above.
- Expected modified files: listed above.
- Required tests: listed above.
- Specific guideline files read before planning: backend `microservice-contracts.md`, `order-create-contracts.md`, `error-handling.md`, `quality-guidelines.md`; frontend `api-contracts.md`, `type-safety.md`, `component-guidelines.md`, `quality-guidelines.md`; guide `cross-layer-thinking-guide.md`.
- Open requirements: none currently identified.
- API / DB / frontend types / DTO alignment: no new fields. Existing `status` remains the shared string field with unknown fallback on the frontend.
