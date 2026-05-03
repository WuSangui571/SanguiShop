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

Valid transitions:

| Current | Operation | Next |
| --- | --- | --- |
| none | create with successful reserve | `created` |
| `created` | cancel + release reserve | `cancelled` |
| `created` | payment confirm | `paid` |
| `paid` | cancel | invalid |

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
- Base: payment flow reads `reservationNo` through internal order snapshot instead of recomputing it.
- Bad: order-service writes order rows before inventory reserve succeeds.
- Bad: order-service trusts body `shopId` or `userId`.
- Bad: cancel path marks order `cancelled` without releasing inventory.

## Timeout Cancellation Addendum

### `POST /internal/orders/timeout-cancellations`

Request:

```json
{
  "shopId": 1,
  "timeoutMinutes": 15,
  "limit": 100
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

- `sangui.compensation.job.run.total{service="order",job="order-timeout",result="success|failed|disabled"}`
- `sangui.compensation.job.item.total{service="order",job="order-timeout",result="scanned|cancelled|skipped|failed"}`
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
