# Order Create Contracts

## Scope

Order Create MVP for `services/sangui-order-service`.

This MVP is the first business flow that turns authenticated user identity plus active product SKUs into a persisted business document. It depends on:

- trusted `SanguiPrincipal` from downstream auth context
- active SKU snapshot lookup from `services/sangui-product-service`
- Flyway-managed `oms_order` and `oms_order_item` schema

## API Contract

### `POST /api/orders`

| API | Auth | Request | Success code | Response data |
| --- | --- | --- | --- | --- |
| `POST /api/orders` | `SanguiPrincipal` required | `CreateOrderRequest` | `ORDER_CREATED` | `OrderResponse` |

Security rules:

- Controller parameter must use `SanguiPrincipal principal`.
- `shopId` and `userId` for authenticated scope must come from `principal.shopId()` and `principal.userId()`.
- Request body `shopId` or `userId` may appear for legacy or malicious callers, but they are never trusted as authenticated identity.
- Missing principal must fail through `SanguiPrincipalArgumentResolver` with `AUTH_TOKEN_MISSING`.

### `CreateOrderRequest`

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

Request rules:

- `requestId` is required and acts as the order-create idempotency key inside `(shopId, userId)`.
- `items` must be non-empty.
- `skuId` must be a positive long.
- `quantity` must be a positive integer.
- Duplicate `skuId` values in one request are rejected with a business error.

### `OrderResponse`

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

Response rules:

- Responses use `ApiResult<OrderResponse>`.
- `status` uses the persisted enum values `created`, `cancelled`, and `paid`.
- Item snapshot data is immutable order data and must come from product snapshot lookup, not from client-submitted price/name fields.

## Product Snapshot Dependency Contract

Order creation reads SKU snapshots from product-service through an internal HTTP contract:

### `POST /internal/products/skus/snapshot`

Request:

```json
{
  "shopId": 1,
  "skuIds": [401, 402]
}
```

Response:

```json
{
  "code": "PRODUCT_SKU_SNAPSHOTS_FETCHED",
  "message": "ok",
  "data": {
    "items": [
      {
        "productId": 301,
        "skuId": 401,
        "skuCode": "shoe-42",
        "skuName": "Sneaker 42",
        "priceCent": 59900
      }
    ]
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T17:00:00+08:00"
}
```

Rules:

- product-service only returns SKUs whose parent product is `active`.
- order-service must treat missing requested SKUs in the response as unavailable and reject the order.
- order-service must not read `pms_*` tables directly.
- write-path fallback must never fake success; downstream failures map to `DOWNSTREAM_TIMEOUT`.

## State Machine

Persisted status values:

- `created`
- `cancelled`
- `paid`

MVP transitions:

| Current | Operation | Next | Notes |
| --- | --- | --- | --- |
| none | create | `created` | Implemented in this MVP. |
| `created` | cancel | `cancelled` | Reserved for future API/workflow. |
| `created` | pay | `paid` | Implemented through payment-service internal confirmation contract. |
| `paid` | cancel | invalid | Future business rule; not implemented in this MVP. |

## Database Contract

Schema env and migration:

| Service | Schema Env | Default Schema | Migration |
| --- | --- | --- | --- |
| `services/sangui-order-service` | `SANGUI_ORDER_MYSQL_SCHEMA` | `sangui_order` | `db/migration/V1__create_order_tables.sql` |

### `oms_order`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `order_no`, `user_id`, `request_id`, `trace_id`, `status`, `total_amount_cent`

Required constraints and indexes:

- `uk_oms_order_shop_order_no (shop_id, order_no)`
- `uk_oms_order_shop_user_request (shop_id, user_id, request_id)`
- `idx_oms_order_shop_user_id (shop_id, user_id, id)`
- `idx_oms_order_shop_status (shop_id, status)`

### `oms_order_item`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `order_id`, `product_id`, `sku_id`, `sku_name`, `price_cent`, `quantity`, `line_amount_cent`

Required constraints and indexes:

- `fk_oms_order_item_order (order_id -> oms_order.id)`
- `idx_oms_order_item_shop_order (shop_id, order_id)`
- `idx_oms_order_item_shop_sku (shop_id, sku_id)`

Money rules:

- all price and total fields use integer cents in `BIGINT`
- `double` and `float` are forbidden

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Duplicate `skuId` in one request | 409 | `ORDER_SKU_DUPLICATED` |
| Unknown or inactive SKU in requested shop scope | 404 | `ORDER_SKU_NOT_FOUND` |
| Same `(shopId, userId, requestId)` with different payload | 409 | `IDEMPOTENCY_CONFLICT` |
| product-service timeout or unavailable | 503 | `DOWNSTREAM_TIMEOUT` |

Idempotency behavior:

- same principal + same `requestId` + same effective items -> return the original order
- same principal + same `requestId` + different effective items -> reject with `IDEMPOTENCY_CONFLICT`

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-order-service" -am "-Dtest=ProductCatalogServiceTest,InternalProductSnapshotControllerTest,OrderMigrationContractTest,OrderCreateServiceTest,OrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Payment linkage note:

- order-service now exposes `POST /internal/orders/payment-snapshot` and `POST /internal/orders/payment-confirmations` for payment-service only.
- these internal APIs preserve the same owner scope and state-machine rules documented above.

## Good / Base / Bad Cases

- Good: `POST /api/orders` persists one `oms_order` row and matching `oms_order_item` rows using principal-derived `shopId` and `userId`.
- Good: duplicate submit with the same effective payload returns the original order instead of creating another one.
- Good: order item snapshots store immutable `skuName` and `priceCent` copied from product-service.
- Base: `cancelled` and `paid` are reserved in schema and response contract before dedicated workflows exist.
- Bad: order-service trusts body `shopId` or `userId`.
- Bad: order-service reads `pms_*` tables directly.
- Bad: downstream product timeout returns fake order success.
