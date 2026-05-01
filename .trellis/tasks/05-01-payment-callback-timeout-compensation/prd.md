# Payment Callback Timeout Compensation MVP

## Goal

Build the first compensation loop for payment interruptions, duplicate callbacks, and unpaid order timeout so order, payment, and inventory state converge even when events arrive late or out of order.

## Scope

- Payment service owns payment result intake and persistence:
  - Add callback handling that records every provider notification in `pay_callback_log`.
  - Add an internal/mock async result path so tests can drive success and failure without a real payment provider.
  - Keep payment settlement idempotent by `shopId`, `paymentNo`, and provider callback identity.
- Order service owns unpaid timeout cancellation:
  - Add a service operation that cancels `created` orders whose payment deadline has expired.
  - Cancellation must release the stored inventory `reservationNo`.
  - Repeated cancellation must be safe and return stable state.
- Inventory service continues to own stock reservation lifecycle:
  - Reuse existing `release` and `confirm` APIs.
  - Do not write inventory tables from order or payment service.
- Specs must capture the compensation matrix across payment, order, and inventory.

## Out Of Scope

- Real third-party payment provider signature verification.
- MQ infrastructure and delayed message deployment.
- User-facing frontend polling screens.
- Distributed transaction framework.

## Contracts And Idempotency

- Payment callback request fields:
  - `shopId`, `paymentNo`, `provider`, `providerTradeNo`, `eventType`, `tradeStatus`, `paidAmount`, `eventTime`, `rawPayload`.
- Callback uniqueness:
  - Prefer unique provider event id if existing schema supports it; otherwise use deterministic callback key from provider, provider trade number, status, and event time.
- Payment success flow:
  - Mark payment as paid only once.
  - Confirm order payment through order-service internal contract.
  - Confirm inventory reservation through existing inventory contract when order is still payable.
- Payment failure/closed flow:
  - Record callback.
  - Mark payment terminal failure only when current payment is not already paid.
  - Do not release inventory directly from payment unless the order cancellation path owns that state transition.
- Timeout cancel flow:
  - Select expired `created` orders.
  - Transition to canceled once.
  - Release inventory reservation once.
  - Late success callback after cancel must not revive the order or confirm inventory.

## Compensation Matrix

| Scenario | Expected Result |
| --- | --- |
| Duplicate success callback | One callback log per unique provider event, one payment settlement, one order paid transition, one inventory confirm. |
| Duplicate timeout cancel | First call cancels and releases reservation; later calls return canceled/no-op. |
| Timeout cancel before success callback | Order remains canceled; inventory remains released; callback is logged and payment must not create a paid order. |
| Success callback before timeout cancel | Order becomes paid; timeout cancel skips paid order; inventory remains confirmed. |
| Payment failure callback before timeout | Payment records failure; order can still timeout-cancel and release reservation. |
| Unknown payment callback | Callback is logged if safe, but settlement fails with mapped business error and no order/inventory mutation. |

## Acceptance Criteria

- [ ] Payment service exposes a callback/mock async entry point and persists callback logs.
- [ ] Callback replay is idempotent and tested.
- [ ] Order service can cancel expired unpaid orders and release inventory.
- [ ] Timeout cancellation replay is idempotent and tested.
- [ ] Tests cover duplicate callback, duplicate cancel, cancel-before-callback, and callback-before-cancel.
- [ ] Backend specs document payment/order/inventory compensation contracts and matrices with concrete fields and tests.
- [ ] Targeted Maven tests for touched services pass.

## Technical Notes

- Prefer synchronous service methods and HTTP/internal controller endpoints for this MVP; keep the design compatible with future MQ delayed cancellation.
- Reuse existing Result envelopes, `SanguiPrincipal`/internal scope patterns, Flyway migrations, repositories, and Feign clients.
- If schema changes are required, add Flyway migrations in the owning service and update executable backend specs.
