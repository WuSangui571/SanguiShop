# Order Create Contracts

## Scope

Order Create + Cancel MVP for `services/sangui-order-service`.

This flow now depends on product-service inventory reservations instead of read-only SKU snapshots.

## External APIs

### `POST /api/orders`

Request:

```json
{
  "shopId": 999,
  "userId": "spoof-user",
  "requestId": "req-20260501-0001",
  "items": [
    {
      "skuId": 401,
      "quantity": 2
    }
  ]
}
```

Response:

```json
{
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "requestId": "req-20260501-0001",
  "status": "created",
  "totalAmountCent": 119800,
  "items": [
    {
      "productId": 301,
      "skuId": 401,
      "skuName": "Sneaker 42",
      "priceCent": 59900,
      "quantity": 2,
      "lineAmountCent": 119800
    }
  ]
}
```

Rules:

- Controller must use trusted `SanguiPrincipal`.
- Effective `shopId` / `userId` come from principal only.
- `requestId` is required and is the order-create idempotency key inside `(shopId, userId)`.
- Duplicate `skuId` values in one request are rejected.
- Order create must reserve inventory before persisting `oms_order`.
- Reservation number is deterministic: `ord:{userId}:{requestId}`.

### `POST /api/orders/{orderId}/cancel`

Response success code: `ORDER_CANCELLED`.

Rules:

- Only the owning principal can cancel.
- Only `created` orders can cancel.
- Cancel must release the associated inventory reservation before the order becomes `cancelled`.
- Repeating cancel on an already `cancelled` order is idempotent and returns the current cancelled snapshot.

### `GET /api/orders/{orderId}`

Response success code: `ORDER_DETAIL`.

Response:

```json
{
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "requestId": "req-20260501-0001",
  "status": "created",
  "totalAmountCent": 119800,
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
  "createdAt": "2026-05-01T21:30:00+08:00",
  "updatedAt": "2026-05-01T21:30:00+08:00"
}
```

Rules:

- Controller must use trusted `SanguiPrincipal`.
- Effective `shopId` / `userId` come from principal only.
- Missing order or an order owned by another user returns `ORDER_NOT_FOUND`.
- DTO fields come from `OrderSnapshot`; controller must not expose `oms_order` entity objects.

### `GET /api/orders?page=&size=`

Response success code: `ORDER_LIST`.

Rules:

- Controller must use trusted `SanguiPrincipal`; query parameters must never carry `shopId` or `userId`.
- `page` defaults to 1 and must be positive.
- `size` defaults to 10, must be positive, and is capped at 50.
- Results are scoped to `(shopId, userId)` and ordered by newest order first.

## Product Inventory Dependency Contract

Order-service uses:

- `POST /internal/products/inventory/reservations`
- `POST /internal/products/inventory/releases`

Rules:

- order-service must not read `pms_*` tables directly.
- inventory reservation response provides immutable `productId`, `skuId`, `skuName`, and `priceCent` snapshot data for order items.
- downstream timeout maps to `DOWNSTREAM_TIMEOUT`.
- inventory shortage maps to `ORDER_STOCK_NOT_ENOUGH`.

## Internal Payment Contract

### `POST /internal/orders/payment-snapshot`

Response data now includes `reservationNo`:

```json
{
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "reservationNo": "ord:10001:req-20260501-0001",
  "status": "created",
  "totalAmountCent": 119800
}
```

### `POST /internal/orders/payment-confirmations`

Rules:

- owner + amount validation stay unchanged
- `paid` remains idempotent
- payment-service uses the returned `reservationNo` to confirm inventory in product-service

## State Machine

Persisted order statuses:

- `created`
- `cancelled`
- `paid`
- `completed`

Valid transitions:

| Current | Operation | Next |
| --- | --- | --- |
| none | create with successful reserve | `created` |
| `created` | cancel + release reserve | `cancelled` |
| `created` | payment confirm | `paid` |
| `paid` | cancel | invalid |

## Customer Receipt Confirmation Addendum

### `POST /api/orders/{orderId}/receipt-confirmations`

Request:

```json
{
  "requestId": "receipt-20260507-0001"
}
```

Response code: `ORDER_RECEIPT_CONFIRMED`.

Rules:

- Controller must use trusted `SanguiPrincipal`; request body must not carry `shopId` or `userId`.
- `requestId` is required and trimmed before persistence.
- Only the owning principal within `(shopId, userId)` can confirm receipt.
- Only `shipped -> completed` is valid for a new confirmation.
- A `completed` order returns the current completed snapshot as an idempotent terminal result.
- `created`, `paid`, `cancelled`, and unsupported statuses return `ORDER_STATUS_INVALID`.
- Logs must include `traceId`, `shopId`, `userId`, `orderId`, `orderNo`, and `requestId`.

Additional response fields:

- `completedAt`

Fulfillment mapping:

