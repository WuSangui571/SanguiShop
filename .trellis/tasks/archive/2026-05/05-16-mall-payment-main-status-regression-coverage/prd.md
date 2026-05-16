# 商城端订单支付状态与订单主状态一致性回归覆盖

## Goal

为商城端客户订单支付刷新、支付成功、订单列表/详情合并链路补齐回归覆盖，防止支付快照状态覆盖订单主生命周期状态。

本任务沿用上一轮管理端缺陷的风险模型：支付状态是支付域事实，订单 `status` 是订单主生命周期事实。商城端在用户直接可见链路中必须保留 `created`、`paid`、`cancelled`、`shipped`、`completed` 等主状态边界，不能因为 `PaymentResponse.status=paid` 把已发货、已完成、已取消订单回退为 `paid`。

## Classification

Complex Task.

原因：
- 涉及商城端订单详情、订单列表、支付刷新、支付成功后的共享 merge helper。
- 需要对齐 frontend API contract、订单/支付 DTO 字段、订单状态生命周期和测试断言。
- 本轮 Codex 只做计划、研究和 Trellis context 准备，不写业务代码；实现交给 DeepSeek 端。

## Scope

### In Scope

- 检查并最小修复商城端支付 merge helper，使支付刷新不会覆盖已有的终态/履约态订单主状态。
- 补充 `frontend/tests/mallOrderStatusModel.spec.ts` 或相关商城端 composable/component 测试，覆盖：
  - `shipped` 订单支付刷新返回 `payment.status=paid` 后，订单主 `status` 仍为 `shipped`。
  - `completed` 订单支付刷新返回 `payment.status=paid` 后，订单主 `status` 仍为 `completed`。
  - `cancelled` 订单支付刷新返回 `payment.status=paid` 后，订单主 `status` 仍为 `cancelled`。
  - 同步断言 detail 和 loaded order list item 都不被错误回退。
- 保留现有创建订单支付成功的行为：`created` + `payment.status=paid` 可以合并为 `status=paid`、`fulfillmentStatus=unshipped`。
- 必要时更新 `.trellis/spec/frontend/api-contracts.md` 的 Mall Order Status APIs 可执行断言点，使 mall 侧支付 merge 合同明确。

### Out of Scope / Forbidden

- 不改后端 API、DTO、数据库、Redis/MQ、支付状态机、订单状态机、网关、权限、Docker、CI。
- 不修改 admin 侧实现，除非只作为参考读取。
- 不新增物流追踪 API，不虚构第三方物流数据。
- 不把 review / fulfillment / payment 派生状态写成订单主状态。
- 不做大规模 UI 重构、样式重构、抽象迁移或无关翻译改动。
- 不改支付失败、重复支付、收货确认、评价提交的既有业务语义，除非测试证明必须做最小联动修复。

## API / Command / Payload Fields

本任务不新增或修改后端 API。必须保持现有商城端 Gateway 路由和字段语义：

| Function | Route | Auth Context | Fields / Assertion Points |
| --- | --- | --- | --- |
| `getPayment(paymentNo)` | `GET /api/payments/{paymentNo}` | `mall` | Reads `PaymentResponse.paymentNo`, `orderId`, `status`; payment `status` must be used only as payment-domain status, not as an unconditional replacement for order main `status`. |
| `createPayment(payload)` | `POST /api/payments` | `mall` | Existing successful `status=paid` response may move a current `created` order to `paid`; duplicate submit guard and stable `paymentNo` remain unchanged. |
| `getOrder(orderId)` | `GET /api/orders/{orderId}` | `mall` | Order snapshot remains source of truth for `status`, `fulfillmentStatus`, `carrier`, `trackingNo`, `shippedAt`, `completedAt`, `reviewed`, and `review`. |
| `listOrders({page,size})` | `GET /api/orders?page=&size=` | `mall` | Loaded list item merge must preserve main order status and not send `shopId` / `userId` query fields. |

Relevant DTO fields:
- `OrderResponse.status`: main lifecycle status, currently `created | cancelled | paid | shipped | completed | string`.
- `OrderResponse.fulfillmentStatus`: fulfillment snapshot, currently `all | unshipped | shipped | completed | string`.
- `OrderResponse.paymentNo`: not present on customer `OrderResponse`; customer payment refresh uses separate `paymentNo` state.
- `PaymentResponse.status`: payment lifecycle status, currently `created | paid | failed | string`.
- `PaymentResponse.orderId`: correlation key for current order/list merge.
- `PaymentResponse.paymentNo`: customer payment number for display/retry/refresh source.

## Validation / Error Matrix

