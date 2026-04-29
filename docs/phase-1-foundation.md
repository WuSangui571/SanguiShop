# Phase 1 Foundation Notes

## Created Modules

- Root Maven parent: `sanguishop`
- Common modules:
  - `sangui-common-core`
  - `sangui-common-web`
  - `sangui-common-security`
  - `sangui-common-redis`
  - `sangui-common-mq`
  - `sangui-common-observability`
- Service modules:
  - `sangui-gateway`
  - `sangui-user-service`
  - `sangui-product-service`
  - `sangui-seckill-service`
  - `sangui-order-service`
  - `sangui-payment-service`
  - `sangui-logistics-service`
  - `sangui-review-service`
  - `sangui-marketing-service`
  - `sangui-search-rec-service`
  - `sangui-ai-service`

## Empty Shells

Business services contain only startup classes and configuration templates. They do not expose business controllers, database entities, mappers, Feign clients, MQ consumers, or scheduled compensation jobs yet.

Gateway contains only a startup class and route/config placeholders. Full JWT validation, RBAC, audit logging, and rate-limiting filters are intentionally deferred.

## Common Boundaries

Allowed in common:

- API response envelope and pagination DTOs.
- Common error-code contracts and base exceptions.
- Trace and `shopId` constants.
- JWT claim names and principal model.
- Redis key naming helper.
- MQ event envelope, event type constants, and topic constants.
- Log/metric field names.

Not allowed in common:

- Order state machines.
- Seckill qualification or stock deduction rules.
- Payment channel rules.
- Product inventory business logic.
- AI/RAG prompts or retrieval policies.

## Configuration Placeholders

All infrastructure values are environment-driven:

- Nacos: `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_GROUP`
- MySQL: `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- RocketMQ: `ROCKETMQ_NAME_SERVER`
- JWT/payment/model secret references: `SANGUI_JWT_PUBLIC_KEY_LOCATION`, `SANGUI_PAYMENT_CALLBACK_SECRET_REF`, `SANGUI_AI_MODEL_API_KEY_REF`

The project stores secret references or empty placeholders only, not real secrets.

## Service Configuration Contract

Spring Cloud standard configuration:

- `spring.application.name`: stable service discovery name, for example `sangui-gateway` or `sangui-user`.
- `spring.config.import`: optional Nacos import using `${spring.application.name}.yml`.
- `spring.cloud.nacos.discovery.*`: discovery address, namespace, and group.
- `spring.cloud.nacos.config.*`: config center address, namespace, group, and file extension.
- `spring.cloud.sentinel.transport.dashboard`: Sentinel dashboard address placeholder.

Sangui application configuration:

- `sangui.service.domain`: service domain name, for example `user`, `order`, or `payment`.
- `sangui.shop.default-shop-id`: local default shop id placeholder only; business code must still carry `shopId`.
- `sangui.infrastructure.nacos.*`: Nacos runtime placeholders used by services.
- `sangui.infrastructure.mysql.*`: schema, host, port, username, and password placeholders.
- `sangui.infrastructure.redis.*`: host, port, and password placeholders.
- `sangui.infrastructure.mq.name-server`: RocketMQ name server placeholder.

Secret convention:

- Secret values are not committed.
- Secret-like configuration must use empty placeholders or `*_REF` names.
- Allowed secret reference variables are `SANGUI_JWT_PUBLIC_KEY_LOCATION`, `SANGUI_PAYMENT_CALLBACK_SECRET_REF`, and `SANGUI_AI_MODEL_API_KEY_REF`.
- Direct local placeholders such as `MYSQL_PASSWORD` and `REDIS_PASSWORD` are permitted only in `.env.example` or local developer environments.

## Service Port and Schema Matrix

| Service | Spring App Name | Port Env | Default Port | MySQL Schema Env | Default Schema |
| --- | --- | --- | --- | --- | --- |
| Gateway | `sangui-gateway` | `SANGUI_GATEWAY_PORT` | `8080` | N/A | N/A |
| User | `sangui-user` | `SANGUI_USER_PORT` | `8101` | `SANGUI_USER_MYSQL_SCHEMA` | `sangui_user` |
| Product | `sangui-product` | `SANGUI_PRODUCT_PORT` | `8102` | `SANGUI_PRODUCT_MYSQL_SCHEMA` | `sangui_product` |
| Seckill | `sangui-seckill` | `SANGUI_SECKILL_PORT` | `8103` | `SANGUI_SECKILL_MYSQL_SCHEMA` | `sangui_seckill` |
| Order | `sangui-order` | `SANGUI_ORDER_PORT` | `8104` | `SANGUI_ORDER_MYSQL_SCHEMA` | `sangui_order` |
| Payment | `sangui-payment` | `SANGUI_PAYMENT_PORT` | `8105` | `SANGUI_PAYMENT_MYSQL_SCHEMA` | `sangui_payment` |
| Logistics | `sangui-logistics` | `SANGUI_LOGISTICS_PORT` | `8106` | `SANGUI_LOGISTICS_MYSQL_SCHEMA` | `sangui_logistics` |
| Review | `sangui-review` | `SANGUI_REVIEW_PORT` | `8107` | `SANGUI_REVIEW_MYSQL_SCHEMA` | `sangui_review` |
| Marketing | `sangui-marketing` | `SANGUI_MARKETING_PORT` | `8108` | `SANGUI_MARKETING_MYSQL_SCHEMA` | `sangui_marketing` |
| Search/Rec | `sangui-search-rec` | `SANGUI_SEARCH_REC_PORT` | `8109` | `SANGUI_SEARCH_REC_MYSQL_SCHEMA` | `sangui_search_rec` |
| AI | `sangui-ai` | `SANGUI_AI_PORT` | `8110` | `SANGUI_AI_MYSQL_SCHEMA` | `sangui_ai` |

## Phase 1 Quality Loop

Run the same checks locally and in CI:

```powershell
.\scripts\verify.ps1
```

The verify script runs:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=<repo>\.m2\repository" test
.\mvnw.cmd -q "-Dmaven.repo.local=<repo>\.m2\repository" -DskipTests package
docker compose -f deploy/docker-compose.yml config
```

When `MAVEN_USER_HOME` is not already set, `scripts/verify.ps1` uses `<repo>\.m2` so local verification does not depend on a writable user-home Maven directory.

CI runs the Unix equivalents in `.github/workflows/ci.yml`:

```bash
./mvnw -q test
./mvnw -q -DskipTests package
docker compose -f deploy/docker-compose.yml config
```

Good cases:

- Maven Wrapper resolves Maven 3.9.9 and Java 21 is active.
- Common contract tests pass for API result, Redis key naming, MQ event envelope, and common error codes.
- Gateway and one business service smoke tests load the Spring context with Nacos/Sentinel disabled for test scope.
- Docker Compose config renders successfully without starting containers.

Base cases:

- Local developers may run `.\scripts\verify.ps1 -SkipDocker` when Docker is not installed.
- Service smoke tests should remain minimal until business adapters are added.

Bad cases:

- Running global `mvn` directly instead of wrapper commands.
- Committing real secrets or direct secret values into service `application.yml`.
- Adding heavy Checkstyle/PMD rules before the phase 1 scaffold stabilizes.
- Skipping Compose config validation after changing `deploy/docker-compose.yml`.

## Deferred Work

- Business APIs and DTO contract tests.
- Database migrations and repository tests.
- Redis Lua scripts and cache tests.
- MQ consumers, retry, DLQ, and idempotency tests.
- Gateway JWT validation and RBAC.
- Full Vue 3 frontend application.
- Production Kubernetes and Helm manifests.
