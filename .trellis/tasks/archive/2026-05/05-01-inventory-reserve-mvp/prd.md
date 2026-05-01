# Inventory Reserve MVP

## Goal
Close the inventory gap in the current commerce flow so order creation and payment no longer succeed without enforceable SKU stock constraints. This MVP should move the system from "can complete a transaction" to "can complete a transaction without overselling".

## Requirements
- Add sellable inventory fields and a minimum reserve/confirm/release contract in the inventory owner service.
- Validate and reserve inventory during order creation before the order is persisted as a payable order.
- Confirm reserved inventory after payment succeeds.
- Release reserved inventory when payment fails or the order/payment is canceled for this MVP-supported paths.
- Keep all inventory operations idempotent across repeated order creation, repeated payment, and repeated release/confirm calls.
- Return stable business errors for insufficient inventory and invalid inventory state transitions.
- Cover reserve idempotency, duplicate payment confirmation, and insufficient stock with automated tests.
- Update backend specs for the inventory/order/payment cross-service contract.

## Acceptance Criteria
- [ ] SKU inventory persistence includes fields that can express available and reserved inventory for sellable stock.
- [ ] Order create path refuses to create a payable order when requested quantity exceeds sellable inventory.
- [ ] Repeating the same order-create idempotency key does not reserve inventory twice.
- [ ] Payment success confirms inventory exactly once even if the payment path is retried.
- [ ] Payment duplicate requests do not cause double-confirmation or negative available inventory.
- [ ] Failing or canceled payment paths supported by this MVP release the prior reservation exactly once.
- [ ] Insufficient inventory returns a business conflict response rather than a generic 500.
- [ ] Specs describe request fields, response fields, validation rules, idempotency keys, error codes, and required tests for the cross-service flow.

## Technical Notes
- This is a backend cross-layer task spanning at least `product-service`, `order-service`, and `payment-service`.
- Keep service ownership explicit: inventory data must stay behind a service contract rather than cross-service table access.
- Prefer synchronous internal HTTP contracts for this MVP to stay consistent with the current order/payment implementation style unless research shows an existing stronger pattern.
- Reserve `shopId` in all storage and contracts; do not hardcode merchant-specific assumptions.
