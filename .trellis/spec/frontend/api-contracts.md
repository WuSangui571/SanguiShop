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

## Admin Product Management APIs

Frontend admin product management must use gateway routes through `services/productApi.ts` with `authContext: 'ops'` until a dedicated admin auth context exists.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminProducts({page,size,status})` | `GET /api/admin/products?page=&size=&status=` | `ops` | Show loading, empty, error, retry; omit `status` when filter is `all`. |
| `getAdminProduct(productId)` | `GET /api/admin/products/{productId}` | `ops` | Load SKU detail after row selection and preserve backend errors. |
| `createProduct(payload)` | `POST /api/admin/products` | `ops` | Build payload from a validated draft; disable duplicate submit while pending. |
| `updateProduct(payload)` | `PUT /api/admin/products/{productId}` | `ops` | Keep `productId` in the path and send body fields compatible with backend DTO. |
| `updateProductStatus(productId,payload)` | `POST /api/admin/products/{productId}/status` | `ops` | Generate `requestId`; disable status buttons while pending. |
| `adjustSkuStock(productId,skuId,payload)` | `POST /api/admin/products/{productId}/skus/{skuId}/stock-adjustments` | `ops` | Generate `requestId`; disable stock action while pending. |

Admin product model rules:

- Page copy must use `useAppPreferences().t()` and new color usage must rely on semantic CSS variables.
- Product status must tolerate unknown backend values by displaying the raw value rather than crashing.
- Money is stored and submitted as integer cents; display uses `formatMoney(cents)`.
- Stock inputs are non-negative integers; SKU prices are positive integer cents.
- `shopId` and `userId` DTO fields are populated from the persisted ops/admin session for compatibility, but backend principal scope is authoritative; frontend must not hardcode a merchant magic value.
- API errors must preserve and display backend `code`, `message`, and `traceId`.

Required tests:

- Product form payload trimming and cents/stock integer validation.
- Duplicate SKU code validation.
- Backend error `code/message/traceId` preservation.
- Duplicate submit guard for save/status/stock write actions.

## Admin Order Management APIs

Frontend admin order management must use gateway routes through `services/orderApi.ts` and `services/paymentApi.ts` with `authContext: 'ops'`.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminOrders({page,size,status,orderNo,userId,fromTime,toTime})` | `GET /api/admin/orders` | `ops` | Show loading, empty, error, retry, pagination; omit `status` when filter is `all`; omit blank text filters. |
| `getAdminOrder(orderId)` | `GET /api/admin/orders/{orderId}` | `ops` | Load order snapshot, item snapshots, `reservationNo`, nullable `paymentNo`, and `traceId`. |
| `cancelAdminOrder(orderId,{requestId})` | `POST /api/admin/orders/{orderId}/cancel` | `ops` | Enable only for `created`; generate `requestId`; disable duplicate clicks while pending. |
| `getAdminPaymentByOrderId(orderId)` | `GET /api/admin/payments/by-order/{orderId}` | `ops` | Refresh payment status by order id; treat `PAYMENT_NOT_FOUND` as no payment row during automatic detail load. |

Admin order model rules:

- Page copy must use `useAppPreferences().t()` and new colors must rely on semantic CSS variables.
- Order status must display `created`, `paid`, and `cancelled` labels and fall back to raw unknown backend values.
- Money is integer cents and display uses `formatMoney(cents)`.
- Time filters from `datetime-local` inputs must be normalized to ISO-8601 values before sending.
- API errors must preserve and display backend `code`, `message`, and `traceId`.
- `paymentNo` in order responses is nullable because order-service must not read payment tables. Payment status is loaded from the payment-service admin route by `orderId`.
- `OPS_COMPENSATION_ADMIN` alone must not show the order management workspace; require `ADMIN` role or `ORDER_MANAGEMENT_ADMIN`.

Required tests:

- Filter payload trimming, `all` omission, blank filter omission, and time normalization.
- Pagination default/clamp behavior.
- Status labels for `created`, `paid`, `cancelled`, and unknown raw values.
- Backend error `code/message/traceId` preservation.
- Duplicate cancel submit guard.
- Cancel request `requestId` generation and trimming.

## Admin Fulfillment Management APIs

