# Frontend API Contracts

## HTTP Client Rules

- Base URL 来自环境变量，不硬编码微服务地址。
- 所有请求经过 Gateway：`/api/...`。
- httpClient 统一注入 Authorization、trace header、超时。
- 统一解析 `ApiResult<T>`，不要在每个组件重复处理 envelope。

## Error Handling

| code | 前端行为 |
| --- | --- |
| `AUTH_TOKEN_EXPIRED` | 清理登录态并跳转登录 |
| `AUTH_FORBIDDEN` | 展示无权限 |
| `VALIDATION_FAILED` | 映射到字段错误 |
| `RATE_LIMITED` | 展示稍后重试，不自动狂重试 |
| `STOCK_NOT_ENOUGH` | 秒杀/购物车展示库存不足 |
| `SECKILL_QUEUE_BUSY` | 展示排队繁忙，可手动重试 |
| `AI_NO_CONTEXT` | 展示未找到依据，不伪造回答 |

## Request ID

写操作必须由前端生成 `requestId`，用于去重和排障。

```ts
const request: SubmitSeckillRequest = {
  shopId,
  activityId,
  skuId,
  quantity: 1,
  seckillToken,
  requestId: crypto.randomUUID(),
}
```