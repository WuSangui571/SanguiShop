# Product Catalog Contracts

## Scope

Product Catalog MVP for `services/sangui-product-service`.

This document covers public catalog read APIs and admin product maintenance. Inventory reservation write paths are documented in [Inventory Reserve Contracts](./inventory-reserve-contracts.md).

## Public Read APIs

| API | Auth | Success code | Response |
| --- | --- | --- | --- |
| `GET /api/products?page=1&size=20` | anonymous allowed | `PRODUCT_LISTED` | `PageResponse<ProductSummaryResponse>` |
| `GET /api/products/{productId}` | anonymous allowed | `PRODUCT_FETCHED` | `ProductDetailResponse` |
| `GET /api/products/{productId}/reviews?page=1&size=10` | anonymous allowed | `PRODUCT_REVIEWS_FETCHED` | `ProductReviewPageResponse` |

Rules:

- public read derives shop scope from `sangui.shop.default-shop-id`
- only `active` products are visible
- public responses never expose persistence entities or audit fields
- product review public read is exposed by product-service and backed by order-service internal projection; product-service must not read `oms_*` tables directly

## Admin Product APIs

| API | Auth | Success code | Response |
| --- | --- | --- | --- |
| `GET /api/admin/products?page=1&size=20&status=active` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_ADMIN_LISTED` | `PageResponse<ProductAdminSummaryResponse>` |
| `GET /api/admin/products/{productId}` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_ADMIN_FETCHED` | `ProductDetailResponse` |
| `POST /api/admin/products` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_CREATED` | `ProductDetailResponse` |
| `PUT /api/admin/products/{productId}` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_UPDATED` | `ProductDetailResponse` |
| `POST /api/admin/products/{productId}/publish` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_PUBLISHED` | `ProductDetailResponse` |
| `POST /api/admin/products/{productId}/status` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_STATUS_UPDATED` | `ProductDetailResponse` |
| `POST /api/admin/products/{productId}/skus/{skuId}/stock-adjustments` | `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission | `PRODUCT_SKU_STOCK_ADJUSTED` | `ProductDetailResponse` |

Security rules:

- controller parameters must use `SanguiPrincipal`
- effective `shopId` and operator identity come from principal only
- missing principal -> `AUTH_TOKEN_MISSING`
- principal without `ADMIN` role and without `PRODUCT_CATALOG_ADMIN` permission -> `AUTH_FORBIDDEN`
- gateway route `sangui-product` must forward both `/api/products/**` and `/api/admin/products/**` to product-service

## Request / Response Shapes

### `ProductAdminSummaryResponse`

```json
{
  "productId": 101,
  "productName": "Sneaker",
  "productDescription": "Daily trainer",
  "minPriceCent": 59900,
  "maxPriceCent": 69900,
  "status": "active",
  "skuCount": 2,
  "availableStockTotal": 30,
  "reservedStockTotal": 0
}
```

Rules:

- `status` filter is optional; omitted means all product statuses in the principal shop scope.
- supported status values are `draft`, `active`, and `inactive`; unknown values fail with `PRODUCT_STATUS_INVALID`.
- stock totals are read-model summaries only; reservation ownership remains in product-service inventory paths.

### `CreateProductRequest` / `UpdateProductRequest`

```json
{
  "shopId": 999,
  "userId": "spoof-user",
  "productName": "Sneaker",
  "productDescription": "Daily trainer",
  "skus": [
    {
      "skuCode": "shoe-42",
      "skuName": "42",
      "priceCent": 59900,
      "availableStock": 20
    }
  ]
}
```

Rules:

- `priceCent` must be positive integer cents
- `availableStock` is optional for compatibility and defaults to `0`
- `shopId` and `userId` remain DTO-compatible fields but admin product service must ignore them for authorization and write ownership; principal `shopId` and `userId` are authoritative
- `skuCode` pattern: `^[A-Za-z0-9_-]+$`
- duplicate `skuCode` values in one request are rejected

### `ProductStatusUpdateRequest`

```json
{
  "status": "inactive",
  "requestId": "req-status-1"
}
```

Rules:

- `requestId` is required for write-path traceability and future idempotency.
- MVP status update sets the requested `draft` / `active` / `inactive` value directly after product existence and permission checks.

### `ProductSkuStockAdjustmentRequest`

```json
{
  "availableStock": 25,
  "requestId": "req-stock-1"
}
```

Rules:

- `availableStock` must be a non-negative integer.
- MVP stock adjustment sets sellable stock to the requested value; it must not mutate `reservedStock`.
- missing SKU under the current principal shop and product returns `PRODUCT_SKU_NOT_FOUND`.

### `ProductDetailResponse`

```json
{
  "productId": 101,
  "productName": "Sneaker",
  "productDescription": "Daily trainer",
  "status": "active",
  "skus": [
    {
      "skuId": 201,
      "skuCode": "shoe-42",
      "skuName": "42",
      "priceCent": 59900,
      "availableStock": 20,
      "reservedStock": 0
    }
  ]
}
```

Rules:

- `reservedStock` is response-only
- public and admin detail both use stable DTOs instead of persistence entities

### `ProductReviewPageResponse`

Route:

- `GET /api/products/{productId}/reviews?page=1&size=10`

Response:

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
      "skuName": "Size 42",
      "merchantReply": {
        "content": "Thanks for the feedback.",
        "repliedAt": "2026-05-08T12:00:00+08:00"
      }
    }
  ]
}
```

Rules:

- `productId` must be a positive path value and must resolve to an active public product in the default shop scope.
- `page` defaults to `1`, `size` defaults to `10`, and `size` is capped at `50`.
- Product-service calls order-service internal `POST /internal/orders/reviews/by-product/query` with `shopId`, `productId`, `page`, and `size`.
- Product-service must forward the current trace id to order-service as `X-Trace-Id`.
- Public item fields must not include raw `shopId`, raw `userId`, `orderId`, `orderNo`, `requestId`, or `traceId`.
- Optional `merchantReply` includes only `content` and `repliedAt`; reply operator, request id, and trace id remain admin-only.
- Hidden review rows are omitted. Visible review rows with hidden replies stay visible but omit `merchantReply`.
- If order-service is unavailable, product-service maps the failure to `DOWNSTREAM_TIMEOUT`.

## Internal Snapshot API

### `POST /internal/products/skus/snapshot`

Request:

```json
{
  "shopId": 1,
  "skuIds": [401, 402]
}
```

Response items remain:

- `productId`
- `skuId`
- `skuCode`
- `skuName`
- `priceCent`

Rules:

- only active product SKUs are returned
- endpoint is read-only
- no audit fields or stock mutation details leak through this API

## Database Contract

Schema env and migrations:

| Service | Schema Env | Default Schema | Migrations |
| --- | --- | --- | --- |
| `services/sangui-product-service` | `SANGUI_PRODUCT_MYSQL_SCHEMA` | `sangui_product` | `db/migration/V1__create_product_catalog_tables.sql`, `db/migration/V2__add_inventory_reservation_support.sql` |

### `pms_product`

Required indexes:

- `idx_pms_product_shop_id_id (shop_id, id)`
- `idx_pms_product_shop_status (shop_id, status)`

### `pms_sku`

Required columns:

- `product_id`
- `sku_code`
- `sku_name`
- `sale_price_cent`
- `available_stock`
- `reserved_stock`
- `created_by`
- `updated_by`

Required constraints / indexes:

- `uk_pms_sku_shop_code (shop_id, sku_code)`
- `idx_pms_sku_shop_product (shop_id, product_id)`
- FK `product_id -> pms_product.id`

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing principal on admin product API | 401 | `AUTH_TOKEN_MISSING` |
| Principal lacks `ADMIN` and `PRODUCT_CATALOG_ADMIN` | 403 | `AUTH_FORBIDDEN` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Product missing in principal shop scope | 404 | `PRODUCT_NOT_FOUND` |
| Public product review path product missing or inactive | 404 | `PRODUCT_NOT_FOUND` |
| Product review pagination validation failure | 400 | `VALIDATION_FAILED` |
| Order-service review projection unavailable | 503 | `DOWNSTREAM_TIMEOUT` |
| SKU missing in principal shop/product scope | 404 | `PRODUCT_SKU_NOT_FOUND` |
| Publish on non-draft product | 409 | `PRODUCT_STATUS_INVALID` |
| Unknown status filter or status update value | 409 | `PRODUCT_STATUS_INVALID` |
| Duplicate `skuCode` in request or DB uniqueness conflict | 409 | `PRODUCT_SKU_CODE_EXISTS` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=ProductCatalogServiceTest,ProductInventoryServiceTest,ProductCatalogControllerTest,InternalProductSnapshotControllerTest,InternalProductInventoryControllerTest,ProductMigrationContractTest,ProductInventoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Product review display changes must also include:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-order-service,services/sangui-gateway" -am "-Dtest=ProductCatalogServiceTest,ProductCatalogControllerTest,ProductReviewQueryServiceTest,InternalOrderReviewControllerTest,GatewayJwtAuthenticationFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Local Mall Demo Seed

The local demo seed is an explicit developer command, not a production startup hook.

Command:

```powershell
.\scripts\seed-mall-demo.ps1
```

Environment overrides:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SANGUI_DEMO_SHOP_ID` | `1` | Demo `shopId`. |
| `SANGUI_DEMO_USER_BASE_URL` | `http://localhost:8101` | Direct user-service origin. |
| `SANGUI_DEMO_PRODUCT_BASE_URL` | `http://localhost:8102` | Direct product-service origin. |
| `SANGUI_DEMO_USERNAME` | `mall_demo_user` | Mall login username. |
| `SANGUI_DEMO_MOBILE` | `13800001001` | Mall login mobile. |
| `SANGUI_DEMO_PASSWORD` | `Passw0rd!` | Local demo password only. |
| `SANGUI_DEMO_ADMIN_USER_ID` | `dev-seed-admin` | Local direct product-service admin operator id. |
| `SANGUI_DEMO_PRODUCT_NAME` | `Sangui Demo Trainer` | Demo product name used for idempotent lookup. |

Seed behavior:

- User creation uses `POST /api/users/register`, then verifies credentials with `POST /api/users/login`.
- Duplicate demo username or mobile is accepted only when login with the configured demo credentials succeeds.
- Product creation uses direct product-service `POST /api/admin/products` and `POST /api/admin/products/{productId}/publish`.
- The product request carries trusted local headers: `X-Sangui-User-Id`, `X-Sangui-Shop-Id`, `X-Sangui-Roles=ADMIN`, and `X-Sangui-Jwt-Id`.
- The command is intended for direct local service URLs even though gateway routes expose `/api/admin/products/**` for the admin UI.
- Product idempotency is based on active public catalog lookup by `productName`, followed by detail validation of expected SKU codes, names, prices, and positive `availableStock`.
- If the demo SKU codes already exist on a hidden draft/inactive product or with a conflicting payload, the command must fail clearly instead of silently overwriting non-demo data.

Seed payload:

```json
{
  "productName": "Sangui Demo Trainer",
  "productDescription": "Local demo product for the SanguiShop mall cart and checkout flow.",
  "skus": [
    {
      "skuCode": "demo-trainer-42",
      "skuName": "Size 42",
      "priceCent": 59900,
      "availableStock": 20
    },
    {
      "skuCode": "demo-trainer-43",
      "skuName": "Size 43",
      "priceCent": 62900,
      "availableStock": 15
    }
  ]
}
```

Validation and error matrix:

| Case | Expected behavior |
| --- | --- |
| Services are not running | Command fails with the failing URL and API error context. |
| User already exists with same credentials | Command logs existing user and continues. |
| User exists with different password | Command fails during login verification. |
| Active demo product exists with matching SKU payload | Command logs existing product and does not create duplicates. |
| Matching product name exists with missing/conflicting SKU data | Command fails with a conflict message. |
| SKU code exists on a non-public product | Admin create returns `PRODUCT_SKU_CODE_EXISTS`; command fails instead of overwriting. |

Good/Base/Bad cases:

- Good: repeated `.\scripts\seed-mall-demo.ps1` runs do not create duplicate active products or duplicate users.
- Good: seeded product appears through `GET /api/products` and `GET /api/products/{productId}` with two SKUs and positive `availableStock`.
- Good: the command is explicitly invoked by a developer and never runs from a production profile automatically.
- Base: a developer may override service origins or demo fields via environment variables for a non-default local environment.
- Bad: service startup automatically creates demo products in production.
- Bad: order-service or payment-service writes `pms_*` tables for seed data.
- Bad: the seed command silently overwrites an existing non-demo product or user.

## Good / Base / Bad Cases

- Good: admin list/detail/create/update/publish/status/stock-adjust derive `shopId` and operator from principal.
- Good: admin product APIs are available through gateway `/api/admin/products/**` and through direct product-service local URLs.
- Good: SKU detail includes `availableStock` and `reservedStock`.
- Good: status update and stock adjustment carry `requestId` and preserve unified `ApiResult<T>` `code/message/data/traceId/timestamp`.
- Good: public read still exposes only active products.
- Good: public product review read returns completed-order review assets through product-service without exposing order identifiers.
- Base: omitted `availableStock` defaults to `0`.
- Base: order-service remains the review projection source until a dedicated review projection service/table is introduced.
- Bad: public read exposes draft products or audit fields.
- Bad: product-service directly reads `oms_order` or `oms_order_review`.
- Bad: price uses floating-point types.
- Bad: admin write trusts body `shopId` or `userId`.
- Bad: stock adjustment mutates `reservedStock` directly.
