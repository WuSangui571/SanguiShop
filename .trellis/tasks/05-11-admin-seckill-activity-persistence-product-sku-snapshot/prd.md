# 管理端秒杀活动真实持久化与 product-service SKU 快照适配

## Classification

Complex Task.

Reasons:
- Touches `services/sangui-seckill-service` persistence, DB migration, repository behavior, and admin write idempotency.
- Touches cross-service product authority through product-service internal SKU snapshot API / client adapter.
- Requires contract/spec updates and Good/Base/Bad tests across API, application, repository, migration, and downstream failure boundaries.

## Current Project State

Previous task `05-11-admin-seckill-activity-backend-contract-tests` is archived and recorded in `.trellis/workspace/codex-agent/journal-1.md`.

Completed in previous work:
- Admin seckill activity API contract exists for list/detail/create/update/status/SKU bind.
- Permissions, state machine, error codes, idempotency rules, and controller/application tests are in place.
- Existing tests passed for `AdminSeckillActivityControllerTest` and `AdminSeckillActivityServiceTest`, and the affected seckill service reactor.

Known remaining boundary from previous work:
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/InMemoryActivityRepository.java` is a temporary repository.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/UnavailableProductSkuSnapshotClient.java` is a temporary product adapter that cannot prove real SKU existence or stock.

## Goal

Replace temporary admin seckill activity storage and product SKU authority stubs with real, contract-tested persistence and product-service SKU snapshot integration.

The implementation must keep the existing admin API shape stable while making runtime behavior match the contract:
- Activities and SKU bindings persist in MySQL-backed `sk_*` tables.
- Admin write idempotency survives process restart and is enforced by DB constraints.
- SKU/product existence, shop scope, and available stock are validated against product-service authority.
- Product-service adapter fallback / downstream failure never returns fake success.

## In Scope

### Seckill Service Persistence

Add Flyway migration(s) under:

```text
services/sangui-seckill-service/src/main/resources/db/migration/
```

Required tables:
- `sk_activity`
- `sk_activity_sku`
- admin write idempotency support table(s), either one typed table or separate operation tables

Required persistence implementation:
- Replace production wiring of `InMemoryActivityRepository` with a MySQL/JDBC repository implementing `ActivityRepository`.
- Keep domain/application/controller contracts stable unless a repository gap requires a narrowly scoped interface adjustment.
- Preserve current response DTOs and existing admin API routes.

### Product-Service SKU Snapshot Boundary

Use or extend product-service internal API:

```http
POST /internal/products/skus/snapshot
```

Existing request shape:

```json
{
  "shopId": 1,
  "skuIds": [401]
}
```

Required response item fields for seckill authority:

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

Adapter:
- Implement real seckill-side `ProductSkuSnapshotClient` adapter that calls product-service and maps the first matching item for `(shopId, skuId)`.
- Propagate `X-Trace-Id`.
- Map product 404 / missing SKU to `PRODUCT_SKU_NOT_FOUND`.
- Map downstream timeout, invalid envelope, null data, or client exception to `DOWNSTREAM_TIMEOUT` or the existing project-standard downstream failure code, never to success.

### Spec Sync

Update the relevant backend specs after implementation:
- `.trellis/spec/backend/seckill-contracts.md`
- `.trellis/spec/backend/database-guidelines.md`
- If product snapshot API fields change, update the product-related contract section in `.trellis/spec/backend/inventory-reserve-contracts.md` or the most appropriate backend contract doc.

## Out of Scope

- No frontend UI changes.
- No Redis pre-deduct implementation.
- No MQ order creation flow changes.
- No public seckill purchase endpoint changes.
- No new admin routes beyond the existing admin seckill activity contract unless strictly needed for tests.
- No direct seckill reads of `pms_*` tables.
- No fallback that fabricates product/SKU data.
- No broad refactor of existing admin seckill state machine, DTO names, or response envelope.

## Existing Admin API Contract To Preserve

Base path:

```text
/api/admin/seckill/activities
```

Routes:

| Method | Path | Success code |
| --- | --- | --- |
| `GET` | `/api/admin/seckill/activities` | `ADMIN_SECKILL_ACTIVITY_LIST` |
| `GET` | `/api/admin/seckill/activities/{activityId}` | `ADMIN_SECKILL_ACTIVITY_DETAIL` |
| `POST` | `/api/admin/seckill/activities` | `ADMIN_SECKILL_ACTIVITY_CREATED` |
| `PUT` | `/api/admin/seckill/activities/{activityId}` | `ADMIN_SECKILL_ACTIVITY_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/status` | `ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/skus` | `ADMIN_SECKILL_ACTIVITY_SKU_BOUND` |

### Create / Update Payload

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

Effective `shopId` comes from `SanguiPrincipal`, not request body.

### Status Payload

