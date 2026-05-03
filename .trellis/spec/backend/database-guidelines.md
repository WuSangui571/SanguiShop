# Database Guidelines

## Scope

适用于 MySQL 表设计、索引、事务、读写分离、分库分表、迁移脚本和数据一致性方案。

## Ownership Rule

每个微服务拥有自己的数据库 schema，禁止跨服务直接读写其他服务数据库。跨服务数据通过 API、Feign、事件或只读投影同步。

## Table Naming

| 服务 | 表前缀 | 示例 |
| --- | --- | --- |
| User | `ums_` | `ums_user`, `ums_role` |
| Product | `pms_` | `pms_spu`, `pms_sku`, `pms_category` |
| Seckill | `sk_` | `sk_activity`, `sk_activity_sku` |
| Order | `oms_` | `oms_order`, `oms_order_item` |
| Payment | `pay_` | `pay_payment_order`, `pay_callback_log` |
| AI | `ai_` | `ai_knowledge_doc`, `ai_chat_session` |

## Required Columns

```sql
id BIGINT PRIMARY KEY,
shop_id BIGINT NOT NULL DEFAULT 1,
created_at DATETIME NOT NULL,
updated_at DATETIME NOT NULL,
deleted TINYINT NOT NULL DEFAULT 0,
version INT NOT NULL DEFAULT 0
```

业务流水表额外包含：

```sql
request_id VARCHAR(64) NULL,
trace_id VARCHAR(64) NULL,
status VARCHAR(32) NOT NULL
```

## Money and Time

- 金额使用 `BIGINT`，单位为分；禁止 `double` / `float` 表示金额。
- 时间字段存 UTC 或明确约定的 `Asia/Shanghai`，API 层输出 ISO-8601。
- 订单号、支付单号、事件 ID 必须全局唯一，不依赖自增 ID 暴露给外部。

## Index Rules

- 秒杀唯一购买约束：`UNIQUE(shop_id, activity_id, user_id)`。
- 支付回调幂等：`UNIQUE(channel, channel_trade_no)`。
- 订单幂等：`UNIQUE(shop_id, user_id, request_id)`，允许 `request_id` 非空时生效。

```sql
CREATE UNIQUE INDEX uk_sk_user_activity ON sk_order_record(shop_id, activity_id, user_id);
```

## Transaction Boundaries

- 单服务单库内使用本地事务。
- 跨服务优先使用 MQ 最终一致性，不默认引入分布式事务。
- Seata 只用于确实需要强一致且已评估性能损耗的场景。
- 秒杀链路禁止在 Redis 预扣减阶段开启长数据库事务。

## Flyway Migration Contract

- 每个服务的迁移脚本放在 `services/<service>/src/main/resources/db/migration/`。
- 文件命名使用 `V{version}__{description}.sql`，例如 `V1__create_user_identity_tables.sql`。
- 已提交的 `V*` 迁移视为不可变；后续结构变更新增 `V2__...`，禁止改写历史脚本。
- 建表迁移默认使用普通 `CREATE TABLE`，避免 `IF NOT EXISTS` 掩盖已存在但结构漂移的表。
- 服务通过 `spring.flyway.locations=classpath:db/migration` 加载迁移。
- schema 必须来自服务专属环境变量，例如 user 服务使用 `SANGUI_USER_MYSQL_SCHEMA`，默认 `sangui_user`。
- secret 不写入迁移脚本或配置文件；连接密码只能来自 `MYSQL_PASSWORD` 等本地环境变量。

Phase 2 user schema baseline:

| Service | Schema Env | Default Schema | Migration |
| --- | --- | --- | --- |
| `services/sangui-user-service` | `SANGUI_USER_MYSQL_SCHEMA` | `sangui_user` | `db/migration/V1__create_user_identity_tables.sql` |

`V1__create_user_identity_tables.sql` 必须创建 `ums_user`，并至少包含：

- 平台列：`id`、`shop_id`、`created_at`、`updated_at`、`deleted`、`version`。
- 登录身份列：`username`、`mobile`、`password_hash`。
- 唯一索引：`uk_ums_user_shop_username(shop_id, username)`、`uk_ums_user_shop_mobile(shop_id, mobile)`。
- 逻辑删除查询索引：`idx_ums_user_shop_deleted(shop_id, deleted)`。

