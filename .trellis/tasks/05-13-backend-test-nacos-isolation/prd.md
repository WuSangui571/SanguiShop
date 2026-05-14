# 收敛后端测试中的 Nacos 连接噪音 / 测试环境隔离

## Goal

Make backend unit and smoke tests quiet and hermetic by default: `mvn test` must not require or attempt live Nacos, Redis, MQ, or MySQL connections. Real external dependencies are allowed only through an explicit integration-test profile or documented manual runtime path.

This task is driven by the previous full Maven test result: tests passed, but output repeatedly included Nacos `localhost:9848 connection refused` noise. The fix should improve smoke signal quality so future failures distinguish "environment is missing" from "tests should have been isolated."

## Task Classification

Complex Task.

Reasons:
- It spans multiple service modules and shared test conventions.
- It changes backend test environment contracts and DevOps/spec documentation.
- It may require Maven/profile or Spring test configuration decisions.
- It must preserve local runtime Nacos behavior for manual development while isolating default tests.

## Scope

In scope:
- Backend test configuration for Maven Surefire/default `test`.
- Spring Boot smoke/context tests for gateway and service modules.
- Test-only disabling of Spring Cloud Alibaba Nacos config/discovery and Sentinel.
- Test-only isolation from live MySQL/Flyway, Redis, RocketMQ, and other live infrastructure where a Spring context would otherwise connect.
- Smoke/local verification command behavior and documentation where needed.
- Spec sync in `.trellis/spec/backend/quality-guidelines.md` and/or `.trellis/spec/backend/observability-devops.md`.

Out of scope:
- Business feature changes.
- API, DTO, database schema, Redis key, MQ topic, or permission changes.
- Removing Nacos/Redis/MQ/MySQL from normal local development runtime.
- Docker Compose changes unless a verification contract needs documentation only.
- Frontend changes.

## Test Environment Contract

Default backend tests:
- Command: `.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" test`
- Must pass without live Nacos, Redis, MQ, or MySQL.
- Must not emit Nacos connection refused noise for `localhost:9848`.
- Must not require Docker services to be running.
- Must keep the project Maven Wrapper as the accepted entrypoint.

Integration tests:
- Real external dependencies may be used only behind an explicit integration profile or manual command.
- If implemented in this task, the profile name should be stable and documented, for example `integration` or `it`.
- Default Surefire execution must not activate that profile.

Spring test properties:
- Test scope should disable or neutralize:
  - `spring.config.import` Nacos import when necessary.
  - `spring.cloud.nacos.config.enabled=false`
  - `spring.cloud.nacos.discovery.enabled=false`
  - `spring.cloud.sentinel.enabled=false`
  - JDBC/Flyway autoconfiguration for context smoke tests that are not persistence integration tests.
- Prefer a reusable test pattern or shared test profile when it reduces duplication without adding hidden runtime behavior.

## API / Command / Payload Fields

No HTTP API or business payload is introduced or changed.

Commands and profiles are the contract:

| Command / Profile | Required Behavior |
| --- | --- |
| `.\mvnw.cmd -q "-Dmaven.repo.local=<repo>\.m2\repository" test` | Default isolated unit/smoke test run; no live Nacos/Redis/MQ/MySQL connection attempts. |
| `.\scripts\smoke-local.ps1 -BackendMode test -SkipDocker -SkipFrontend` | Runs backend tests through smoke entrypoint and must produce clean backend test signal without Nacos connection refused noise. |
| Optional explicit integration profile, for example `-Pintegration` | Only path allowed to connect to live Nacos/Redis/MQ/MySQL from tests; must be documented if added. |

No request/response payload fields apply.

## Validation / Error Matrix

