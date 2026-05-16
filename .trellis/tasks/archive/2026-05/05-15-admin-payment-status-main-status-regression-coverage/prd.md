# 管理端支付状态与订单主状态一致性回归覆盖

## Goal

补齐管理端订单列表、订单详情、前端支付刷新合并逻辑的回归覆盖，证明 `paymentStatus` / `paymentNo` 只能作为支付展示快照，不得覆盖或派生覆盖订单主生命周期 `status`。

本任务优先补测试与可执行 spec 断言。除非测试暴露现有缺陷，否则不修改业务实现；若必须修复，实现变更必须最小化并只围绕管理端订单支付快照合并。

## Scope Classification

Complex Task:

- 跨 backend order-service、backend payment-service contract、frontend admin order model/view、frontend/backend spec。
- 涉及跨服务响应字段合并规则和状态展示一致性。
- 目标是防回归覆盖与交接，不由 Codex 端编码实现。

## API / Payload Contract

### Backend Admin Order List

`GET /api/admin/orders`

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

Contract:

- `status` is always the persisted order main lifecycle status from `OrderStatus`.
- `paymentNo` is nullable and must not imply or overwrite `status`.
- `completed`, `cancelled`, and `shipped` must remain unchanged even when `paymentNo` exists.

### Backend Admin Order Detail

`GET /api/admin/orders/{orderId}`

Relevant response fields:

- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `requestId`
- `reservationNo`
- nullable `paymentNo`
- `status`
- `totalAmountCent`
- nullable `traceId`
- `createdAt`
- `updatedAt`
- `items[]`
- `statusTimeline[]`

Contract:

- `status` is the order main lifecycle status.
- `statusTimeline[].status` follows backend-provided order lifecycle timeline values.
- Payment snapshot fields must not alter `status` or timeline nodes.

### Frontend Admin Payment Refresh

`getAdminPaymentByOrderId(orderId)`

Gateway route:

- `GET /api/admin/payments/by-order/{orderId}`

Relevant payment response fields:

- `paymentId`
- `paymentNo`
- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `channel`
- `status` as payment status
- `amountCent`

Frontend merge contract:

- Successful refresh may merge `paymentNo` into current order detail/list display snapshot.
- Successful refresh may show payment status in the payment-specific display area.
- Successful refresh must not assign payment `status` to order `status`.
- Unknown payment status must use payment-status fallback only and must not affect order main-status label.
- `PAYMENT_NOT_FOUND` must be treated as no payment row for display and must preserve the existing order main `status`.

## Validation / Error Matrix

| Case | Expected Behavior | Required Assertion |
| --- | --- | --- |
| Admin order list row has `status=completed` and `paymentNo` | List response/display remains completed | Backend list status equals `completed`; frontend main label is completed |
| Admin order detail has `status=cancelled` and `paymentNo` | Detail response/display remains cancelled | Backend detail status equals `cancelled`; frontend main label is cancelled |
| Admin order detail has `status=shipped` and payment refresh returns `status=paid` | Payment snapshot is updated, order main status remains shipped | Frontend detail/list item `status` remains `shipped` |
| Payment refresh returns unknown payment status | Payment display uses fallback, order main status label unchanged | Unknown payment status does not alter order status label |
| Payment refresh returns `PAYMENT_NOT_FOUND` | No payment row is shown or current payment fields are preserved per existing model behavior; order main status unchanged | Existing order `status` and label remain unchanged |
| Backend payment row exists for terminal order | Order-service admin projection does not derive main status from payment | Service/controller tests assert `status` unchanged |

## Good / Base / Bad Cases

Good:

- Backend admin list preserves `completed`, `cancelled`, and `shipped` main statuses when `paymentNo` exists.
- Backend admin detail preserves `completed`, `cancelled`, and `shipped` main statuses and timeline when payment snapshot fields exist.
- Frontend successful payment refresh merges only payment-specific fields and keeps order main `status`.
- Frontend unknown payment status and `PAYMENT_NOT_FOUND` preserve order main status label.
- Regression tests prove the failure mode by asserting list/detail model state after refresh.

