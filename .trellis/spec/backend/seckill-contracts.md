# Seckill Contracts

## Scope / Trigger

任何涉及秒杀活动、限时库存、抢购资格、秒杀令牌、Redis 扣减、异步下单、订单创建、支付联动的改动必须读取本文件。

## End-to-End Flow

1. 前端加载活动和服务器时间。
2. 用户登录后请求秒杀令牌。
3. Gateway 校验 JWT、限流、转发。
4. Seckill Service 校验活动状态、令牌、购买资格。
5. Redis Lua 原子预扣库存并记录用户购买标识。
6. Seckill Service 发送 `SECKILL_ORDER_REQUESTED` 事件。
7. Order Service 消费事件，创建订单，落库唯一约束防重复。
8. Payment Service 发起支付，回调后更新订单状态。

## API Contracts

### Admin Seckill Activity Management APIs

所有管理端秒杀活动 API 经 Gateway 暴露，路径前缀 `/api/admin/seckill/activities`。

#### Routes

| Method | Path | Purpose | Success code |
| --- | --- | --- | --- |
| `GET` | `/api/admin/seckill/activities` | 分页查询管理端秒杀活动 | `ADMIN_SECKILL_ACTIVITY_LIST` |
| `GET` | `/api/admin/seckill/activities/{activityId}` | 查询活动详情与 SKU 绑定快照 | `ADMIN_SECKILL_ACTIVITY_DETAIL` |
| `POST` | `/api/admin/seckill/activities` | 创建活动及初始 SKU 绑定 | `ADMIN_SECKILL_ACTIVITY_CREATED` |
| `PUT` | `/api/admin/seckill/activities/{activityId}` | 更新活动基础信息和可编辑 SKU 配置 | `ADMIN_SECKILL_ACTIVITY_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/status` | 管理端状态流转 | `ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/skus` | 绑定或更新单个 SKU 活动库存/秒杀价 | `ADMIN_SECKILL_ACTIVITY_SKU_BOUND` |

#### Principal and Scope

- Controller 使用 `SanguiPrincipal`，`shopId` 以 principal 为权威。
- `ADMIN` role 或 `SECKILL_ACTIVITY_ADMIN` permission 允许访问。
- `OPS_COMPENSATION_ADMIN` alone 必须被服务端拒绝，返回 403 / `AUTH_FORBIDDEN`。
- 所有查询和写入必须以 `principal.shopId()` 限定。

#### Activity Status State Machine

```text
draft -> scheduled -> active -> ended
```

- Status transitions are explicit and must be validated server-side.
- Invalid transitions return `SECKILL_ACTIVITY_STATUS_INVALID`.

#### Request Fields

`GET /api/admin/seckill/activities`

| Field | Source | Validation |
| --- | --- | --- |
| `page` | query | default `1`, min `1` |
| `size` | query | default `20`, min `1`, max `100` |
| `status` | query | optional; blank or `all` means no status filter |

`POST /api/admin/seckill/activities` and `PUT /api/admin/seckill/activities/{activityId}`

```json
{
  "shopId": 1,
  "userId": "90001",
  "activityName": "Spring flash sale",
  "description": "optional",
  "startsAt": "2026-05-12T10:00:00+08:00",
  "endsAt": "2026-05-12T12:00:00+08:00",
  "requestId": "admin-generated-id",
  "skus": [
    {
      "productId": 301,
      "skuId": 401,
      "activityStock": 10,
      "seckillPriceCent": 49900
    }
  ]
}
```

Required behavior:

- `activityName` is required after trim.
- `startsAt` / `endsAt` are ISO-8601 strings accepted as `OffsetDateTime`.
- `startsAt < endsAt`; equal or reversed range returns `VALIDATION_FAILED`.
- `requestId` is required for all writes.
- `skus` may be empty or omitted for an admin draft. A draft without SKU can be filled later through `POST /api/admin/seckill/activities/{activityId}/skus`.
- `activityStock >= 0`.
- `seckillPriceCent > 0`.

`POST /api/admin/seckill/activities/{activityId}/status`