| Signal | Expected Classification | Required Response |
| --- | --- | --- |
| Default `mvn test` emits `localhost:9848`, `9848 connection refused`, or Nacos client reconnect noise | Blocking for this task | Identify the test/module that starts Nacos client and isolate it in test scope. |
| Default `mvn test` fails because Nacos/Redis/MQ/MySQL/Docker is unavailable | Blocking for this task | Move that behavior behind mocks, disabled auto-config, contract tests, or explicit integration profile. |
| A smoke test loads Spring context and uses real DB/Flyway connection | Blocking unless it is an explicit integration test | Disable JDBC/Flyway in that smoke test or move it to integration profile. |
| Persistence migration contract test reads SQL resources only and does not connect to MySQL | Accepted | Keep as default unit test. |
| Explicit integration profile connects to live dependencies | Accepted only when documented | Verify it is opt-in and not part of default Surefire run. |
| Smoke script hides a backend test failure | Blocking | Script must fail on selected backend command failures. |

## Good / Base / Bad Cases

Good:
- Full default Maven test passes without Docker or live Nacos and output contains no `localhost:9848 connection refused`.
- Gateway, user, product, order, payment, logistics, and other active service smoke tests load Spring context with Nacos/Sentinel disabled.
- SQL migration contract tests continue to validate migration resources without live MySQL.
- Spec documents state that default unit/smoke tests must not depend on live external infrastructure.

Base:
- Some skeletal services without meaningful beans receive minimal application smoke tests only if they can load without external dependencies.
- A common test profile or annotation is used only if it stays transparent and does not mask business test failures.
- Full local runtime validation still uses Docker Compose and service startup outside Maven unit tests.

Bad:
- Default `mvn test` requires Nacos, Redis, RocketMQ, MySQL, or Docker.
- Test isolation is achieved by deleting production Nacos config from `application.yml`.
- Smoke tests connect to `localhost` services and accept connection refused logs as harmless.
- A catch-all skip disables meaningful context tests or hides Surefire failures.

## Acceptance Criteria

- [ ] Default Maven test run passes without live external dependencies.
- [ ] Default Maven test output no longer contains Nacos `localhost:9848 connection refused` noise.
- [ ] Gateway/user/logistics service smoke coverage either exists or is documented with equivalent context tests that do not start Nacos clients.
- [ ] All service smoke tests that load Spring context disable Nacos config/discovery and Sentinel, and isolate JDBC/Flyway unless the test is explicitly persistence-focused.
- [ ] If an integration-test profile is introduced or standardized, it is opt-in and documented.
- [ ] `.trellis/spec/backend/quality-guidelines.md` and/or `.trellis/spec/backend/observability-devops.md` records the executable rule: default backend unit/smoke tests must not depend on live Nacos/Redis/MQ/MySQL.
- [ ] Smoke-local backend test mode remains compatible with the isolated Maven test contract.

## Relevant Specs

- `.trellis/spec/backend/quality-guidelines.md`: definition of done, test strategy, existing bad case forbidding service smoke tests from depending on live Nacos/Redis/MQ/MySQL.
- `.trellis/spec/backend/observability-devops.md`: Maven Wrapper contract, local smoke validation command, local dependency runtime boundaries.
- `.trellis/spec/backend/directory-structure.md`: service module boundaries and location for config/tests.
- `.trellis/spec/backend/messaging-cache-guidelines.md`: Redis/MQ external dependency expectations and tests-required boundary.
- `.trellis/spec/backend/database-guidelines.md`: Flyway and database contract tests should validate resources without live MySQL unless explicitly manual/integration.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: configuration/secret/external dependency boundary thinking.
- `.trellis/spec/guides/architecture-review-checklist.md`: review order for deployment, observability, security, and tests.

## Code Patterns Found

- Existing service smoke test pattern:
  - `services/sangui-gateway/src/test/java/com/sangui/shop/gateway/SanguiGatewayApplicationSmokeTest.java`
  - `services/sangui-user-service/src/test/java/com/sangui/shop/user/SanguiUserApplicationSmokeTest.java`
  - `services/sangui-product-service/src/test/java/com/sangui/shop/product/SanguiProductApplicationSmokeTest.java`
  - `services/sangui-order-service/src/test/java/com/sangui/shop/order/SanguiOrderApplicationSmokeTest.java`
  - `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/SanguiPaymentApplicationSmokeTest.java`
