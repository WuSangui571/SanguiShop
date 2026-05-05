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

## Compensation Ops Audit Event

Compensation ops auth and replay/reconcile actions must emit one unified audit log line with the prefix `Ops audit event.` so grep and structured log pipelines can aggregate them across services.

Required fields:

- `action`: one of `ops.auth.login`, `ops.auth.refresh`, `ops.order.timeout-replay.manual`, `ops.order.timeout-replay.bulk`, `ops.order.compensation.query`, `ops.payment.reconcile.manual`, `ops.payment.reconcile.bulk`, `ops.payment.compensation.query`
- `outcome`: `success`, `failed`, or `denied`
- `traceId`, `method`, `path`, `shopId`, `userId`, `username`, `jwtId`
- `permission`: use `OPS_COMPENSATION_ADMIN` for protected compensation ops surfaces
- Auth actions: include `userIdentifier` and `ip`; success should also include resolved `username`
- Manual replay / reconcile: include `operator`, `targetType`, `targetId`, and domain `result`
- Bulk replay / reconcile: include `operator`, `targetType`, `targetCount`, `dryRun`, and domain `result`
- Denied or failed paths: include `errorCode` and sanitized `reason`

Search contract:

- Repo-backed operator guide: `docs/compensation-ops-audit-search.md`.
- Dashboard audit filters generate Kibana KQL, Kibana Lucene, and Loki LogQL templates from `traceId`, `operator`, `action`, `outcome`, and `shopId`.
- Query templates target log pipelines. They must not call a compensation service endpoint or read order/payment history tables directly.
- Kibana KQL base: `message : "Ops audit event." and traceId : "<traceId>" and action : "<action>" and outcome : "<outcome>"`.
- Kibana Lucene base: `message:"Ops audit event." AND traceId:"<traceId>" AND action:"<action>" AND outcome:"<outcome>"`.
- Loki LogQL base: `{app=~"sangui-.*"} |= "Ops audit event." |= "traceId=<traceId>" |= "action=<action>" |= "outcome=<outcome>"`.
- When structured parsing is unavailable, operators may search raw fragments such as `traceId=<traceId>` and `operator=<operator>` in the message body.

Good/Base/Bad cases:

- Good: `POST /api/users/ops/login` success logs `action=ops.auth.login`, `outcome=success`, `userIdentifier`, `username`, `traceId`
- Good: `POST /internal/orders/timeout-replays/manual` forbidden logs `action=ops.order.timeout-replay.manual`, `outcome=denied`, `permission=OPS_COMPENSATION_ADMIN`, `errorCode=AUTH_FORBIDDEN`
- Good: dashboard "View audit trail" after manual payment reconcile fills `traceId=<response.traceId>`, `operator=<replayOperator>`, `action=ops.payment.reconcile.manual`, and `outcome=success`
- Good: dashboard "View audit trail" after a failed replay request fills the error response `traceId`, the attempted replay action, and `outcome=failed`
- Base: Query endpoints only need denial audit events; successful search/list queries do not need a second business-success audit line
- Bad: order-service, payment-service, and user-service each invent different field names for operator, target, or outcome
- Bad: audit logs include passwords, JWT strings, or unsanitized multi-line exception messages
- Bad: a frontend audit search panel calls internal service APIs to search logs; it should generate operator queries unless a separate log-search API contract is defined

## Tracing

- Gateway 生成或透传 `traceId`。
- Feign、MQ、异步线程必须传递 traceId。
- 日志、响应、MQ event envelope 使用同一个 traceId。