- `completed` exposes `fulfillmentStatus=completed` and keeps carrier/tracking/shipped snapshot fields.

Database addendum:

- `services/sangui-order-service/src/main/resources/db/migration/V7__add_order_receipt_confirmation_snapshot.sql`
- Required columns on `oms_order`: `receipt_request_id`, `receipt_trace_id`, `completed_at`.
- Required index: `idx_oms_order_shop_completed_created (shop_id, status, completed_at)`.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Invalid path id or blank `requestId` | 400 | `VALIDATION_FAILED` |
| Missing order or wrong owner | 404 | `ORDER_NOT_FOUND` |
| Confirm `created`, `paid`, `cancelled`, or unsupported status | 409 | `ORDER_STATUS_INVALID` |

Required tests:

```powershell
mvn -q "-Dtest=OrderReceiptConfirmationServiceTest,OrderControllerTest,OrderQueryServiceTest,OrderReceiptConfirmationMigrationContractTest" test
```

Good/Base/Bad cases:

- Good: shipped order becomes completed and persists receipt request id, trace id, and completed time.
- Good: completed order replay returns the completed snapshot without mutating logistics fields.
- Good: wrong user receives `ORDER_NOT_FOUND`.
- Base: no inventory, payment, MQ, or logistics-service side effect is required for receipt confirmation in this MVP.
- Bad: frontend marks an order completed without a backend state transition.

## Customer Order Review Addendum

Order review MVP is owned by `services/sangui-order-service` because the first-phase user experience needs completed order state, one-order-one-review uniqueness, and `OrderResponse.reviewed/review` snapshots without cross-service projection.

### `POST /api/orders/{orderId}/reviews`

Request:

```json
{
  "requestId": "review-20260508-0001",
  "rating": 5,
  "content": "Product and delivery matched expectations.",
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
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "userId": "10001",
  "rating": 5,
  "content": "Product and delivery matched expectations.",
  "imageUrls": [],
  "requestId": "review-20260508-0001",
  "traceId": "trace-review",
  "createdAt": "2026-05-08T10:00:00+08:00"
}
```

Rules:

- Controller must use trusted `SanguiPrincipal`; request body must not carry `shopId` or `userId`.
- Only the owning principal within `(shopId, userId)` can review an order.
- Only `completed` orders can receive a new review.
- `created`, `paid`, `shipped`, `cancelled`, and unsupported statuses return `ORDER_STATUS_INVALID`.
- Replaying the same `(shopId, userId, requestId)` with the same `orderId`, `rating`, normalized `content`, and normalized `imageUrls` returns the original review.
- Replaying the same `(shopId, userId, requestId)` with a different payload returns `IDEMPOTENCY_CONFLICT`.
- Reviewing the same order with a different request id returns `ORDER_REVIEW_ALREADY_EXISTS`.
- Logs must include `traceId`, `shopId`, `userId`, `orderId`, `orderNo`, `requestId`, `rating`, and `outcome`.

### `GET /api/orders/{orderId}/review`

Response code: `ORDER_REVIEW_DETAIL`.

Rules:

- Controller uses trusted `SanguiPrincipal`.
- Missing order or wrong owner returns `ORDER_NOT_FOUND`.
- An owned order without a review returns success with `data = null` so deep-link recovery can distinguish "no review yet" from missing access.

### Order Response Review Snapshot

`GET /api/orders/{orderId}` and `GET /api/orders` add compatible fields:

```json
{
  "reviewed": true,
  "review": {
    "orderReviewId": 9001,
    "shopId": 1,
    "orderId": 101,
    "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
    "userId": "10001",
    "rating": 5,
    "content": "Product and delivery matched expectations.",
    "imageUrls": [],
    "requestId": "review-20260508-0001",
    "traceId": "trace-review",
    "createdAt": "2026-05-08T10:00:00+08:00"
  }
}
```

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Invalid path id, blank `requestId`, missing/non-1..5 rating, content > 500, image count > 6, or blank image URL | 400 | `VALIDATION_FAILED` |
| Missing order or wrong owner | 404 | `ORDER_NOT_FOUND` |
| Review non-`completed` order | 409 | `ORDER_STATUS_INVALID` |
| Same order reviewed with different `requestId` | 409 | `ORDER_REVIEW_ALREADY_EXISTS` |
| Same `requestId` with different payload | 409 | `IDEMPOTENCY_CONFLICT` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service" -am "-Dtest=OrderReviewServiceTest,OrderControllerTest,OrderQueryServiceTest,OrderReviewMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: completed owned order can create one review and response snapshots expose `reviewed=true`.
- Good: same request id and same payload replays the existing review without inserting another row.
- Good: same order with another request id returns `ORDER_REVIEW_ALREADY_EXISTS`.
- Good: wrong user receives `ORDER_NOT_FOUND`.
- Base: `imageUrls` is a validated string array only; real upload/storage remains out of scope.
- Bad: frontend marks an order reviewed without backend review creation.