| Case | Expected Frontend Behavior |
| --- | --- |
| Current order is `created`, matching `PaymentResponse.orderId`, `payment.status=paid` | Merge detail and list item to `status=paid`, `fulfillmentStatus=unshipped` when no newer fulfillment status exists. |
| Current order is `paid` with `fulfillmentStatus=unshipped`, matching paid payment | Preserve `status=paid`, keep `fulfillmentStatus=unshipped`. |
| Current order is `shipped`, matching paid payment | Preserve `status=shipped` and shipped logistics fields; do not regress to `paid`. |
| Current order is `completed`, matching paid payment | Preserve `status=completed`, `fulfillmentStatus=completed`, `completedAt`, review eligibility/reviewed state. |
| Current order is `cancelled`, matching paid payment | Preserve `status=cancelled`; payment/cancel/receipt/review actions remain disabled by existing model rules. |
| Payment response has mismatched `orderId` | Leave current detail and loaded list unchanged. |
| Payment response has non-`paid` or unknown payment `status` | Leave current detail and loaded list unchanged; payment-specific display may still keep payment response if existing behavior already does so. |
| Payment refresh API fails | Preserve existing order detail/list snapshot and display backend `code/message/traceId`. |
| Duplicate pending payment refresh | Do not send a second request; keep existing guard. |

## Good / Base / Bad Cases

- Good: `created` order plus successful payment still becomes `paid` awaiting shipment.
- Good: `shipped`, `completed`, and `cancelled` order snapshots remain in their main lifecycle status after a payment refresh returns `paid`.
- Good: detail and currently loaded order list use the same non-overwrite merge behavior.
- Good: unknown order status and unknown fulfillment status retain raw fallback behavior.
- Base: customer orders without `paymentNo` may derive a payment summary from order snapshot, but no synthetic `PaymentResponse` is invented.
- Base: `GET /api/orders/{orderId}` remains authoritative for fulfillment and completion snapshots; payment refresh is an enrichment path only.
- Bad: `PaymentResponse.status=paid` unconditionally assigns `OrderResponse.status='paid'`.
- Bad: a completed or shipped order moves back into the paid/awaiting-shipment filter after payment refresh.
- Bad: a cancelled order re-enables payment or cancellation actions because payment refresh rewrote the main status.

## Acceptance Criteria

- [ ] Existing `created -> paid` payment success/refresh behavior remains covered and passing.
- [ ] Model tests cover terminal/main states `shipped`, `completed`, and `cancelled` after matching `payment.status=paid` merge.
- [ ] Tests assert both current detail and loaded list item status preservation where applicable.
- [ ] If the production helper currently overwrites terminal states, it is fixed with the smallest possible change.
- [ ] `.trellis/spec/frontend/api-contracts.md` documents the mall payment merge non-overwrite contract if the existing Mall Order Status section is too abstract.
- [ ] No backend/API/DB/payment state machine/order state machine changes are made.
- [ ] Required frontend targeted tests pass.

## Expected Files To Modify

- `frontend/src/views/mall/mallOrderStatusModel.ts`: likely minimal helper change around payment-to-order merge.
- `frontend/tests/mallOrderStatusModel.spec.ts`: primary regression tests for pure model behavior.
- `frontend/tests/mallCheckoutModel.spec.ts`: optional composable regression if pure model coverage is not enough to prove detail/list behavior through `refreshPayment`.
- `.trellis/spec/frontend/api-contracts.md`: optional contract clarification for mall payment merge.

Files to read/reference but not necessarily modify:
- `frontend/src/composables/useMallOrderStatus.ts`
- `frontend/src/views/mall/MallStorefrontView.vue`
- `frontend/src/types/api/order.ts`
- `frontend/src/types/api/payment.ts`
- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/views/admin/orderManagementModel.test.ts`

## Required Tests

Run targeted frontend tests first:

```powershell
cmd /c npx vitest run --reporter=verbose tests/mallOrderStatusModel.spec.ts tests/mallCheckoutModel.spec.ts
```

Run broader frontend quality checks before returning to Codex check/finish-work:

```powershell
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

Backend tests are not required unless DeepSeek changes backend code, which is out of scope.

## Notes For Implementer

- Prefer extending the existing `applyMallPaymentToOrder` / `applyMallPaymentToOrderList` tests before touching implementation.
- Use the admin-side fix only as a pattern reference. Do not copy admin behavior blindly because mall still needs `created -> paid` immediate merge.
- The expected implementation shape is probably a guard inside `applyMallPaymentToOrder`: only promote to `paid` when the current order main status is `created` or already `paid` without a newer terminal lifecycle status. Preserve `shipped`, `completed`, `cancelled`, and unknown main statuses.
- Keep `fulfillmentStatus` preservation conservative. Do not overwrite `shipped` or `completed` fulfillment snapshots with `unshipped`.
