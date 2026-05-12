# Focused Code Research

## Relevant Specs

- `.trellis/spec/backend/directory-structure.md`: service layer boundaries; `api`, `application`, `domain`, `infrastructure`, and `client` placement rules.
- `.trellis/spec/backend/microservice-contracts.md`: DTO/envelope rules, `shopId`, write idempotency, Feign/client fallback must not fake success.
- `.trellis/spec/backend/database-guidelines.md`: `sk_` table prefix, required platform columns, Flyway migration naming, uniqueness/index/test expectations.
- `.trellis/spec/backend/seckill-contracts.md`: admin seckill activity routes, request/response fields, status machine, idempotency rules, temporary repository/product adapter boundary.
- `.trellis/spec/backend/gateway-security.md`: admin API service-side RBAC remains required; trusted principal shop scope.
- `.trellis/spec/backend/error-handling.md`: validation/business/downstream exception mapping and fallback tests.
- `.trellis/spec/backend/logging-guidelines.md`: trace propagation and sensitive data boundaries.
- `.trellis/spec/backend/quality-guidelines.md`: targeted Maven reactor command shape and Good/Base/Bad review habit.
- `.trellis/spec/backend/inventory-reserve-contracts.md`: product-service owns SKU stock and inventory; other services must not read `pms_*` directly.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: cross-layer data/API/DB/idempotency checklist.
- `.trellis/spec/guides/seckill-thinking-guide.md`: seckill-specific idempotency and stock authority risks.
- `.trellis/spec/guides/architecture-review-checklist.md`: microservice boundary, DB contract, and fallback review order.

## Code Patterns Found

- Existing admin seckill controller/API contract:
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/api/AdminSeckillActivityController.java`
  - Uses `ApiResult`, `SanguiPrincipal`, `X-Trace-Id`, and existing success codes.
- Existing admin seckill application contract:
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/application/AdminSeckillActivityService.java`
  - Validates admin role/permission, normalizes inputs, uses `ActivityRepository`, validates `ProductSkuSnapshotClient`, enforces create/status/SKU idempotency in service.
- Existing temporary repository to replace:
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/InMemoryActivityRepository.java`
  - Implements `ActivityRepository` with maps keyed by `shopId:requestId`, `shopId:activityId:requestId`, and `shopId:activityId:skuId`.
- Existing product authority boundary in seckill:
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/domain/ProductSkuSnapshotClient.java`
  - Requires `productId`, `productName`, `skuId`, `skuCode`, `skuName`, `priceCent`, `availableStock`.
- Current temporary product adapter:
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/UnavailableProductSkuSnapshotClient.java`
  - Always unavailable; must not remain as production product authority after this task.
- Existing product-service internal snapshot endpoint:
  - `services/sangui-product-service/src/main/java/com/sangui/shop/product/api/InternalProductSnapshotController.java`
  - Route: `POST /internal/products/skus/snapshot`.
  - Current DTO returns `productId`, `skuId`, `skuCode`, `skuName`, `priceCent`; it does not currently include `productName` or `availableStock`.
- Existing product-service stock model:
  - `services/sangui-product-service/src/main/java/com/sangui/shop/product/domain/ProductSkuRecord.java`
  - Product inventory contract indicates `availableStock` and `reservedStock` are product-service-owned.
- Existing HTTP client style to product-service:
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/infrastructure/client/HttpProductCatalogClient.java`
  - Uses `RestClient`, timeout properties, `X-Trace-Id`, `ApiResult<T>` envelope mapping, and maps downstream product errors to service-domain exceptions.
- Existing product migration contract pattern:
  - `services/sangui-product-service/src/test/java/com/sangui/shop/product/infrastructure/persistence/ProductMigrationContractTest.java`
  - `services/sangui-product-service/src/test/java/com/sangui/shop/product/infrastructure/persistence/ProductInventoryMigrationContractTest.java`
  - Static migration contract style is available if live MySQL is not used.

## Files Likely To Modify