```json
{
  "status": "scheduled",
  "requestId": "admin-generated-id"
}
```

`POST /api/admin/seckill/activities/{activityId}/skus`

```json
{
  "productId": 301,
  "skuId": 401,
  "activityStock": 10,
  "seckillPriceCent": 49900,
  "requestId": "admin-generated-id"
}
```

Required behavior:

- `productId`, `skuId`, `requestId`, `activityStock` required.
- `activityStock >= 0`.
- `seckillPriceCent` optional only if backend can preserve an existing price; otherwise require `> 0`.
- Backend must validate SKU existence and stock using product-service authority via `ProductSkuSnapshotClient` interface.

#### Response Fields

All responses use `ApiResult<T>` with code, message, data, traceId, timestamp.

List item fields: `activityId`, `activityName`, `status`, `startsAt`, `endsAt`, `serverTime`, `skuCount`, `totalActivityStock`, `soldCount`.

Detail extends list item with: `description`, `skus`.

SKU response fields: `productId`, `productName`, `skuId`, `skuCode`, `skuName`, `priceCent`, `seckillPriceCent`, `availableStock`, `activityStock`, `soldCount`.

#### Validation & Error Matrix

| Condition | HTTP | code |
| --- | --- | --- |
| Principal lacks `ADMIN` and `SECKILL_ACTIVITY_ADMIN` | 403 | `AUTH_FORBIDDEN` |
| Missing/blank `activityName` | 400 | `VALIDATION_FAILED` |
| Invalid ISO time or `startsAt >= endsAt` | 400 | `VALIDATION_FAILED` |
| Missing write `requestId` | 400 | `VALIDATION_FAILED` |
| Activity not found in current shop | 404 | `SECKILL_ACTIVITY_NOT_FOUND` |
| Invalid status value or transition | 409 | `SECKILL_ACTIVITY_STATUS_INVALID` |
| SKU not found/unavailable in product-service authority | 404 | `PRODUCT_SKU_NOT_FOUND` |
| `activityStock > availableStock` | 409 | `PRODUCT_STOCK_NOT_ENOUGH` |
| Same `requestId` with identical payload | 200 | original success code |
| Same `requestId` with changed payload | 409 | `IDEMPOTENCY_CONFLICT` |

#### Idempotency

- All write operations require `requestId`.
- Same `requestId` with identical normalized payload returns the existing result (replay idempotent). For create/update, the normalized payload includes `activityName`, `description`, `startsAt`, `endsAt`, and every SKU item's `productId`, `skuId`, `activityStock`, and `seckillPriceCent`.
- Same `requestId` with different payload returns `IDEMPOTENCY_CONFLICT`.
- Status transition replay with same `requestId` and same target status is idempotent.
- Status writes persist a status request record keyed by `shopId + activityId + requestId`; replaying the same key with a different target status returns `IDEMPOTENCY_CONFLICT`.
- SKU bind replay with same `requestId`, `skuId`, `activityStock`, `seckillPriceCent` is idempotent.
- SKU bind validates that the requested `productId` matches the backend product snapshot for the requested `skuId`; a mismatch returns `PRODUCT_SKU_NOT_FOUND`.

#### Production Persistence & Product Authority

Production `ActivityRepository` is `JdbcActivityRepository` backed by `sk_activity`, `sk_activity_sku`, and `sk_activity_status_request` tables.

