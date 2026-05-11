# Focused Code Research

## Relevant Specs

- `.trellis/spec/backend/directory-structure.md`: Backend package/layer ownership. New seckill API should follow `api`, `application`, `domain`, `infrastructure`, `client` layout.
- `.trellis/spec/backend/microservice-contracts.md`: REST envelope, `shopId`, write idempotency, DTO separation, error matrix, contract tests.
- `.trellis/spec/backend/gateway-security.md`: Admin API auth, JWT claims, service-side RBAC, `AUTH_FORBIDDEN`, principal `shopId` protection.
- `.trellis/spec/backend/database-guidelines.md`: Seckill table prefix `sk_`, required platform columns, unique index/idempotency guidance, migration contract if persistence is added.
- `.trellis/spec/backend/messaging-cache-guidelines.md`: Seckill Redis/MQ rules; this task should avoid implementing runtime Redis/MQ unless explicitly scoped later.
- `.trellis/spec/backend/seckill-contracts.md`: Current public seckill token/order contract; must be extended with admin activity APIs.
- `.trellis/spec/backend/error-handling.md`: `SanguiException`/validation/global handler mapping and response safety.
- `.trellis/spec/backend/logging-guidelines.md`: `traceId`, key business context, sensitive data restrictions.
- `.trellis/spec/backend/quality-guidelines.md`: WebMvc/service/contract test expectations and targeted Maven module selector style.
- `.trellis/spec/frontend/api-contracts.md`: Existing frontend-side Admin Seckill Activity Management API contract to align with backend route/payload/error shape.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Cross-layer API/DTO/shopId/idempotency questions.
- `.trellis/spec/guides/seckill-thinking-guide.md`: Activity state, server time, stock authority, duplicate request risks.
- `.trellis/spec/guides/architecture-review-checklist.md`: Service boundary, API/data contract, security, observability and tests.

## Code Patterns Found

- `common/sangui-common-core/src/main/java/com/sangui/shop/common/core/api/ApiResult.java`: Standard response envelope fields are `code`, `message`, `data`, `traceId`, `timestamp`.
- `common/sangui-common-core/src/main/java/com/sangui/shop/common/core/error/CommonErrorCode.java`: Existing shared errors include `VALIDATION_FAILED`, `AUTH_FORBIDDEN`, `IDEMPOTENCY_CONFLICT`, but seckill/product-specific errors need local domain enums or reuse.
- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPrincipal.java`: Trusted principal carries `userId`, `shopId`, `roles`, `permissions`, `jwtId`.
- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPermissionConstants.java`: Existing admin permissions do not include `SECKILL_ACTIVITY_ADMIN`; implementation should add it here.
- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/GlobalApiExceptionHandler.java`: Maps `SanguiException` and Bean Validation into `ApiResult.failure(...)` with `traceId`.
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/AdminOrderController.java`: Admin controller route/envelope/traceId pattern.
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/api/AdminOrderControllerTest.java`: WebMvc test pattern with `SanguiPrincipalArgumentResolver`, principal request attr, envelope assertions and error mapping.
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/AdminOrderManagementService.java`: Service-layer `requireAdmin(...)`, principal `shopId` scope, status parsing, validation, response assembly.
- `services/sangui-logistics-service/src/main/java/com/sangui/shop/logistics/application/AdminFulfillmentService.java`: Admin permission check, requestId trim/required, idempotency conflict, principal-scoped repository calls.
- `services/sangui-logistics-service/src/test/java/com/sangui/shop/logistics/application/AdminFulfillmentServiceTest.java`: In-memory repository/client service tests for idempotency and `OPS_COMPENSATION_ADMIN` denial.
- `services/sangui-product-service/src/main/java/com/sangui/shop/product/application/ProductInventoryService.java`: Product SKU authority and stock error pattern; maps missing SKU and insufficient stock to product domain errors.
- `frontend/src/types/api/seckill.ts`: Frontend DTO field names for admin seckill list/detail/draft/status/SKU bind.
- `frontend/src/services/seckillApi.ts`: Frontend route contract already uses `/api/admin/seckill/activities` with `authContext: 'ops'`.