Frontend admin fulfillment management must use gateway routes through `services/fulfillmentApi.ts` with `authContext: 'ops'`.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminFulfillments({page,size,status,orderNo,userId,fromTime,toTime})` | `GET /api/admin/fulfillments` | `ops` | Show loading, empty, error, retry, pagination; omit `status` when filter is `all`; omit blank text filters. |
| `getAdminFulfillment(orderId)` | `GET /api/admin/fulfillments/{orderId}` | `ops` | Load order fulfillment snapshot and preserve backend errors. |
| `shipAdminFulfillment(orderId,{requestId,carrier,trackingNo})` | `POST /api/admin/fulfillments/{orderId}/ship` | `ops` | Enable only for unshipped paid orders; trim fields; generate/preserve `requestId`; disable duplicate clicks while pending. |

Admin fulfillment model rules:

- Page copy must use `useAppPreferences().t()` and new colors must rely on semantic CSS variables.
- Fulfillment status must display `unshipped` and `shipped`, and fall back to raw unknown backend values.
- Money is integer cents and display uses `formatMoney(cents)`.
- Time filters from `datetime-local` inputs must be normalized to ISO-8601 values before sending.
- API errors must preserve and display backend `code`, `message`, and `traceId`.
- `OPS_COMPENSATION_ADMIN` alone must not show the fulfillment workspace; require `ADMIN` role or `LOGISTICS_FULFILLMENT_ADMIN`.

Required tests:

- Filter payload trimming, `all` omission, blank filter omission, and time normalization.
- Status labels for `unshipped`, `shipped`, and unknown raw values.
- Ship payload trimming and `requestId` trimming.
- Backend error `code/message/traceId` preservation.
- Duplicate ship submit guard.

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
- Fulfillment display uses order detail fields: `fulfillmentStatus`, `carrier`, `trackingNo`, and `shippedAt`.
- `paid` with `fulfillmentStatus=unshipped` displays "待发货" / "Awaiting shipment".
- `shipped` displays carrier and tracking number without calling a tracking API.

Required tests:

- Order detail load from `orderId`.
- Payment refresh by `paymentNo`.
- Duplicate cancel click guard.
- API errors preserve `code`, `message`, and `traceId`.

## Mall Cart Draft MVP

Frontend cart is a local draft only. It must not introduce a backend cart table or a new cart API for the MVP.

Storage contract:

- File pattern: `frontend/src/composables/useMallCart.ts` plus pure model helpers under `frontend/src/views/mall/`.
- Storage API: `window.localStorage`.
- Key shape: `sangui.mall.cart.v1:{shopId}:{userId}`.
- The key must be rebuilt from the current mall session after login/bootstrap and cleared from memory on sign-out.
- Cart data for a different `{shopId,userId}` must not be loaded into the current session.

Persisted item fields:

```json
{
  "version": 1,
  "shopId": 1,
  "userId": "10001",
  "items": [
    {
      "shopId": 1,
      "userId": "10001",
      "productId": 301,
      "productName": "Daily trainer",
      "skuId": 401,
      "skuName": "42",
      "priceCent": 59900,
      "availableStock": 2,
      "quantity": 1,
      "addedAt": "2026-05-06T10:00:00.000Z",
      "updatedAt": "2026-05-06T10:00:00.000Z"
    }
  ]
}
```

Rules:

- `priceCent` and `availableStock` are display snapshots only. Final price and stock validity come from `POST /api/orders`.
- Cart checkout must build the normal `CreateOrderRequest` with `shopId`, `userId`, `requestId`, and `items[]` containing only `skuId` and `quantity`.
- Quantity controls clamp to positive integers and keep an upper UI bound from the local stock snapshot when available, but backend order creation remains the final validation point.
- Checkout must disable duplicate submit while pending and preserve the same `requestId` until cart contents change or checkout succeeds.
- Successful checkout clears only submitted SKU items from the current cart.
- Failed checkout must keep cart items and show backend `code`, `message`, and `traceId`.
- Shared order result UI may create mock payment for any current `created` order and must preserve the generated `paymentNo` across payment retry attempts.

Validation and error matrix:

| Case | Frontend behavior |
| --- | --- |
| No mall session | Add/checkout blocked with sign-in guidance. |
| Different `shopId/userId` | Load an empty cart for the new session key. |
| Duplicate SKU added | Merge quantities into one cart line. |
| Quantity below 1 | Clamp to 1. |
| Checkout duplicate click | Return no second request while pending. |
| `ORDER_STOCK_NOT_ENOUGH` or unavailable SKU | Keep cart items and display backend error plus `traceId`. |

Required tests:

- Add item, merge duplicate SKU, remove item, clear cart.
- Quantity lower and upper boundaries.
- `localStorage` persistence and `{shopId,userId}` isolation.
- Multi-item `CreateOrderRequest` payload shape.
- Duplicate checkout guard.
- Checkout failure preserves items and displays `traceId`.
- Shared result payment submit preserves `paymentNo` for retry.

## Public Environment Configuration

- `VITE_API_BASE_URL`: optional gateway origin prefix; empty means same origin.
- `VITE_DEFAULT_SHOP_ID`: optional dashboard default `shopId`.
- `VITE_KIBANA_DISCOVER_URL`: optional absolute Kibana Discover URL for compensation ops audit search links.
- `VITE_LOKI_EXPLORE_URL`: optional absolute Grafana/Loki Explore URL for compensation ops audit search links.
- Observability URLs are public client configuration and must not contain credentials, API keys, or secrets.
- Missing or invalid observability URLs must degrade to copy-only query templates.
