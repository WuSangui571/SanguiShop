# SanguiShop

SanguiShop is a single-merchant ecommerce platform scaffolded as a Spring Boot and Spring Cloud Alibaba multi-module project.

## Phase 1 Scope

This repository currently contains the foundation skeleton only:

- Maven parent project and module aggregation.
- Common technical modules for API envelope, error codes, trace IDs, JWT claim constants, Redis key naming, MQ event envelope, and observability field names.
- Gateway shell with Spring Cloud Gateway route placeholders.
- Service shells for user, product, seckill, order, payment, logistics, review, marketing, search recommendation, and AI.
- Local dependency placeholders for MySQL, Redis, Nacos, and RocketMQ under `deploy/`.

No complete business workflow is implemented in this phase.

## Module Layout

```text
common/
  sangui-common-core/
  sangui-common-web/
  sangui-common-security/
  sangui-common-redis/
  sangui-common-mq/
  sangui-common-observability/
services/
  sangui-gateway/
  sangui-user-service/
  sangui-product-service/
  sangui-seckill-service/
  sangui-order-service/
  sangui-payment-service/
  sangui-logistics-service/
  sangui-review-service/
  sangui-marketing-service/
  sangui-search-rec-service/
  sangui-ai-service/
deploy/
frontend/
docs/
```

## Verification

```powershell
mvn -q -DskipTests compile
mvn -q test
```

PowerShell may block npm's `.ps1` shim on some Windows machines. Use `cmd /c npm ...` for frontend commands when a full frontend project is added.

## Secret Policy

Do not commit real database passwords, Redis passwords, MQ credentials, JWT private keys, payment secrets, model API keys, or `.env` files. Use `deploy/.env.example` as the contract for local environment variables.
