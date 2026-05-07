# 用户侧支付创建失败与支付恢复体验补强

## Goal

Strengthen the customer checkout continuation after an order is created: created order -> mock payment -> payment failure and retry -> payment success syncing order detail, list, filters, and URL state.

## Scope

- Frontend mall customer order and payment experience.
- Pure model/composable tests around payment retry, recovery, duplicate guard, and order state sync.
- Frontend API spec sync for discovered cart `requestId` lifecycle details.

Out of scope:

- Backend payment state machine changes.
- Refunds, after-sales, real payment provider integration, or compensation job changes.
- New backend database tables, Redis keys, MQ events, or API endpoints.

## Requirements

- Classify payment creation failures into auth expired, order not payable, duplicate payment conflict, backend validation failure, system/downstream failure, and unknown failure.
- Preserve backend `code`, `message`, and `traceId` in payment failure UI/model state.
- Preserve the same `paymentNo` after a failed payment attempt so retry uses the same idempotency key.
- Generate a new `paymentNo` only when switching the selected order, after payment succeeds, or after a new order is created.
- Block duplicate payment submit while the first submit is pending; the second call must not send an API request.
- Allow retry after failure with the same `paymentNo`.
- After payment succeeds, immediately sync the selected order detail, visible list item, and active status/filter model to `paid` / awaiting shipment.
- Keep `orderId` and `paymentNo` in URL state after payment success so refresh/deep-link recovery can explain payment status source.
- Allow manual payment status refresh only for orders with a known `paymentNo`.
- For historical orders without `paymentNo`, display only an order snapshot-derived payment summary and do not fabricate a `PaymentResponse`.
- If payment status refresh fails, keep the current order detail and surface backend error details.
- Update `.trellis/spec/frontend/api-contracts.md` to document cart `requestId` lifecycle: session restore may create one request id, cart content changes regenerate it, failed checkout keeps it, and successful checkout clears/regenerates for the next checkout.

## Acceptance Criteria

- [x] Payment failure classification covers auth, not payable, duplicate/idempotency conflict, validation, system/downstream, and unknown errors.
- [x] Failed payment keeps `paymentNo` and `traceId` visible/available for retry.
- [x] Duplicate pending payment submit is guarded without issuing a second request.
- [x] Retrying after failure uses the same `paymentNo`.
- [x] Successful payment updates current detail/list/filter-visible status to paid and awaiting shipment.
- [x] URL state preserves `orderId` and `paymentNo` after payment success.
- [x] Manual refresh with `paymentNo` merges payment response into current order state.
- [x] Manual refresh failure preserves the current order detail.
- [x] Historical order without `paymentNo` renders a snapshot payment summary without a fake `PaymentResponse`.
- [x] Tests cover the required retry, guard, sync, refresh failure, and historical snapshot boundaries.
- [x] Relevant frontend API spec is updated with cart `requestId` lifecycle details.

## Technical Notes

- Gateway payment routes remain `POST /api/payments` and `GET /api/payments/{paymentNo}` through `services/paymentApi.ts` with `authContext: 'mall'`.
- `paymentNo` is the frontend-provided idempotency key for mock payment initiation.
- The UI may derive a payment summary from order status when `paymentNo` is absent, but must not store that as a real `PaymentResponse`.
- Backend error detail must remain unlocalized business data; frontend fallback guidance may use typed translations.
