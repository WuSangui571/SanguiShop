# Type Safety Guidelines

## API Types

前端 API 类型以后端契约为准。

```ts
export interface ApiResult<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}
```

## Money

- 内部金额使用 `number` 表示分。
- 展示统一 `formatMoney(cents)`。
- 禁止在前端用浮点数计算最终支付金额；最终金额以后端为准。

## Time

- API 时间使用 string，命名如 `startsAt`, `expiresAt`。
- 倒计时使用服务端时间校准。
- 禁止假设用户本地时钟准确。

## DTO Compatibility

- 新增字段必须可选或有默认处理。
- 枚举类型要有 unknown fallback，避免后端新增状态导致页面崩溃。