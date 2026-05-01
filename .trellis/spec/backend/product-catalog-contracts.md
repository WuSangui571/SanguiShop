# Product Catalog Contracts

## Scope

Product Catalog MVP for `services/sangui-product-service`.

This MVP is the first real business domain running on the completed auth chain:

- user-service issues JWT
- gateway validates JWT and forwards trusted `X-Sangui-*` headers
- product-service consumes `SanguiPrincipal` through `sangui-common-web`

## API Contracts

### Public Read APIs

| API | Auth | Request | Success code | Response data |
| --- | --- | --- | --- | --- |
| `GET /api/products?page=1&size=20` | optional/anonymous | query: `page`, `size` | `PRODUCT_LISTED` | `PageResponse<ProductSummaryResponse>` |
| `GET /api/products/{productId}` | optional/anonymous | path: `productId` | `PRODUCT_FETCHED` | `ProductDetailResponse` |

Public read behavior:

- Anonymous browsing is allowed.
- Public read derives shop scope from `sangui.shop.default-shop-id` and must not scan across all shops.
- Public read only returns products in `active` status.
- Public responses never expose persistence entities or internal audit fields such as `created_by` / `updated_by`.

### Admin Write APIs

| API | Auth | Request | Success code | Response data |
| --- | --- | --- | --- | --- |
| `POST /api/admin/products` | `SanguiPrincipal` required + `ADMIN` role | `CreateProductRequest` | `PRODUCT_CREATED` | `ProductDetailResponse` |
| `PUT /api/admin/products/{productId}` | `SanguiPrincipal` required + `ADMIN` role | `UpdateProductRequest` | `PRODUCT_UPDATED` | `ProductDetailResponse` |
| `POST /api/admin/products/{productId}/publish` | `SanguiPrincipal` required + `ADMIN` role | no body | `PRODUCT_PUBLISHED` | `ProductDetailResponse` |

Security rule:

- Controller parameters must use `SanguiPrincipal principal`.
- `shopId` for writes must come from `principal.shopId()`, not from request body or query params.
- Authenticated operator identity must come from `principal.userId()`, not from request body `userId`.
- Missing principal must fail through `SanguiPrincipalArgumentResolver` with `AUTH_TOKEN_MISSING`.
- Non-admin principal must fail with `AUTH_FORBIDDEN`.

## Request / Response Shapes

### `CreateProductRequest`

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
      "priceCent": 59900
    }
  ]
}
```

Notes:

- `shopId` and `userId` may appear in request bodies for malicious or legacy callers, but they are never trusted for authenticated scope.
- `productName` max length: `128`
- `productDescription` max length: `2048`
- `skus` must be non-empty
- `skuCode` pattern: `^[A-Za-z0-9_-]+$`
- `priceCent` must be a positive integer in cents

`UpdateProductRequest` has the same field contract as `CreateProductRequest`.

### `ProductSummaryResponse`

```json
{
  "productId": 101,
  "productName": "Sneaker",
  "productDescription": "Daily trainer",
  "minPriceCent": 59900,
  "maxPriceCent": 69900,
  "status": "active"
}
```

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
      "priceCent": 59900
    }
  ]
}
```

## State Machine

Persisted status values:

- `draft`
- `active`
- `inactive`

MVP transitions:

| Current | Operation | Next | Notes |
| --- | --- | --- | --- |
| none | create | `draft` | New products are always created as draft. |
| `draft` | update | `draft` | Update does not change status in MVP. |
| `draft` | publish | `active` | Supported publish path. |
| `active` | update | `active` | Content updates are allowed without status change. |
| `active` | publish | invalid | Return `PRODUCT_STATUS_INVALID`. |
| `inactive` | publish | invalid | Return `PRODUCT_STATUS_INVALID`. |

`inactive` is reserved in the persisted contract so future shelf-management flows can reuse the same enum without schema churn.

## Database Contract

Schema env and migration:

| Service | Schema Env | Default Schema | Migration |
| --- | --- | --- | --- |
| `services/sangui-product-service` | `SANGUI_PRODUCT_MYSQL_SCHEMA` | `sangui_product` | `db/migration/V1__create_product_catalog_tables.sql` |

### `pms_product`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `product_name`, `product_description`, `status`
- audit columns: `created_by`, `updated_by`

Required indexes:

- `idx_pms_product_shop_id_id (shop_id, id)`
- `idx_pms_product_shop_status (shop_id, status)`

### `pms_sku`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `product_id`, `sku_code`, `sku_name`, `sale_price_cent`
- audit columns: `created_by`, `updated_by`

Required constraints / indexes:

- `uk_pms_sku_shop_code (shop_id, sku_code)`
- `idx_pms_sku_shop_product (shop_id, product_id)`
- FK `product_id -> pms_product.id`

Money rule:

- All prices use integer cents in `BIGINT`.
- `double` / `float` are forbidden for prices.

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing principal on admin write API | 401 | `AUTH_TOKEN_MISSING` |
| Non-admin principal on admin write API | 403 | `AUTH_FORBIDDEN` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Product does not exist in principal shop scope | 404 | `PRODUCT_NOT_FOUND` |
| Publish on non-draft product | 409 | `PRODUCT_STATUS_INVALID` |
| Duplicate `skuCode` in request or DB uniqueness conflict | 409 | `PRODUCT_SKU_CODE_EXISTS` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=ProductMigrationContractTest,ProductCatalogServiceTest,ProductCatalogControllerTest,SanguiProductApplicationSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: anonymous `GET /api/products` returns only active products in `ApiResult<PageResponse<...>>`.
- Good: admin create/update/publish derive `shopId` and operator from `SanguiPrincipal`, even if body contains `shopId` / `userId`.
- Good: publish moves `draft -> active`.
- Base: `inactive` remains reserved in schema and response contract even before a dedicated unpublish endpoint exists.
- Bad: product write handlers trust request body `shopId` or `userId`.
- Bad: public read exposes draft products or audit fields.
- Bad: price uses floating-point types or response fields.
