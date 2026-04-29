# Logging Guidelines

## Scope

适用于后端日志、审计日志、链路追踪、指标埋点和排障输出。

## Log Format

生产日志输出 JSON，至少包含 `timestamp`、`level`、`service`、`traceId`、`shopId`、`userId`、`event`、`message`、`durationMs`。

## Level Rules

- DEBUG：本地调试，生产默认关闭。
- INFO：关键业务状态变化，如订单创建、支付成功、知识库导入完成。
- WARN：可恢复异常、降级、限流、重复消息、幂等冲突。
- ERROR：不可恢复或需要人工介入，如支付对账失败、DLQ 堆积、数据不一致。

## Sensitive Data

禁止日志输出：密码、JWT、支付密钥、模型 API Key、完整身份证/手机号、支付原始签名串、系统 prompt。

## Required Business Logs

| 场景 | 必须字段 |
| --- | --- |
| 登录失败 | `userIdentifier`, `ip`, `reason` |
| 秒杀请求接受 | `activityId`, `skuId`, `userId`, `requestNo` |
| 订单创建 | `orderNo`, `userId`, `amount` |
| 支付回调 | `paymentNo`, `channel`, `channelTradeNo`, `verifyResult` |
| MQ 消费失败 | `eventId`, `eventType`, `retryCount`, `errorCode` |
| AI 调用 | `sessionId`, `model`, `retrievalMs`, `modelMs`, `topK` |

## Tracing

- Gateway 生成或透传 `traceId`。
- Feign、MQ、异步线程必须传递 traceId。
- 日志、响应、MQ event envelope 使用同一个 traceId。