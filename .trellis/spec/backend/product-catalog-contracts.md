# Product Catalog Contracts

## Scope

Product Catalog MVP for `services/sangui-product-service`.

This document covers public catalog read APIs and admin product maintenance. Inventory reservation write paths are documented in [Inventory Reserve Contracts](./inventory-reserve-contracts.md).

## Public Read APIs

| API | Auth | Success code | Response |
| --- | --- | --- | --- |
| `GET /api/products?page=1&size=20` | anonymous allowed | `PRODUCT_LISTED` | `PageResponse<ProductSummaryResponse>` |
| `GET /api/products/{productId}` | anonymous allowed | `PRODUCT_FETCHED` | `ProductDetailResponse` |

Rules:

- public read derives shop scope from `sangui.shop.default-shop-id`
- only `active` products are visible
- public responses never expose persistence entities or audit fields

## Admin Write APIs

| API | Auth | Success code | Response |
| --- | --- | --- | --- |
| `POST /api/admin/products` | `ADMIN` principal required | `PRODUCT_CREATED` | `ProductDetailResponse` |
| `PUT /api/admin/products/{productId}` | `ADMIN` principal required | `PRODUCT_UPDATED` | `ProductDetailResponse` |
| `POST /api/admin/products/{productId}/publish` | `ADMIN` principal required | `PRODUCT_PUBLISHED` | `ProductDetailResponse` |

Security rules:

- controller parameters must use `SanguiPrincipal`
- effective `shopId` and operator identity come from principal only
- missing principal -> `AUTH_TOKEN_MISSING`
- non-admin principal -> `AUTH_FORBIDDEN`

## Request / Response Shapes

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
- `skuCode` pattern: `^[A-Za-z0-9_-]+$`
- duplicate `skuCode` values in one request are rejected

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
| Missing principal on admin write API | 401 | `AUTH_TOKEN_MISSING` |
| Non-admin principal on admin write API | 403 | `AUTH_FORBIDDEN` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Product missing in principal shop scope | 404 | `PRODUCT_NOT_FOUND` |
| Publish on non-draft product | 409 | `PRODUCT_STATUS_INVALID` |
| Duplicate `skuCode` in request or DB uniqueness conflict | 409 | `PRODUCT_SKU_CODE_EXISTS` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=ProductCatalogServiceTest,ProductInventoryServiceTest,ProductCatalogControllerTest,InternalProductSnapshotControllerTest,InternalProductInventoryControllerTest,ProductMigrationContractTest,ProductInventoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: admin create/update/publish derive `shopId` and operator from principal.
- Good: SKU detail includes `availableStock` and `reservedStock`.
- Good: public read still exposes only active products.
- Base: omitted `availableStock` defaults to `0`.
- Bad: public read exposes draft products or audit fields.
- Bad: price uses floating-point types.
- Bad: admin write trusts body `shopId` or `userId`.
