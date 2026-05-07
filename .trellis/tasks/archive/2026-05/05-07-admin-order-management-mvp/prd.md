# 管理端订单管理 MVP

## Goal

为 `/admin` 工作区新增订单管理入口，让运营人员可以按订单维度查询用户下单、支付状态、取消状态、商品快照、库存预留结果和排障 trace 信息，并支持对未支付订单执行取消和状态刷新。

## Scope

- 前端：扩展 `/admin` 工作区导航，新增订单列表、筛选、分页、详情、取消未支付订单、刷新订单/支付状态。
- 后端：如现有 order-service 仅覆盖商城用户侧 API，则新增 admin order list/detail/cancel API；gateway 增加 `/api/admin/orders/**` 路由和权限保护。
- 契约：补齐 backend/frontend spec，明确 admin order API、状态展示、错误矩阵、requestId 规则和测试断言。

## Requirements

- 管理端导航新增「订单管理」，与「商品管理」「补偿运维」保持独立边界。
- 订单管理入口面向 `ADMIN` / 订单运营权限，不混入补偿运维的 `OPS_COMPENSATION_ADMIN` 语义。
- 订单列表支持按状态、订单号、用户、时间范围筛选。
- 列表展示订单号、用户、金额、状态、支付单号、创建时间、商品件数。
- 列表必须处理 loading、empty、error、retry、pagination。
- 订单详情展示基础信息、商品快照、SKU、数量、单价、总价。
- 详情展示 `reservationNo`、`paymentNo`、`traceId`，用于排查库存预留和支付链路。
- 详情展示状态流转信息，至少覆盖 `created`、`paid`、`cancelled`，未知状态必须 fallback 显示原始值。
- 支持取消未支付订单，仅允许 `created` 状态触发。
- 支持刷新订单状态；如有 `paymentNo`，支持刷新支付状态。
- 所有写按钮 pending 时 disabled，避免重复提交。
- 取消写请求必须带前端生成的 `requestId`。
- 前端文案必须接入 `useAppPreferences().t()`；业务数据如订单号、支付单号、traceId、后端 `code/message` 不翻译。
- 新 UI 颜色继续使用 CSS 变量，不在页面组件中硬编码主题色。
- 后端必须从 trusted principal 推导 `shopId` 和 operator scope，不信任前端传入 `shopId` / `userId` 来扩大查询范围。
- 后端错误保持统一 envelope，至少覆盖 `AUTH_FORBIDDEN`、`ORDER_NOT_FOUND`、`ORDER_STATUS_INVALID`、`VALIDATION_FAILED`。

## Proposed Admin API Contract

### `GET /api/admin/orders`

Query parameters:

- `page`: optional positive integer, default `1`
- `size`: optional positive integer, default `20`, max `100`
- `status`: optional enum/string, omit or `all` means no status filter
- `orderNo`: optional trimmed string
- `userId`: optional trimmed string
- `fromTime`: optional ISO-8601 string
- `toTime`: optional ISO-8601 string

Response code: `ADMIN_ORDER_LIST`

Response data:

```json
{
  "page": 1,
  "size": 20,
  "total": 1,
  "items": [
    {
      "orderId": 101,
      "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
      "shopId": 1,
      "userId": "10001",
      "status": "created",
      "totalAmountCent": 119800,
      "paymentNo": "PAY-20260501-0001",
      "itemCount": 2,
      "traceId": "trace-order-create",
      "createdAt": "2026-05-01T21:30:00+08:00",
      "updatedAt": "2026-05-01T21:30:00+08:00"
    }
  ]
}
```

Rules:

- Gateway route is `/api/admin/orders/**`.
- Downstream service must enforce admin/order-ops permission, not rely only on gateway.
- `shopId` comes from trusted principal.
- Filter `userId` is a query condition inside the trusted shop scope, not the principal user scope.
- Results are ordered by newest first with deterministic tie-breaker by `orderId DESC`.

### `GET /api/admin/orders/{orderId}`

Response code: `ADMIN_ORDER_DETAIL`

Response data:

```json
{
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "requestId": "req-20260501-0001",
  "reservationNo": "ord:10001:req-20260501-0001",
  "paymentNo": "PAY-20260501-0001",
  "status": "created",
  "totalAmountCent": 119800,
  "traceId": "trace-order-create",
  "createdAt": "2026-05-01T21:30:00+08:00",
  "updatedAt": "2026-05-01T21:30:00+08:00",
  "items": [
    {
      "productId": 301,
      "skuId": 401,
      "skuName": "Sneaker 42",
      "priceCent": 59900,
      "quantity": 2,
      "lineAmountCent": 119800
    }
  ],
  "statusTimeline": [
    {
      "status": "created",
      "occurredAt": "2026-05-01T21:30:00+08:00",
      "traceId": "trace-order-create"
    }
  ]
}
```

Rules:

- Missing order or wrong shop scope returns `ORDER_NOT_FOUND`.
- DTOs must be snapshots, not `oms_order` entities.
- If the current schema does not store a full status history, MVP timeline may derive from stable row timestamps and current status, and the spec must state that limitation.

