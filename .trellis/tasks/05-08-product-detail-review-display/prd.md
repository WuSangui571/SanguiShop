# 商品详情页评价展示与购买反馈沉淀

## Goal

把已完成订单产生的一单一评沉淀为商品详情页可见的只读评价资产，验证评价从订单完成态到商品侧展示的跨页面一致性，并为后续商家评价管理、评价审核、推荐/搜索权重打基础。

## Scope

- Backend: add a product-facing public review query API backed by order-service review/order snapshot data for this MVP.
- Frontend: add product detail review summary and paged review list under the existing mall product detail area.
- Tests: cover query filtering, pagination, empty state, error preservation, and non-blocking product purchase flow.
- Specs: update backend/frontend contracts with executable API shape, validation matrix, and required tests.

## Non-Goals

- No new review submission entry on product detail.
- No merchant review management, reply, hide, audit, or moderation.
- No payment refund, after-sales, inventory compensation, or order state machine changes.
- No product-service direct reads of `oms_*` tables.
- No public exposure of `orderId`, `orderNo`, `requestId`, or `traceId` in product review list items.

## Backend Requirements

- Add public read API: `GET /api/products/{productId}/reviews?page=&size=`.
- API is anonymous read, scoped to default `shopId`.
- Data source only includes reviews whose owning order is `completed`.
- Results are filtered to the requested `productId` by immutable order item snapshot.
- Pagination defaults: `page=1`, `size=10`, cap `size` at `50`.
- Sort order: `created_at DESC, id DESC`.
- Response fields:
  - `averageRating`
  - `reviewCount`
  - `items[]`
  - item fields: `reviewId`, `rating`, `content`, `imageUrls`, `createdAt`, `maskedUserId`, `skuName`
- Public product review items must not include `shopId`, `userId`, `orderId`, `orderNo`, `requestId`, or `traceId`.

## Frontend Requirements

- Product detail loads review data after selecting/loading a product.
- Product purchase area and product review area must be independent; review loading failure must not block SKU selection, cart, or checkout.
- Review area handles `loading`, `empty`, `error`, and `retry`.
- Empty copy displays “暂无评价” or localized equivalent.
- Display rating, content, created time, SKU name, masked user identifier, average rating, and total count.
- Product detail refresh/deep-link restore must reload the selected product’s reviews.
- Returning to product detail after submitting an order review should show the new review after product detail is reopened/refreshed.

## Acceptance Criteria

- [ ] Product with no completed-order reviews shows review summary count `0` and empty state.
- [ ] Product with completed-order reviews shows average rating, count, and paged items ordered newest first.
- [ ] Query only returns reviews for the requested `productId`.
- [ ] Reviews tied to non-`completed` orders are excluded.
- [ ] Public review response does not expose ordinary order identifiers or trace fields.
- [ ] Frontend preserves backend API error `code/message/traceId` for review query failures.
- [ ] Product purchase controls remain usable when review query fails.
- [ ] Rating/time/masked user display is covered by model tests.

## Contract Draft

`GET /api/products/{productId}/reviews?page=1&size=10`

Success code: `PRODUCT_REVIEWS_FETCHED`.

Response data:

```json
{
  "productId": 301,
  "averageRating": 4.5,
  "reviewCount": 2,
  "page": 1,
  "size": 10,
  "items": [
    {
      "reviewId": 9001,
      "rating": 5,
      "content": "Product matched expectations.",
      "imageUrls": [],
      "createdAt": "2026-05-08T10:00:00+08:00",
      "maskedUserId": "10***01",
      "skuName": "Size 42"
    }
  ]
}
```

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| invalid `productId`, `page`, or `size` | 400 | `VALIDATION_FAILED` |
| product has no reviews | 200 | `PRODUCT_REVIEWS_FETCHED` with `reviewCount=0`, `items=[]` |

Good/Base/Bad cases:

- Good: query joins review rows to completed order snapshots and order item product snapshot, scopes by `shop_id`, and sorts by `created_at DESC, id DESC`.
- Good: frontend shows product detail and checkout controls even when review query returns an error.
- Base: API lives in order-service for this MVP while exposing a product-facing route and DTO.
- Bad: product detail page calls order detail/list APIs to infer product reviews.
- Bad: public response exposes `orderNo`, `requestId`, `traceId`, or raw `userId`.

## Implementation Plan

1. Research existing order review persistence, public product routes, gateway assumptions, and mall product detail state.
2. Add backend DTO/domain/service/repository query for product review summary page.
3. Add controller route and tests for filtering, completed-only source, pagination, and public field boundary.
4. Add frontend API types/service and mall review view model helpers.
5. Add product detail UI section with loading/empty/error/retry and localized copy.
6. Add frontend tests for display formatting, error preservation, retry, and purchase flow independence.
7. Update relevant backend/frontend spec files.
8. Run targeted backend/frontend checks and fix `$check` findings.
