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

## Admin Seckill Activity Management APIs

Frontend admin seckill activity management must use gateway routes through `services/seckillApi.ts` with `authContext: 'ops'`. This is a frontend-side contract for the admin activity workspace until the backend admin seckill API is implemented.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminSeckillActivities({page,size,status})` | `GET /api/admin/seckill/activities` | `ops` | Show loading, empty, error, retry; omit `status` when filter is `all`. |
| `getAdminSeckillActivity(activityId)` | `GET /api/admin/seckill/activities/{activityId}` | `ops` | Load activity detail and bound SKU snapshot; preserve current detail when a later detail/SKU snapshot load fails. |
| `createAdminSeckillActivity(payload)` | `POST /api/admin/seckill/activities` | `ops` | Build payload from a validated draft; include `requestId`; trim text/time fields; preserve draft and backend errors on failure; disable duplicate submit while pending. |
| `updateAdminSeckillActivity(activityId,payload)` | `PUT /api/admin/seckill/activities/{activityId}` | `ops` | Keep `activityId` in the path; include `requestId`; preserve detail/draft and backend errors on failure; disable duplicate submit while pending. |
| `updateAdminSeckillActivityStatus(activityId,{status,requestId})` | `POST /api/admin/seckill/activities/{activityId}/status` | `ops` | Generate `requestId`; preserve detail and backend errors on failure; disable duplicate status writes while pending. |
| `bindAdminSeckillActivitySku(activityId,{productId,skuId,activityStock,seckillPriceCent,requestId})` | `POST /api/admin/seckill/activities/{activityId}/skus` | `ops` | Generate `requestId`; validate non-negative `activityStock` and `activityStock <= availableStock`; preserve detail and backend errors on failure; disable duplicate SKU writes while pending. |

Admin seckill activity model rules:

- Page copy must use `useAppPreferences().t()` and new colors must rely on semantic CSS variables.
- Activity status must display `draft`, `scheduled`, `active`, and `ended` labels and fall back to the raw unknown backend value.
- Activity status and time display must use backend-provided `status`, `serverTime`, `startsAt`, and `endsAt`; the admin UI must not infer authoritative status from the local clock.
- Activity API errors must preserve and display backend `code`, `message`, and `traceId`.
- `OPS_COMPENSATION_ADMIN` alone must not show the seckill activity workspace; require `ADMIN` role or `SECKILL_ACTIVITY_ADMIN`.
- `shopId` and `userId` draft fields are populated from the persisted ops/admin session for compatibility, but backend principal scope is authoritative; frontend must not hardcode a merchant magic value.
- `requestId` is required for all write payloads including create and update (`AdminSeckillActivityDraftRequest` must include `requestId` field).
- Money is integer cents. `priceCent` and `seckillPriceCent` are display or submitted cents values; final price and stock validity remain backend facts.
- Activity stock inputs are non-negative integers and must not exceed the current SKU `availableStock` snapshot in the UI. Backend `STOCK_NOT_ENOUGH` or `PRODUCT_STOCK_NOT_ENOUGH` still remains authoritative.
- `status=all` must be omitted from list query payloads.

Validation and error matrix:

| Case | Frontend behavior |
| --- | --- |
| No admin session or no seckill access prop | Do not call activity list or write APIs. |
| `OPS_COMPENSATION_ADMIN` only | Do not render the seckill workspace tab or view. |
| List query failure | Show backend `message`, `code`, and `traceId`; do not show empty state. |
| Successful empty list | Show the localized empty state. |
| Unknown status | Render the raw backend status without crashing. |
| Create/update/status/SKU write failure | Preserve draft/detail, restore buttons, and display backend `code/message/traceId`. |
| Duplicate pending write | Do not send a second request. |
| Negative activity stock | Block locally with validation error and do not call API. |
| Activity stock above available stock | Block locally with validation error and do not call API. |

Required tests:

- App workspace permission tests for `ADMIN`, `SECKILL_ACTIVITY_ADMIN`, and `OPS_COMPENSATION_ADMIN` alone.
- Prop gate and missing session prevent list loading.
- List failure, retry, empty success, and `status=all` omission.
- Status labels for `draft`, `scheduled`, `active`, `ended`, and unknown raw values.
- Server time/ISO display uses response fields.
- Create/update/status/SKU failure recovery preserves draft/detail and backend `traceId`.
- Duplicate pending create/update/status/SKU writes send one request.
- Request `requestId` generation for status and SKU write payloads.
- Activity stock validation blocks negative and over-available values.

Good/Base/Bad cases:

- Good: an admin with `SECKILL_ACTIVITY_ADMIN` can use the workspace, failed writes preserve current activity/SKU snapshots, and list failures keep backend trace details visible.
- Good: `status=all` is omitted from list query payloads and unknown status values render as raw text.
- Base: the frontend API contract may be mocked until the backend admin seckill API exists, but the route, payload field names, and required tests stay documented here.
- Bad: `OPS_COMPENSATION_ADMIN` sees the seckill activity workspace.
- Bad: frontend local time is treated as authoritative for activity status.
- Bad: duplicate pending writes send multiple requests or activity stock above available stock reaches the API.

## Admin Order Management APIs

Frontend admin order management must use gateway routes through `services/orderApi.ts` and `services/paymentApi.ts` with `authContext: 'ops'`.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminOrders({page,size,status,orderNo,userId,fromTime,toTime})` | `GET /api/admin/orders` | `ops` | Show loading, empty, error, retry, pagination; supported status filters are `created`, `paid`, `cancelled`, `shipped`, and `completed`; omit `status` when filter is `all`; omit blank text filters. |
| `getAdminOrder(orderId)` | `GET /api/admin/orders/{orderId}` | `ops` | Load order snapshot, item snapshots, `reservationNo`, nullable `paymentNo`, and `traceId`. |
| `cancelAdminOrder(orderId,{requestId})` | `POST /api/admin/orders/{orderId}/cancel` | `ops` | Enable only for `created`; generate `requestId`; disable duplicate clicks while pending. |
| `getAdminPaymentByOrderId(orderId)` | `GET /api/admin/payments/by-order/{orderId}` | `ops` | Refresh payment status by order id; treat `PAYMENT_NOT_FOUND` as no payment row during automatic detail load. |

