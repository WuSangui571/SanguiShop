# Journal - sangui (Part 1)

> AI development session journal
> Started: 2026-04-29

---

## Session 1: SanguiShop Phase 1 Foundation Scaffold

**Date**: 2026-04-29
**Task**: `phase-1-foundation`
**Branch**: `main`

### Summary

Built the first-phase SanguiShop foundation scaffold with Maven multi-module structure, common technical modules, service shells, local dependency placeholders, and documentation.

### Main Changes

- Added the root Maven parent project and module aggregation for `common/` and `services/`.
- Added common modules for API envelopes, error codes, tracing, JWT claim constants, Redis key naming, MQ event envelopes, and observability field names.
- Added Gateway and business service shells for user, product, seckill, order, payment, logistics, review, marketing, search recommendation, and AI.
- Added local `deploy/docker-compose.yml` and `.env.example` for MySQL, Redis, Nacos, and RocketMQ.
- Added frontend placeholder docs and phase-1 foundation documentation.
- Updated backend DevOps spec with the phase-1 local environment contract.

### Git Commits

| Hash | Message |
|------|---------|
| `2d4df65` | `feat: 新增项目的基础脚手架` |

### Testing

- [OK] `mvn -q -DskipTests compile` passed.
- [WARN] `mvn -q test` and `mvn -q -DskipTests package` were blocked in the Codex sandbox by access to `C:\Users\CodexSandboxOffline\.m2\repository`.

### Status

[OK] **Completed**

### Next Steps

- Run local verification outside the sandbox if Maven can access the normal local repository.
- Start the next task only after deciding which business domain should be implemented first.



## Session 2: 补 Maven Wrapper

**Date**: 2026-04-29
**Task**: 补 Maven Wrapper
**Branch**: `main`

### Summary

为项目补充 Maven Wrapper 入口 mvnw、mvnw.cmd 和 .mvn/wrapper 配置，固定 Maven 3.9.9；同步后端 DevOps/Quality 规范改为优先使用 wrapper 命令，并补充跨平台换行约束。已验证 mvnw.cmd -v 可运行；测试/编译在当前沙箱因 Maven 依赖网络解析被拦截未完成。

### Main Changes

- Added Maven Wrapper entrypoints `mvnw` and `mvnw.cmd`.
- Added `.mvn/wrapper/maven-wrapper.properties` pinned to Apache Maven 3.9.9 with SHA-512 verification.
- Added `.gitattributes` rules for wrapper line endings across Windows and Unix CI.
- Updated backend DevOps and Quality specs to prefer wrapper-based Maven commands.

### Git Commits

| Hash | Message |
|------|---------|
| `ce2b520` | feat:补 Maven Wrapper |

### Testing

- [OK] `.\mvnw.cmd -v` reports Apache Maven 3.9.9.
- [WARN] `.\mvnw.cmd -q test` and `.\mvnw.cmd -q -DskipTests compile` were attempted but blocked by sandboxed Maven dependency resolution/network access.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Phase 1 Quality Closure

**Date**: 2026-04-29
**Task**: Phase 1 Quality Closure
**Branch**: `main`

### Summary

为第一阶段脚手架建立最小质量闭环：补充 common 契约单元测试、Gateway 和 User 服务 smoke test、GitHub Actions CI、本地 scripts/verify.ps1、Docker Compose config 校验入口、Maven Enforcer Java/Maven 版本约束，并同步 phase-1 文档与后端质量/DevOps spec。

### Main Changes

- Added common contract tests for `ApiResult`, `CommonErrorCode`, `RedisKeyBuilder`, and `EventEnvelope`.
- Added Gateway and User service smoke tests to verify startup classes and basic Spring context loading.
- Added `.github/workflows/ci.yml` and `scripts/verify.ps1` for Maven tests, package verification, and Docker Compose config validation.
- Added Maven Enforcer rules for Java 21 and Maven 3.9.9+.
- Updated `docs/phase-1-foundation.md` plus backend DevOps/Quality specs with executable verify commands and required assertion points.

### Git Commits

| Hash | Message |
|------|---------|
| `c955df4` | feat:最小单元测试、CI/verify 脚本、Docker Compose 配置校验和基础构建规范 |

### Testing

- [OK] Human reported testing completed before recording.
- [OK] Commit `c955df4` exists on `main`.
- [OK] Working directory was clean before session recording.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Phase 2 Persistence Foundation

**Date**: 2026-04-29
**Task**: Phase 2 Persistence Foundation
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Flyway | Added user-service Flyway runtime baseline with JDBC/MySQL dependencies and environment-driven datasource/Flyway configuration. |
| User Schema | Added `V1__create_user_identity_tables.sql` for `ums_user`, including platform columns, account identity fields, unique username/mobile indexes, and logical-delete/status indexes. |
| Tests | Added `UserMigrationContractTest` to verify migration resource naming, required columns, and required indexes without live MySQL. Kept user smoke test independent from live datasource/Flyway. |
| Spec Sync | Updated backend database and quality guidelines with executable migration contract, validation commands, Good/Base/Bad cases, and repository test strategy. |
| Docs/Config | Added `docs/phase-2-persistence-foundation.md` and documented user schema/Flyway env placeholders in `deploy/.env.example`. |
| Verification | Static checks, XML parse, Docker Compose config validation, and Trellis task validation passed. Maven test was attempted but blocked in the agent sandbox by network dependency resolution; human tested and committed locally. |

