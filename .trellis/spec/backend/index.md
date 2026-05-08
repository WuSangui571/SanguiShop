# SanguiShop Backend Spec Index

> SanguiShop backend executable coding standards. Default stack: Spring Boot, Spring Cloud Alibaba, Nacos, Sentinel, Redis, MySQL, MQ, Spring Security JWT, and Spring AI Alibaba.

## Project Positioning

SanguiShop is a single-merchant ecommerce platform, but all core business tables, cache keys, events, and API contracts must reserve `shop_id` / `shopId`. Default single-merchant values may come from configuration, but business code must not hardcode merchant magic numbers.

## Spec Map

| Spec | When to Read | Core Focus |
| --- | --- | --- |
| [Directory Structure](./directory-structure.md) | Before adding or moving backend code | Microservice boundaries, package layout, layer rules |
| [Microservice Contracts](./microservice-contracts.md) | Before API / Feign / event / cross-service DTO work | DTOs, envelopes, idempotency, error codes |
| [Gateway & Security](./gateway-security.md) | Before gateway / auth / permission / secret changes | JWT, RBAC, rate limit, sensitive config |
| [Database Guidelines](./database-guidelines.md) | Before table / index / transaction / migration work | MySQL design, uniqueness, Flyway, money/time fields |
| [Messaging & Cache Guidelines](./messaging-cache-guidelines.md) | Before Redis / MQ / async consistency work | Redis keys, TTL, retries, consumer behavior |
| [Seckill Contracts](./seckill-contracts.md) | Before seckill, stock, or order-upstream changes | Seckill API, Redis pre-deduct, MQ ordering, idempotency |
| [AI/RAG Guidelines](./ai-rag-guidelines.md) | Before AI / RAG work | Spring AI, vector store, prompt and safety boundaries |
| [Error Handling](./error-handling.md) | Before controller / service / Feign / MQ work | Exception mapping, business vs system failures |
| [Logging Guidelines](./logging-guidelines.md) | Before key-path logging or audit changes | JSON logs, trace IDs, sensitive data boundaries |
| [Observability & DevOps](./observability-devops.md) | Before deploy / monitoring / CI-CD changes | Docker, K8s, Prometheus, ELK, backup |
| [Inventory Reserve Contracts](./inventory-reserve-contracts.md) | Before SKU stock / reserve / confirm / release changes | Inventory ownership, reservation APIs, schema, idempotency |
| [Order Create Contracts](./order-create-contracts.md) | Before order create / order-service schema changes | Order create API, principal scope, product snapshot, idempotency |
| [Payment Pay Contracts](./payment-pay-contracts.md) | Before payment API / payment-service / order pay status changes | Payment API, internal order pay contract, idempotency, schema |
| [Upload Storage Contracts](./upload-storage-contracts.md) | Before upload / file storage / public asset URL work | Multipart upload boundary, file limits, public URL safety |
| [Quality Guidelines](./quality-guidelines.md) | Before review or completion | Tests, review habits, forbidden patterns |

## Pre-Development Checklist

- [ ] Confirm which service owns the change: `user`, `product`, `seckill`, `order`, `payment`, `logistics`, `review`, `marketing`, `search-rec`, `ai`, or `gateway`.
- [ ] Define request / response / event fields before implementation; never reverse-engineer contracts from entities.
- [ ] For order / payment / inventory / seckill work, define idempotency keys, unique indexes, retry behavior, and compensation strategy first.
- [ ] For Redis / MQ work, define key or topic naming, TTL, serialization, and duplicate-consume behavior first.
- [ ] For external APIs, define gateway auth, rate limit, input validation, and error codes first.
- [ ] Secrets such as JWT secrets, payment keys, and model API keys must not enter the repository; use Nacos, Vault, or K8s Secret.

## Quality Check

- [ ] `mvn test` covers the touched services; when contracts change, add integration or contract tests.
- [ ] Controllers, Feign clients, and MQ consumers include parameter validation and error mapping.
- [ ] Key business paths record enough context such as `traceId`, `shopId`, `userId`, `orderNo`, or `activityId`.
- [ ] Seckill, payment, and order code proves idempotency instead of assuming the frontend will never repeat requests.
- [ ] New DB fields, Redis keys, and MQ events are written into the matching backend spec docs.