Executable validation:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -pl services/sangui-user-service -Dtest=UserMigrationContractTest test
docker compose -f deploy/docker-compose.yml config
```

Manual local Flyway run:

```powershell
docker compose -f deploy/docker-compose.yml up -d mysql
.\mvnw.cmd -pl services/sangui-user-service -am -DskipTests package
java -jar services\sangui-user-service\target\sangui-user-service-0.1.0-SNAPSHOT.jar
```

## Tests Required

- Repository/Mapper 测试覆盖唯一索引、分页、逻辑删除、乐观锁。
- 订单/支付/秒杀表必须有并发幂等测试。
- 迁移脚本至少在本地 Docker MySQL 执行一次。

## Order Timeout Compensation Index

`services/sangui-order-service/src/main/resources/db/migration/V3__add_order_timeout_lookup_index.sql` supports unpaid order timeout cancellation.

Required index:

```sql
CREATE INDEX idx_oms_order_shop_status_created ON oms_order (shop_id, status, created_at);
```

Executable validation:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service" -am "-Dtest=OrderMigrationContractTest,OrderTimeoutCancelServiceTest,InternalOrderTimeoutControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: timeout compensation queries `shop_id`, `status = created`, and `created_at <= cutoff`.
- Base: `created_at` is the timeout basis until a dedicated payment deadline column is introduced.
- Bad: timeout compensation scans all orders without `shop_id` and `status` predicates.

## Payment Reconcile Compensation Index

`services/sangui-payment-service/src/main/resources/db/migration/V3__add_payment_reconcile_lookup_index.sql` supports stale `created` payment reconciliation.

Required index:

```sql
CREATE INDEX idx_pay_payment_order_shop_status_created ON pay_payment_order (shop_id, status, created_at);
```

Executable validation:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-payment-service" -am "-Dtest=PaymentMigrationContractTest,PaymentReservationMigrationContractTest,PaymentReconcileMigrationContractTest,PaymentReconcileServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: reconcile compensation queries `shop_id`, `status = created`, and `created_at <= cutoff`.
- Base: `created_at` is the reconcile basis until a dedicated provider deadline or next-poll timestamp is introduced.
- Bad: payment reconcile scans all payment rows without `shop_id` and `status` predicates.

Repository test strategy:

| Case | 断言重点 |
| --- | --- |
| Good | 正常插入并按 `shop_id` + username/mobile 查询。 |
| Good | 分页排序稳定，且不泄露其他 `shop_id` 数据。 |
| Good | 逻辑删除后默认查询排除 `deleted=1` 数据。 |
| Good | 乐观锁更新递增 `version`，过期版本更新失败。 |
| Base | 仅有迁移脚本、尚无 repository 时，至少保留 SQL contract test。 |
| Bad | 重复 `(shop_id, username)` 或 `(shop_id, mobile)` 必须被唯一索引拒绝。 |
| Bad | repository 查询遗漏 `shop_id` 条件。 |
## Compensation Ops Metadata Columns

Manual replay / ops-surface work stores the latest compensation attempt on the business row instead of introducing a second history table.

Order migration:

- `services/sangui-order-service/src/main/resources/db/migration/V4__add_order_compensation_ops_columns.sql`

Payment migration:

- `services/sangui-payment-service/src/main/resources/db/migration/V4__add_payment_compensation_ops_columns.sql`

Required columns on both `oms_order` and `pay_payment_order`:

- `last_compensation_result`
- `last_compensation_error_code`
- `last_compensation_reason`
- `last_compensation_trace_id`
- `last_compensation_trigger`
- `last_compensated_at`

Rules:

- Latest-compensation columns are nullable because legacy rows may have no compensation attempts yet.
- `last_compensation_result` is bounded to operational outcomes such as `cancelled`, `settled`, `skipped`, or `failed`; do not store free-form prose in this field.
- `last_compensation_error_code` stores a stable machine-readable code or skip reason key.
- `last_compensation_reason` stores a sanitized one-line message safe for ops query surfaces.
- `last_compensation_trace_id` must reuse the request/job trace id that was emitted to logs.
- `last_compensation_trigger` distinguishes at least `manual` and `scheduler`.
- `last_compensation_operator` stores the explicit operator for manual single/bulk replay and stays `NULL` for scheduler-triggered runs.

Executable validation:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=OrderCompensationOpsMigrationContractTest,PaymentCompensationOpsMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: a manual replay updates latest-compensation columns on the same order/payment row that operators query later.
- Good: a scheduler retry overwrites latest-compensation columns with the latest attempt instead of appending hidden state elsewhere.
- Good: every scheduler or manual attempt appends a separate history row with `trace_id`, `trigger_type`, and `operator`.
- Base: latest metadata remains on the business row while history rows accumulate separately.
- Bad: storing stack traces or multi-line raw payloads in `last_compensation_reason`.
- Bad: adding ops query APIs without persisting any latest-compensation metadata.

## Compensation Attempt History Tables

Follow-up compensation operations persist immutable attempt history alongside latest row metadata.

Order migration:

- `services/sangui-order-service/src/main/resources/db/migration/V5__add_order_compensation_attempt_history.sql`

Payment migration:

- `services/sangui-payment-service/src/main/resources/db/migration/V5__add_payment_compensation_attempt_history.sql`

Rules:

- Attempt history is append-only from application code; do not update prior attempt rows to reflect the latest state.
- `operator` is nullable for scheduler runs and required for manual single/bulk replay requests.
- `reason` remains sanitized single-line operational text; do not persist stack traces or raw payloads.
- Keep latest metadata on business rows for fast query APIs; the history table is the audit trail, not a replacement for the fast-path row snapshot.
