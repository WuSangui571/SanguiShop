# Inventory Reserve Contracts

## Scope

Inventory Reserve MVP for `services/sangui-product-service`.

This contract makes product-service the single owner of sellable stock and inventory reservations for normal order flow. Order-service and payment-service must never read or write `pms_*` inventory state directly.

## Ownership Rules

- SKU stock lives in product-service only.
- Order create writes inventory through internal reserve API only.
- Payment success finalizes inventory through internal confirm API only.
- Order cancel or payment-failure compensation releases inventory through internal release API only.
- Payment reconcile may mark terminal invalid payment rows `failed`, but it still must not release inventory directly.
- Reservation idempotency is owned by `(shopId, reservationNo)`.
- Payment failure callbacks do not release inventory directly; order cancellation or timeout cancellation owns release.
- Late payment success after timeout cancellation must not confirm a released reservation.

## Internal Inventory APIs

### `POST /internal/products/inventory/reservations`

Request:

```json
{
  "shopId": 1,
  "reservationNo": "ord:10001:req-20260501-0001",
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
  "code": "PRODUCT_INVENTORY_RESERVED",
  "message": "ok",
  "data": {
    "reservationNo": "ord:10001:req-20260501-0001",
    "status": "reserved",
    "items": [
      {
        "productId": 301,
        "skuId": 401,
        "skuCode": "shoe-42",
        "skuName": "Sneaker 42",
        "priceCent": 59900,
        "quantity": 2
      }
    ]
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T21:00:00+08:00"
}
```

Rules:

- `shopId` is required.
- `reservationNo` is required and is the write-path idempotency key.
- `items` must be non-empty, positive `skuId`, positive `quantity`.
- Duplicate `skuId` values in one request are rejected.
- Only active product SKUs can be reserved.
- Successful reserve decrements `available_stock` and increments `reserved_stock`.
- Same `(shopId, reservationNo)` + same payload returns the original reservation instead of double-deducting.
- Same `(shopId, reservationNo)` + different payload returns `IDEMPOTENCY_CONFLICT`.

### `POST /internal/products/inventory/confirmations`

Request:

```json
{
  "shopId": 1,
  "reservationNo": "ord:10001:req-20260501-0001"
}
```

Response:

```json
{
  "code": "PRODUCT_INVENTORY_CONFIRMED",
  "message": "ok",
  "data": {
    "reservationNo": "ord:10001:req-20260501-0001",
    "status": "confirmed",
    "items": []
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T21:05:00+08:00"
}
```

Rules:

- Confirm is idempotent for already `confirmed` reservations.
- Only `reserved -> confirmed` is valid.
- Confirm decreases `reserved_stock` only; `available_stock` stays unchanged because stock is now sold.

### `POST /internal/products/inventory/releases`

Request:

```json
{
  "shopId": 1,
  "reservationNo": "ord:10001:req-20260501-0001"
}
```

Response:

```json
{
  "code": "PRODUCT_INVENTORY_RELEASED",
  "message": "ok",
  "data": {
    "reservationNo": "ord:10001:req-20260501-0001",
    "status": "released",
    "items": []
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T21:06:00+08:00"
}
```

Rules:

- Release is idempotent for already `released` reservations only when invoked through the same owner flow; repeated release must not add stock twice.
- Only `reserved -> released` is valid.
- Release increments `available_stock` and decrements `reserved_stock`.

## Admin Product Contract Addendum

`CreateProductRequest` and `UpdateProductRequest` SKU items now support:

```json
{
  "skuCode": "shoe-42",
  "skuName": "42",
  "priceCent": 59900,
  "availableStock": 20
}
```

Rules:

- `availableStock` is optional for compatibility and defaults to `0`.
- `reservedStock` is response-only and always owned by inventory write paths.

## Database Contract

Schema env and migrations:

| Service | Schema Env | Default Schema | Migrations |
| --- | --- | --- | --- |
| `services/sangui-product-service` | `SANGUI_PRODUCT_MYSQL_SCHEMA` | `sangui_product` | `db/migration/V1__create_product_catalog_tables.sql`, `db/migration/V2__add_inventory_reservation_support.sql` |

### `pms_sku`

Additional required columns:

- `available_stock BIGINT NOT NULL DEFAULT 0`
- `reserved_stock BIGINT NOT NULL DEFAULT 0`

### `pms_inventory_reservation`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `reservation_no`, `product_id`, `sku_id`, `sku_code`, `sku_name`, `price_cent`, `quantity`, `status`, `trace_id`

Required constraints and indexes:

- `uk_pms_inventory_reservation_shop_no_sku (shop_id, reservation_no, sku_id)`
- `idx_pms_inventory_reservation_shop_no (shop_id, reservation_no)`
- `idx_pms_inventory_reservation_shop_status (shop_id, status)`
- `idx_pms_inventory_reservation_shop_sku (shop_id, sku_id)`

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Unknown or inactive SKU | 404 | `PRODUCT_SKU_NOT_FOUND` |
| Stock not enough | 409 | `PRODUCT_STOCK_NOT_ENOUGH` |
| Reservation missing on confirm/release | 404 | `PRODUCT_INVENTORY_RESERVATION_NOT_FOUND` |
| Invalid reservation transition | 409 | `PRODUCT_INVENTORY_RESERVATION_STATUS_INVALID` |
| Same reservationNo with different payload | 409 | `IDEMPOTENCY_CONFLICT` |

### Internal Product Snapshot Response Fields

`POST /internal/products/skus/snapshot` response item fields:

```json
{
  "productId": 301,
  "productName": "Running Shoe",
  "skuId": 401,
  "skuCode": "RS-42",
  "skuName": "42",
  "priceCent": 59900,
  "availableStock": 20
}
```

The `productName` field is resolved from the owning `pms_product` record; `availableStock` is the current sellable stock from `pms_sku`. Unknown/inactive SKUs for the requested `shopId` are silently omitted from the response — consumers must treat missing requested SKU as `PRODUCT_SKU_NOT_FOUND`. Cross-service callers must propagate `X-Trace-Id`; seckill-service does this through `ProductSkuSnapshotClient.findBySkuId(Long shopId, Long skuId, String traceId)`.

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=ProductCatalogServiceTest,ProductInventoryServiceTest,ProductCatalogControllerTest,InternalProductSnapshotControllerTest,InternalProductInventoryControllerTest,ProductMigrationContractTest,ProductInventoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: reserve once for `ord:10001:req-001` deducts stock exactly once and returns stable snapshot fields.
- Good: confirm after payment moves reservation to `confirmed` without restoring `available_stock`.
- Good: release after cancel restores `available_stock` exactly once.
- Base: `availableStock` omitted on admin write defaults to `0`.
- Bad: order-service or payment-service reads `pms_sku` directly.
- Bad: duplicate reserve with same key deducts stock twice.
- Bad: release or confirm succeeds from an impossible prior status.
- Bad: payment callback confirms inventory after order timeout already released the reservation.