**Commit**: `c32d916 feat:Persistence Foundation`

**Archived Task**: `.trellis/tasks/archive/2026-04/04-29-phase-2-persistence-foundation`


### Git Commits

| Hash | Message |
|------|---------|
| `c32d916` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: User Registration Login MVP

**Date**: 2026-04-29
**Task**: User Registration Login MVP
**Branch**: `main`

### Summary

Implemented and tested user registration/login API with validation, password hashing, JWT issuance, ApiResult errors, and WebMvc/service coverage.

### Main Changes

Implemented and human-tested the User Registration/Login MVP.

| Area | Summary |
| --- | --- |
| API | Added public `POST /api/users/register` and `POST /api/users/login` endpoints returning the standard `ApiResult` envelope. |
| DTO/Validation | Added validated request/response DTOs for registration and login without exposing persistence entities. |
| Security | Added BCrypt password hashing and HS256 JWT issuance with required Sangui claims: `sub`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, and `jti`. |
| Error Handling | Extended `SanguiException` with HTTP status support and mapped duplicate/credential/config failures to stable error codes. |
| Persistence | Added a JDBC repository adapter over `ums_user` with shop-scoped username/mobile lookups and login timestamp updates. |
| Tests | Added WebMvc, service, and JWT issuer tests covering happy path, validation, duplicates, invalid credentials, password hashing, and token claims. |
| Spec Sync | Added `.trellis/spec/backend/authentication-contracts.md` with executable endpoint, JWT, error, and test contracts. |

Human verification completed before recording. Code was committed as `855c5ae feat:User Registration/Login MVP`.


### Git Commits

| Hash | Message |
|------|---------|
| `855c5ae` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: Local User Service Startup Validation

**Date**: 2026-05-01
**Task**: Local User Service Startup Validation
**Branch**: `main`

### Summary

Verified local User Service startup and committed MySQL/Flyway startup fixes.

### Main Changes

Recorded local startup validation for the User Service auth MVP after human testing and commit.

| Area | Summary |
| --- | --- |
| Local Runtime | Human verified local startup with Docker Desktop, MySQL, Nacos, PowerShell environment variables, Maven install, and `spring-boot:run`. |
| Manual API Check | Human verified `POST /api/users/register` returns `USER_REGISTERED` and `POST /api/users/login` returns `USER_LOGGED_IN` with a Bearer JWT. |
| MySQL Runtime | Adjusted local Compose MySQL image from `mysql:8.4` to `mysql:8.0` for the verified local environment. |
| Flyway Runtime | Added `org.flywaydb:flyway-mysql` to `services/sangui-user-service` so Flyway can run MySQL migrations at application startup. |
| Scope Decision | Kept `flyway-mysql` limited to user-service because it is currently the only service with real `spring.datasource`, `spring.flyway`, migrations, and repository code. Other service modules only keep MySQL placeholders for future work. |

Commit recorded: `54c0c2c fix: support local MySQL Flyway startup`.

Useful verified startup outline:
1. Start Docker Desktop.
2. Run `docker compose --env-file deploy\.env -f deploy\docker-compose.yml up -d mysql nacos`.
3. Set PowerShell env vars for MySQL, Nacos, `SANGUI_JWT_SECRET`, `SANGUI_JWT_TTL_SECONDS`, and `SPRING_DATASOURCE_URL` with `allowPublicKeyRetrieval=true`.
4. Run `.\mvnw.cmd "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services\sangui-user-service -am -DskipTests install`.
5. Run `.\mvnw.cmd "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services\sangui-user-service spring-boot:run`.
6. Test register/login at `http://localhost:8101/api/users/register` and `http://localhost:8101/api/users/login`.


### Git Commits