```json
{
  "status": "scheduled",
  "requestId": "admin-generated-id"
}
```

### SKU Bind Payload

```json
{
  "productId": 301,
  "skuId": 401,
  "activityStock": 10,
  "seckillPriceCent": 49900,
  "requestId": "admin-generated-id"
}
```

## Data Contract

### `sk_activity`

Required columns:
- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `shop_id BIGINT NOT NULL DEFAULT 1`
- `activity_name VARCHAR(...) NOT NULL`
- `description VARCHAR(...) NULL`
- `status VARCHAR(32) NOT NULL`
- `starts_at DATETIME NOT NULL`
- `ends_at DATETIME NOT NULL`
- `request_id VARCHAR(64) NOT NULL`
- `trace_id VARCHAR(64) NULL`
- `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
- `deleted TINYINT NOT NULL DEFAULT 0`
- `version INT NOT NULL DEFAULT 0`

Required constraints/indexes:
- unique idempotency index for create/update request replay, at minimum `(shop_id, request_id)` for non-deleted rows.
- status/page query index such as `(shop_id, status, created_at, id)`.
- detail lookup must scope by `(shop_id, id, deleted)`.

### `sk_activity_sku`

Required columns:
- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `shop_id BIGINT NOT NULL DEFAULT 1`
- `activity_id BIGINT NOT NULL`
- `product_id BIGINT NOT NULL`
- `product_name VARCHAR(...) NOT NULL`
- `sku_id BIGINT NOT NULL`
- `sku_code VARCHAR(...) NOT NULL`
- `sku_name VARCHAR(...) NOT NULL`
- `price_cent BIGINT NOT NULL`
- `seckill_price_cent BIGINT NOT NULL`
- `available_stock BIGINT NOT NULL`
- `activity_stock BIGINT NOT NULL`
- `sold_count BIGINT NOT NULL DEFAULT 0`
- `request_id VARCHAR(64) NULL`
- `trace_id VARCHAR(64) NULL`
- `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
- `deleted TINYINT NOT NULL DEFAULT 0`
- `version INT NOT NULL DEFAULT 0`

Required constraints/indexes:
- unique SKU binding index `(shop_id, activity_id, sku_id)`.
- idempotency index for SKU bind replay `(shop_id, activity_id, request_id)` when `request_id` is non-null.
- query index `(shop_id, activity_id, deleted)`.

### Admin Status Idempotency

Persist status request records keyed by:

```text
shop_id + activity_id + request_id
```

Required fields:
- target status
- trace id
- created/updated timestamps
- platform columns if using a dedicated table

Same key with same target status returns current snapshot. Same key with different target status returns `IDEMPOTENCY_CONFLICT`.

## Validation / Error Matrix

| Condition | HTTP | Code | Required assertion |
| --- | --- | --- | --- |
| Principal lacks `ADMIN` and `SECKILL_ACTIVITY_ADMIN` | 403 | `AUTH_FORBIDDEN` | service rejects even if gateway would pass |
| Missing / blank `activityName` | 400 | `VALIDATION_FAILED` | trim before validation |
| Invalid ISO timestamp or `startsAt >= endsAt` | 400 | `VALIDATION_FAILED` | no row inserted |
| Missing / blank write `requestId` | 400 | `VALIDATION_FAILED` | no row inserted |
| Activity missing or wrong shop | 404 | `SECKILL_ACTIVITY_NOT_FOUND` | repository scopes by `shop_id` |
| Invalid status value / transition | 409 | `SECKILL_ACTIVITY_STATUS_INVALID` | status request not persisted for invalid new transition unless contract says replay same current status |
| Product-service returns missing SKU or response omits requested SKU | 404 | `PRODUCT_SKU_NOT_FOUND` | seckill does not persist activity/SKU |
| Product snapshot `productId` differs from request `productId` | 404 | `PRODUCT_SKU_NOT_FOUND` | cross-product bind rejected |
| `activityStock > availableStock` | 409 | `PRODUCT_STOCK_NOT_ENOUGH` | no row mutation |
| Product-service timeout / invalid envelope / fallback | 503 | `DOWNSTREAM_TIMEOUT` | never fake success |
| Same `requestId` with identical normalized create/update payload | 200 | original success code | returns stored row after restart-capable persistence |
| Same `requestId` with changed create/update payload | 409 | `IDEMPOTENCY_CONFLICT` | DB uniqueness plus payload comparison prevents mutation |
| Same SKU-bind `requestId` with identical payload | 200 | `ADMIN_SECKILL_ACTIVITY_SKU_BOUND` | returns existing/current activity snapshot |
| Same SKU-bind `requestId` with changed payload | 409 | `IDEMPOTENCY_CONFLICT` | no row mutation |
| Same status `requestId` with same target | 200 | `ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED` | returns current snapshot |
| Same status `requestId` with different target | 409 | `IDEMPOTENCY_CONFLICT` | no status mutation |

