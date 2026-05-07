# 管理端发货/物流履约 MVP

## Goal

让运营在管理端查看已支付待发货订单，填写承运商与物流单号完成发货；同时让商城端用户能在订单详情中看到待发货/已发货状态与物流单号，补齐从支付完成到商家发货的电商闭环。

## Scope Decision

- 任务类型：fullstack，涉及 frontend、gateway、logistics-service、order-service、user-service/common-security、DB migration、spec 更新。
- 服务边界：MVP 使用已有 `services/sangui-logistics-service` 承载物流履约记录与 admin fulfillment API；订单状态仍归 `order-service` 所有。
- 订单状态推进：由 logistics-service 调用 order-service 内部契约完成 `paid -> shipped`，logistics-service 不直接读写 `oms_*` 表。
- 权限边界：新增 `LOGISTICS_FULFILLMENT_ADMIN`，`ADMIN` 也允许操作；不复用 `OPS_COMPENSATION_ADMIN`，不把发货放入补偿运维。
- 用户侧物流轨迹：本期只做发货结果只读展示，不接三方轨迹接口。

## Requirements

### Backend Contract

- 新增管理端 Gateway route：`/api/admin/fulfillments/**` -> logistics-service，JWT protected。
- 新增权限常量：`LOGISTICS_FULFILLMENT_ADMIN`。
- logistics-service 管理 API：
  - `GET /api/admin/fulfillments`
  - `GET /api/admin/fulfillments/{orderId}`
  - `POST /api/admin/fulfillments/{orderId}/ship`
- `ship` request body:
  - `requestId` required, trimmed, max length bounded.
  - `carrier` required, trimmed.
  - `trackingNo` required, trimmed.
- 所有 admin API 使用 trusted `SanguiPrincipal`，effective `shopId` 从 principal 推导；前端不得传 `shopId` 扩权。
- 只允许 `paid -> shipped`；`created`、`cancelled`、已 `shipped` 且 payload 不同必须拒绝或走幂等冲突。
- 重复同一 `requestId` 且 payload 相同返回既有发货结果；同 `requestId` payload 不同返回 `IDEMPOTENCY_CONFLICT`。
- 内部 order-service 契约：
  - `POST /internal/orders/shipments/confirmations`
  - 输入包含 `shopId`、`orderId`、`requestId`、`carrier`、`trackingNo`。
  - 只由 order-service 验证订单存在、shop scope、状态为 `paid`，并更新订单状态为 `shipped`。
- 商城端订单详情 `GET /api/orders/{orderId}` 需要返回发货只读字段，至少包含 `fulfillmentStatus`、`carrier`、`trackingNo`、`shippedAt`。

### Data Model

- order-service：
  - 扩展 `OrderStatus` 增加 `shipped`。
  - `oms_order` 可增加 fulfillment 快照字段，供用户订单详情稳定展示：
    - `fulfillment_status`
    - `carrier`
    - `tracking_no`
    - `shipped_at`
    - `shipment_request_id`
    - `shipment_trace_id`
- logistics-service：
  - 新增 `lgs_shipment` 表。
  - 必备字段：`id`、`shop_id`、`order_id`、`order_no`、`user_id`、`carrier`、`tracking_no`、`status`、`request_id`、`trace_id`、`created_at`、`updated_at`、`deleted`、`version`。
  - 唯一约束：
    - `UNIQUE(shop_id, order_id)` 防止订单重复发货。
    - `UNIQUE(shop_id, request_id)` 防止重复提交生成多票。
  - 查询索引：
    - `(shop_id, status, created_at)`
    - `(shop_id, order_no)`
    - `(shop_id, user_id, created_at)`

### Admin Frontend

- `/admin` 增加「发货管理」工作区，要求 `ADMIN` role 或 `LOGISTICS_FULFILLMENT_ADMIN` permission。
- 发货列表筛选：
  - 订单号、用户、发货状态、时间范围、分页。
- 列表展示：
  - 订单号、用户、金额、支付状态、履约状态、承运商/物流单号、创建时间。
- UI 状态：
  - loading、empty、error、retry、pagination。
  - 发货提交 pending disabled，防重复提交。
- 发货操作：
  - 仅 paid / awaiting shipment 订单启用。
  - 输入 carrier、trackingNo，提交时 trim。
  - 成功后刷新列表与详情。
  - 错误展示保留 backend `code/message/traceId`。
- 文案通过 `useAppPreferences().t()`，新增颜色只使用 CSS 变量。

### Mall Frontend

- 商城订单详情/订单结果区域展示发货状态。
- 未发货显示「待发货」。
- 已发货显示承运商、物流单号、发货时间。
- 对未知后端状态做 fallback，显示原始状态而不是崩溃。

## Acceptance Criteria

- [ ] 管理端有独立发货管理入口，补偿运维权限无法看到或操作发货。
- [ ] paid 订单可填写承运商和物流单号并发货，返回 `ADMIN_FULFILLMENT_SHIPPED`。
- [ ] created/cancelled 订单发货返回业务错误，不改变订单或 shipment。
- [ ] 重复相同 `requestId` + 相同 payload 返回既有结果。
- [ ] 重复相同 `requestId` + 不同 payload 返回 `IDEMPOTENCY_CONFLICT`。
- [ ] shop scope 隔离：不同 `shopId` principal 查询不到/发不了货。
- [ ] 发货后用户订单详情展示已发货、承运商、物流单号。
- [ ] 前端发货表单 trim payload，pending 时不重复提交，错误保留 `traceId`。
- [ ] backend/frontend spec 更新为可执行契约，包括 API、字段、状态机、错误矩阵、Good/Base/Bad cases、测试命令。

## Test Plan

- Backend targeted tests:
  - logistics service shipment application/controller/repository/migration tests.
  - order service internal shipment confirmation service/controller/migration tests.
  - gateway protected route test.
  - user/common-security permission test.
- Frontend tests:
  - fulfillment model query trimming, status label fallback, ship payload trimming.
  - requestId generation/preservation.
  - duplicate submit guard.
  - backend error `code/message/traceId` preservation.
  - mall order fulfillment label fallback.
- Verification commands should use targeted Maven reactor selectors and frontend `npm run typecheck`, `npm run lint`, `npm run build`, plus affected Vitest tests where sandbox allows.

## Implementation Plan

1. Configure Trellis fullstack task context with backend/frontend specs and cross-layer guide.
2. Add executable backend spec addendum for fulfillment contract before implementation.
3. Implement backend permission, gateway route, logistics shipment schema/repository/service/controller, and order internal shipment confirmation.
4. Extend order detail DTOs for admin/mall fulfillment fields.
5. Implement frontend admin fulfillment service/types/model/composable/view and `/admin` navigation gate.
6. Extend mall order display with fulfillment state.
7. Add focused backend and frontend tests.
8. Run `$check` quality pass, fix violations, then produce `$finish-work` commands.

## Open Questions Before Coding

- 是否接受新增独立权限 `LOGISTICS_FULFILLMENT_ADMIN`，并让 legacy admin session 默认拥有该权限以便本地管理端可见？
- 发货列表是否以 logistics-service 为主并通过内部 order snapshot 获取 paid 待发货订单，还是 order-service 暴露 admin fulfillment projection 给 logistics-service？推荐后者作为内部 order read contract，避免 logistics-service 读 `oms_*`。
- `shipped` 是否作为订单主状态直接加入现有 `OrderStatus`？推荐加入，否则商城端主状态仍停在 `paid`，履约状态需要双状态解释，复杂度反而更高。
