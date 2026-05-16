# 商城订单收货确认浏览器冒烟覆盖

## Goal

为商城端订单中心补齐 `shipped -> completed` 收货确认的 Chromium browser smoke 覆盖，承接已完成的支付、取消、管理端发货高风险链路，验证真实 Vue 页面、Playwright route mock、商城 session、订单 detail/list/filter 状态同步和错误 envelope 展示。

本任务只覆盖前端浏览器冒烟测试与必要的 test fixture/mock 支撑；不改变后端 API、数据库、网关、Redis/MQ、业务 DTO 契约或生产实现，除非现有 smoke 无法接入已存在的生产能力。若执行中发现必须修改生产实现才能满足已存在 contract，应保持最小改动并在交接中标明。

## Scope Classification

Complex Task。

原因：

- 涉及商城端订单生命周期写路径 `shipped -> completed`。
- 涉及 gateway API command、`requestId`、JWT/session gate、错误 envelope 和重复点击防护。
- 验收需要 detail、list、filter 多视图联动。
- 主要代码基础已存在，但浏览器 smoke 验收矩阵需要按契约补齐。

## API / Command Contract

Command:

- `confirmOrderReceipt(orderId, { requestId })`

Gateway route:

- `POST /api/orders/{orderId}/receipt-confirmations`

Auth context:

- `mall`
- 未登录或无 mall session 时不得调用 `/api/orders` 或 `/api/orders/{orderId}/receipt-confirmations`。

Payload:

```json
{
  "requestId": "receipt-<uuid-or-test-id>"
}
```

Request rules:

- `requestId` 必须存在，浏览器 smoke 需要断言 payload 中有非空 `requestId`。
- 路径 `orderId` 必须来自当前选中的 shipped 订单。
- 请求体不得携带 `shopId` 或 `userId`。
- pending 期间重复点击确认收货按钮不得发送第二个 POST。

Success response:

- envelope `code` 可使用 `ORDER_RECEIPT_CONFIRMED`。
- `data` 为更新后的 `OrderResponse`。
- 成功后当前 detail 和已加载 list item 合并为：
  - `status = completed`
  - `fulfillmentStatus = completed`
  - `completedAt` 有值
  - `carrier`
  - `trackingNo`
  - `shippedAt`
- 成功后当前 filter/list 应反映订单从 shipped 移入 completed；如果当前 shipped filter 变空，empty state 应解释状态已变化，而不是订单丢失。

Failure response:

- envelope 必须保留并展示：
  - `code`
  - `message`
  - `traceId`
- 失败后 detail/list 仍保持 shipped 物流快照：
  - `status` 或 `fulfillmentStatus` 仍可判定为 shipped
  - `carrier`
  - `trackingNo`
  - `shippedAt`
- 失败后按钮恢复可再次尝试。

## Validation / Error Matrix

| Case | Mock HTTP | Mock code | Required browser assertion |
| --- | --- | --- | --- |
| No mall session | n/a | n/a | 登录表单可见，order band 不渲染，不调用订单列表/详情/确认收货 API。 |
| Select shipped order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | detail 显示 shipped、承运商、运单号、发货时间，确认收货按钮 enabled。 |
| Select created order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | 确认收货按钮 disabled，不发送 receipt confirmation。 |
| Select paid/unshipped order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | 确认收货按钮 disabled，不发送 receipt confirmation。 |
| Select cancelled order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | 确认收货按钮 disabled，不发送 receipt confirmation。 |
| Select completed order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | 确认收货按钮 disabled，detail 保持 completed。 |
| Select unknown status order | 200 | `ORDER_LIST` / `ORDER_DETAIL` | raw unknown status 可见，确认收货按钮 disabled。 |
| Successful receipt confirmation | 200 | `ORDER_RECEIPT_CONFIRMED` | POST path/payload/header 正确；detail/list/filter 变为 completed；物流快照仍可见。 |
| Duplicate pending click | pending then 200 | `ORDER_RECEIPT_CONFIRMED` | pending 按钮 disabled/显示 confirming；重复点击只产生 1 次 POST。 |
| Receipt confirmation failure | 409 or 503 | `ORDER_STATUS_INVALID` or `DOWNSTREAM_TIMEOUT` | 显示 `code/message/traceId`；detail/list 保留 shipped carrier/trackingNo/shippedAt；按钮恢复。 |

## Acceptance Criteria

- [ ] `frontend/e2e/mall-order-status-smoke.spec.ts` 覆盖未登录不调用 API。
- [ ] shipped 订单 detail 显示确认收货按钮且按钮 enabled。
- [ ] `created`、`paid + unshipped`、`cancelled`、`completed`、unknown 订单禁用确认收货，不触发 POST。
- [ ] 成功提交时 Playwright route 断言：
  - [ ] `Authorization: Bearer mock-jwt-token`
  - [ ] path 为 `/api/orders/{orderId}/receipt-confirmations`
  - [ ] payload 仅含非空 `requestId`
  - [ ] payload 不含 `shopId` / `userId`