## Product-Facing Review Query Addendum

Order-service owns the MVP review source and exposes an internal projection for product-service. This keeps public product detail APIs product-facing while avoiding product-service direct reads of order tables.

### `POST /internal/orders/reviews/by-product/query`

Request:

```json
{
  "shopId": 1,
  "productId": 301,
  "page": 1,
  "size": 10
}
```

Response code: `PRODUCT_REVIEWS_FETCHED`.

Response data:

```json
{
  "productId": 301,
  "averageRating": 4.5,
  "reviewCount": 2,
  "page": 1,
  "size": 10,
  "items": [
    {
      "reviewId": 9001,
      "rating": 5,
      "content": "Product matched expectations.",
      "imageUrls": [],
      "createdAt": "2026-05-08T10:00:00+08:00",
      "maskedUserId": "10***01",
      "skuName": "Size 42"
    }
  ]
}
```

Rules:

- Query source is `oms_order_review` joined to completed `oms_order` rows and scoped by `shop_id`.
- Product filtering uses immutable `oms_order_item.product_id`; order-service must not call product-service to infer product membership for this query.
- Only reviews whose order status is `completed` enter the public projection.
- Results sort by `review.created_at DESC, review.id DESC`.
- `averageRating` is rounded to one decimal; no-review products return `0.0`, `reviewCount=0`, and `items=[]`.
- `maskedUserId` is derived server-side; raw `userId`, `orderId`, `orderNo`, `requestId`, and `traceId` must not leave the internal projection.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing or invalid `shopId` / `productId` | 400 | `VALIDATION_FAILED` |
| `page <= 0` or `size <= 0` / `size > 50` | 400 | `VALIDATION_FAILED` |
| Product has no completed-order reviews | 200 | `PRODUCT_REVIEWS_FETCHED` with empty list |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service" -am "-Dtest=ProductReviewQueryServiceTest,InternalOrderReviewControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: completed orders with reviews appear under the purchased product id and include SKU snapshot name.
- Good: created, paid, shipped, cancelled, or unsupported order statuses do not appear.
- Good: public projection hides raw user/order/trace fields.
- Base: an order-level review may represent the purchased product item snapshot until per-SKU review rows exist.
- Bad: product detail frontend calls customer order list/detail to assemble reviews.

## Admin Review Management Addendum

### `GET /api/admin/reviews`

Response code: `ADMIN_REVIEW_LIST`.

Request query fields:

- `page`: optional positive integer, default `1`.
- `size`: optional positive integer, default `20`, capped at `100`.
- `productId`: optional positive product snapshot filter using `oms_order_item.product_id`.
- `rating`: optional integer `1..5`.
- `userId`: optional exact user filter inside trusted shop scope.
- `visibility`: optional `visible`, `hidden`, or `all`; omit or `all` means no visibility filter.
- `fromTime` / `toTime`: optional ISO-8601 timestamp filters on `oms_order_review.created_at`.

Response item fields:

- `reviewId`, `orderId`, `orderNo`, `productId`, `skuId`, `skuName`
- `rating`, `content`, `imageCount`, `maskedUserId`, `createdAt`
- `visibilityStatus`, nullable `visibilityReason`, nullable `visibilityRequestId`, nullable `visibilityOperator`, nullable `visibilityTraceId`, nullable `visibilityUpdatedAt`

Rules:

- Controller must use trusted `SanguiPrincipal`; request query must not carry `shopId`.
- order-service must enforce `ADMIN` role or `REVIEW_MANAGEMENT_ADMIN` permission.
- Results are scoped by trusted `shopId` and ordered by `review.created_at DESC, review.id DESC`.
- Product filtering uses immutable `oms_order_item.product_id`; order-service must not call product-service to infer product membership.

### `POST /api/admin/reviews/{reviewId}/visibility`

Request:

```json
{
  "visibility": "hidden",
  "reason": "Contains sensitive content",
  "requestId": "review-vis-20260508-0001"
}
```

Response code: `ADMIN_REVIEW_VISIBILITY_UPDATED`.

Rules:

