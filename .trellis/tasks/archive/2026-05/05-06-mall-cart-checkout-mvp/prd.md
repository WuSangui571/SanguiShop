# 商城端购物车与多商品结算 MVP

## Goal

把商城端从“商品详情直接购买单个 SKU”推进到“加入本地购物车、调整数量、合并结算、多商品订单结果可恢复”的 MVP 流程。后端 `POST /api/orders` 已支持多 items，本任务优先复用既有订单创建、支付、订单恢复契约，不新增购物车后端表。

## Scope

- Frontend primary: local cart model/composable, product detail actions, cart UI, multi-item checkout payload, pending guard, persistence and error display.
- Backend narrow verification: confirm order creation multi-item path remains covered; only if product list stock summary blocks MVP display, add a narrow response field or keep add-to-cart constrained to detail-level SKU data.
- No backend cart table, no cross-device cart sync, no final-price trust from local snapshots.

## Requirements

- Cart draft stores only client snapshot fields such as `shopId`, `userId`, `productId`, `skuId`, `skuName`, `priceCent`, `quantity`, optional product display metadata, and timestamps.
- Cart storage key must be isolated by at least `shopId` and current user identity to avoid login-switch leakage.
- Cart supports add item, remove item, quantity stepper, clear cart, and persistence reload.
- Quantity must have clear lower and upper boundaries; invalid values must not create invalid order payloads.
- Product detail exposes both “Add to cart” and “Buy now”. Buy now keeps the existing direct checkout path.
- Cart checkout builds `CreateOrderRequest.items[]` with multiple SKU lines and reuses existing order result/payment status UI.
- Checkout pending state prevents duplicate submit.
- On successful order creation, clear only the submitted cart items.
- On backend order creation failure, surface backend message and `traceId`.
- MVP must treat local price/stock as display draft only; backend order creation result remains the source of final order amount and validity.

## Acceptance Criteria

- [ ] User can log in, add two SKUs to cart, adjust quantities, remove an item, and clear cart.
- [ ] Cart survives refresh for the same `shopId/userId` and does not leak across login identity changes.
- [ ] Multi-item checkout sends a `CreateOrderRequest` containing all selected cart items.
- [ ] Duplicate checkout clicks while pending do not submit a second order request.
- [ ] Order creation success clears submitted items and shows the existing order/payment result area.
- [ ] Backend validation failures such as insufficient stock or unavailable SKU display message plus `traceId`.
- [ ] Existing “Buy now” single-item flow still works.
- [ ] Existing order/payment recovery after refresh still works.

## Test Plan

- Frontend unit/model tests:
  - add item merge behavior
  - quantity boundaries
  - remove and clear
  - localStorage key isolation and reload
  - multi-item order payload
  - duplicate checkout guard
  - traceId error presentation
- Backend tests:
  - reuse existing order create tests if multi-item coverage exists
  - add a focused multi-item order create test only if coverage is missing
- Manual acceptance:
  - login
  - add two SKUs to cart
  - adjust quantity
  - checkout
  - mock pay
  - refresh and recover order/payment state

## Implementation Plan

1. Read relevant frontend/backend specs and shared cross-layer guide.
2. Research existing mall checkout/session/order patterns and current product DTO shape.
3. Implement a cart model/composable consistent with current composable style.
4. Wire product detail actions and cart UI into the mall storefront.
5. Reuse existing order creation/payment state handling for cart checkout.
6. Add focused frontend tests and backend multi-item test only if needed.
7. Run `$check` quality pass, fix issues, then run `$finish-work` verification commands.

## Contract Notes

- No new API endpoint is planned.
- Existing order create payload remains:
  - `POST /api/orders`
  - `items[]`
  - each item carries `productId`, `skuId`, `quantity`
- Local cart payload must not include fields that backend treats as authoritative price.
- Error display must use the existing Result/error envelope handling and preserve `traceId`.
