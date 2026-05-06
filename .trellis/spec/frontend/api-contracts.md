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

## Mall Order Status APIs

Frontend customer order status flows must use these gateway routes through `services/orderApi.ts` and `services/paymentApi.ts`:

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `createOrder(payload)` | `POST /api/orders` | `mall` | Disable duplicate submit while pending and preserve `requestId`. |
| `getOrder(orderId)` | `GET /api/orders/{orderId}` | `mall` | Load detail after refresh from `orderId` URL state. |
| `listOrders({page,size})` | `GET /api/orders?page=&size=` | `mall` | Show empty/loading/error states and never send `shopId` / `userId` query fields. |
| `cancelOrder(orderId)` | `POST /api/orders/{orderId}/cancel` | `mall` | Enable only for `created` orders and guard duplicate clicks. |
| `getPayment(paymentNo)` | `GET /api/payments/{paymentNo}` | `mall` | Manual refresh only unless bounded polling with cleanup is explicitly implemented. |

Order status display rules:

- `created` means unpaid and cancellable.
- `paid` means payment complete and cancel is disabled.
- `cancelled` means payment actions and cancel are disabled.
- If `paymentNo` is unavailable for a historical order, the UI may derive the payment summary from order status but must not invent a `PaymentResponse`.

Required tests:

- Order detail load from `orderId`.
- Payment refresh by `paymentNo`.
- Duplicate cancel click guard.
- API errors preserve `code`, `message`, and `traceId`.

## Public Environment Configuration

- `VITE_API_BASE_URL`: optional gateway origin prefix; empty means same origin.
- `VITE_DEFAULT_SHOP_ID`: optional dashboard default `shopId`.
- `VITE_KIBANA_DISCOVER_URL`: optional absolute Kibana Discover URL for compensation ops audit search links.
- `VITE_LOKI_EXPLORE_URL`: optional absolute Grafana/Loki Explore URL for compensation ops audit search links.
- Observability URLs are public client configuration and must not contain credentials, API keys, or secrets.
- Missing or invalid observability URLs must degrade to copy-only query templates.
