# Error Handling Guidelines

## Scope

适用于 Controller、Application Service、Feign、MQ Consumer、Gateway Filter、AI/RAG 调用、支付回调和定时补偿任务。

## Error Code Format

错误码使用大写 snake case，按领域分组：`AUTH_TOKEN_EXPIRED`、`VALIDATION_FAILED`、`ORDER_NOT_FOUND`、`PAYMENT_SIGNATURE_INVALID`、`SECKILL_DUPLICATE_BUY`、`AI_MODEL_TIMEOUT`、`DOWNSTREAM_TIMEOUT`。

## Exception Mapping

| 异常类型 | HTTP | 说明 |
| --- | --- | --- |
| `ValidationException` / `MethodArgumentNotValidException` | 400 | 参数错误 |
| `AuthenticationException` | 401 | 未登录或 token 无效 |
| `AccessDeniedException` | 403 | 权限不足 |
| `BusinessException` | 409/422 | 业务冲突，如库存不足 |
| `DownstreamException` | 503 | 下游不可用 |
| `RateLimitException` | 429 | 限流 |
| 未知异常 | 500 | 记录 error，返回通用消息 |

## Response Rule

- 外部响应不得包含 SQL、堆栈、密钥、内部 URL、模型 prompt。
- `traceId` 必须返回给前端，便于排查。
- 业务失败和系统失败要区分：库存不足不是 500。

## Payment Callback Rule

1. 验签。
2. 检查支付单是否存在。
3. 幂等检查：若已成功，返回渠道要求的成功响应。
4. 校验金额、币种、订单号。
5. 更新支付单和订单状态。
6. 发布支付成功事件。

## Tests Required

- Global exception handler 单元测试。
- 参数校验字段级错误测试。
- Feign fallback 不产生假成功测试。
- MQ consumer 重试/DLQ 测试。
- 支付重复回调测试。