Admin order model rules:

- Page copy must use `useAppPreferences().t()` and new colors must rely on semantic CSS variables.
- Order status must display `created`, `paid`, `cancelled`, `shipped`, and `completed` labels and fall back to raw unknown backend values.
- Order status timeline descriptions must recognize `created`, `paid`, `cancelled`, `shipped`, and `completed`; unknown timeline statuses must use the localized unknown-status description without dropping the node.
- Admin deep links must support `/admin?workspace=order&orderId={orderId}` and load the selected order detail without requiring a list click.
- Shareable admin order URL params are `workspace=order`, `orderId`, `status`, `orderNo`, `userId`, `from`, `to`, `page`, and `size`; blank text filters and `status=all` must be omitted from request payloads.
- Admin order filter persistence uses `sessionStorage` key `sangui.admin.order.filters.v1` with versioned JSON. Invalid or unavailable storage must fall back to default filters.
- Money is integer cents and display uses `formatMoney(cents)`.
- Time filters from `datetime-local` inputs must be normalized to ISO-8601 values before sending.
- API errors must preserve and display backend `code`, `message`, and `traceId`.
- `paymentNo` in order responses is nullable because order-service must not read payment tables. Payment status is loaded from the payment-service admin route by `orderId`.
- Refreshing admin payment status must write the returned `paymentNo` and `paid` status into the current order detail/list display snapshot while preserving unknown status fallback.
- `OPS_COMPENSATION_ADMIN` alone must not show the order management workspace; require `ADMIN` role or `ORDER_MANAGEMENT_ADMIN`.

Required tests:

- Filter payload trimming, `all` omission, blank filter omission, and time normalization.
- Deep-link `orderId` parsing and persisted filter restore.
- Admin order URL params omit empty filters and preserve page/size.
- Pagination default/clamp behavior.
- Status labels and timeline descriptions for `created`, `paid`, `cancelled`, `shipped`, `completed`, and unknown raw values.
- Payment refresh display snapshot merge for current order detail/list item.
- Backend error `code/message/traceId` preservation.
- Duplicate cancel submit guard.
- Cancel confirmation must prevent accidental cancellation before the request is sent.
- Cancel request `requestId` generation and trimming.

## Admin Review Management APIs

Frontend admin review management must use gateway routes through `services/orderApi.ts` with `authContext: 'ops'`.

| Function | Route | Auth Context | Required UI Handling |
| --- | --- | --- | --- |
| `listAdminReviews({page,size,productId,rating,userId,visibility,fromTime,toTime})` | `GET /api/admin/reviews` | `ops` | Show loading, empty, error, retry, pagination; omit `visibility` when filter is `all`; omit blank text filters. |
| `updateAdminReviewVisibility(reviewId,{visibility,reason,requestId})` | `POST /api/admin/reviews/{reviewId}/visibility` | `ops` | Generate `requestId`; disable duplicate visibility writes while pending; preserve backend errors. |
| `upsertAdminReviewReply(reviewId,{content,requestId})` | `POST /api/admin/reviews/{reviewId}/reply` | `ops` | Trim content; generate `requestId`; disable duplicate reply writes while pending; preserve backend errors. |
| `updateAdminReviewReplyVisibility(reviewId,{visibility,requestId})` | `POST /api/admin/reviews/{reviewId}/reply/visibility` | `ops` | Generate `requestId`; enable only when a reply exists; disable duplicate reply visibility writes while pending. |

Admin review model rules:

- Page copy must use `useAppPreferences().t()` and new colors must rely on semantic CSS variables.
- Visibility values display `visible` and `hidden`, with raw fallback for unknown backend values.
- Time filters from `datetime-local` inputs must be normalized to ISO-8601 values before sending.
- API errors must preserve and display backend `code`, `message`, and `traceId`.
- `OPS_COMPENSATION_ADMIN` alone must not show the review management workspace; require `ADMIN` role or `REVIEW_MANAGEMENT_ADMIN`.
- The UI must not delete or mutate user-authored review content; user review visibility writes and merchant reply writes are the only review management actions in phase 2.
- Admin review items may include `imageUrls` alongside `imageCount`. The model must render public review image URLs as thumbnails, tolerate old/partial payloads where only `imageCount` exists, and show an unknown-image fallback instead of crashing.
- Image load failures must render a non-blocking placeholder with a stable local error code and the failed public URL/review id. Hidden/restore/reply controls must remain usable after a thumbnail fails.
- Merchant reply content is required after trim and limited to 300 characters.
- Reply hidden state is separate from review hidden state: hiding the reply must not hide the review.

Required tests:

- Filter payload trimming, `all` omission, blank filter omission, product/rating normalization, and time normalization.
- Visibility labels for `visible`, `hidden`, and unknown raw values.
- Hide/restore payload trimming and `requestId` trimming.
- Reply payload trimming, reply visibility payload trimming, and reply state labels.
- Admin review image model for URL list, failed-load placeholder state, and unknown count fallback.
- Backend error `code/message/traceId` preservation.
- Duplicate hide/restore/reply submit guard.

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
- When a fulfillment response also displays order main `status`, it must use the admin order main-status labels for `created`, `paid`, `cancelled`, `shipped`, and `completed`; it must not derive the order main label from `fulfillmentStatus`.
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
| `confirmOrderReceipt(orderId,{requestId})` | `POST /api/orders/{orderId}/receipt-confirmations` | `mall` | Enable only for shipped orders, generate `requestId`, guard duplicate clicks, and preserve shipped detail on failure. |
| `createOrderReview(orderId,{requestId,rating,content,imageUrls})` | `POST /api/orders/{orderId}/reviews` | `mall` | Enable only for completed unreviewed orders, generate `requestId`, guard duplicate clicks, and preserve completed detail on failure. |
| `uploadReviewImage(file)` | `POST /api/uploads/review-images` | `mall` | Send `multipart/form-data` part `file`; disable review submit while upload is pending; preserve draft and backend error on failure. |
| `getOrderReview(orderId)` | `GET /api/orders/{orderId}/review` | `mall` | Use only if `OrderResponse.review` is not enough; nullable success means no review yet. |
| `getPayment(paymentNo)` | `GET /api/payments/{paymentNo}` | `mall` | Manual refresh only unless bounded polling with cleanup is explicitly implemented. |
| `listProductReviews(productId,{page,size,withImages})` | `GET /api/products/{productId}/reviews?page=&size=&withImages=` | `none` | Load product reviews independently from product detail, omit false `withImages`, and preserve backend errors. |

Order status display rules:

- `created` means unpaid and cancellable.
- `paid` means payment complete and cancel is disabled.
- `cancelled` means payment actions and cancel are disabled.
- If `paymentNo` is unavailable for a historical order, the UI may derive the payment summary from order status but must not invent a `PaymentResponse`.
- Failed `POST /api/payments` attempts must keep the current `paymentNo`, backend `code/message/traceId`, and a classified retry reason. Retry uses the same `paymentNo` unless the selected order changes, payment succeeds, or a new order is created.
- While `POST /api/payments` is pending, the payment action must reject duplicate clicks without sending a second request.
- A successful payment response with `status=paid` must immediately merge the current order detail and loaded order list item to `status=paid` and `fulfillmentStatus=unshipped` when no newer fulfillment status is available.
- Fulfillment display uses order detail fields: `fulfillmentStatus`, `carrier`, `trackingNo`, and `shippedAt`.
- `paid` with `fulfillmentStatus=unshipped` displays "待发货" / "Awaiting shipment".
- `shipped` displays carrier and tracking number without calling a tracking API.
- `completed` means the user confirmed receipt; payment, cancel, and confirm receipt actions are disabled.
- Successful receipt confirmation merges `status=completed`, `fulfillmentStatus=completed`, and `completedAt` into current detail and loaded list item.
- Failed receipt confirmation must keep the shipped detail/logistics snapshot and display backend `code`, `message`, and `traceId`.
- While receipt confirmation is pending, repeat clicks must be ignored without sending another request.
- Manual order refresh uses `GET /api/orders/{orderId}` as the source of truth for `fulfillmentStatus`, `carrier`, `trackingNo`, and `shippedAt`; it must not call or fabricate a logistics tracking API.
- While order detail refresh is pending, repeat refresh clicks must be ignored without sending another request. Failed refresh keeps the current detail/list snapshot and allows a later retry.
- If a refreshed order moves out of the active order-status filter, the empty state must explain that the current order status changed rather than implying the order disappeared.
- Deep-linked customer orders loaded from `/mall?orderId=...` must explain shipped logistics as an order snapshot and must not require a payment number or tracking API.
- Deep-linked completed customer orders must render completion from the order snapshot and must not show an enabled confirm-receipt action.
- Completed unreviewed customer orders show a review action/form; created, paid/unshipped, shipped, cancelled, and unknown statuses disable review with explicit reason.
- While review submission is pending, repeat clicks must be ignored without sending a second request.
- Successful review submission merges `reviewed=true` and `review` into current detail and loaded list item without moving the order out of the completed main filter.
- Failed review submission must keep the completed detail/order snapshot and display backend `code`, `message`, and `traceId`.
- Deep-linked completed reviewed orders must render the review snapshot and must not show an enabled review submit button.
- Review image upload accepts JPEG, PNG, and WebP files. The frontend must submit only upload response `url` values in `imageUrls`.
- Review image upload failures must preserve order detail, rating/content draft, already uploaded image previews, and backend `code/message/traceId`.
- While any review image upload is pending, review submit must be disabled and no review request may be sent.
- Removing an uploaded preview must remove that URL from the final `createOrderReview` payload.
- Successful review submission must render the submitted image snapshot in order detail and may refresh matching open product reviews asynchronously.