- Requires `ADMIN` role or `REVIEW_MANAGEMENT_ADMIN`.
- `requestId` is required, trimmed, and persisted as `visibility_request_id`.
- `visibility` must be `visible` or `hidden`.
- Operator is the trusted principal user id; body user fields are not accepted.
- Writes persist latest moderation snapshot fields on `oms_order_review`.
- Same `requestId` and same target visibility returns the current snapshot.
- Same `requestId` with different target visibility returns `IDEMPOTENCY_CONFLICT`.
- Hiding keeps the original review row and content intact.
- Public product review projection must filter hidden reviews.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Missing admin/review permission | 403 | `AUTH_FORBIDDEN` |
| Invalid pagination, path id, rating, visibility, request body, or time range | 400 | `VALIDATION_FAILED` |
| Review missing or wrong shop scope | 404 | `ORDER_REVIEW_NOT_FOUND` |
| Same visibility request id with conflicting target status | 409 | `IDEMPOTENCY_CONFLICT` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-user-service" -am "-Dtest=AdminReviewManagementServiceTest,AdminReviewControllerTest,OrderReviewVisibilityMigrationContractTest,ProductReviewQueryServiceTest,OpsAuthServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: admin list only returns current trusted shop data and supports product/rating/user/time/visibility filters.
- Good: hidden reviews disappear from `GET /api/products/{productId}/reviews` while admin list can still query them.
- Good: visibility writes persist operator, trace id, request id, reason, and update time.
- Base: moderation history is latest-snapshot only until `oms_order_review_moderation_log` is introduced.
- Bad: frontend hides reviews locally without calling the backend write API.

## Database Contract

Schema env and migrations:

| Service | Schema Env | Default Schema | Migrations |
| --- | --- | --- | --- |
| `services/sangui-order-service` | `SANGUI_ORDER_MYSQL_SCHEMA` | `sangui_order` | `db/migration/V1__create_order_tables.sql`, `db/migration/V2__add_order_inventory_reservation.sql` |

### `oms_order`

Required business columns:

- `order_no`
- `user_id`
- `request_id`
- `reservation_no`
- `trace_id`
- `status`
- `total_amount_cent`

Required constraints and indexes:

- `uk_oms_order_shop_order_no (shop_id, order_no)`
- `uk_oms_order_shop_user_request (shop_id, user_id, request_id)`
- `uk_oms_order_shop_reservation_no (shop_id, reservation_no)`
- `idx_oms_order_shop_user_id (shop_id, user_id, id)`
- `idx_oms_order_shop_status (shop_id, status)`

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Detail/list pagination validation failure | 400 | `VALIDATION_FAILED` |
| Order detail missing or wrong owner | 404 | `ORDER_NOT_FOUND` |
| Duplicate `skuId` in one request | 409 | `ORDER_SKU_DUPLICATED` |
| Unknown or inactive SKU | 404 | `ORDER_SKU_NOT_FOUND` |
| Stock not enough | 409 | `ORDER_STOCK_NOT_ENOUGH` |
| Same requestId with different payload | 409 | `IDEMPOTENCY_CONFLICT` |
| product-service timeout or unavailable | 503 | `DOWNSTREAM_TIMEOUT` |
| Cancel on `paid` or unsupported status | 409 | `ORDER_STATUS_INVALID` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-order-service" -am "-Dtest=ProductInventoryServiceTest,InternalProductInventoryControllerTest,OrderCreateServiceTest,OrderCancelServiceTest,OrderPaymentServiceTest,InternalOrderPaymentControllerTest,OrderControllerTest,OrderMigrationContractTest,OrderInventoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: create order reserves stock exactly once and persists `reservation_no`.
- Good: duplicate submit with same principal + requestId returns the original order.
- Good: cancel order releases inventory and returns `cancelled`.
- Good: order detail/list use principal `(shopId,userId)` scope and return only the current user's order snapshots.
- Base: payment flow reads `reservationNo` through internal order snapshot instead of recomputing it.
- Bad: order-service writes order rows before inventory reserve succeeds.
- Bad: order-service trusts body `shopId` or `userId`.
- Bad: list/detail endpoints accept `shopId` or `userId` query parameters to widen scope.
- Bad: cancel path marks order `cancelled` without releasing inventory.

## Timeout Cancellation Addendum

### `POST /internal/orders/timeout-cancellations`

Request:

```json
{
  "shopId": 1,
  "orderId": 101,
  "trigger": "scheduler",
  "result": "failed",
  "operator": "ops-user",
  "traceId": "trace-order-manual",
  "fromTime": "2026-05-03T12:00:00+08:00",
  "toTime": "2026-05-03T12:30:00+08:00",
  "pageNo": 1,
  "pageSize": 20
}
```

Response:

```json
{
  "shopId": 1,
  "scannedCount": 2,
  "cancelledCount": 1,
  "skippedCount": 1,
  "failedCount": 0
}
```

Response code: `ORDER_TIMEOUT_CANCELLED`.

Rules:

- `shopId` is required; single-merchant defaults must not be hardcoded in business logic.
- `timeoutMinutes` and `limit` are optional positive values.
- Default timeout is 15 minutes; default limit is 100; maximum limit is 500.
- Query candidates from `oms_order` where `shop_id = ?`, `status = created`, and `created_at <= now - timeoutMinutes`.
- Each cancellation must release inventory reservation before transitioning `created -> cancelled`.
- Repeated timeout cancellation is idempotent because already `cancelled` or `paid` orders are no longer selected.
- Batch execution must continue when one order release/update fails; failed rows remain visible through `failedCount` and logs.
- This MVP uses a synchronous internal endpoint/job-style service; delayed MQ can call the same service later.