## Good / Base / Bad Cases

Good:
- Creating an activity with one valid product-service SKU persists one `sk_activity` row and one `sk_activity_sku` row scoped to principal `shopId`.
- Listing activities uses trusted `shopId`, returns stable pagination, status filter, SKU count, total activity stock, and sold count from persisted rows.
- Replaying identical create/update/status/SKU requests after repository replacement returns the original/current snapshot without duplicate rows.
- Binding the same `skuId` again updates the existing `(shop_id, activity_id, sku_id)` row rather than duplicating it.
- Cross-shop lookup cannot read or mutate another shop's activity or SKU binding.
- Product-service unavailable maps to downstream failure and does not persist a draft based on fake SKU data.

Base:
- `available_stock` is a product snapshot copied at admin bind time; it does not reserve or decrement product stock in this task.
- Product snapshot query can stay batch-shaped even if seckill adapter asks for one SKU at a time.
- MySQL migration contract tests can be static SQL contract tests if live Docker MySQL is not available locally.

Bad:
- seckill-service reads `pms_sku` or product-service database directly.
- `InMemoryActivityRepository` remains the production repository bean after this task.
- fallback returns a synthetic SKU snapshot.
- unique indexes omit `shop_id`.
- request body `shopId` overrides trusted principal.
- repository tests only cover happy path and miss idempotency/cross-shop uniqueness.

## Required Tests

### Seckill Service

Required targeted command:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-seckill-service" -am "-Dtest=AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest,SeckillActivityMigrationContractTest,JdbcActivityRepositoryTest,ProductSkuSnapshotClientAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Required assertion points:
- Existing controller and service contract tests still pass.
- Migration contract test verifies `sk_activity`, `sk_activity_sku`, status/admin idempotency table(s), platform columns, `shop_id`, `request_id`, unique indexes, and lookup indexes.
- Repository test verifies create, update, list paging, status transition optimistic check, SKU upsert, logical delete filtering if implemented, cross-shop isolation, and idempotency lookup.
- Repository test verifies uniqueness rejects duplicate `(shop_id, request_id)`, duplicate `(shop_id, activity_id, sku_id)`, and conflicting status/SKU request ids.
- Product adapter test verifies successful mapping, missing SKU, product mismatch by service layer, cross-shop request body propagation, stock shortage through service, invalid envelope, and downstream exception.

### Product Service

Required targeted command if product snapshot response fields change:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=InternalProductSnapshotControllerTest,ProductCatalogServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Required assertion points:
- Internal SKU snapshot response includes `productName` and `availableStock` if seckill requires them.
- Only active product SKUs for the requested `shopId` are returned.
- Unknown or inactive SKU is omitted or mapped according to the existing product contract; seckill adapter must treat missing requested SKU as `PRODUCT_SKU_NOT_FOUND`.

### Cross-Service Contract

Required targeted command after all related tests exist:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-seckill-service" -am "-Dtest=InternalProductSnapshotControllerTest,ProductCatalogServiceTest,AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest,SeckillActivityMigrationContractTest,JdbcActivityRepositoryTest,ProductSkuSnapshotClientAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Implementation Notes For DeepSeek

- Prefer `JdbcTemplate`/existing JDBC style if no MyBatis pattern exists in seckill service; product-service already uses JDBC persistence patterns.
- Keep `ActivityRepository` as the application boundary where possible.
- If `ActivityRepository.create(...)` currently doubles as update, either preserve that behavior in the JDBC implementation or split methods narrowly with tests updated to express the new repository contract.
- Add `@Transactional` at application or repository boundaries where multiple tables must be written atomically.
- Store timestamps consistently with existing services; API layer already emits ISO strings.
- Do not remove existing tests; adapt them to real repository only where needed.
- Keep a test fake `ProductSkuSnapshotClient` for application tests; real adapter tests should focus on HTTP/envelope mapping.

## Acceptance Criteria

- [ ] Production seckill repository bean is MySQL-backed, not in-memory.
- [ ] Seckill Flyway migration creates real activity/SKU/idempotency persistence with required indexes.
- [ ] Product-service internal SKU snapshot response provides all fields required by `ProductSkuSnapshotClient` or the seckill adapter has an explicit, tested mapping strategy.
- [ ] Product adapter maps missing SKU, cross-shop absence, stock shortage, and downstream failure without fake success.
- [ ] Existing admin seckill API route shape, response codes, permission behavior, and state machine remain stable.
- [ ] `.trellis/spec/backend/seckill-contracts.md` and `.trellis/spec/backend/database-guidelines.md` are updated with real table/index/test command details.
- [ ] Required targeted Maven tests pass and output proves intended test classes ran.
