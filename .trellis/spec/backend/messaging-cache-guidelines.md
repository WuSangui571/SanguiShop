# Messaging & Cache Guidelines

## Scope

适用于 Redis、Redisson、布隆过滤器、Lua 脚本、Kafka/RabbitMQ/RocketMQ、异步事件、重试和死信补偿。

## Redis Key Naming

```text
sangui:{env}:{service}:{domain}:{identifier}
```

Examples:

```text
sangui:prod:seckill:stock:{activityId}:{skuId}
sangui:prod:seckill:bought:{activityId}:{userId}
sangui:prod:gateway:rate:user:{userId}:seckill
sangui:prod:product:detail:{shopId}:{skuId}
sangui:prod:ai:session:{sessionId}
```

## Redis Rules

- 所有缓存必须定义 TTL；永久缓存需要在 spec 中解释原因。
- 秒杀库存扣减必须用 Lua 保证原子性。
- 缓存对象 JSON 字段必须兼容新增字段。
- 热点商品缓存需要预热；缓存击穿使用互斥锁或逻辑过期。
- 布隆过滤器用于无效商品/活动快速拒绝，但不能作为最终一致判断。

## Lua Return Contract

| 返回值 | 含义 |
| --- | --- |
| `1` | 预扣成功 |
| `0` | 库存不足 |
| `-1` | 用户已购买或超过限制 |
| `-2` | 活动未初始化 |

## MQ Naming

| 事件 | Topic/Exchange | Consumer Group/Queue |
| --- | --- | --- |
| 秒杀请求下单 | `sangui.seckill.order.requested` | `order-service.create-seckill-order` |
| 订单创建成功 | `sangui.order.created` | `product-service.deduct-stock`, `payment-service.prepare` |
| 支付成功 | `sangui.payment.paid` | `order-service.mark-paid` |
| 订单超时取消 | `sangui.order.timeout.cancelled` | `product-service.release-stock` |
| 知识库文档导入 | `sangui.ai.knowledge.imported` | `ai-service.embedding-build` |

## Consumer Rules

- 消费者必须幂等：用 `eventId` 或业务唯一键去重。
- 消费失败可重试，但不可无限阻塞主队列。
- 重试耗尽进入 DLQ 或补偿表，并提供人工/定时修复入口。
- 消费成功后记录消费日志，至少包含 `eventId`, `eventType`, `traceId`, `status`。

## Tests Required

- Lua 脚本：库存不足、重复购买、并发扣减。
- Consumer：重复消息、失败重试、非法状态迁移。
- Cache：TTL、序列化兼容、缓存穿透/击穿保护。