### Scheduled Timeout Compensation Job

Config keys:

- `sangui.compensation.order-timeout.enabled`
- `sangui.compensation.order-timeout.shop-id`
- `sangui.compensation.order-timeout.timeout-minutes`
- `sangui.compensation.order-timeout.limit`
- `sangui.compensation.order-timeout.initial-delay-ms`
- `sangui.compensation.order-timeout.fixed-delay-ms`

Deploy env keys:

- `SANGUI_ORDER_TIMEOUT_COMPENSATION_ENABLED`
- `SANGUI_ORDER_TIMEOUT_COMPENSATION_SHOP_ID`
- `SANGUI_ORDER_TIMEOUT_COMPENSATION_TIMEOUT_MINUTES`
- `SANGUI_ORDER_TIMEOUT_COMPENSATION_LIMIT`
- `SANGUI_ORDER_TIMEOUT_COMPENSATION_INITIAL_DELAY_MS`
- `SANGUI_ORDER_TIMEOUT_COMPENSATION_FIXED_DELAY_MS`

Rules:

- Scheduler is disabled by default; enabling it turns the timeout-cancel path into an in-process recurring compensation job.
- `shop-id` must come from configuration, typically `${SANGUI_DEFAULT_SHOP_ID}`, and must not be hardcoded in Java business logic.
- Each batch generates a job `traceId` and logs `jobName`, `shopId`, `timeoutMinutes`, `limit`, `durationMs`, `scannedCount`, `cancelledCount`, `skippedCount`, and `failedCount`.
- Batch-fatal logs must also include `errorType`, `errorCode`, and sanitized `message` instead of dumping raw multi-line stack traces for expected test scenarios.
- Candidates are re-read by `orderId` before release to reduce stale-scan races; non-`created` rows are skipped.

Metrics contract:

- `sangui.compensation.job.run.total{service="order",job="order-timeout",trigger="scheduler|manual",result="success|failed|disabled"}`
- `sangui.compensation.job.item.total{service="order",job="order-timeout",trigger="scheduler|manual",result="scanned|cancelled|skipped|failed"}`
- Do not tag these counters with `traceId`; keep traceability in logs and keep metrics cardinality bounded.

Alert thresholds:

- Critical: `increase(sangui_compensation_job_run_total{service="order",job="order-timeout",result="failed"}[5m]) > 0`
- Warning: `increase(sangui_compensation_job_item_total{service="order",job="order-timeout",result="failed"}[15m]) >= 1`
- Warning: if `SANGUI_ORDER_TIMEOUT_COMPENSATION_ENABLED=true`, investigate when `increase(sangui_compensation_job_run_total{service="order",job="order-timeout",result="success"}[15m]) == 0`

Additional state transitions:

| Current | Operation | Next | Notes |
| --- | --- | --- | --- |
| `created` | timeout cancel + release reserve | `cancelled` | owned by order-service |
| `paid` | timeout cancel | `paid` | skipped |
| `cancelled` | timeout cancel replay | `cancelled` | skipped |

Database addendum:

- `services/sangui-order-service/src/main/resources/db/migration/V3__add_order_timeout_lookup_index.sql`
- Required index: `idx_oms_order_shop_status_created (shop_id, status, created_at)`.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Timeout cancellation missing `shopId` | 400 | `VALIDATION_FAILED` |
| `timeoutMinutes <= 0` or `limit <= 0` | 400 | `VALIDATION_FAILED` |
| Inventory release downstream failure | 503 | `DOWNSTREAM_TIMEOUT` |

Additional required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-order-service" -am "-Dtest=OrderCreateServiceTest,OrderCancelServiceTest,OrderTimeoutCancelServiceTest,OrderTimeoutCompensationSchedulerTest,OrderPaymentServiceTest,InternalOrderTimeoutControllerTest,OrderMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: timeout cancellation releases inventory and returns cancelled/skipped counts.
- Good: duplicate timeout cancellation does not release inventory twice.
- Good: paid order is skipped when payment callback wins before timeout.
- Good: one failing row increments `failedCount` without aborting the rest of the batch.
- Good: scheduler metrics expose run results and batch item counts without adding high-cardinality tags.
- Base: timeout selection uses `created_at` cutoff until a dedicated payment deadline field is introduced.
- Bad: timeout cancellation selects paid orders or hardcodes shop id.
## Compensation Ops Surface Addendum

### `POST /internal/orders/compensation-records/query`

Request:

```json
{
  "shopId": 1,
  "timeoutMinutes": 15,
  "limit": 100
}
```

Response code: `ORDER_COMPENSATION_RECORDS_FETCHED`.

Response data:

```json
{
  "shopId": 1,
  "pageNo": 1,
  "pageSize": 20,
  "total": 1,
  "items": [
    {
      "order": {
        "orderId": 101,
        "orderNo": "ORD-001",
        "userId": "10001",
        "reservationNo": "ord:10001:req-001",
        "status": "cancelled",
        "totalAmountCent": 59900,
        "traceId": "trace-order-create",
        "createdAt": "2026-05-03T12:00:00+08:00",
        "updatedAt": "2026-05-03T12:10:00+08:00",
        "lastCompensationResult": "failed",
        "lastCompensationErrorCode": "DOWNSTREAM_TIMEOUT",
        "lastCompensationReason": "inventory release timeout",
        "lastCompensationTraceId": "order-timeout-job-xxx",
        "lastCompensationTrigger": "scheduler",
        "lastCompensationOperator": null,
        "lastCompensatedAt": "2026-05-03T12:16:00+08:00"
      },
      "matchedAttemptCount": 1,
      "totalAttemptCount": 2,
      "latestAttemptAt": "2026-05-03T12:16:00+08:00",
      "attempts": [
        {
          "attemptId": 9001,
          "orderId": 101,
          "orderNo": "ORD-001",
          "reservationNo": "ord:10001:req-001",
          "result": "failed",
          "errorCode": "DOWNSTREAM_TIMEOUT",
          "reason": "inventory release timeout",
          "traceId": "order-timeout-job-xxx",
          "trigger": "scheduler",
          "operator": null,
          "createdAt": "2026-05-03T12:16:00+08:00",
          "updatedAt": "2026-05-03T12:16:00+08:00"
        }
      ]
    }
  ]
}
```

Rules:

- `shopId` is required.
- `pageNo` defaults to 1; `pageSize` defaults to 20 and must stay capped at 100.
- History filtering is backed by `oms_order_compensation_attempt`, not only `oms_order.last_compensation_*`.
- Supported filters are `orderId`, `trigger`, `result`, `operator`, `traceId`, and optional `fromTime` / `toTime`.
- Pagination is applied to distinct `orderId` aggregates ordered by latest matched `created_at DESC, order_id DESC`.
- Each aggregate returns the latest order snapshot from `oms_order` plus the full ordered attempt detail list from `oms_order_compensation_attempt`.
- Query responses must expose `createdAt`, `updatedAt`, `lastCompensatedAt`, and per-attempt `createdAt` / `updatedAt`.

### `POST /internal/orders/timeout-replays/manual`

Request:

```json
{
  "shopId": 1,
  "orderId": 101,
  "timeoutMinutes": 15
}
```

Response code: `ORDER_TIMEOUT_REPLAYED_MANUALLY`.

Response data:

```json
{
  "result": "cancelled",
  "errorCode": null,
  "reason": null,
  "order": {
    "orderId": 101,
    "orderNo": "ORD-001",
    "status": "cancelled",
    "lastCompensationResult": "cancelled",
    "lastCompensationTraceId": "trace-manual-order",
    "lastCompensationTrigger": "manual",
    "lastCompensatedAt": "2026-05-03T12:20:00+08:00"
  }
}
```

Rules:

- Manual timeout replay reuses the same release + status transition path as scheduler timeout cancellation.
- Missing order returns `ORDER_NOT_FOUND`.
- Non-`created` orders or rows that have not crossed the timeout threshold return HTTP 200 with `result = skipped`; they do not force a status change.
- Replay success persists latest-compensation metadata on `oms_order`.
- Replay failure persists `lastCompensationResult = failed` plus sanitized `errorCode` / `reason`.
- Manual replay logs must include `traceId`, `trigger=manual`, `shopId`, `orderId`, `orderNo`, `reservationNo`, `result`, and current `orderStatus`.

Additional database addendum:

- `services/sangui-order-service/src/main/resources/db/migration/V4__add_order_compensation_ops_columns.sql`
- Required latest-compensation columns on `oms_order`:
  - `last_compensation_result`
  - `last_compensation_error_code`
  - `last_compensation_reason`
  - `last_compensation_trace_id`
  - `last_compensation_trigger`
  - `last_compensated_at`

Additional validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Query/manual request missing `shopId` | 400 | `VALIDATION_FAILED` |
| `orderId <= 0`, `pageNo <= 0`, or `pageSize <= 0` | 400 | `VALIDATION_FAILED` |
| `fromTime > toTime` | 400 | `VALIDATION_FAILED` |
| Manual replay order missing | 404 | `ORDER_NOT_FOUND` |
| Inventory release timeout during manual replay | 503 | `DOWNSTREAM_TIMEOUT` |

Additional required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-order-service" -am "-Dtest=OrderTimeoutCancelServiceTest,OrderCompensationOpsServiceTest,InternalOrderTimeoutControllerTest,InternalOrderCompensationControllerTest,OrderCompensationQueryResponseJsonTest,OrderCompensationOpsMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: manual replay returns `cancelled`, `skipped`, or `failed` without inventing a new state machine.
- Good: query surfaces show both latest business status and nested attempt history in one response.
- Good: manual replay and scheduler both overwrite the same latest-compensation fields, append history rows, and share the same metrics family.
- Good: dry-run bulk replay previews bounded work without mutating rows or history.
- Base: latest metadata remains the fast summary view while history rows preserve every attempt for drill-down.
- Bad: manual replay forces a not-yet-timeout order into `cancelled`.
- Bad: ops query omits `traceId` or key timestamps needed for troubleshooting.

