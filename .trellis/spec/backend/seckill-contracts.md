# Seckill Contracts

## Scope / Trigger

任何涉及秒杀活动、限时库存、抢购资格、秒杀令牌、Redis 扣减、异步下单、订单创建、支付联动的改动必须读取本文件。

## End-to-End Flow

1. 前端加载活动和服务器时间。
2. 用户登录后请求秒杀令牌。
3. Gateway 校验 JWT、限流、转发。
4. Seckill Service 校验活动状态、令牌、购买资格。
5. Redis Lua 原子预扣库存并记录用户购买标识。
6. Seckill Service 发送 `SECKILL_ORDER_REQUESTED` 事件。
7. Order Service 消费事件，创建订单，落库唯一约束防重复。
8. Payment Service 发起支付，回调后更新订单状态。

## API Contracts

`POST /api/seckill/activities/{activityId}/token`

```json
{"shopId":1,"skuId":10001}
```

`POST /api/seckill/orders`

```json
{
  "shopId": 1,
  "activityId": 9001,
  "skuId": 10001,
  "quantity": 1,
  "seckillToken": "opaque-token",
  "requestId": "client-generated-id"
}
```

Accepted response:

```json
{"accepted":true,"requestNo":"SKQ202604290001","status":"QUEUED"}
```

## Idempotency and Uniqueness

- Redis bought key：`sangui:{env}:seckill:bought:{activityId}:{userId}`。
- DB 唯一索引：`UNIQUE(shop_id, activity_id, user_id)`。
- MQ event id：`eventId` 唯一。
- 客户端 `requestId` 只能辅助排查，不作为唯一购买约束的唯一来源。

## State Machine

```text
QUEUED -> ORDER_CREATED -> WAITING_PAYMENT -> PAID -> FULFILLING -> COMPLETED
QUEUED -> FAILED
WAITING_PAYMENT -> CANCELLED_TIMEOUT
```

## Validation & Error Matrix

| 条件 | code | 可重试 |
| --- | --- | --- |
| 活动不存在 | `SECKILL_ACTIVITY_NOT_FOUND` | 否 |
| 活动未开始 | `SECKILL_NOT_STARTED` | 可稍后重试 |
| 活动已结束 | `SECKILL_ENDED` | 否 |
| token 缺失/过期 | `SECKILL_TOKEN_INVALID` | 需重新获取 |
| 库存不足 | `STOCK_NOT_ENOUGH` | 否 |
| 用户已购买 | `SECKILL_DUPLICATE_BUY` | 否，返回已有状态 |
| 队列繁忙 | `SECKILL_QUEUE_BUSY` | 可重试，带退避 |

## Tests Required

- Lua 并发扣减测试。
- 同用户重复购买测试。
- MQ 重复消费测试。
- 订单创建失败补偿测试。
- JMeter/Locust 压测报告：QPS、P95/P99、Redis 耗时、MQ 积压、DB 写入速率。