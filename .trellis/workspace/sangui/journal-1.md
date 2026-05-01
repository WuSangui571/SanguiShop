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
