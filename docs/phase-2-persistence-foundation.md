# Phase 2 Persistence Foundation Notes

## Scope

Phase 2 starts the persistence baseline with the user service only. It adds Flyway runtime migration support, the first `ums_user` table migration, and a repository testing strategy before register/login APIs or repository adapters are implemented.

## User Service Flyway Runtime

The user service owns the `sangui_user` schema and loads migrations from:

```text
services/sangui-user-service/src/main/resources/db/migration
```

The first migration is:

```text
services/sangui-user-service/src/main/resources/db/migration/V1__create_user_identity_tables.sql
```

Runtime placeholders are environment-driven:

| Property | Env | Default |
| --- | --- | --- |
| `spring.datasource.url` | `MYSQL_HOST`, `MYSQL_PORT`, `SANGUI_USER_MYSQL_SCHEMA` | `localhost`, `3306`, `sangui_user` |
| `spring.datasource.username` | `MYSQL_USERNAME` | `sangui_app` |
| `spring.datasource.password` | `MYSQL_PASSWORD` | empty |
| `spring.flyway.enabled` | `SANGUI_USER_FLYWAY_ENABLED` | `true` |
| `spring.flyway.default-schema` | `SANGUI_USER_MYSQL_SCHEMA` | `sangui_user` |

## User Schema Contract

`V1__create_user_identity_tables.sql` creates `ums_user` with the platform columns required by the database spec:

```sql
id BIGINT PRIMARY KEY,
shop_id BIGINT NOT NULL DEFAULT 1,
created_at DATETIME NOT NULL,
updated_at DATETIME NOT NULL,
deleted TINYINT NOT NULL DEFAULT 0,
version INT NOT NULL DEFAULT 0
```

The table also introduces account identity fields that later user/auth APIs can build on:

| Column | Purpose |
| --- | --- |
| `username` | Stable account login name inside one shop. |
| `mobile` | Optional mobile login identity inside one shop. |
| `email` | Optional email identity reserved for later use. |
| `password_hash` | Password hash storage only; no plaintext password. |
| `status` | Account lifecycle marker, defaulting to `ACTIVE`. |
| `last_login_at` | Optional login audit timestamp. |

Required indexes:

- `uk_ums_user_shop_username` protects duplicate usernames within `shop_id`.
- `uk_ums_user_shop_mobile` protects duplicate mobile numbers within `shop_id` when mobile is present.
- `idx_ums_user_shop_deleted` supports tenant-scoped logical-delete filtering.
- `idx_ums_user_status` supports future account status queries.

## Local Verification

Cheap contract test without a live database:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services/sangui-user-service -Dtest=UserMigrationContractTest test
```

Full local verification:

```powershell
.\scripts\verify.ps1
```

Docker Compose config validation:

```powershell
docker compose -f deploy/docker-compose.yml config
```

Manual Flyway execution through application startup:

```powershell
docker compose -f deploy/docker-compose.yml up -d mysql
.\mvnw.cmd -pl services/sangui-user-service -am -DskipTests package
java -jar services\sangui-user-service\target\sangui-user-service-0.1.0-SNAPSHOT.jar
```

When the app starts with `SANGUI_USER_FLYWAY_ENABLED=true`, Flyway should create or validate `ums_user` in `sangui_user`.

The migration intentionally uses plain `CREATE TABLE` instead of `CREATE TABLE IF NOT EXISTS` so an already-existing but drifted table fails fast during Flyway execution.

## Repository Test Strategy

Repository tests should be added when the first user repository or mapper is implemented. Until then, `UserMigrationContractTest` acts as a schema contract test.

Required future repository cases:

| Case | Required Assertions |
| --- | --- |
| Good | Insert a user with required fields, then load by `shop_id` and username/mobile. |
| Good | Pagination returns deterministic ordering and never leaks rows from another `shop_id`. |
| Good | Logical delete sets `deleted=1` and default reads exclude deleted rows. |
| Good | Optimistic locking increments `version` and rejects stale updates. |
| Bad | Duplicate `(shop_id, username)` is rejected by `uk_ums_user_shop_username`. |
| Bad | Duplicate `(shop_id, mobile)` is rejected by `uk_ums_user_shop_mobile` when mobile is present. |
| Bad | A query without `shop_id` is forbidden in repository review. |

Preferred implementation path:

1. Add repository/mapper code only with a Testcontainers MySQL or dedicated Docker MySQL repository test.
2. Keep smoke tests independent from live MySQL by excluding datasource/Flyway auto-configuration.
3. Keep migrations immutable once committed; add `V2__...` for schema changes.
