# 商品详情评价摘要增强

## Goal

Enhance product detail public review readability and filtering by adding backend-owned rating summary data, image-only filtering, and a steadier frontend review browsing experience without touching order lifecycle, payment, inventory, refund, or logistics state machines.

## Scope

- Backend public review read model across order-service internal projection and product-service public API.
- Product detail frontend review summary, image-only filter, pagination state, and failure isolation.
- Order review submission refresh hint/refresh wiring when an already-open product detail matches the reviewed product.
- Contract docs and focused tests for backend, product-service forwarding, frontend formatting, payloads, and error preservation.

## Requirements

- `GET /api/products/{productId}/reviews` returns `ratingDistribution` for stars `1..5`, total visible review count, and average rating based only on visible reviews.
- Public review query supports an image-only filter parameter. Preferred public parameter name is `withImages`; backend may normalize an alias only if existing conventions require it.
- Hidden reviews are excluded from both summary and list.
- Visible reviews with hidden merchant replies remain visible, but `merchantReply` is omitted.
- Public payload must not expose `userId`, `orderId`, `requestId`, `traceId`, or `operator`.
- order-service remains source owner for review data. product-service must continue using internal projection and must not read `oms_*` review tables directly.
- Frontend product detail shows average rating, total count, five-star distribution percentages, and a "只看有图" toggle.
- Pagination controls should show current page/total summary, keep disabled states correct, and avoid major layout jump during loading.
- Review loading errors must not block SKU, cart, or buy-now interactions.
- Merchant replies continue rendering under visible review items.
- After review submission, if the current product detail matches the reviewed order item, refresh the public reviews or show a refresh affordance. The order-detail state update must remain independent from product review loading.
- Frontend error handling preserves backend `code`, `message`, and `traceId`.

## Acceptance Criteria

- [ ] Backend order-service internal projection supports image-only filtering and visible-review rating distribution.
- [ ] product-service public API forwards image-only filter and returns the extended public response shape.
- [ ] Public summary excludes hidden reviews and includes visible reviews regardless of reply visibility.
- [ ] Product detail UI renders summary, distribution bars, image-only toggle, stable pagination, empty state, loading state, and retryable error state.
- [ ] Review submission path refreshes or prompts refresh for matching open product detail without blocking order detail status updates.
- [ ] Specs are updated in backend order/product contracts and frontend API contracts with concrete fields, pagination/filter semantics, empty-result behavior, and tests.
- [ ] Backend and frontend focused tests cover distribution, image-only filter, hidden review/reply boundaries, DTO shape, formatting, pagination state, and error preservation.

## Technical Plan

1. Read applicable Trellis specs and existing review/query implementation.
2. Extend order-service projection query/request/response DTOs with `withImages` and `ratingDistribution`.
3. Update repository query logic and service tests for visible-only summary and image filtering.
4. Extend product-service client/controller/service DTOs and tests to forward `withImages` and expose the new public shape.
5. Update frontend API types and product detail review model/UI to render summary, toggle with-images filtering, and preserve pagination/error behavior.
6. Wire review submission completion to refresh matching product detail reviews or present a refresh prompt without coupling order detail state to product review loading.
7. Sync `.trellis/spec/backend/order-create-contracts.md`, `.trellis/spec/backend/product-catalog-contracts.md`, and `.trellis/spec/frontend/api-contracts.md`.
8. Run `$check` quality pass, fix findings, then run focused backend/frontend tests and provide final test/git commands.

## Non-Goals

- AI review summarization.
- Refund, after-sales, payment reversal, inventory compensation, logistics, or order state-machine changes.
- Direct product-service database access to order review tables.
- Exposing internal audit identifiers in public review payloads.
