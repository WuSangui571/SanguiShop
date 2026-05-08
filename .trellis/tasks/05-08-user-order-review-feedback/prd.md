# 用户侧订单评价与已完成订单反馈体验

## Goal

在用户订单完成后补齐低风险的评价反馈闭环：用户只能评价自己名下、当前店铺内、已完成且尚未评价的订单；评价提交具备幂等保护；订单详情和列表能稳定展示“待评价/已评价”状态；深链恢复 completed 订单时能正确禁用重复评价。

## Scope

- 用户侧订单评价 API、评价快照 DTO、幂等与重复评价边界。
- 评价数据持久化、唯一约束、迁移契约测试。
- 商城订单详情和 completed 列表项的评价状态展示、提交、防重复点击和错误保留。
- 深链 `/mall?orderId=...` 恢复 completed 已评价订单。
- 后端与前端 spec 同步。

## Out of Scope

- 退款/售后申请与资金逆向流。
- 商品详情页评价聚合展示。
- 商家侧评价管理、审核、隐藏、回复。
- 图片真实上传/存储服务；本期最多保留图片 URL 数组边界，若现有上传契约不足则先不接图片。
- MQ 同步、搜索索引、推荐系统消费评价。

## Recommended Architecture

本期推荐先将“订单评价提交与订单详情快照”放在 `services/sangui-order-service` 内实现，原因：

- 现有用户侧订单 API 已经由 `/api/orders/**` 归属 order-service，确认收货也在同一服务内完成。
- 本期核心边界是订单完成态、一单一评、订单详情/list 同步，直接在 order-service 查询和返回 `reviewed/review` 能避免前端 N+1 拉取和跨服务读库。
- 仓库已有 `sangui-review-service` 壳服务与 gateway `/api/reviews/**` 路由，但还没有领域实现。将商品详情评价聚合、商家侧评价管理、评价审核放到后续任务时，再定义 review-service 投影或迁移契约更稳。

如果决定强制由 review-service 拥有评价域，本 PRD 需要改为：review-service 提供 `/api/reviews/orders/{orderId}`，并通过 order-service 内部 API 校验订单归属和 completed 状态；订单详情/list 的 `reviewed` 需要额外投影或前端 detail 级加载。该方案范围明显更大，不建议作为本期默认切法。

## Backend Contract

### API

`POST /api/orders/{orderId}/reviews`

Request:

```json
{
  "requestId": "review-20260508-0001",
  "rating": 5,
  "content": "物流很快，商品符合预期",
  "imageUrls": []
}
```

Response code: `ORDER_REVIEW_CREATED`.

Response data:

```json
{
  "orderReviewId": 9001,
  "shopId": 1,
  "orderId": 101,
  "orderNo": "ORD...",
  "userId": "10001",
  "rating": 5,
  "content": "物流很快，商品符合预期",
  "imageUrls": [],
  "requestId": "review-20260508-0001",
  "traceId": "trace-review",
  "createdAt": "2026-05-08T10:00:00+08:00"
}
```

`GET /api/orders/{orderId}/review`

- Response code: `ORDER_REVIEW_DETAIL`.
- Missing review for an owned order may return `data: null` with success, or `ORDER_REVIEW_NOT_FOUND`; implementation should choose one behavior and document it before coding. Preferred: success with nullable data for easier frontend deep-link restoration.

`GET /api/orders/{orderId}` and `GET /api/orders`

Add compatible response fields:

```json
{
  "reviewed": true,
  "review": {
    "orderReviewId": 9001,
    "rating": 5,
    "content": "物流很快，商品符合预期",
    "imageUrls": [],
    "requestId": "review-20260508-0001",
    "traceId": "trace-review",
    "createdAt": "2026-05-08T10:00:00+08:00"
  }
}
```

Rules:

- Controller uses trusted `SanguiPrincipal`; request body must not carry `shopId` or `userId`.
- Effective scope is `(principal.shopId, principal.userId, orderId)`.
- Missing order or wrong owner returns `ORDER_NOT_FOUND`.
- New review is allowed only when order `status=completed`.
- `created`, `paid`, `shipped`, `cancelled`, and unsupported statuses return `ORDER_STATUS_INVALID`.
- Same `(shopId, userId, requestId)` replay returns the original review response.
- Same order with a different requestId returns `ORDER_REVIEW_ALREADY_EXISTS`.
- Same requestId with different payload returns `IDEMPOTENCY_CONFLICT`.
- Logs include `traceId`, `shopId`, `userId`, `orderId`, `orderNo`, `requestId`, `rating`, and `outcome`.

### Validation

| Field | Rule |
| --- | --- |
| `orderId` | path id `>= 1` |
| `requestId` | required, trimmed, max 64 |
| `rating` | integer 1-5 |
| `content` | optional after trim; max length to be fixed during implementation, preferred 500 |
| `imageUrls` | optional; max count to be fixed during implementation, preferred 6; no blank URL |

### Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Invalid path id or request body | 400 | `VALIDATION_FAILED` |
| Missing order or wrong owner | 404 | `ORDER_NOT_FOUND` |
| Non-completed order | 409 | `ORDER_STATUS_INVALID` |
| Same order different requestId after review exists | 409 | `ORDER_REVIEW_ALREADY_EXISTS` |
| Same requestId different payload | 409 | `IDEMPOTENCY_CONFLICT` |

### Database

Migration: `services/sangui-order-service/src/main/resources/db/migration/V8__create_order_review_tables.sql`

Table: `oms_order_review`

Required columns:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `shop_id BIGINT NOT NULL DEFAULT 1`
- `order_id BIGINT NOT NULL`
- `order_no VARCHAR(64) NOT NULL`
- `user_id VARCHAR(64) NOT NULL`
- `rating INT NOT NULL`
- `content VARCHAR(500) NULL`
- `image_urls JSON NULL` or `TEXT NULL` depending existing project parser support
- `request_id VARCHAR(64) NOT NULL`
- `trace_id VARCHAR(64) NULL`
- `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
- `deleted TINYINT NOT NULL DEFAULT 0`
- `version INT NOT NULL DEFAULT 0`

Required constraints and indexes:

- `uk_oms_order_review_shop_order (shop_id, order_id)`
- `uk_oms_order_review_shop_user_request (shop_id, user_id, request_id)`
- `idx_oms_order_review_shop_user_created (shop_id, user_id, created_at)`

## Frontend Contract

Types:

- Add `OrderReviewResponse`.
- Add `CreateOrderReviewRequest`.
- Add `reviewed?: boolean`.
- Add `review?: OrderReviewResponse | null`.

API service:

- `createOrderReview(orderId, payload)` -> `POST /api/orders/{orderId}/reviews`.
- `getOrderReview(orderId)` -> `GET /api/orders/{orderId}/review` only if needed beyond `OrderResponse.review`.

UI/model rules:

- completed + not reviewed shows “评价” action.
- completed + reviewed shows “已评价” state and disables submit.
- created/paid/shipped/cancelled/unknown disable evaluation with explicit reason.
- pending submit ignores duplicate click and sends no second request.
- success merges `reviewed=true` and `review` into current detail and loaded order list item; item stays in completed main filter.
- failure preserves completed order detail and displays backend `code/message/traceId`.
- deep-linked completed order with `reviewed=true` or non-null `review` must not show an enabled submit button.
- no separate “待评价/已评价” top-level filter in this phase; completed list item displays the review state inline.

## Acceptance Criteria

- [ ] completed, unreviewed owned order can submit one review.
- [ ] created, paid/unshipped, shipped, cancelled, and unknown statuses cannot submit review and show clear reasons.
- [ ] already reviewed order disables repeat review.
- [ ] duplicate pending frontend submit sends no second request.
- [ ] repeated backend `requestId` returns the original review response.
- [ ] same order with different `requestId` returns `ORDER_REVIEW_ALREADY_EXISTS`.
- [ ] same requestId with different payload returns `IDEMPOTENCY_CONFLICT`.
- [ ] success updates detail/list/filter state to “已评价” without removing the order from completed.
- [ ] failure preserves completed detail/order snapshot and shows backend `code/message/traceId`.
- [ ] deep-link completed reviewed order restores reviewed state and disables review submission.
- [ ] backend tests cover controller validation, principal ownership, state machine, idempotency, duplicate order review, migration constraints, and log context.
- [ ] frontend tests cover action state matrix, duplicate pending guard, success merge, failure retention, and deep-link restore.
- [ ] `.trellis/spec/backend/order-create-contracts.md`, `.trellis/spec/backend/database-guidelines.md`, and `.trellis/spec/frontend/api-contracts.md` are updated with concrete review contracts.

## Implementation Plan

1. Backend contract and persistence
   - Add review DTOs, domain record/repository methods, migration contract test, and error code.
   - Extend order query snapshot/response mapping with nullable review summary.

2. Backend use case/API
   - Add review service and controller endpoints.
   - Enforce principal scope, completed-only state, validation, idempotency, duplicate order review behavior, and logs.

3. Frontend model/API
   - Add API types and service functions.
   - Add pure model helpers for review action state, review labels, and merge behavior.

4. Frontend UI
   - Add completed order detail review form/action.
   - Show completed list inline “待评价/已评价”.
   - Keep deep-link and active filter behavior stable.

5. Tests and spec sync
   - Add targeted backend and frontend tests.
   - Update cross-layer specs with endpoint, fields, validation/error matrix, and required commands.

## Relevant Specs Read

- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/order-create-contracts.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/architecture-review-checklist.md`

## Open Decision Before Coding

- Confirm implementation ownership: proceed with order-service-owned MVP as recommended, or force review-service ownership despite wider cross-service scope.
- Confirm image handling: keep `imageUrls` field with validation only, or omit images entirely until upload/storage contract exists.
