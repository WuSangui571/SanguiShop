# Backend Quality Guidelines

## Definition of Done

- [ ] 代码符合对应 spec，特别是契约、幂等、错误码、日志和测试。
- [ ] 新增/修改 API 有 request/response 示例。
- [ ] 新增 DB/MQ/Redis 契约已写入 spec。
- [ ] `./mvnw -q test` / `.\mvnw.cmd -q test` 通过；必要时补集成测试。
- [ ] 不包含真实 secret、临时 debug 日志、无用 TODO。
- [ ] 关键链路可观测：日志、traceId、metrics。

## Review Habits

Review 时先看契约，再看实现：

1. API/DTO/Event 是否稳定、命名清晰、兼容。
2. 幂等和并发是否正确。
3. 事务边界是否过大或跨服务耦合。
4. 错误处理是否区分业务失败和系统失败。
5. 日志是否足够排障且不泄露敏感信息。
6. 测试是否覆盖 Good/Base/Bad cases。

## Forbidden Patterns

- Controller 直接操作 Mapper、Redis、MQ。
- 跨服务直接查库。
- 金额使用浮点数。
- 写接口无幂等键。
- Feign 写操作 fallback 返回成功。
- MQ consumer 无去重。
- Redis Key 无 TTL 或命名无服务前缀。
- 日志输出 token、密码、支付签名、AI prompt。
- 在代码中硬编码环境地址、密钥、单商家 magic number。

## Test Strategy

| 类型 | 适用场景 | 断言重点 |
| --- | --- | --- |
| Unit Test | domain/application service | 业务分支、状态机、边界值 |
| WebMvc Test | controller | 参数校验、错误码、响应 envelope |
| Repository Test | mapper/repository | SQL、索引约束、分页、逻辑删除 |
| Integration Test | Redis/MQ/MySQL | 序列化、事务、重试、幂等 |
| Contract Test | Feign/API/Event | 字段兼容、必填、版本 |
| Load Test | 秒杀/AI | QPS、P95/P99、资源瓶颈 |

## Targeted Maven Reactor Tests

Targeted backend service tests must use the project Maven Wrapper and must keep module selection explicit. When a `-Dtest` selector is meant for service modules only, do not run it against the full root reactor.

Compensation ops audit changes should use the repo-local script entry first:

```powershell
.\scripts\verify-compensation-ops-audit.ps1
```

The script is a cross-platform `pwsh` entrypoint. It must select `.\mvnw.cmd` when `$IsWindows` is true and `./mvnw` when `$IsWindows` is false.

Script contract:

- Default `-Service all` runs `InternalOrderCompensationControllerTest` and `InternalPaymentCompensationControllerTest`.
- `-Service order` runs only `InternalOrderCompensationControllerTest` with `-pl services/sangui-order-service`.
- `-Service payment` runs only `InternalPaymentCompensationControllerTest` with `-pl services/sangui-payment-service`.
- `-MavenRepoLocal <path>` overrides the local Maven repository path, for example `.\scripts\verify-compensation-ops-audit.ps1 -MavenRepoLocal .\.m2\repository`.
- `-PrintCommandOnly` must print the resolved command, module selector, and test selector, then exit successfully without invoking Maven.
- The script must print the Maven executable, module selector, test selector, expanded command, and expected test class names before Maven starts; reviewers must still confirm Maven executed those classes.
- If local Windows PowerShell policy blocks direct `.ps1` execution, use `powershell -ExecutionPolicy Bypass -File .\scripts\verify-compensation-ops-audit.ps1` for that process only.

Raw Maven fallback command for troubleshooting or non-script environments:

```powershell
.\mvnw.cmd -q -pl services/sangui-order-service,services/sangui-payment-service -am "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Non-Windows fallback:

```bash
./mvnw -q -pl services/sangui-order-service,services/sangui-payment-service -am "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Why this shape is required:

