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
