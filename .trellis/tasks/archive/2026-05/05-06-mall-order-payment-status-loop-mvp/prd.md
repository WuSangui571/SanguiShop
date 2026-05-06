# Mall Order Payment Status Loop MVP

## Goal

Build a recoverable mall order and payment status loop so a logged-in customer can create an order, return to or refresh the page, see what was purchased, inspect payment state, and cancel unpaid orders.

## Scope

- Backend: confirm payment detail API availability, add narrow external order query APIs if missing, and enforce trusted principal ownership.
- Frontend: add typed order query/cancel API calls, order result/detail/list recovery UI, payment status refresh, and invalid-action guards.
- Tests: cover backend ownership and state rules plus frontend loading, payment refresh, cancellation guard, and traceId error display.

## Requirements

- Confirm `GET /api/payments/{paymentNo}` remains available for payment status lookup.
- If absent, add external order APIs:
  - `GET /api/orders/{orderId}`
  - `GET /api/orders?page=&size=`
  - cancellation endpoint should use the existing contract if available; otherwise expose a narrow unpaid-order cancel endpoint.
- Backend must use `SanguiPrincipal` as the trusted source for `shopId` and `userId`; it must not trust request body or query ownership fields.
- Order responses should include `orderId`, `orderNo`, `status`, `totalAmountCent`, `items`, and `createdAt` / `updatedAt` only if supported by existing DTO and persistence model.
- Frontend order API/DTO must mirror backend DTOs and avoid deriving fields from entities.
- Frontend must preserve error display using `code`, `message`, and `traceId`.
- Mall UI must show item snapshots, quantities, order amount, order status, and payment status after checkout.
- Refreshing an order detail URL must reload order/payment state.
- The mall user should be able to recover recent orders through a list view.
- `created` / unpaid orders can be cancelled.
- `paid` / `cancelled` states must disable illegal actions.
- Mock payment success should show the paid result.
- Payment status refresh is manual in this MVP unless existing code already has bounded polling infrastructure.

## Acceptance Criteria

- [ ] Logged-in customer creates an order and sees an order result/detail view.
- [ ] Mock payment succeeds and the UI shows paid payment state.
- [ ] Browser refresh on the order result/detail URL reloads the order state.
- [ ] Customer can open a recent order list and inspect an order.
- [ ] Created/unpaid order can be cancelled once and duplicate cancellation clicks are guarded.
- [ ] Paid order cannot be cancelled from the UI and backend rejects paid cancellation.
- [ ] Backend tests cover owner scope, order not found, unauthorized access, and cancelling paid orders.
- [ ] Frontend tests cover order detail loading, payment refresh, cancel duplicate guard, and traceId error display.

## Contract Depth

### APIs

- `GET /api/orders/{orderId}`
  - Auth: mall/customer JWT resolved to `SanguiPrincipal`.
  - Ownership: `{shopId,userId}` from principal only.
  - Response: result envelope containing order detail DTO.
  - Errors: unauthorized, not found, forbidden/ownership mismatch, validation failure.
- `GET /api/orders?page=&size=`
  - Auth and ownership same as detail.
  - Query: bounded page and size.
  - Response: result envelope containing paged customer order summaries or details depending on existing DTO pattern.
- Cancel endpoint:
  - Reuse existing endpoint if present.
  - Auth and ownership same as detail.
  - Allowed only for unpaid/created orders.
  - Reject paid/cancelled orders.

### Cases

- Good: current principal reads own order and sees item snapshots.
- Base: current principal lists orders with default pagination and empty page support.
- Bad: a different principal cannot read or cancel another user's order; paid order cancellation fails.

## Technical Notes

- No database migration is planned unless existing schema lacks fields required for ownership-safe read contracts.
- Keep single-merchant default support, but business code must still carry `shopId`.
- Frontend should prefer existing mall checkout/session patterns and avoid N+1 product lookups for order item display.
- Payment refresh remains manual for this MVP to keep the result flow deterministic and easy to test.
