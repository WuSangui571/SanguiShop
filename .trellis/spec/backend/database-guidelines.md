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

## Tests Required

- Repository/Mapper 测试覆盖唯一索引、分页、逻辑删除、乐观锁。
- 订单/支付/秒杀表必须有并发幂等测试。
- 迁移脚本至少在本地 Docker MySQL 执行一次。