### Bulk Replay Addendum

`POST /internal/orders/timeout-replays/bulk`

Request fields:

- `shopId`
- `dryRun`
- `operator`
- `limit`
- one of `timeoutMinutes` or `orderIds`

Rules:

- `dryRun=true` must not mutate `oms_order` or append compensation history.
- `operator` is required for accountability even during dry-run.
- `limit` is required, positive, and capped at 500.
- Bulk replay reuses the same single-record timeout replay path for each item; no second compensation state machine is allowed.
- Per-item results are bounded to `would-cancel`, `cancelled`, `skipped`, or `failed`.

V5 addendum:

- `services/sangui-order-service/src/main/resources/db/migration/V5__add_order_compensation_attempt_history.sql`
- latest-compensation columns on `oms_order` now include `last_compensation_operator`
- history table `oms_order_compensation_attempt` is append-only and stores `order_id`, `order_no`, `reservation_no`, `result`, `error_code`, `reason`, `trace_id`, `trigger_type`, and `operator`

## Admin Order Management Addendum

### `GET /api/admin/orders`

Response code: `ADMIN_ORDER_LIST`.

Request query fields:

- `page`: optional positive integer, default `1`.
- `size`: optional positive integer, default `20`, capped at `100`.
- `status`: optional `created`, `paid`, `cancelled`; omit or `all` means no status filter.
- `orderNo`: optional trimmed partial match.
- `userId`: optional exact user filter inside trusted shop scope.
- `fromTime` / `toTime`: optional ISO-8601 timestamp filters on `created_at`.

Response data fields:

- `page`, `size`, `total`.
- `items[]`: `orderId`, `orderNo`, `shopId`, `userId`, `status`, `totalAmountCent`, nullable `paymentNo`, `itemCount`, nullable `traceId`, `createdAt`, `updatedAt`.

Rules:

- Gateway route `/api/admin/orders/**` points to order-service and requires JWT.
- order-service must enforce `ADMIN` role or `ORDER_MANAGEMENT_ADMIN` permission; `OPS_COMPENSATION_ADMIN` alone is not enough.
- Effective `shopId` comes from trusted `SanguiPrincipal`; request query must not carry `shopId`.
- `userId` is only a filter within the trusted shop scope, not the authenticated operator identity.
- Results are ordered by `created_at DESC, id DESC`.
- `itemCount` is the sum of order item quantities for the returned snapshot.
- `paymentNo` is nullable in order-service responses. order-service must not query payment-service tables directly; admin UI may call payment-service admin status API by `orderId`.

### `GET /api/admin/orders/{orderId}`

Response code: `ADMIN_ORDER_DETAIL`.

Response data fields:

- Order base fields: `orderId`, `orderNo`, `shopId`, `userId`, `requestId`, `reservationNo`, nullable `paymentNo`, `status`, `totalAmountCent`, nullable `traceId`, `createdAt`, `updatedAt`.
- `items[]`: immutable product/SKU snapshot fields from `oms_order_item`.
- `statusTimeline[]`: `status`, `occurredAt`, `traceId`.

Rules:

- Missing order or order outside trusted shop scope returns `ORDER_NOT_FOUND`.
- DTOs must be snapshots, not `oms_order` entity objects.
- MVP timeline is derived from order row timestamps: always include `created`; include current non-`created` status at `updatedAt`. A full immutable status history table is out of scope.

### `POST /api/admin/orders/{orderId}/cancel`

Request:

```json
{
  "requestId": "adm-cancel-20260507-0001"
}
```

Response code: `ADMIN_ORDER_CANCELLED`.

Rules:

- Requires `ADMIN` role or `ORDER_MANAGEMENT_ADMIN`.
- `requestId` is required for frontend duplicate-submit traceability.
- Cancel reuses the same order-service release + status transition path as customer cancel.
- Only `created` can transition to `cancelled`; `paid` or unsupported statuses return `ORDER_STATUS_INVALID`.
- Repeated cancel of an already `cancelled` order returns the current cancelled snapshot, matching the existing customer cancel idempotency behavior.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Missing admin/order permission | 403 | `AUTH_FORBIDDEN` |
| Invalid pagination, path id, request body, or time range | 400 | `VALIDATION_FAILED` |
| Invalid `status` filter | 409 | `ORDER_STATUS_INVALID` |
| Missing order or wrong shop scope | 404 | `ORDER_NOT_FOUND` |
| Cancel paid or unsupported status | 409 | `ORDER_STATUS_INVALID` |
| Inventory release downstream failure | 503 | `DOWNSTREAM_TIMEOUT` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-gateway" -am "-Dtest=AdminOrderManagementServiceTest,AdminOrderControllerTest,GatewayJwtAuthenticationFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: admin list filters only within trusted `shopId` and exposes `itemCount`, `traceId`, and nullable `paymentNo`.
- Good: admin detail exposes `reservationNo`, item snapshots, and a derived status timeline.
- Good: admin cancel releases inventory through the existing order cancellation path before returning `cancelled`.
- Base: `paymentNo` stays nullable in order responses; payment status is read via payment-service admin API.
- Bad: order-service directly reads `pay_*` payment tables or trusts a frontend `shopId`.