- [ ] pending 期间重复点击只发一次 POST，并能观察到 disabled/loading UI。
- [ ] 成功后当前 detail 显示 completed，当前 loaded list 对应卡片同步为 completed，completed filter count/list 同步。
- [ ] 若在 shipped filter 内确认成功导致当前 filter 为空，empty state 使用“状态已变化”语义。
- [ ] 失败后显示 backend `code/message/traceId`，并保留 shipped `carrier/trackingNo/shippedAt`。
- [ ] smoke fixture 支持 shipped/completed/error/deferred receipt confirmation mock，且复用现有 gateway envelope mock 形状。
- [ ] 不新增 live backend、DB、Redis/MQ、Nacos、第三方物流 API 依赖。

## Good / Base / Bad Cases

Good:

- shipped 订单确认收货后进入 completed，detail/list/filter 同步，并保留物流快照用于订单历史展示。
- 重复点击在 pending 期间被 UI 和 composable guard 阻断，只发一次 POST。
- 失败 envelope 的 `code/message/traceId` 完整显示，且 shipped 快照不丢失。
- 未登录状态不访问 protected order/receipt APIs。

Base:

- 本任务以 Playwright route mock 作为 browser smoke 的后端替身，不要求 live backend。
- 如果当前 frontend API spec 已覆盖 receipt confirmation，则不补 spec；若发现缺少 command/payload/error matrix，再补 `.trellis/spec/frontend/api-contracts.md`。
- 现有 model/composable 单测已经覆盖的行为不重复大规模改写，只在必要时补浏览器级回归。

Bad:

- 前端在未收到后端成功 response 前本地把订单标为 completed。
- POST payload 携带 `shopId` / `userId` 或缺少 `requestId`。
- 失败后 detail/list 丢失 `carrier/trackingNo/shippedAt`，或隐藏 `traceId`。
- completed、cancelled、created、paid/unshipped、unknown 订单仍可点击确认收货。
- 为测试引入 live backend、真实 token、真实物流 API、DB、Redis/MQ 或 Nacos 依赖。

## Files Likely To Modify

- `frontend/e2e/mall-order-status-smoke.spec.ts`
  - 增加 receipt confirmation route mock、request counting、payload/header assertions、success/failure/deferred cases。
- `frontend/e2e/fixtures/mallOrderStatusSmoke.ts`
  - 扩展 shipped/completed/error/deferred receipt mock helpers 或订单 fixtures。

Possible only if smoke exposes a real contract gap:

- `frontend/src/views/mall/MallStorefrontView.vue`
  - 仅当按钮 loading/disabled/detail/list/filter 现有 UI 无法满足 smoke 时最小修正。
- `frontend/src/composables/useMallOrderStatus.ts`
  - 仅当 pending duplicate guard 或 failure preservation 有生产缺口时最小修正。
- `frontend/src/views/mall/mallOrderStatusModel.ts`
  - 仅当 status/filter/empty-state 判断存在生产缺口时最小修正。
- `frontend/src/composables/useAppPreferences.ts`
  - 仅当现有文案缺失导致按钮/empty-state 无法表达验收语义时最小补 key。

## Explicitly Out Of Scope

- 后端 Java 实现、Maven 测试、数据库 migration、网关 route、RBAC/JWT 过滤器。
- 新增或修改订单 API 契约字段。
- 真实物流查询、物流 service、MQ/Redis、Nacos、Docker/CI。
- 管理端订单/履约 smoke 的重构或扩展。
- 商品、库存、秒杀、评价业务新功能。
- 大规模 UI 重构或抽象化。

## Required Tests And Assertion Points

Focused browser smoke:

```powershell
cd frontend
cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium
```

Full smoke:

```powershell
cd frontend
cmd /c npm run test:smoke
```

Focused unit/component regression if production files change:

```powershell
cd frontend
cmd /c npx vitest run --reporter=verbose src/views/mall/MallStorefrontView.spec.ts tests/mallOrderStatusModel.spec.ts tests/mallCheckoutModel.spec.ts
```

Full frontend verification:

```powershell
cd frontend
cmd /c npm test
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm run build
```

Final diff hygiene:

```powershell
git diff --check
```

No backend Maven command is required unless DeepSeek changes backend code, which this PRD forbids.

## Spec Update Decision

Initial Codex research found the existing specs already document the relevant frontend and backend receipt confirmation contract:

- `.trellis/spec/frontend/api-contracts.md` includes `confirmOrderReceipt(orderId,{requestId})`, success merge, failure preservation, duplicate pending guard, and required tests.
- `.trellis/spec/backend/order-create-contracts.md` includes `POST /api/orders/{orderId}/receipt-confirmations`, `requestId`, trusted principal, valid statuses, error matrix, and Good/Base/Bad cases.

Therefore no spec update is planned. If implementation discovers a missing browser-specific command/payload/error assertion matrix, update `.trellis/spec/frontend/api-contracts.md` narrowly and record why.