- `JdbcActivityRepository` replaces `InMemoryActivityRepository` — the in-memory implementation is no longer a production Spring bean.
- `ProductSkuSnapshotClientAdapter` is the production `ProductSkuSnapshotClient` implementation. The port signature is `findBySkuId(Long shopId, Long skuId, String traceId)`. It calls `POST /internal/products/skus/snapshot` on product-service via RestClient, sends `X-Trace-Id = traceId`, and maps the first matching item for `(shopId, skuId)`.
- Adapter maps missing SKU to `Optional.empty()` so the application returns `PRODUCT_SKU_NOT_FOUND`; productId mismatch is rejected by the application as `PRODUCT_SKU_NOT_FOUND`; downstream errors map to `DOWNSTREAM_TIMEOUT`; stock shortage maps to `PRODUCT_STOCK_NOT_ENOUGH`. It never returns a fake SKU snapshot.
- Internal DTO (`ProductSnapshotRequest` / `ProductSnapshotResponse`) lives in the adapter class; public response DTO is `ProductSkuSnapshotClient.ProductSkuSnapshot`.
- Application tests use a fake `ProductSkuSnapshotClient` and do not depend on the real adapter.
- `ActivityRepository.findSkuByRequestId(Long shopId, Long activityId, String requestId)` must include `shopId` in the lookup. `ActivityRepository.saveStatusRequest(Long shopId, Long activityId, String requestId, SeckillActivityStatus targetStatus, String traceId)` must persist `trace_id` to `sk_activity_status_request`.

#### Tests Required

- Controller WebMvc contract tests for all 6 routes.
- Service validation tests: activityName trim, time order, requestId required.
- Permission tests: ADMIN allowed, SECKILL_ACTIVITY_ADMIN allowed, OPS_COMPENSATION_ADMIN alone denied.
- Cross-shop isolation tests.
- Status transition tests: valid transitions, invalid transitions, unknown status.
- Idempotency tests: create/status/SKU replay identical and changed payload.
- SKU stock boundary tests: SKU not found, activityStock = availableStock, activityStock > availableStock, negative stock.
- Migration contract test: `SeckillActivityMigrationContractTest` validates `V1` SQL structure including platform columns, `shop_id`, `request_id`, `status`, unique indexes, and lookup indexes.
- Repository test: `JdbcActivityRepositoryTest` verifies create, update, list paging, status transition, SKU upsert, cross-shop isolation, and idempotency lookup queries.
- Adapter test: `ProductSkuSnapshotClientAdapterTest` verifies successful mapping, `X-Trace-Id` propagation, missing SKU, invalid envelope, and downstream exception patterns.

### Flash Sale Token & Order APIs

`POST /api/seckill/activities/{activityId}/token`

```json
{"shopId":1,"skuId":10001}
```

`POST /api/seckill/orders`

```json
{
  "shopId": 1,
  "activityId": 9001,
  "skuId": 10001,
  "quantity": 1,
  "seckillToken": "opaque-token",
  "requestId": "client-generated-id"
}
```

Accepted response:

```json
{"accepted":true,"requestNo":"SKQ202604290001","status":"QUEUED"}
```

## Idempotency and Uniqueness

- Redis bought key：`sangui:{env}:seckill:bought:{activityId}:{userId}`。
- DB 唯一索引：`UNIQUE(shop_id, activity_id, user_id)`。
- MQ event id：`eventId` 唯一。
- 客户端 `requestId` 只能辅助排查，不作为唯一购买约束的唯一来源。

## State Machine

```text
QUEUED -> ORDER_CREATED -> WAITING_PAYMENT -> PAID -> FULFILLING -> COMPLETED
QUEUED -> FAILED
WAITING_PAYMENT -> CANCELLED_TIMEOUT
```

## Validation & Error Matrix

| 条件 | code | 可重试 |
| --- | --- | --- |
| 活动不存在 | `SECKILL_ACTIVITY_NOT_FOUND` | 否 |
| 活动未开始 | `SECKILL_NOT_STARTED` | 可稍后重试 |
| 活动已结束 | `SECKILL_ENDED` | 否 |
| token 缺失/过期 | `SECKILL_TOKEN_INVALID` | 需重新获取 |
| 库存不足 | `STOCK_NOT_ENOUGH` | 否 |
| 用户已购买 | `SECKILL_DUPLICATE_BUY` | 否，返回已有状态 |
| 队列繁忙 | `SECKILL_QUEUE_BUSY` | 可重试，带退避 |

## Tests Required

- Lua 并发扣减测试。
- 同用户重复购买测试。
- MQ 重复消费测试。
- 订单创建失败补偿测试。
- JMeter/Locust 压测报告：QPS、P95/P99、Redis 耗时、MQ 积压、DB 写入速率。