## Fulfillment / Shipment Addendum

### Persisted State

Order-service owns the order state machine. Fulfillment MVP adds:

- `shipped`

Valid shipment transition:

| Current | Operation | Next |
| --- | --- | --- |
| `paid` | shipment confirmation | `shipped` |
| `created` | shipment confirmation | invalid |
| `cancelled` | shipment confirmation | invalid |
| `shipped` with same request/payload | shipment confirmation replay | `shipped` |
| `shipped` with different request/payload | shipment confirmation replay | invalid / idempotency conflict |

### `POST /internal/orders/fulfillment-records/query`

Request:

```json
{
  "shopId": 1,
  "page": 1,
  "size": 20,
  "fulfillmentStatus": "unshipped",
  "orderNo": "ORD",
  "userId": "10001",
  "fromTime": "2026-05-07T10:00:00+08:00",
  "toTime": "2026-05-07T18:00:00+08:00"
}
```

Response code: `ORDER_FULFILLMENT_RECORDS_FETCHED`.

Rules:

- `shopId` is required and comes from logistics-service trusted principal scope.
- `fulfillmentStatus=unshipped` means order `status = paid`.
- `fulfillmentStatus=shipped` means order `status = shipped`.
- Omit or `all` means both `paid` and `shipped`.
- Query never returns `created` or `cancelled` rows.
- Results are ordered by `created_at DESC, id DESC`.

Response item fields:

- `orderId`, `orderNo`, `shopId`, `userId`, `status`, `fulfillmentStatus`, `totalAmountCent`, `carrier`, `trackingNo`, `shippedAt`, `traceId`, `createdAt`, `updatedAt`.

### `POST /internal/orders/shipments/confirmations`

Request:

```json
{
  "shopId": 1,
  "orderId": 101,
  "requestId": "ship-20260507-0001",
  "carrier": "SF Express",
  "trackingNo": "SF1234567890"
}
```

Response code: `ORDER_SHIPPED`.

Rules:

- order-service validates `(shopId, orderId)` scope.
- Only `paid -> shipped` is accepted for a new shipment.
- Existing `shipped` order with same `shipment_request_id`, `carrier`, and `tracking_no` is idempotent and returns current snapshot.
- Existing `shipped` order with a different `requestId`, `carrier`, or `trackingNo` returns `IDEMPOTENCY_CONFLICT`.
- `created` and `cancelled` return `ORDER_STATUS_INVALID`.
- `carrier` and `trackingNo` are trimmed and persisted as the order fulfillment snapshot.
- `shipment_trace_id` is the request trace id used to confirm shipment.

Order detail APIs expose read-only fulfillment fields:

- `fulfillmentStatus`: `unshipped` for `paid`, `shipped` for `shipped`, otherwise `pending`.
- `carrier`
- `trackingNo`
- `shippedAt`

Database addendum:

- `services/sangui-order-service/src/main/resources/db/migration/V6__add_order_shipment_snapshot.sql`
- Required columns on `oms_order`: `fulfillment_status`, `carrier`, `tracking_no`, `shipped_at`, `shipment_request_id`, `shipment_trace_id`.
- Required index: `idx_oms_order_shop_fulfillment_created (shop_id, fulfillment_status, created_at)`.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing `shopId`, invalid `orderId`, blank `requestId`, blank `carrier`, blank `trackingNo` | 400 | `VALIDATION_FAILED` |
| Missing order or wrong shop scope | 404 | `ORDER_NOT_FOUND` |
| Ship `created` or `cancelled` order | 409 | `ORDER_STATUS_INVALID` |
| Replayed shipped order with different request/payload | 409 | `IDEMPOTENCY_CONFLICT` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service" -am "-Dtest=OrderShipmentServiceTest,InternalOrderShipmentControllerTest,OrderShipmentMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: a paid order becomes shipped and persists carrier, tracking number, shipped time, request id, and trace id.
- Good: customer order detail shows fulfillment fields after shipment.
- Good: same shipment request and same payload returns the current shipped snapshot.
- Base: full third-party logistics tracking is out of scope; carrier/tracking number are operator-entered snapshots.
- Bad: logistics-service writes `oms_order` directly or hardcodes shop id.
