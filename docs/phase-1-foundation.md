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

## Deferred Work

- Business APIs and DTO contract tests.
- Database migrations and repository tests.
- Redis Lua scripts and cache tests.
- MQ consumers, retry, DLQ, and idempotency tests.
- Gateway JWT validation and RBAC.
- Full Vue 3 frontend application.
- Production Kubernetes and Helm manifests.