### `POST /api/admin/orders/{orderId}/cancel`

Request:

```json
{
  "requestId": "adm-cancel-20260507-0001"
}
```

Response code: `ADMIN_ORDER_CANCELLED`

Rules:

- Only `created` orders can be cancelled.
- Cancel must reuse order-service cancellation logic so inventory reservation is released before status becomes `cancelled`.
- Repeated cancel for an already cancelled order should be idempotent only if existing service cancel semantics support it; otherwise return `ORDER_STATUS_INVALID` and document behavior before implementation.
- The admin cancel idempotency key is `requestId` scoped by `(shopId, operatorUserId, orderId)` if a new admin operation log/table is introduced; if no new table is added in MVP, frontend duplicate guard plus service status idempotency is the minimum.

## Error Matrix

| Case | HTTP | code | UI behavior |
| --- | --- | --- | --- |
| Missing token | 401 | `AUTH_TOKEN_MISSING` | route to login/session recovery |
| Permission missing | 403 | `AUTH_FORBIDDEN` | show forbidden state |
| Invalid filter/page/request body | 400 | `VALIDATION_FAILED` | show backend message and keep filter values |
| Order missing or outside shop scope | 404 | `ORDER_NOT_FOUND` | show empty detail/error state |
| Cancel paid/cancelled/unsupported order | 409 | `ORDER_STATUS_INVALID` | keep current detail visible and show backend `traceId` |
| Downstream inventory release failure | 503 | `DOWNSTREAM_TIMEOUT` | keep cancel button recoverable after pending ends |

## Frontend Acceptance Criteria

- [ ] `/admin` navigation includes Product Management, Order Management, and Compensation Ops as separate entries.
- [ ] Order list supports status, orderNo, userId, fromTime, toTime, pagination, retry.
- [ ] List shows orderNo, user, amount, status, paymentNo, createdAt, item count.
- [ ] Empty/loading/error states render without layout overlap on desktop and mobile widths.
- [ ] Detail shows order snapshot, items, reservationNo, paymentNo, traceId, and status timeline.
- [ ] Cancel button only enables for `created` orders and is disabled while pending.
- [ ] Cancel request includes a generated `requestId`.
- [ ] Refresh actions preserve backend `code/message/traceId` on errors.
- [ ] New copy uses `useAppPreferences().t()` for `zh-Hans`, `zh-Hant`, and `en`.
- [ ] New colors use semantic CSS variables.

## Backend Acceptance Criteria

- [ ] Gateway routes `/api/admin/orders/**` to order-service and requires JWT.
- [ ] order-service enforces admin/order-ops permission and trusted `shopId`.
- [ ] Admin list filters by trusted shop scope and optional order status/orderNo/user/time range.
- [ ] Admin detail returns order snapshot, items, reservationNo, paymentNo when available, traceId, and status timeline.
- [ ] Admin cancel reuses the existing cancel/release reservation path and rejects invalid status transitions.
- [ ] Controllers validate pagination, path ids, and request body.
- [ ] Error responses use unified envelope with `code/message/traceId`.

## Tests

Frontend:

- [ ] Filter payload building omits `all`/blank filters and preserves valid time values.
- [ ] Pagination model clamps/defaults page and size.
- [ ] Status labels handle `created`, `paid`, `cancelled`, and unknown raw values.
- [ ] Backend error `code/message/traceId` is preserved.
- [ ] Cancel duplicate submit guard sends at most one request while pending.
- [ ] `requestId` is generated for cancel and reused during the pending attempt.

Backend:

- [ ] Admin list returns only trusted shop orders and applies filters.
- [ ] Admin detail returns `ORDER_NOT_FOUND` for missing or wrong shop order.
- [ ] Admin cancel succeeds for `created` and releases reservation through existing path.
- [ ] Admin cancel rejects `paid`/unsupported status with `ORDER_STATUS_INVALID`.
- [ ] Permission denial returns `AUTH_FORBIDDEN`.
- [ ] Gateway route/security test covers `/api/admin/orders/**`.

Spec:

- [ ] Update backend order/admin API contract with request/response/error matrix/tests.
- [ ] Update frontend API contract with admin order API, UI state, requestId, traceId rules.

## Out of Scope

- Shipping/fulfillment management.
- Refund/after-sales workflows.
- Manual payment reconciliation beyond existing compensation ops.
- Bulk cancel or bulk export.
- New payment provider integration.
- Full immutable status history table unless existing schema already supports it.

## Implementation Plan

1. Research existing admin product and compensation ops patterns for routing, auth context, API services, composables, tests, gateway routes, and service permissions.
2. Confirm existing order-service and payment-service data/query capabilities, especially whether `paymentNo` can be resolved from order-service without cross-service DB reads.
3. Update backend/frontend specs with executable admin order contract before or alongside implementation.
4. Implement backend admin order list/detail/cancel contracts and gateway route/security coverage.
5. Implement frontend API service/types, pure model helpers, composable, route/nav integration, and page UI.
6. Add targeted frontend and backend tests.
7. Run targeted typecheck/lint/build and Maven service tests.
8. Run `$check` quality pass and fix findings before `$finish-work`.