Seckill service:
- `services/sangui-seckill-service/pom.xml`: add JDBC/Flyway/test dependencies only if missing.
- `services/sangui-seckill-service/src/main/resources/application.yml`: configure datasource/flyway/client properties if missing.
- `services/sangui-seckill-service/src/main/resources/db/migration/V1__create_seckill_activity_tables.sql`: new migration; version may differ depending on existing migration state.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/domain/ActivityRepository.java`: only if current create/update/idempotency contract needs a narrow method split.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/InMemoryActivityRepository.java`: remove production bean or restrict to tests/dev profile.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/JdbcActivityRepository.java`: new real repository.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/ProductSkuSnapshotClientAdapter.java`: new real product-service adapter.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/client/*`: possible DTOs for product snapshot request/response if not reusing product-service client DTOs.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/application/AdminSeckillActivityService.java`: expected minor transaction/idempotency adaptation only; preserve API behavior.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/infrastructure/SeckillActivityMigrationContractTest.java`: new migration contract test.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/infrastructure/JdbcActivityRepositoryTest.java`: new repository test.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/infrastructure/ProductSkuSnapshotClientAdapterTest.java`: new adapter/fallback test.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/application/AdminSeckillActivityServiceTest.java`: keep application tests; may continue using fake repository/client or add repository-backed scenarios.

Product service:
- `services/sangui-product-service/src/main/java/com/sangui/shop/product/client/dto/ProductSkuSnapshotItemResponse.java`: likely add `productName` and `availableStock`.
- `services/sangui-product-service/src/main/java/com/sangui/shop/product/api/InternalProductSnapshotController.java`: map added fields.
- `services/sangui-product-service/src/main/java/com/sangui/shop/product/application/ProductCatalogService.java`: confirm `listActiveSkuSnapshots` provides needed data.
- `services/sangui-product-service/src/test/java/com/sangui/shop/product/api/InternalProductSnapshotControllerTest.java`: assert new fields.
- `services/sangui-product-service/src/test/java/com/sangui/shop/product/application/ProductCatalogServiceTest.java`: assert active/shop-scoped SKU snapshots include stock fields if not already covered.

Spec:
- `.trellis/spec/backend/seckill-contracts.md`: replace temporary adapter/repository boundary with real persistence and product snapshot adapter details.
- `.trellis/spec/backend/database-guidelines.md`: add seckill table/index/migration/test command contract.
- `.trellis/spec/backend/inventory-reserve-contracts.md` or `.trellis/spec/backend/microservice-contracts.md`: document product snapshot field expansion if changed.

## Risk / Boundary Notes

- Production must not keep `InMemoryActivityRepository` as the active `ActivityRepository` bean.
- seckill-service must not directly read product-service `pms_*` tables; product-service remains SKU and stock authority.
- Product snapshot endpoint currently lacks `productName` and `availableStock`; either expand product-service response or deliberately revise seckill contract, but do not silently set placeholders.
- `ActivityRepository.create(...)` currently also updates an existing activity when ID exists; the JDBC repository must either preserve this behavior or the interface must be split with tests updated.
- Write idempotency must be DB-backed, not only service-memory payload comparison.
- Unique indexes must include `shop_id`; single-merchant defaults are acceptable only as config/default values, not business magic numbers.
- Adapter fallback/downstream exceptions must map to failure. Returning `Optional.empty()` for all failures would incorrectly map infrastructure failure to `PRODUCT_SKU_NOT_FOUND`; distinguish missing SKU from downstream failure where possible.
- If MySQL-backed tests are too heavy locally, keep migration contract tests static but still add repository tests against a real JDBC-compatible test setup if the project already has one.

## Required Tests

Seckill targeted:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-seckill-service" -am "-Dtest=AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest,SeckillActivityMigrationContractTest,JdbcActivityRepositoryTest,ProductSkuSnapshotClientAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Product targeted if snapshot DTO changes:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service" -am "-Dtest=InternalProductSnapshotControllerTest,ProductCatalogServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Cross-service targeted:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-product-service,services/sangui-seckill-service" -am "-Dtest=InternalProductSnapshotControllerTest,ProductCatalogServiceTest,AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest,SeckillActivityMigrationContractTest,JdbcActivityRepositoryTest,ProductSkuSnapshotClientAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