Required tests:

- Order detail load from `orderId`.
- Payment refresh by `paymentNo`.
- Failed payment preserves `paymentNo`, backend `traceId`, and retry classification.
- Duplicate pending payment submit does not send a second request.
- Payment success and payment refresh merge paid/awaiting-shipment state into detail and list.
- Paid awaiting-shipment order refresh merges shipped logistics snapshot into detail/list/filter.
- Order refresh failure keeps the current paid detail/list snapshot.
- Duplicate pending order refresh sends no second request, and failure permits retry.
- Shipped logistics placeholders cover missing `carrier`, `trackingNo`, and `shippedAt`.
- Shipped receipt confirmation succeeds and moves detail/list/filter to completed.
- Receipt confirmation failure preserves shipped detail and backend trace.
- Duplicate pending receipt confirmation sends no second request.
- Deep-linked completed order displays completed snapshot and disables receipt action.
- Completed unreviewed order review submission succeeds and updates detail/list state to reviewed.
- Review image upload payload uses `multipart/form-data` and does not override the browser-generated content type boundary.
- Review image delete preview removes that URL from submitted `imageUrls`.
- Review image upload failure preserves the completed order detail, rating/content draft, uploaded previews, and backend trace.
- Completed unreviewed order review submission carries uploaded `imageUrls`.
- Duplicate pending review submission sends no second request.
- Review failure preserves completed detail and backend trace.
- Deep-linked completed reviewed order displays review snapshot and disables review action.
- Unknown fulfillment status falls back to the raw backend value.
- Active filter empty state distinguishes status movement after refresh.
- Duplicate cancel click guard.
- API errors preserve `code`, `message`, and `traceId`.

Mall product review display rules:

- Product detail review data uses `services/productApi.ts` and `types/api/product.ts`; page components must not call order APIs to derive product reviews.
- Product review loading has independent `loading`, `empty`, `error`, and `retry` states.
- Product review query failure must not disable SKU selection, add-to-cart, buy-now, or existing checkout controls.
- Empty review response displays the localized no-review empty state.
- Product review response summary comes from the backend: `averageRating`, `reviewCount`, `ratingDistribution`, `page`, and `size`; the frontend must not fabricate public summary counts from the current page items.
- `ratingDistribution` is displayed as five rows for ratings `5..1`; missing keys are treated as zero only for defensive rendering.
- The "with images" toggle sends `withImages=true`, resets to page `1`, and keeps summary/list/pagination aligned with the backend filtered response. False is omitted from the query payload.
- Review item display includes rating, content, `createdAt`, `skuName`, backend-provided `maskedUserId`, and optional `merchantReply`.
- Review item image URLs may be shown as public review thumbnails, but the frontend must not infer image-only filtering locally.
- Product review item must not display backend reply audit fields such as operator, request id, or trace id.
- Product review API errors must preserve backend `code`, `message`, and `traceId`.
- Successful customer review submission may refresh matching open product detail reviews asynchronously when the selected product appears in the reviewed order items; this refresh must not block order detail/list state updates.

Required tests:

- Product review summary, rating distribution, pagination summary, image URLs, and item formatting for rating, time, SKU name, masked user, and optional merchant reply.
- Product review query payload includes `withImages=true` only when the image-only filter is active.
- Optional merchant reply absence handling.
- Product with no reviews renders empty state.
- Product review API error preservation through the shared mall API error formatter.

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
- Cart restore for a signed-in `{shopId,userId}` may initialize a new checkout `requestId`; any cart content change after restore regenerates it for the next checkout attempt.
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
