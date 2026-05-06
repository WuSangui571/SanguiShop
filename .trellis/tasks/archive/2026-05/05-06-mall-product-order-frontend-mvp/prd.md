# 商城端商品浏览与下单入口 Frontend MVP

## Goal

Build the first usable mall-side customer flow in the existing Vue 3 frontend: browse products, inspect SKU inventory, create an order with an idempotency key, and enter the mock payment path.

This task is frontend-first. It must consume the existing backend API contracts instead of changing backend behavior or inferring fields from database entities.

## Scope

- Add a mall customer-facing product list view.
- Add a product detail view with SKU selection, stock state, quantity control, and checkout entry.
- Add a lightweight checkout/payment entry panel or view that creates an order and then creates a mock payment.
- Add typed API clients for product, order, and payment contracts.
- Add composable/model tests for Good/Base/Bad cases.

## Existing API Contracts

### Product List

- Method: `GET /api/products`
- Auth: optional / anonymous allowed
- Query:
  - `page`
  - `size`
- Envelope code: `PRODUCT_LISTED`
- Data shape:
  - `pageNo`
  - `pageSize`
  - `total`
  - `items[]`
    - `productId`
    - `productName`
    - `productDescription`
    - `minPriceCent`
    - `maxPriceCent`
    - `status`

Known gap: the current list response does not expose SKU stock summary. The frontend must not issue N+1 detail requests to fake this. List inventory summary is deferred unless a narrow backend contract is added.

### Product Detail

- Method: `GET /api/products/{productId}`
- Auth: optional / anonymous allowed
- Envelope code: `PRODUCT_FETCHED`
- Data shape:
  - `productId`
  - `productName`
  - `productDescription`
  - `status`
  - `skus[]`
    - `skuId`
    - `skuCode`
    - `skuName`
    - `priceCent`
    - `availableStock`
    - `reservedStock`

### Create Order

- Method: `POST /api/orders`
- Auth: required JWT
- Request:
  - `shopId`
  - `userId`
  - `requestId`
  - `items[]`
    - `skuId`
    - `quantity`
- Effective `shopId` and `userId` are owned by trusted principal on the backend. Frontend still sends contract fields when available, but must not treat body values as authority.
- Idempotency key: `requestId`
- Envelope code: `ORDER_CREATED`
- Response:
  - `orderId`
  - `orderNo`
  - `shopId`
  - `userId`
  - `requestId`
  - `status`
  - `totalAmountCent`
  - `items[]`
    - `productId`
    - `skuId`
    - `skuName`
    - `priceCent`
    - `quantity`
    - `lineAmountCent`

### Create Payment

- Method: `POST /api/payments`
- Auth: required JWT
- Request:
  - `shopId`
  - `userId`
  - `orderId`
  - `paymentNo`
  - `channel`
- Request must not include amount; payment amount comes from backend order snapshot.
- Idempotency key: `paymentNo`
- MVP channel: `mock`
- Envelope code: `PAYMENT_PAID`
- Response:
  - `paymentId`
  - `paymentNo`
  - `orderId`
  - `orderNo`
  - `shopId`
  - `userId`
  - `channel`
  - `status`
  - `amountCent`

## Requirements

- Use existing `services/httpClient` instead of component-level `fetch`.
- Extend `httpClient` with GET support while preserving envelope parsing, trace headers, auth injection, and auth failure handling.
- Add `types/api/product.ts`, `types/api/order.ts`, and `types/api/payment.ts` matching backend DTO fields exactly.
- Add `services/productApi.ts`, `services/orderApi.ts`, and `services/paymentApi.ts`.
- Use cents for all internal money values and `formatMoney(cents)` for display.
- Do not compute final payable amount as authority in the frontend; show backend order/payment response amounts after submission.
- Generate stable write identifiers:
  - order `requestId` when entering checkout/submitting order
  - payment `paymentNo` before payment submission
- Disable submit buttons while create-order or create-payment is in flight.
- Surface API errors with code, message, and traceId where available.
- Handle at least these states:
  - product list loading / empty / error
  - product detail loading / missing SKU / out of stock / error
  - checkout submitting / order created / payment paid / failed
  - auth failure for order/payment

## Acceptance Criteria

- [ ] A customer can browse published products from `GET /api/products`.
- [ ] A customer can open a product detail view and choose one available SKU.
- [ ] Quantity controls cannot submit zero/negative quantity or exceed selected SKU available stock.
- [ ] Clicking checkout creates one order with a generated `requestId`.
- [ ] Double-clicking or repeated submit while pending cannot create duplicate in-flight requests.
- [ ] Payment entry creates a mock payment using generated `paymentNo` and backend returned `orderId`.
- [ ] `PRODUCT_STOCK_NOT_ENOUGH`, `ORDER_STOCK_NOT_ENOUGH`, `PRODUCT_SKU_NOT_FOUND`, `AUTH_TOKEN_EXPIRED`, `AUTH_FORBIDDEN`, `IDEMPOTENCY_CONFLICT`, and `DOWNSTREAM_TIMEOUT` are displayed with a user-readable message and traceId if present.
- [ ] Unit tests cover normal detail-to-order flow, SKU out of stock, duplicate submit guard, and backend error traceId display.
- [ ] `npm run typecheck`, `npm run build`, and `npm run test` pass from `frontend/`.

## Out of Scope

- Backend DTO or controller changes.
- Cart persistence beyond the active checkout draft.
- Real payment provider integration.
- Payment callback simulation UI.
- Product recommendation, search ranking, or seckill.
- Admin product creation/editing.

## Implementation Plan

1. Confirm contracts and context.
2. Add API DTO types and product/order/payment service modules.
3. Extend `httpClient` with a typed `getJson` helper and tests.
4. Add mall flow model/composable for product detail checkout and idempotent submit.
5. Add Vue views/components for list, detail, and checkout/payment entry using existing styling patterns while separating admin ops and mall shell state.
6. Add tests for Good/Base/Bad cases.
7. Run `$check`: inspect changed files against frontend/backend specs, run frontend typecheck/build/test, and fix findings.
8. Run `$finish-work`: summarize verification and provide user-facing test and git sync commands.

## Backend Follow-Up Candidate

If list-level stock summary remains required for acceptance, create a narrow backend task to add inventory summary fields to `ProductSummaryResponse`, for example:

- `totalAvailableStock`
- `skuCount`
- optional `hasStock`

That follow-up must update product API contract tests and frontend DTOs together.