- `-pl` limits the targeted test selector to the owning service modules instead of every module in the root reactor.
- `-am` builds required upstream local SNAPSHOT dependencies from the same checkout.
- `-Dsurefire.failIfNoSpecifiedTests=false` is allowed only with `-am` for upstream dependency modules that do not own the selected service test classes.

Required review checks:

- Confirm the Maven output shows the intended service test classes ran; the no-specified-tests flag must not hide a typo in the target class names.
- Confirm the touched service modules are listed in `-pl`; do not rely on root `-Dtest` discovery for service-specific controller tests.
- If the command is copied into a runbook, include the expected assertion points, not only the raw command.

Manual GitHub workflow triage contract:

- File: `.github/workflows/compensation-ops-audit.yml`.
- Trigger: manual `workflow_dispatch` only; no default `pull_request` gate without an explicit runtime decision.
- Runner: `ubuntu-latest`.
- Java: Temurin 21 via `actions/setup-java@v5`.
- Checkout: `actions/checkout@v6` with workflow `permissions: contents: read`, `persist-credentials: false`, and `submodules: false`.
- Linux wrapper setup: run `chmod +x ./mvnw` before invoking the `pwsh` script.
- Acceptance command: `./scripts/verify-compensation-ops-audit.ps1 -Service <all|order|payment>`.

Failure matrix:

| Signal | Step | Blocking | Owner | Required Response |
| --- | --- | --- | --- | --- |
| `Node.js 20 actions are deprecated` | Checkout or Setup Java annotation | Usually non-blocking warning | GitHub runner/action runtime | Upgrade official actions to Node 24-compatible majors before diagnosing Maven. |
| `/usr/bin/git failed with exit code 128` | Checkout | Blocking | Checkout/ref/token/repo metadata | Read the exact `fatal:` line in the Checkout step before changing scripts. |
| `No url found for submodule path 'Trellis' in .gitmodules` | Checkout | Blocking | Repo metadata | Ensure the tracked gitlink has matching root `.gitmodules` metadata; keep this workflow `submodules: false` unless submodule contents are required. |
| `./mvnw` permission denied | chmod or pwsh script | Blocking | Linux wrapper setup | Keep `chmod +x ./mvnw`; continue to use the project wrapper rather than global `mvn`. |
| Windows path used on Linux, or Linux path used on Windows | pwsh script | Blocking | PowerShell portability | Verify the script prints `./mvnw` on non-Windows and `.\mvnw.cmd` on Windows. |
| Target Surefire class missing from Maven output | Maven test | Blocking for acceptance | Maven selector/test contract | Check actual Surefire output for `InternalOrderCompensationControllerTest` and/or `InternalPaymentCompensationControllerTest`; do not accept the script preamble as proof. |

Manual acceptance evidence must include:

- GitHub run URL, branch, commit, `service` input, and runner.
- Checkout result and any exact `fatal:` line if checkout failed.
- Action versions and whether any Node runtime annotation remains.
- Java version assertion for Temurin 21.
- Maven executable line printed by the script.
- Module selector and test selector printed by the script.
- Actual Surefire class output proving the expected controller test class or classes ran.
- Final conclusion: accepted with no warnings/errors, accepted with non-blocking warning, or rejected with blocking failure category.

Good/Base/Bad cases:

- Good: compensation ops audit changes run `.\scripts\verify-compensation-ops-audit.ps1` and verify both `InternalOrderCompensationControllerTest` and `InternalPaymentCompensationControllerTest` executed.
- Good: a single-service investigation uses `.\scripts\verify-compensation-ops-audit.ps1 -Service order` or `.\scripts\verify-compensation-ops-audit.ps1 -Service payment` and confirms the selected test class ran.
- Good: the manual `.github/workflows/compensation-ops-audit.yml` `workflow_dispatch` workflow uses Node 24-compatible official actions, declares `permissions: contents: read`, sets checkout `persist-credentials: false` and `submodules: false`, runs `./scripts/verify-compensation-ops-audit.ps1 -Service <all|order|payment>` on `ubuntu-latest` with `pwsh`, and the run log confirms the target test class or classes executed.
- Good: `service=all` completes with no warning/error annotations, `Maven executable: ./mvnw`, both service modules in `-pl`, and both target controller test classes in Surefire output.
- Base: the run succeeds with a warning annotation; the reviewer classifies it with the failure matrix and records why it is non-blocking or schedules the action upgrade.
- Base: a direct Maven fallback uses `-pl <service> -am "-Dtest=<OwningServiceTest>" "-Dsurefire.failIfNoSpecifiedTests=false" test` and confirms that test class ran.
- Base: a GitHub checkout failure such as `/usr/bin/git` exit code `128` is diagnosed from the `Checkout` step `fatal:` line before changing Maven selectors or the PowerShell script; `No url found for submodule path '<path>' in .gitmodules` means a tracked gitlink is missing `.gitmodules` metadata.
- Base: full `.\mvnw.cmd -q test` remains valid before release or broad backend changes.
- Bad: a checkout failure leads to edits in the Maven script even though the `pwsh` step never started.
- Bad: a reviewer accepts the run from the script line `Expected Maven output should show` without checking actual Surefire class output.
- Bad: root `.\mvnw.cmd -q "-Dtest=<service-controller-test>" test` can fail in common modules with `No tests matching pattern`.
- Bad: `-pl <service>` without `-am` can fail on a clean checkout because required local SNAPSHOT dependencies are not installed.
- Bad: global `mvn` is not accepted in reproducible project docs or CI instructions.
- Bad: compensation ops audit controller tests are added to the default `pull_request` CI gate before an explicit runtime tradeoff decision.

## Phase 1 Foundation Tests

The minimum scaffold test suite must stay cheap and executable:

| Module | Test | Required Assertions |
| --- | --- | --- |
| `common/sangui-common-core` | `ApiResultJsonTest` | JSON envelope fields are exactly `code`, `message`, `data`, `traceId`, `timestamp`. |
| `common/sangui-common-core` | `CommonErrorCodeTest` | Baseline codes include auth, validation, rate-limit, secret-missing, downstream-timeout, idempotency, and internal errors. |
| `common/sangui-common-redis` | `RedisKeyBuilderTest` | Key shape is `sangui:{env}:{service}:{domain}:{identifier}`. |
| `common/sangui-common-mq` | `EventEnvelopeJsonTest` | MQ envelope fields match the event contract. |
| `services/sangui-gateway` | smoke test | Startup class exists and Spring context loads with external config clients disabled. |
| `services/sangui-user-service` | smoke test | Startup class exists and Spring context loads with external config clients disabled. |

Good/Base/Bad cases:

- Good: `.\scripts\verify.ps1` passes all Maven tests, package, and Compose config validation.
- Base: `.\scripts\verify.ps1 -SkipDocker` is allowed only for local machines without Docker.
- Bad: A new common contract has no serialization or shape test.
- Bad: A service smoke test depends on live Nacos, Redis, MQ, or MySQL.

## Phase 2 Persistence Tests

The persistence baseline starts with user-service migration contract coverage:

| Module | Test | Required Assertions |
| --- | --- | --- |
| `services/sangui-user-service` | `UserMigrationContractTest` | Flyway resource exists at `db/migration/V1__create_user_identity_tables.sql`, creates `ums_user`, includes required platform columns, and declares username/mobile uniqueness plus logical-delete index. |

Good/Base/Bad cases:

- Good: `UserMigrationContractTest` passes without live MySQL.
- Good: A manual run with Docker MySQL starts `sangui-user-service` and lets Flyway create or validate `ums_user`.
- Base: Before repository code exists, schema SQL contract tests are sufficient and repository tests remain documented as required next work.
- Bad: A service smoke test requires live MySQL/Flyway.
- Bad: A migration adds tables or indexes without updating `.trellis/spec/backend/database-guidelines.md`.
