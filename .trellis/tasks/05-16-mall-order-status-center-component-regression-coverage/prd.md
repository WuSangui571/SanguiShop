# 商城端订单状态中心组件级回归覆盖

## Goal

补齐商城端订单状态页面和共享订单状态面板的组件级回归测试，把上一轮 `mallOrderStatusModel` 对 payment merge 的非覆盖保护提升到真实 UI 渲染、按钮可用性、错误展示和 composable 集成路径层面。

本任务只做前端测试与必要的前端 contract/spec 同步，不扩大到后端状态机、API、DB、MQ、权限、infra 或业务实现重构。

## Background

上一轮已修复并提交商城端支付合并模型层行为：`PaymentResponse.status` 是支付域事实，只能在当前订单主状态为 `created` 时把订单推进到 `paid` / `unshipped`；对 `paid`、`shipped`、`completed`、`cancelled` 和未知订单主状态，支付刷新和支付成功回调必须保留现有订单生命周期状态。

当前剩余风险在 UI 消费层：

- 商城订单状态页面可能把模型正确结果渲染成错误文案，例如把已发货、已完成、已取消或未知状态显示成待发货。
- 共享订单状态面板可能在按钮 disabled/loading 文案上出现终态订单可刷新的回退。
- `useMallOrderStatus` 的 `refreshPayment` 与 `acceptPayment` 路径可能在 detail/list/filter 同步上与模型断言不一致。
- 支付刷新失败时，页面可能丢失当前订单快照，或者没有保留 backend `code/message/traceId`。

## Scope Classification

Complex Task.

理由：范围跨商城端页面组件、共享面板、composable 集成路径、测试夹具和前端 contract 文档；虽然不涉及后端/API/DB，但需要先用 PRD 和 Trellis context 固定边界，避免把 UI 回归覆盖误扩成业务实现或后端状态机改造。

## In Scope

- 为 `MallStorefrontView.vue` 或现有商城端组件测试补商城订单状态页面/共享状态面板组件级回归。
- 覆盖 payment refresh 后 `shipped`、`completed`、`cancelled`、未知订单主状态不显示为待发货。
- 覆盖 `useMallOrderStatus.refreshPayment` 与 `useMallOrderStatus.acceptPayment` 后 detail、list、active filter / empty-state 的一致行为。
- 覆盖支付刷新按钮状态：
  - 终态订单禁用或不可触发刷新。
  - `created` / `paid` 状态文案合理，不把支付域状态误当订单主生命周期。
- 覆盖错误态：支付刷新失败后保留当前订单快照，并显示 backend `code`、`message`、`traceId`。
- 如当前 UI 行为只在模型 spec 中描述，补充 `.trellis/spec/frontend/api-contracts.md` 的组件/UI 断言点。

## Out of Scope / Forbidden Changes

- 不修改后端服务、controller、DTO、数据库 migration、Redis、MQ、gateway、auth、Docker、CI。
- 不新增或修改 API route、HTTP method、query/body 字段、Result envelope shape。
- 不改支付状态机、订单状态机、履约状态机。
- 不改业务状态合并实现，除非 DeepSeek 在测试落地时发现组件测试无法表达现有公开行为；如必须改实现，应先停下并回报原因。
- 不重构商城大页面结构、不拆新大型组件、不引入新测试框架。
- 不硬编码 `shopId` / `userId` 魔法值到业务代码；测试夹具中可使用明确的 mock 值。

## API / Command / Payload Contract

本任务不改变 API，只验证前端继续遵守现有 gateway contract。

| Function | Route | Auth Context | Payload / Fields | UI Assertion Focus |
| --- | --- | --- | --- | --- |
| `getOrder(orderId)` | `GET /api/orders/{orderId}` | `mall` | Path: `orderId` | detail snapshot remains source of truth for order main status, fulfillment, logistics, review |
| `listOrders({page,size})` | `GET /api/orders?page=&size=` | `mall` | Query: `page`, `size`; must not send `shopId` / `userId` | list item stays aligned with detail after payment merge |
| `getPayment(paymentNo)` | `GET /api/payments/{paymentNo}` | `mall` | Path: `paymentNo` | payment status refresh must not overwrite order main status beyond `created` |
| `createPayment` / existing payment submit path | existing service route | `mall` | existing payment payload; no field change | `acceptPayment` path uses the same non-overwrite merge behavior |

Relevant response fields:

- Order detail/list: `id`, `orderNo`, `status`, `fulfillmentStatus`, `paymentNo`, `carrier`, `trackingNo`, `shippedAt`, `completedAt`, `reviewed`, `review`.
- Payment: `paymentNo`, `orderId`, `status`.
- Error: `code`, `message`, `traceId`.

## Validation / Error Matrix