| Hash | Message |
|------|---------|
| `54c0c2c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: Gateway JWT Authentication MVP

**Date**: 2026-05-01
**Task**: Gateway JWT Authentication MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Gateway Auth | Added Gateway JWT Authentication MVP using a reactive global filter for `/api/**`, with public pass-through for `POST /api/users/register` and `POST /api/users/login`. |
| JWT Validation | Gateway validates HS256 signature, issuer, `iat`/`exp`, required claims, and returns standard `ApiResult` auth failures. |
| Downstream Context | Gateway strips spoofed Sangui identity headers and forwards trusted `X-Sangui-User-Id`, `X-Sangui-Shop-Id`, `X-Sangui-Roles`, `X-Sangui-Permissions`, and `X-Sangui-Jwt-Id`. |
| User Service Compatibility | User-service JWT issuer now includes `iss` and shares the configurable `SANGUI_JWT_ISSUER` contract with gateway. |
| Shared Contracts | Added `SIGNATURE_INVALID`, shared JWT issuer claim, and shared Sangui identity header names. |
| Tests | Added gateway JWT filter tests and extended JWT issuer tests for issuer behavior. |
| Spec Sync | Updated `.trellis/spec/backend/authentication-contracts.md` with executable Gateway JWT contract, headers, error matrix, and required test commands. |

Human verification completed before recording. The following commands were reported passing:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-user-service" -am "-Dtest=HmacJwtUserTokenIssuerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-core,common/sangui-common-security,services/sangui-gateway,services/sangui-user-service" -am -DskipTests compile
```

Manual API checks reported:
- Existing `alice` login succeeded at `POST http://localhost:8101/api/users/login`, returning a Bearer JWT containing `iss=sanguishop`.
- New `bob` registration succeeded at `POST http://localhost:8101/api/users/register` with `USER_REGISTERED`.

Commit recorded: `cc5b2ff feat:Gateway JWT Authentication MVP`.


### Git Commits

| Hash | Message |
|------|---------|
| `cc5b2ff` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: Downstream Auth Context MVP

**Date**: 2026-05-01
**Task**: Downstream Auth Context MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Downstream Auth Context | Added common downstream authentication context support so servlet business services consume trusted Gateway identity headers through shared code instead of parsing headers by hand. |
| Common Security | Extended `SanguiPrincipal` with `jwtId`, added `SanguiPrincipalHeaderParser`, and added `SanguiSecurityContext` for request-local principal access. |
| Common Web | Added `SanguiAuthenticationContextFilter`, `SanguiPrincipalArgumentResolver`, and auto-configuration wiring for the filter and MVC resolver. |
| Security Contract | Required identity comes only from trusted `X-Sangui-*` headers; DTO/query/body `userId` and `shopId` are not treated as authenticated identity. |
| Spec Sync | Updated `.trellis/spec/backend/authentication-contracts.md` with executable downstream auth context contract, validation matrix, Good/Base/Bad cases, and test command. |
| Tests | Added parser, servlet filter, and argument resolver tests covering full headers, missing/invalid user/shop, optional principal, required rejection, DTO/query/body ignore behavior, and ThreadLocal cleanup. |

Human committed implementation before recording.

Verification reported during implementation:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-security,common/sangui-common-web" -am "-Dtest=SanguiPrincipalHeaderParserTest,SanguiAuthenticationContextFilterTest,SanguiPrincipalArgumentResolverTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-security,common/sangui-common-web" -am test
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-security,common/sangui-common-web,services/sangui-gateway" -am -DskipTests compile
git diff --check
```

Commit recorded: `a7328d3 feat: add downstream auth context`.


### Git Commits

| Hash | Message |
|------|---------|
| `a7328d3` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: Product Catalog MVP

**Date**: 2026-05-01
**Task**: Product Catalog MVP
**Branch**: `main`

### Summary

Implemented the Product Catalog MVP with Flyway schema, public/admin APIs, SanguiPrincipal-enforced writes, tests, and backend contract documentation.

### Main Changes

| Area | Summary |
| --- | --- |
| Product Service | Added the first real product business service implementation on top of the completed auth chain in `services/sangui-product-service`. |
| Persistence | Added JDBC + Flyway runtime wiring, `SANGUI_PRODUCT_MYSQL_SCHEMA` / `SANGUI_PRODUCT_FLYWAY_ENABLED` env contracts, and `V1__create_product_catalog_tables.sql` for `pms_product` and `pms_sku`. |
| Product APIs | Added anonymous `GET /api/products` and `GET /api/products/{productId}` plus admin write APIs for create, update, and publish. |
| Auth Context | Admin write controllers consume `SanguiPrincipal`; write flows derive `shopId` and operator identity from trusted principal instead of request body `shopId` / `userId`. |
| Business Rules | Added product status machine with `draft`, `active`, `inactive`, and MVP publish transition `draft -> active`. |
| Public Scope | Fixed public product reads to derive shop scope from `sangui.shop.default-shop-id` instead of scanning across shops. |
| Tests | Added smoke, migration contract, application service, and WebMvc coverage for product catalog behavior and auth-context assertions. |
| Spec Sync | Added executable backend contract doc `.trellis/spec/backend/product-catalog-contracts.md` for API/database/error/test expectations. |

**Verification**:
- Human tested locally and committed `58683bc feat(product): implement product catalog mvp`.
- Verified in agent with product-service Maven test suite and module compile.


### Git Commits

| Hash | Message |
|------|---------|
| `58683bc` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: Order Create MVP

**Date**: 2026-05-01
**Task**: Order Create MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Order Service | Implemented the first order creation business flow in `services/sangui-order-service` with JDBC persistence, Flyway schema, `POST /api/orders`, principal-derived identity, request-id idempotency, and order item SKU snapshot storage. |
| Product Snapshot Integration | Added internal product snapshot API `POST /internal/products/skus/snapshot` so order creation reads active SKU snapshot data through service contracts instead of cross-service database access. |
| Runtime Dependency Fix | Removed the accidental runtime dependency from order-service to product-service and localized snapshot DTOs in order-service, fixing Flyway classpath pollution and duplicate migration loading. |
| Testing | Added and verified migration contract, application service, WebMvc controller, and internal product snapshot controller tests for the new order and product integration contracts. |
| Spec Sync | Added `.trellis/spec/backend/order-create-contracts.md`, updated backend spec index, and extended product catalog contract documentation with the internal snapshot API. |

**Verification**:
- Human tested Maven test suite and compile commands locally.
- Human reproduced and validated the startup issue caused by product-service runtime dependency, then committed the dependency fix.
- Verified in agent with module test suite and compile after the fix.


### Git Commits

| Hash | Message |
|------|---------|
| `befcf39` | (see git log) |
| `c852047` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: IDEA Local Startup Stabilization

**Date**: 2026-05-01
**Task**: IDEA Local Startup Stabilization
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Local Startup | Unified local MySQL JDBC URLs in user-service, product-service, and order-service so IDEA-based startup no longer relies on per-window PowerShell overrides. |
| MySQL Connectivity | Added `allowPublicKeyRetrieval=true` to the three service datasource URLs, fixing the shared MySQL 8 local authentication failure (`Public Key Retrieval is not allowed`). |
| IDEA Workflow | Human verified the three small business services can now start successfully from IDEA after moving away from manual terminal-based service startup. |

**Verification**:
- Human tested successful IDEA startup for user-service, product-service, and order-service.
- Commit recorded: `f82afb7 fix(config): support mysql public key retrieval for local startup`.


### Git Commits

| Hash | Message |
|------|---------|
| `f82afb7` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: Payment Pay MVP

**Date**: 2026-05-01
**Task**: Payment Pay MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Payment Service | Implemented `services/sangui-payment-service` as the first payment write-path service with JDBC, Flyway, payment idempotency on `paymentNo`, and synchronous order pay orchestration. |
| Payment API | Added authenticated `POST /api/payments` using `SanguiPrincipal`, principal-derived shop/user scope, and stable `PAYMENT_PAID` response envelope. |
| Order Integration | Added internal order-service payment snapshot and payment confirmation APIs so payment-service can move orders from `created` to `paid` without direct database access. |
| Persistence | Added `V1__create_payment_tables.sql` for `pay_payment_order` and `pay_callback_log`, plus payment repository support and status persistence. |
| Tests | Added payment smoke, migration contract, application service, and controller tests, plus order-side payment service/controller tests for the cross-service contract. |
| Spec Sync | Added `.trellis/spec/backend/payment-pay-contracts.md`, updated backend spec index, and extended order create contract docs with executable payment linkage notes. |

**Verification**:
- Human started the full local flow successfully after initializing the `sangui_payment` schema and permissions.
- Human committed `cf4536c feat(payment): implement payment pay mvp`.
- Verified in agent with module compile and targeted Maven test suites for order/payment.


### Git Commits

| Hash | Message |
|------|---------|
| `cf4536c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 13: Inventory Reserve MVP

**Date**: 2026-05-01
**Task**: Inventory Reserve MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Product Service | Added sellable inventory fields to SKU data, introduced inventory reservation persistence, and exposed internal reserve / confirm / release APIs with reservation idempotency keyed by `(shopId, reservationNo)`. |
| Order Service | Changed order creation to reserve inventory before persisting orders, stored `reservationNo` on orders, exposed reservation-aware payment snapshots, and added order cancellation that releases reserved inventory. |
| Payment Service | Extended payment records with `reservationNo` and updated the pay path to confirm inventory reservations alongside order payment confirmation under idempotent replay. |
| Database & Contracts | Added V2 Flyway migrations for product, order, and payment services and synchronized backend executable specs for inventory reserve, order create, payment pay, and product catalog contracts. |
| Testing | Added targeted application, controller, and migration contract tests for reserve / confirm / release, order cancel, reservation-aware payment flow, and replay safety. |

**Verification**:
- Human committed `fe3ac1c feat(inventory): implement reserve mvp` after testing.
- Verified in agent with targeted Maven test suites for product/order/payment inventory reservation and payment replay coverage.


### Git Commits

| Hash | Message |
|------|---------|
| `fe3ac1c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 14: Payment Callback Timeout Compensation MVP

**Date**: 2026-05-01
**Task**: Payment Callback Timeout Compensation MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Payment Callback | Added mock async callback handling through `POST /api/payments/callbacks/mock`, recording provider notifications in `pay_callback_log` before state mutation. |
| Payment Polling | Added `GET /api/payments/{paymentNo}` so clients can poll current payment status by trusted principal scope. |
| Payment State | Extended payment status with `failed`; success callbacks settle payment/order/inventory, while failure callbacks do not release inventory directly. |
| Order Timeout | Added internal timeout cancellation through `POST /internal/orders/timeout-cancellations`, scanning expired `created` orders and releasing inventory before cancelling. |
| Database | Added order timeout lookup migration `V3__add_order_timeout_lookup_index.sql` with `idx_oms_order_shop_status_created(shop_id, status, created_at)`. |
| Compensation Matrix | Covered duplicate success callback, duplicate timeout cancellation, cancel-before-callback, callback-before-cancel, and failure callback cases. |
| Spec Sync | Updated backend payment, order, inventory, and database specs with concrete APIs, fields, indexes, validation matrix, and required tests. |

**Verification**:
- Human tested targeted order/payment Maven suite successfully after implementation.
- Agent also verified affected modules with `mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am test`.
- Commit recorded: `d83ff6c feat(payment): implement callback timeout compensation mvp`.

**Next Notes**:
- The payment/order/inventory consistency loop now handles the major async and timeout disorder cases without introducing MQ yet.
- Future work can either deepen compensation infrastructure with scheduled jobs/MQ, or move upward into user-visible order/payment lifecycle behavior.


### Git Commits

| Hash | Message |
|------|---------|
| `d83ff6c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 15: Compensation Scheduler / Reconcile MVP

**Date**: 2026-05-03
**Task**: Compensation Scheduler / Reconcile MVP
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Order Service | Added scheduled timeout compensation with config-driven enable switch, batch parameters, batch stats logging, and per-record failure isolation while reusing the existing timeout cancellation path. |
| Payment Service | Added scheduled reconcile job for stale `created` payment rows, repository batch lookup by `shopId/status/created_at`, terminal invalid-order demotion to `failed`, and batch stats logging. |
| Database & Spec | Added payment reconcile lookup index migration and updated backend executable specs for order timeout, payment reconcile, inventory ownership, and database validation. |
| Testing | Added scheduler/reconcile unit and migration contract coverage, then human-tested and committed the feature plus follow-up log output cleanup. |

**Verification**:
- Human completed local testing and committed `fe389f9 feat(compensation): add scheduler reconcile mvp`.
- Human also committed `c3ee16a fix:????????` to reduce noisy expected-failure stack traces in test output.
- Working tree was clean at record time.


### Git Commits

| Hash | Message |
|------|---------|
| `fe389f9` | (see git log) |
| `c3ee16a` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 16: Compensation Observability / Config Hardening

**Date**: 2026-05-03
**Task**: Compensation Observability / Config Hardening
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Deploy Config | Added scheduler env examples for order timeout compensation and payment reconcile jobs in `deploy/.env.example`. |
| Order Observability | Added Micrometer batch run/item counters plus consistent batch logs for `OrderTimeoutCompensationScheduler`. |
| Payment Observability | Added Micrometer batch run/item counters plus consistent batch logs for `PaymentReconcileScheduler`. |
| Logging Hardening | Standardized failure logs to include `errorType`, `errorCode`, and sanitized `message` instead of noisy stack traces for expected test failures. |
| Spec Sync | Updated backend observability, order-create, and payment-pay specs with env keys, metrics names, log fields, and alert-threshold guidance. |
| Verification | Human ran targeted order/payment compensation and controller/migration test suites successfully after commit. |


### Git Commits

| Hash | Message |
|------|---------|
| `b935f3f` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 17: Compensation Ops Surface / Manual Replay

**Date**: 2026-05-03
**Task**: Compensation Ops Surface / Manual Replay
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Payment Ops | Added internal compensation query API for `created` and `failed` payments plus single-payment manual reconcile replay. |
| Order Ops | Added internal compensation query API for timeout-eligible `created` and `cancelled` orders plus single-order manual timeout replay. |
| Persistence | Added V4 migrations and latest-compensation metadata columns on `pay_payment_order` and `oms_order` for result, reason, traceId, trigger, and timestamp. |
| Observability | Reused the same compensation metrics family for manual runs and scheduler runs, and added structured audit logs for manual/scheduler compensation outcomes. |
| Spec & Tests | Updated backend database/order/payment specs and added controller, service, and migration contract coverage for the ops surface. |

**Verification**:
- Human completed manual testing and committed `badf0d4 feat(compensation): add ops surface and manual replay`.
- Task `05-03-compensation-ops-surface-manual-replay` was archived after record-session.


### Git Commits

| Hash | Message |
|------|---------|
| `badf0d4` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 18: Compensation task closure and CI fix

**Date**: 2026-05-03
**Task**: Compensation task closure and CI fix
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Description |
| --- | --- |
| Compensation Ops | ????/??????????? replay ???Grafana/Prometheus ?????????? |
| CI Fix | ?? `OrderTimeoutCancelServiceTest` ????? `put(...)` ????????? `lastCompensationOperator` ???????? |
| Verification | ???? Maven ?????GitHub Actions CI ???? |

**Outcome**
- ?? `05-03-compensation-history-dashboard-bulk-replay` ???
- ?????????????????????


### Git Commits

| Hash | Message |
|------|---------|
| `18a67d2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 19: Compensation Attempt History Query Surface

**Date**: 2026-05-03
**Task**: Compensation Attempt History Query Surface
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Order Query Surface | Reworked `/internal/orders/compensation-records/query` into a history-backed aggregate query with filters for `shopId`, `orderId`, `trigger`, `result`, `operator`, `traceId`, time range, and paged drill-down attempt detail. |
| Payment Query Surface | Reworked `/internal/payments/compensation-records/query` into a history-backed aggregate query with filters for `shopId`, `orderId`, `paymentNo`, `trigger`, `result`, `operator`, `traceId`, time range, and paged drill-down attempt detail. |
| Repository & DTOs | Added attempt-history query models, JDBC summary/detail queries, aggregate DTOs, and response shapes that combine latest business-row snapshot with ordered attempt records. |
| Tests | Added order/payment compensation ops service tests, controller tests, and JSON contract tests; kept migration contract coverage in the targeted test suite. |
| Spec Sync | Updated backend database, order-create, and payment-pay specs with the new history-query contract, pagination behavior, and drill-down response pattern. |

**Verification**:
- Human committed `aa9f853 feat(compensation): add attempt history query surface`.
- AI ran targeted Maven tests and compile successfully with workspace-local Maven repo.


### Git Commits

| Hash | Message |
|------|---------|
| `aa9f853` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 20: Compensation Dashboard Query Wiring

**Date**: 2026-05-03
**Task**: Compensation Dashboard Query Wiring
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Frontend Scaffold | Added a minimal Vue 3 + TypeScript + Vite frontend scaffold in `frontend/` so compensation ops UI can run inside this repository instead of staying at placeholder level. |
| Compensation Dashboard | Wired a real order/payment compensation dashboard with shared gateway HTTP client, API DTOs, view switching, filters, pagination, status cards, and attempt drill-down. |
| Query Contracts | Mapped frontend request/response types directly to the history-backed order/payment compensation query contracts, including latest metadata and nested attempt detail. |
| Quality Fixes | Repaired frontend check failures by narrowing `tsconfig` scope and importing Vitest APIs explicitly in the unit test file. |
| Verification | Human reran `npm install`, `npm run lint`, `npm run typecheck`, `npm run test`, and `npm run build`; all passed locally after the fixes. |

**Verification**:
- Human committed `eba9baa feat(compensation): add dashboard query wiring`.
- Human committed `d9b3cda fix(frontend): repair dashboard frontend checks`.
- Human confirmed local frontend lint, typecheck, test, and production build all passed on May 3, 2026.


### Git Commits

| Hash | Message |
|------|---------|
| `eba9baa` | (see git log) |
| `d9b3cda` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 21: Compensation Dashboard Replay Wiring and Gateway Auth Hardening

**Date**: 2026-05-05
**Task**: Compensation Dashboard Replay Wiring and Gateway Auth Hardening
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Frontend Replay Wiring | Wired manual replay and bounded bulk replay into the existing compensation dashboard, including operator input, dry-run, per-action feedback, duplicate-submit guards, URL/localStorage state restore, export, and traceId copy. |
| Gateway Routing & CORS | Exposed `/api/internal/orders/**` and `/api/internal/payments/**` through gateway, added load-balancer support, and allowed browser CORS preflight requests to pass without JWT rejection. |
| Auth & RBAC Closure | Moved internal compensation ops onto trusted `SanguiPrincipal` flow and enforced `ADMIN` role plus `shopId` match in order/payment compensation services. |
| Tests & Spec Sync | Added frontend model tests, updated gateway and compensation controller/service tests, and synced backend auth/gateway specs for internal compensation ops behavior. |

**Verification**:
- Human committed `cec6495 feat(compensation): wire dashboard replay and tighten gateway auth`.
- Human ran `npm run lint`, `npm run typecheck`, `npm run build`, and `npm run test` successfully in `frontend/` on May 5, 2026.
- Human ran targeted Maven tests for order/payment compensation ops and `GatewayJwtAuthenticationFilterTest` successfully from the repository root on May 5, 2026.


### Git Commits

| Hash | Message |
|------|---------|
| `cec6495` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 22: Compensation Ops Dashboard Auth Session Closure

**Date**: 2026-05-05
**Task**: Compensation Ops Dashboard Auth Session Closure
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Ops Auth Contract | Added dedicated ops login and session refresh endpoints in user-service so compensation dashboard access no longer depends on manual browser token injection. |
| Admin Identity Model | Introduced config-driven ops admin identity mapping via `sangui.security.ops.admins[]`, keeping internal compensation ops on the existing `ADMIN` role baseline. |
| Frontend Session UX | Added auth shell, login page, forbidden page, persisted session restore, proactive refresh, sign-out, and unified 401/403 behavior for the dashboard. |
| Gateway/Auth Alignment | Treated `/api/users/ops/login` as a public auth path at gateway level while keeping JWT protection for refresh and existing internal compensation APIs. |
| Verification | Human verified frontend `lint`, `typecheck`, `build`, and Vitest all passed; AI verified targeted user-service and gateway Maven tests with workspace-local Maven repo. |

**Verification**:
- Human committed `a84fafa feat(compensation): close ops dashboard auth session loop`.
- Human ran `npm run test`, `npm run lint`, `npm run typecheck`, `npm run build`, and `npm run test` successfully in `frontend/` on May 5, 2026.
- Human ran targeted Maven tests for user-service ops auth and gateway JWT filter successfully from repository root on May 5, 2026.
- Manual browser acceptance checklist was added at `frontend/ops-auth-manual-checklist.md`.


### Git Commits

| Hash | Message |
|------|---------|
| `a84fafa` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 23: Compensation Ops Admin Permission Model And Environment Rollout

**Date**: 2026-05-05
**Task**: Compensation Ops Admin Permission Model And Environment Rollout
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Ops Permission Model | Replaced broad compensation ops `ADMIN` issuance with dedicated `OPS_COMPENSATION_ADMIN` permission claims so ops dashboard sessions are least-privilege. |
| User-Service Access Config | Introduced `sangui.security.ops.bindings[]` as the target environment contract, while keeping legacy `sangui.security.ops.admins[]` as a rollback-compatible fallback. |
| Downstream Authorization | Updated order-service and payment-service compensation ops paths to require the dedicated ops permission plus trusted `shopId` match instead of direct `ADMIN` role checks. |
| Rollout Documentation | Added executable rollout guidance for Nacos YAML, environment variables, verification, and rollback in `docs/compensation-ops-admin-rollout.md`, and synced auth/security specs and local sample config. |
| Verification | Human ran targeted Maven tests and compile successfully after the implementation, then committed `c375654 feat(compensation): formalize ops permission rollout`. |

**Verification**:
- Human ran `mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services/sangui-user-service,services/sangui-order-service,services/sangui-payment-service -am "-Dtest=OpsAuthControllerTest,OpsAuthServiceTest,OrderCompensationOpsServiceTest,PaymentCompensationOpsServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` successfully on May 5, 2026.
- Human ran `mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services/sangui-user-service,services/sangui-order-service,services/sangui-payment-service -am -DskipTests compile` successfully on May 5, 2026.


### Git Commits

| Hash | Message |
|------|---------|
| `c375654` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 24: Compensation Ops Audit Logging And Operation Traceability

**Date**: 2026-05-05
**Task**: Compensation Ops Audit Logging And Operation Traceability
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Unified Ops Audit | Added a shared `OpsAuditLogger` in common-web so ops auth and compensation actions now emit one consistent `Ops audit event.` shape with trace, identity, permission, target, outcome, and sanitized error context. |
| Auth Audit Coverage | Wired `POST /api/users/ops/login` and `POST /api/users/ops/session/refresh` to log success, failure, and denial outcomes without exposing secrets or raw JWT content. |
| Compensation Action Audit | Wired order manual/bulk timeout replay and payment manual/bulk reconcile endpoints to emit operator-scoped audit events for both success and failure paths, including `dryRun` and target scope. |
| Forbidden Audit Boundary | Extended the global API exception handler to emit ops-specific `403` audit events for protected auth and compensation surfaces while avoiding duplicate logging when a controller already recorded the event. |
| Spec And Verification | Updated backend logging/security specs with the executable ops audit contract and added controller tests asserting audit log emission for auth, success, bulk dry-run, and forbidden cases. |

**Verification**:
- Human confirmed the three Maven verification commands completed without errors on May 5, 2026.
- Human completed manual testing and committed `30cde02 feat(compensation): unify ops audit events`.


### Git Commits

| Hash | Message |
|------|---------|
| `30cde02` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 25: Compensation Ops Audit Search Planning

**Date**: 2026-05-05
**Task**: Compensation Ops Audit Search Planning
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Task Setup | Created Trellis task `05-05-compensation-ops-audit-search-export` for the compensation ops audit search/export panel. |
| PRD | Defined fullstack scope, requirements, acceptance criteria, risks, and implementation plan for audit field contracts, Kibana/Loki templates, dashboard audit search, and replay/reconcile audit trace jumps. |
| Context | Added backend logging/security/contracts/observability/order/payment specs and frontend API/type/component/quality specs into task implement/check context files. |
| Verification Reported | Human ran frontend Vitest/typecheck/build and targeted Maven audit-controller tests successfully on May 5, 2026. |
| Important Note | Commit `2ff97d2` contains Trellis task/context/PRD files only; no frontend/docs/backend business implementation files are present yet, so the implementation task remains active and should not be archived. |

**Next Step**:
- Continue the active task by implementing the PRD: repo-backed audit field/query-template documentation, dashboard audit search panel, and replay/reconcile "View audit trail" state flow.


### Git Commits

| Hash | Message |
|------|---------|
| `2ff97d2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 26: Compensation Ops Audit Search Workflow

**Date**: 2026-05-05
**Task**: Compensation Ops Audit Search Workflow
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Audit Search Contract | Added `docs/compensation-ops-audit-search.md` with canonical `Ops audit event.` fields, supported action values, Kibana KQL/Lucene templates, Loki LogQL templates, common searches, and Good/Base/Bad cases. |
| Backend Spec Sync | Updated `.trellis/spec/backend/logging-guidelines.md` so audit-search fields, query templates, and dashboard trace-jump behavior are part of the executable logging contract. |
| Dashboard Audit Entry | Added an ops dashboard audit search panel with filters for `shopId`, `traceId`, `operator`, `action`, and `outcome`, generating copyable Kibana KQL, Kibana Lucene, and Loki LogQL queries. |
| Replay Traceability | Wired manual/bulk replay and payment reconcile feedback to a `View audit trail` flow that fills audit filters from response/error `traceId`, replay operator, mapped audit action, and outcome without rerunning compensation operations. |
| Frontend State/Test Coverage | Extended dashboard model state persistence, URL params, audit query generation, replay-action mapping, and model tests. |

**Verification**:
- AI verified `cmd /c npm run typecheck` and `cmd /c npm run build` passed in `frontend/`.
- AI could not run Vitest in sandbox because Vite/esbuild spawn failed with `EPERM`; elevation request was blocked by unavailable approval review.
- Human verified `cd frontend; cmd /c npm run typecheck; cmd /c npm run build` passed on May 5, 2026.
- No Java source changed; backend change was spec/docs only, so no Maven test was required for this implementation pass.

**Status**:
- Task `05-05-compensation-ops-audit-search-export` archived after commit `b279d63 feat(compensation): add ops audit search workflow`.


### Git Commits

| Hash | Message |
|------|---------|
| `b279d63` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 27: Compensation Ops Audit Observability Links

**Date**: 2026-05-05
**Task**: Compensation Ops Audit Observability Links
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Observability Link Closure | Added configured `Open in Kibana` and `Open in Loki` actions to the compensation ops audit panel while keeping `Copy query` as the always-available fallback. |
| Frontend Env Contract | Introduced typed `VITE_KIBANA_DISCOVER_URL` and `VITE_LOKI_EXPLORE_URL` handling, validating absolute `http`/`https` URLs and rejecting missing, invalid, or credential-bearing URLs. |
| Query URL Generation | Reused generated Kibana KQL, Kibana Lucene, and Loki LogQL templates as the canonical source, then encoded them into Kibana Discover `_a` state or Grafana/Loki Explore `left` state. |
| UI Behavior | Disabled open buttons when the matching platform URL is unavailable and kept copy feedback unchanged for operator workflows. |
| Docs And Spec Sync | Updated the audit search runbook, frontend README, frontend API env contract, and backend logging guidelines with executable observability-link behavior. |
| Verification | Human verified frontend lint, typecheck, production build, and Vitest all passed after commit `44acbf6`. |

**Verification**:
- Human ran `cmd /c npm run lint` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run typecheck` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run build` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run test` successfully in `frontend/` on May 5, 2026: 3 test files passed, 13 tests passed.

**Status**:
- Task `05-05-compensation-ops-audit-observability-links` archived after commit `44acbf6 feat(compensation): add ops audit observability links`.


### Git Commits

| Hash | Message |
|------|---------|
| `44acbf6` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 28: Compensation Ops Audit UI Component Tests

**Date**: 2026-05-05
**Task**: Compensation Ops Audit UI Component Tests
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
| --- | --- |
| Task Scope | Completed frontend quality enhancement for compensation ops audit observability actions without changing API contracts or audit query generation behavior. |
| Component Coverage | Added component-level coverage for audit query open buttons, including disabled fallback when links are unavailable, enabled state when links exist, copied-state rendering, and emitted `AuditQueryKind` values for Kibana KQL, Kibana Lucene, and Loki LogQL. |
| Window Open Coverage | Added `useCompensationDashboard` audit observability tests for real `openAuditQuery` behavior: no window open when links are unavailable, and configured Kibana/Loki links open with `_blank` and `noopener,noreferrer`. |
| Testability Fix | Introduced optional `auditObservabilityConfig` injection for `useCompensationDashboard` so tests can exercise link behavior without mutating production env access; production default still reads `import.meta.env`. |
| SFC Test Fix | Replaced the full-page SFC custom-renderer test, which failed in Vitest node mode due SFC SSR context injection, with a small `AuditQueryTemplateCard.ts` Vue component and focused component test. |
| Task Tracking | Created and archived Trellis task `05-05-compensation-ops-audit-ui-component-tests`. |

**Verification**:
- Human ran `cmd /c npm run lint` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run typecheck` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run build` successfully in `frontend/` on May 5, 2026.
- Human ran `cmd /c npm run test` successfully in `frontend/` on May 5, 2026: 5 test files passed, 18 tests passed.

**Commits**:
- `3a3d6f3`: added ops audit observability component and hook tests.
- `9710bba`: fixed the audit component test by replacing the SFC custom-renderer path with a focused TS Vue component.

**Status**:
- Task `05-05-compensation-ops-audit-ui-component-tests` archived after commit `9710bba`.


### Git Commits

| Hash | Message |
|------|---------|
| `3a3d6f3` | (see git log) |
| `9710bba` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 29: Compensation Ops Audit Manual Acceptance Checklist

**Date**: 2026-05-05
**Task**: Compensation Ops Audit Manual Acceptance Checklist
**Branch**: `main`

### Summary

Added the real-environment operator checklist for compensation ops audit observability, covering Kibana/Loki env configuration, button states, View audit trail success and failure filters, Kibana/Loki query verification, denied audit checks, and copy-only fallback. Human verified frontend lint, typecheck, build, and Vitest: 5 files passed, 18 tests passed.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `a013f3a` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 30: Compensation Ops Bulk Failed Audit Assertions

**Date**: 2026-05-06
**Task**: Compensation Ops Bulk Failed Audit Assertions
**Branch**: `main`

### Summary

Added backend controller tests for order bulk timeout replay and payment bulk reconcile failed paths, asserting unified Ops audit event fields including outcome=failed, targetCount, dryRun, errorCode, path, and method. Human verified the targeted Maven reactor command passed.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e9d3981` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 31: Compensation Ops Audit Backend Test Runbook

**Date**: 2026-05-06
**Task**: Compensation Ops Audit Backend Test Runbook
**Branch**: `main`

### Summary

固化 compensation ops audit 后端 Maven reactor 测试命令：在 docs/compensation-ops-audit-search.md 增加 Backend Regression Test Runbook，在 backend quality spec 增加 Targeted Maven Reactor Tests，说明 -pl、-am、failIfNoSpecifiedTests 的使用边界和 Good/Base/Bad 案例；人工已用文档命令验证 order/payment controller audit tests 通过并输出预期 Ops audit event 场景。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `7f75f10` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