## Files Likely To Modify

- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPermissionConstants.java`: Add `SECKILL_ACTIVITY_ADMIN`.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/api/AdminSeckillActivityController.java`: New admin route controller.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/api/dto/*`: New request/response DTO records matching PRD/frontend fields.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/application/AdminSeckillActivityService.java`: Principal permission, validation, idempotency, status transition and SKU bind orchestration.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/domain/*`: Activity status, error code, repository/client interfaces, record/snapshot models.
- `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/infrastructure/*`: Optional persistence/product SKU snapshot adapter if DeepSeek implements beyond in-memory tests.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/api/AdminSeckillActivityControllerTest.java`: WebMvc contract tests.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/application/AdminSeckillActivityServiceTest.java`: Validation, permission, idempotency, transition, SKU stock boundary tests.
- `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/infrastructure/persistence/SeckillActivityMigrationContractTest.java`: Required if migration is added.
- `.trellis/spec/backend/seckill-contracts.md`: Add backend admin activity API contract and tests.
- `.trellis/spec/frontend/api-contracts.md`: Update only if backend contract intentionally differs from current frontend section.
- `services/sangui-gateway/**`: Only if route configuration explicitly excludes `/api/admin/seckill/**`; research did not confirm a required gateway file change.
- `services/sangui-seckill-service/pom.xml`: Only if web/validation/test dependencies are insufficient for controller tests; current module already depends on common-web/security/core and `spring-boot-starter-test`.

## Risk / Boundary Notes

- Service ownership should be seckill-service; marketing-service is also skeletal but this task is about activity mechanics at the seckill entry point, not marketing campaigns.
- Do not trust frontend `shopId`, `userId`, `availableStock`, or local status inference. Backend principal and product-service stock authority win.
- If persistence is added, write down `sk_` table schema and idempotency indexes in backend specs. A minimal in-memory repository is acceptable for first executable contract tests only if PRD/spec still states the future DB contract.
- `SECKILL_ACTIVITY_ADMIN` does not exist yet in common security constants, so permission tests will fail until added.
- Existing `GlobalApiExceptionHandler` only handles `MethodArgumentNotValidException`; if controller uses constraint violations on query/path params, confirm existing behavior or add focused handling/tests.
- Stock error code must be chosen consistently. PRD allows `PRODUCT_STOCK_NOT_ENOUGH` or `STOCK_NOT_ENOUGH`; current product domain already uses `PRODUCT_STOCK_NOT_ENOUGH`.
- Gateway should authenticate and forward trusted headers, but downstream service must independently reject missing/insufficient principal permissions.
- Runtime seckill concurrency concerns (Redis Lua/MQ) remain out of scope. Do not implement them under this admin contract task.

## Required Tests

- `AdminSeckillActivityControllerTest`: route contract, envelope fields, traceId/timestamp presence, request validation, error mapping, principal argument use.
- `AdminSeckillActivityServiceTest`: `ADMIN` and `SECKILL_ACTIVITY_ADMIN` allowed; `OPS_COMPENSATION_ADMIN` alone denied; principal shop scope; activityName trim; time order; requestId required.
- Idempotency tests for create/update/status/SKU bind identical replay versus changed payload conflict.
- Status transition tests for valid and invalid transitions.
- SKU bind tests for found/not found SKU, `activityStock = availableStock`, `activityStock > availableStock`, negative stock.
- Spec assertions by review: backend seckill contract updated; frontend API contract updated only when field/error choices differ.

## Suggested Test Commands

```powershell
.\mvnw.cmd -q -pl services/sangui-seckill-service -am "-Dtest=AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

If common permission constants change, include common tests or rely on `-am` compile plus a targeted common test if added:

```powershell
.\mvnw.cmd -q -pl common/sangui-common-security,services/sangui-seckill-service -am "-Dtest=AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Before final handoff back to Codex check/finish-work, run at least:

```powershell
.\mvnw.cmd -q -pl services/sangui-seckill-service -am test
```
