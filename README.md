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

### One-click smoke validation

```powershell
.\scripts\smoke-local.ps1
```

Default run validates Git hygiene, Docker Compose config, backend compile, and frontend typecheck/build. The script uses quiet Compose validation by default so local `.env` values are not printed.
If Windows PowerShell blocks local `.ps1` execution, use a process-scoped bypass:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-local.ps1
```

### Skip flags

| Flag | Effect |
| --- | --- |
| `-SkipDocker` | Skip Docker-compose check (machine without Docker) |
| `-SkipBackend` | Skip Maven compile/test |
| `-SkipFrontend` | Skip npm typecheck/build |
| `-BackendMode test` | Run full Maven test suite instead of compile |
| `-PrintCommandOnly` | Print resolved commands and exit, no side effects |

### Check interpretation

- `PASS`: check completed successfully.
- `FAIL`: check failed; investigate the reported area.
- `SKIP`: check skipped by an explicit flag, or because an optional project area is absent; not a failure, but note omitted checks.
- Missing tools for selected checks are `FAIL`. Use an explicit skip flag, such as `-SkipDocker`, when the local machine intentionally lacks that tool.

### RocketMQ tracked/ignored boundary

- Tracked: `deploy/rocketmq/broker.conf`.
- Ignored (gitignored runtime artifacts): `deploy/rocketmq/broker-store/`, `deploy/rocketmq/broker-logs/`, `deploy/rocketmq/namesrv-logs/`.

### Fallback commands

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=<root>\.m2\repository" -DskipTests compile
.\mvnw.cmd -q "-Dmaven.repo.local=<root>\.m2\repository" test
docker compose -f deploy/docker-compose.yml config --quiet
cmd /c npm --prefix frontend run typecheck
cmd /c npm --prefix frontend run build
```

Windows frontend commands must use `cmd /c npm --prefix frontend ...` to avoid PowerShell npm shim policy issues.

## Secret Policy

Do not commit real database passwords, Redis passwords, MQ credentials, JWT private keys, payment secrets, model API keys, or `.env` files. Use `deploy/.env.example` as the contract for local environment variables.
