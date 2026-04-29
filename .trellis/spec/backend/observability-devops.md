# Observability & DevOps Guidelines

## Scope

适用于 Docker Compose、Kubernetes、CI/CD、Prometheus/Grafana、ELK/EFK、Zipkin/Jaeger、备份、配置和发布。

## Local Development

- 本地开发优先使用 `docker-compose.yml` 启动 MySQL、Redis、Nacos、MQ、Gateway、核心服务。
- 每个服务必须能通过环境变量覆盖数据库、Redis、Nacos、MQ、JWT、AI 配置。
- `.env.example` 只包含变量名和示例占位值，不包含真实 secret。

### Phase 1 Local Env Contract

本地依赖配置入口：

- `deploy/.env.example`：环境变量示例，只能使用占位值。
- `deploy/docker-compose.yml`：MySQL、Redis、Nacos、RocketMQ 本地依赖。
- `services/*/src/main/resources/application.yml`：服务配置模板，必须优先读取环境变量。

第一阶段必须保留并使用以下环境变量名：

| Area | Environment Variables |
| --- | --- |
| 基础 | `SANGUI_ENV`, `SANGUI_DEFAULT_SHOP_ID` |
| MySQL | `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| Nacos | `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_GROUP` |
| RocketMQ | `ROCKETMQ_NAME_SERVER` |
| Gateway | `SANGUI_GATEWAY_PORT`, `SANGUI_CORS_ALLOWED_ORIGINS`, `SANGUI_GATEWAY_RATE_LIMIT_ENABLED` |
| Service Ports | `SANGUI_USER_PORT`, `SANGUI_PRODUCT_PORT`, `SANGUI_SECKILL_PORT`, `SANGUI_ORDER_PORT`, `SANGUI_PAYMENT_PORT`, `SANGUI_LOGISTICS_PORT`, `SANGUI_REVIEW_PORT`, `SANGUI_MARKETING_PORT`, `SANGUI_SEARCH_REC_PORT`, `SANGUI_AI_PORT` |
| Secret References | `SANGUI_JWT_PUBLIC_KEY_LOCATION`, `SANGUI_PAYMENT_CALLBACK_SECRET_REF`, `SANGUI_AI_MODEL_API_KEY_REF` |

Good:

```yaml
password: ${MYSQL_PASSWORD:}
api-key-ref: ${SANGUI_AI_MODEL_API_KEY_REF:}
```

Bad:

```yaml
password: my-real-password
api-key: sk-real-token
```

Required checks:

```bash
./mvnw -q -DskipTests compile
./mvnw -q test
```

### Maven Wrapper Contract

Root build commands must use the project Maven Wrapper instead of requiring a globally installed Maven:

```bash
./mvnw -q test
.\mvnw.cmd -q test
```

Wrapper files live at:

- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

The wrapper pins `mavenVersion=3.9.9` and `distributionUrl` to the Apache Maven 3.9.9 binary distribution. Keep checksum validation enabled when changing the Maven distribution.

### Phase 1 Verify Contract

Executable local command:

```powershell
.\scripts\verify.ps1
```

The script must run, in order:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=<repo>\.m2\repository" test
.\mvnw.cmd -q "-Dmaven.repo.local=<repo>\.m2\repository" -DskipTests package
docker compose -f deploy/docker-compose.yml config
```

When `MAVEN_USER_HOME` is not already set, `scripts/verify.ps1` must set it to `<repo>\.m2` before invoking Maven.

CI entrypoint:

- File: `.github/workflows/ci.yml`
- Java: Temurin 21
- Maven commands: `./mvnw -q test`, then `./mvnw -q -DskipTests package`
- Docker Compose command: `docker compose -f deploy/docker-compose.yml config`

Validation matrix:

| Case | Command | Expected Result |
| --- | --- | --- |
| Good | `.\scripts\verify.ps1` | Tests pass, package succeeds, Compose config renders. |
| Base | `.\scripts\verify.ps1 -SkipDocker` | Tests and package pass when Docker is unavailable locally. |
| Bad | `mvn test` | Not accepted for CI/docs because it bypasses the project wrapper. |
| Bad | Real secret in `application.yml` | Reject; use empty placeholders or `*_REF` variables. |

Required assertion points:

- Common tests assert `ApiResult` JSON fields: `code`, `message`, `data`, `traceId`, `timestamp`.
- Redis tests assert key shape `sangui:{env}:{service}:{domain}:{identifier}`.
- MQ tests assert event envelope fields: `eventId`, `eventType`, `version`, `occurredAt`, `shopId`, `traceId`, `payload`.
- Smoke tests cover `sangui-gateway` and at least one business service with Nacos/Sentinel disabled for test scope.
- Maven Enforcer requires Java 21 and Maven 3.9.9 or newer.

如果 Windows PowerShell 执行 npm `.ps1` shim 被策略拦截，前端命令使用 `cmd /c npm ...`。

## Kubernetes Rules

- 每个服务使用 Deployment + Service。
- Gateway/前端入口使用 Ingress 或 Nginx。
- 配置使用 ConfigMap，密钥使用 Secret/Vault。
- 秒杀、订单、AI 服务必须可水平扩容。
- Redis、MySQL、MQ 生产环境优先使用托管服务或 StatefulSet + 持久卷。

## CI/CD Gates

```bash
./mvnw test
./mvnw -DskipTests package
docker compose -f deploy/docker-compose.yml config
npm run build   # 如果前端变更
```

推荐增加 CheckStyle/PMD/SonarQube、Dependency vulnerability scan、Docker image scan、Contract tests。

## Monitoring

Prometheus + Grafana Dashboard 至少覆盖 HTTP QPS/P95/P99/错误率、JVM、Redis、MQ、MySQL、AI 模型耗时、向量检索耗时、token 消耗。

## Backup and Disaster Recovery

- MySQL 每日全量 + 增量/binlog 备份。
- Nacos 使用 DB 存储并备份配置。
- MQ 开启持久化，核心 topic/queue 有保留策略。
- 定期演练恢复流程，不只配置备份。
