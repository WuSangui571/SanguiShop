# Microservice Contracts

## Scope / Trigger

实现 REST API、OpenFeign、MQ 事件、跨服务 DTO、前后端接口或多服务事务时必须读取本文件。

## Core Contract Rules

- 所有外部 API 使用 JSON；时间使用 ISO-8601 字符串或统一毫秒时间戳，不能混用。
- 所有业务请求必须携带或可解析出 `shopId`。
- 所有写操作必须有幂等键：`requestId`、`orderNo`、`paymentNo`、`activityId + userId` 等。
- Feign DTO、MQ Event、前端 Response DTO 不得直接暴露数据库 entity。
- 跨服务只共享契约，不共享业务实现。

## REST Response Envelope

```json
{
  "code": "ORDER_CREATED",
  "message": "ok",
  "data": {},
  "traceId": "01J...",
  "timestamp": "2026-04-29T15:00:00+08:00"
}
```

## Required Request Fields

| 场景 | 必填字段 | 幂等字段 | 说明 |
| --- | --- | --- | --- |
| 创建订单 | `shopId`, `userId`, `items`, `requestId` | `requestId` | 重复点击返回同一订单或明确冲突 |
| 秒杀下单 | `shopId`, `activityId`, `skuId`, `userId`, `seckillToken` | `activityId + userId` | 同用户同活动只允许成功一次 |
| 支付发起 | `shopId`, `orderNo`, `amount`, `channel` | `paymentNo` | 金额以分为单位 |
| 支付回调 | `paymentNo`, `channelTradeNo`, `paidAmount`, `sign` | `channelTradeNo` | 必须验签后更新状态 |
| AI 问答 | `shopId`, `sessionId`, `question` | `requestId` 可选 | 输出包含来源或无来源说明 |

## Feign Rules

- Feign client 放在被调用服务的 `client` 包或独立 API jar。
- Feign 超时、重试、Sentinel fallback 必须显式配置。
- 查询接口可安全降级；订单、支付、库存写操作 fallback 不得假装成功。

```java
@FeignClient(name = "sangui-product", contextId = "productInventoryClient", fallbackFactory = ProductInventoryFallbackFactory.class)
public interface ProductInventoryClient {
    @PostMapping("/internal/products/inventory/reserve")
    Result<ReserveInventoryResponse> reserve(@Valid @RequestBody ReserveInventoryRequest request);
}
```

## MQ Event Envelope

```json
{
  "eventId": "evt_20260429_000001",
  "eventType": "SECKILL_ORDER_REQUESTED",
  "version": 1,
  "occurredAt": "2026-04-29T15:00:00+08:00",
  "shopId": 1,
  "traceId": "01J...",
  "payload": {}
}
```

## Validation & Error Matrix

| 条件 | HTTP | code | 行为 |
| --- | --- | --- | --- |
| 缺少 JWT | 401 | `AUTH_TOKEN_MISSING` | 网关拒绝 |
| 无权限 | 403 | `AUTH_FORBIDDEN` | 网关或服务拒绝 |
| 参数校验失败 | 400 | `VALIDATION_FAILED` | 返回字段级错误 |
| 幂等键重复且已成功 | 200 | 原成功 code | 返回原业务结果 |
| 幂等键重复但参数不同 | 409 | `IDEMPOTENCY_CONFLICT` | 拒绝并告警 |
| 下游超时 | 503 | `DOWNSTREAM_TIMEOUT` | 可重试或降级 |
| 库存不足 | 409 | `STOCK_NOT_ENOUGH` | 业务失败，不重试 |

## Tests Required

- Contract test：序列化 request/response/event，断言字段名和必填项。
- Idempotency test：重复请求、乱序回调、重复 MQ 消费。
- Fallback test：Feign 超时/熔断时不得产生假成功。