| Case | Expected Behavior |
| --- | --- |
| `created` order + successful payment refresh/payment success `status=paid` | UI may show paid/awaiting-shipment state; detail and matching list item move to `paid` / `unshipped`. |
| `paid` order + successful payment refresh `status=paid` | UI remains paid/awaiting-shipment; no accidental lifecycle regression or duplicate action enablement. |
| `shipped` order + successful payment refresh `status=paid` | UI keeps shipped status/logistics; must not display awaiting shipment. |
| `completed` order + successful payment refresh `status=paid` | UI keeps completed snapshot; receipt/payment refresh actions disabled as applicable; must not display awaiting shipment. |
| `cancelled` order + successful payment refresh `status=paid` | UI keeps cancelled snapshot; payment/cancel/refresh actions disabled as applicable; must not display awaiting shipment. |
| unknown order `status` + successful payment refresh `status=paid` | UI displays raw/fallback unknown order status; must not coerce to paid/awaiting shipment. |
| payment refresh fails with backend error | Existing detail/list snapshot remains visible; backend `code`, `message`, and `traceId` are rendered; retry remains possible only when the order state allows it. |
| duplicate pending payment refresh | No second request is sent; disabled/loading state is visible if the UI exposes the action. |

## Good / Base / Bad Cases

- Good: `shipped`, `completed`, `cancelled`, and unknown statuses keep their UI labels and logistics/completion snapshots after `getPayment(paymentNo)` returns `status=paid`.
- Good: `refreshPayment` and `acceptPayment` both update detail and list through the same guarded behavior and keep filter/empty-state consistent.
- Good: failed payment refresh preserves current order snapshot and displays `code/message/traceId`.
- Base: `created` order can still become `paid` / `unshipped` after successful payment response.
- Base: `paid` / `unshipped` order still shows the existing awaiting-shipment copy where appropriate.
- Bad: shipped/completed/cancelled/unknown order renders as awaiting shipment after payment refresh.
- Bad: a terminal order exposes an enabled payment refresh action that can mutate visible order lifecycle state.
- Bad: payment refresh failure clears detail/list or hides backend trace fields.
- Bad: component tests only assert pure model helpers and do not mount the actual UI/composable path.

## Acceptance Criteria

- [ ] Component-level tests cover payment refresh rendering for `shipped`, `completed`, `cancelled`, and unknown order statuses and prove they do not show as awaiting shipment.
- [ ] Component/composable integration tests cover `refreshPayment` and `acceptPayment` consistency across detail, list, and active filter / empty-state behavior.
- [ ] Tests cover payment refresh button disabled/loading/copy for terminal orders and for `created` / `paid` orders.
- [ ] Tests cover payment refresh failure preserving current order snapshot and rendering backend `code`, `message`, and `traceId`.
- [ ] Existing model-level regression tests remain passing.
- [ ] `.trellis/spec/frontend/api-contracts.md` is updated only if the component/UI assertion points are currently missing from the Mall Order Status section.
- [ ] No backend/API/DB/infra/auth/MQ/Redis changes are made.

## Expected Files To Inspect

- `frontend/src/views/mall/MallStorefrontView.vue`
- `frontend/src/composables/useMallOrderStatus.ts`
- `frontend/src/views/mall/mallOrderStatusModel.ts`
- `frontend/tests/mallOrderStatusModel.spec.ts`
- Existing `frontend/tests/*Mall*`, `frontend/src/views/mall/*.spec.ts`, or colocated component specs.
- `frontend/src/services/orderApi.ts`
- `frontend/src/services/paymentApi.ts`
- `frontend/src/types/api/order.ts`
- `frontend/src/types/api/payment.ts`
- `.trellis/spec/frontend/api-contracts.md`

## Expected Files To Modify

- One or more existing mall component/composable specs, likely:
  - `frontend/tests/mallOrderStatusModel.spec.ts` if it already contains integration helpers.
  - Existing `MallStorefrontView` / mall component spec if present.
  - A new focused component spec only if no existing suitable file exists.
- `.trellis/spec/frontend/api-contracts.md` only if UI-level assertions are not already explicit.

## Required Tests And Assertion Points

Minimum focused run:

```powershell
cmd /c npx vitest run --reporter=verbose tests/mallOrderStatusModel.spec.ts <component-spec-file>
```

Broader frontend verification after implementation:

```powershell
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

Assertion points:

- UI text/semantic state for `shipped`, `completed`, `cancelled`, and unknown statuses after payment refresh.
- Absence of awaiting-shipment copy for non-created progressed statuses.
- Detail snapshot status, fulfillment/logistics/completion fields remain intact.
- Matching list item status and filter classification remain aligned with detail.
- Refresh button disabled/loading behavior and duplicate-request guard.
- Backend error `code`, `message`, and `traceId` rendered after failure.

Backend Maven tests are not required unless implementation unexpectedly touches backend files.

## Implementation Notes For DeepSeek

- Prefer extending existing test fixtures and mount helpers instead of creating parallel fake state systems.
- Keep tests close to public user behavior: mounted component text/buttons where possible, composable integration only where the UI mount would be too broad.
- Do not use `any` to bypass API types; update test builders with typed partial helpers if necessary.
- Preserve current localized copy conventions through `useAppPreferences().t()`; tests should assert stable labels already used by the component where feasible.
- Treat unknown backend enum values as raw/fallback display, not as a reason to coerce lifecycle state.

## Open Questions

None at PRD time. If implementation discovers that a required UI assertion cannot be expressed without changing production behavior, stop and report before editing business implementation.