Base:

- No API route, DTO field, database schema, auth, gateway, Redis, MQ, Docker, CI, or compensation behavior changes are required.
- `paymentNo` remains nullable in order responses.
- Payment-service admin route remains the source for payment status by order id.
- Existing unknown order-status fallback remains raw value based.

Bad:

- Frontend assigns payment response `status` into `AdminOrder.status`.
- Backend admin order projection overwrites order main status from payment state.
- A `PAYMENT_NOT_FOUND` refresh clears or changes order main status.
- Tests pass by checking only rendered text globally without inspecting the target row/detail/timeline context.
- Implementation expands into payment workflow, order state machine transitions, database migrations, or route/security changes.

## Acceptance Criteria

- [ ] Backend admin order service tests cover payment snapshot presence for terminal/main statuses and assert main `status` preservation.
- [ ] Backend admin order controller tests cover serialized list/detail JSON with payment snapshot fields and unchanged main `status`.
- [ ] Frontend admin order model tests cover successful `getAdminPaymentByOrderId` merge for `shipped`, `completed`, and/or `cancelled` main statuses.
- [ ] Frontend tests cover unknown payment status and `PAYMENT_NOT_FOUND` preserving order main status label.
- [ ] If component rendering is touched, component tests assert scoped list/detail/timeline text, not only global text presence.
- [ ] Backend/frontend specs include executable assertion points if current wording is not specific enough.
- [ ] No business implementation files are changed unless a failing regression test proves a real bug; any fix is minimal and documented.

## Expected Files Likely To Modify

Backend tests:

- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/AdminOrderManagementServiceTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/api/AdminOrderControllerTest.java`

Frontend tests:

- `frontend/src/views/admin/orderManagementModel.test.ts`
- `frontend/src/views/admin/OrderManagementView.spec.ts`

Spec, if assertion points need tightening:

- `.trellis/spec/backend/order-create-contracts.md`
- `.trellis/spec/backend/payment-pay-contracts.md`
- `.trellis/spec/frontend/api-contracts.md`

Possible implementation files only if tests expose current defects:

- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/views/admin/OrderManagementView.vue`
- `frontend/src/types/api/order.ts`
- `frontend/src/services/paymentApi.ts`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/AdminOrderManagementService.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/AdminOrderController.java`

## Out Of Scope / Forbidden Changes

- Do not add or change database migrations.
- Do not change payment/order state machine transitions.
- Do not change gateway routes, auth roles, permissions, or JWT handling.
- Do not read payment tables from order-service.
- Do not make payment-service read order tables for admin payment status.
- Do not introduce Redis, MQ, scheduler, compensation, or external payment provider behavior.
- Do not refactor unrelated admin order UI layout or fulfillment/review flows.
- Do not change public customer order/payment behavior.

## Required Tests

Targeted frontend:

```powershell
cmd /c npx vitest run --reporter=verbose src/views/admin/orderManagementModel.test.ts src/views/admin/OrderManagementView.spec.ts
```

Frontend quality:

```powershell
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

Targeted backend order-service:

```powershell
.\mvnw.cmd -q -pl services/sangui-order-service -am "-Dtest=AdminOrderManagementServiceTest,AdminOrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Optional payment admin route regression if payment-service behavior is touched:

```powershell
.\mvnw.cmd -q -pl services/sangui-payment-service -am "-Dtest=AdminPaymentControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Final whitespace check:

```powershell
git diff --check
```

## DeepSeek Implementation Notes

- Start from tests that demonstrate payment refresh/status merge expectations.
- Prefer extending existing fixtures/builders in current tests over creating new abstractions.
- Assert exact target object state in model tests after refresh, including `status`, `paymentNo`, and any payment display fields.
- In component tests, scope assertions to the selected row/detail/timeline to avoid false positives from duplicated labels.
- If a production fix is needed, preserve existing API field names and use type-safe DTO mapping instead of broad object spread that can copy payment `status` into order `status`.