- Existing smoke tests disable:
  - `spring.cloud.nacos.discovery.enabled=false`
  - `spring.cloud.nacos.config.enabled=false`
  - `spring.cloud.sentinel.enabled=false`
  - JDBC/Flyway autoconfiguration where the service has DB dependencies.
- Existing WebMvc test pattern neutralizes Nacos import:
  - `services/sangui-seckill-service/src/test/java/com/sangui/shop/seckill/api/AdminSeckillActivityControllerTest.java` uses `spring.config.import=` plus Nacos disabled properties.
- Existing local smoke script:
  - `scripts/smoke-local.ps1` runs Maven wrapper with repo-local Maven repository and supports `-BackendMode test`.

## Files Likely To Modify

Expected implementation files:
- `services/*/src/test/java/**/Sangui*ApplicationSmokeTest.java`: add missing smoke tests or align properties.
- Possibly `services/*/src/test/resources/application-test.yml` or another test resource file if a shared test profile is chosen.
- Possibly root/service `pom.xml` files if an explicit integration profile is introduced.
- `scripts/smoke-local.ps1` only if smoke output assertion or command contract needs a script-level change.
- `.trellis/spec/backend/quality-guidelines.md`: record default test isolation rule and Good/Base/Bad cases.
- `.trellis/spec/backend/observability-devops.md`: record smoke/backend test command contract if command/profile behavior changes.

Current modules observed with Nacos runtime config in `application.yml`:
- `services/sangui-gateway`
- `services/sangui-user-service`
- `services/sangui-product-service`
- `services/sangui-seckill-service`
- `services/sangui-order-service`
- `services/sangui-payment-service`
- `services/sangui-logistics-service`
- `services/sangui-review-service`
- `services/sangui-marketing-service`
- `services/sangui-search-rec-service`
- `services/sangui-ai-service`

Observed current smoke coverage:
- Present: gateway, user, product, order, payment.
- Missing application smoke tests: seckill, logistics, review, marketing, search-rec, ai.

## Risk / Boundary Notes

- Do not remove production/local-dev `spring.config.import: optional:nacos:...` from main service `application.yml` merely to quiet tests; default runtime still expects Nacos support.
- Avoid hiding failures through broad test skips. Context smoke tests should still assert application startup class and context creation.
- Do not introduce live Testcontainers or Docker requirements for default tests.
- If using a shared base annotation or profile, ensure it is easy for future tests to discover and does not make integration tests impossible.
- Services with schedulers or RestClient adapters may require explicit mocks or disabled scheduling only in smoke tests.
- SQL migration contract tests should remain resource/string-based unless explicitly moved to integration testing.

## Required Tests and Assertion Points

Primary verification:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" test
```

Required assertions:
- Exit code is zero.
- Output contains no `localhost:9848`.
- Output contains no `9848 connection refused`.
- Output contains no Nacos reconnect/error noise indicating attempted live Nacos access.

Smoke entry verification:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-local.ps1 -BackendMode test -SkipDocker -SkipFrontend
```

Required assertions:
- Exit code is zero.
- Backend test step uses `.\mvnw.cmd` and repo-local Maven repository.
- Output contains no Nacos connection refused noise.

Focused smoke tests, if useful during implementation:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-gateway,services/sangui-user-service,services/sangui-logistics-service" -am "-Dtest=*ApplicationSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Required assertions:
- Gateway, user, logistics smoke tests run when present.
- No live Nacos connection is attempted.
- No live MySQL/Flyway connection is attempted by smoke tests.

## Implementation Handoff Notes

Recommended approach for DeepSeek:
- First reproduce and identify the module that emits `localhost:9848` by running default tests or a narrowed reactor.
- Prefer a reusable test property/profile only if it makes all service smoke tests consistent.
- Add missing application smoke tests for services with startup classes where they are cheap and isolated.
- Keep persistence tests as contract/resource tests unless an explicit integration profile is intentionally added.
- Update specs after code changes, then rerun full verification.

