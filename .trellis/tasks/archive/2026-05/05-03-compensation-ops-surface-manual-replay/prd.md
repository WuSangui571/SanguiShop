# Compensation Ops Surface / Manual Replay

## Goal

Provide a minimal operator-facing compensation surface for the existing order timeout and payment reconcile flows so humans can:

- inspect stuck `created` / `failed` payments and timeout / `cancelled` orders
- manually replay one payment reconcile or one order timeout cancellation
- see the latest compensation outcome, failure reason, traceId, and key timestamps
- keep manual runs inside the same metrics and audit logging path as scheduled runs

## Scope

- Backend only
- Services: `sangui-payment-service`, `sangui-order-service`
- Internal APIs only for this slice; no gateway/admin UI/auth work in this task
- Spec/doc sync required because this changes API contracts, DB schema, and observability contracts

## Requirements

- Add internal query API for payment compensation ops:
  - support viewing `created` and `failed` payment rows
  - include latest compensation result metadata and key timestamps
- Add internal query API for order timeout ops:
  - support viewing timeout-candidate `created` orders and `cancelled` orders
  - include latest compensation result metadata and key timestamps
- Add manual single-record replay API for payment reconcile by business key
- Add manual single-record replay API for order timeout cancellation by business key
- Manual replay must reuse the same business settlement/cancel path as existing scheduler logic
- Manual replay must emit the same compensation metrics family and structured audit logs
- Persist latest compensation outcome metadata on order/payment rows so queries can return it

## Acceptance Criteria

- [ ] Operators can query eligible/stuck payment rows and see current status plus latest compensation metadata
- [ ] Operators can query timeout/cancel order rows and see current status plus latest compensation metadata
- [ ] Manual payment reconcile reuses existing settle path and returns a deterministic result: `settled`, `skipped`, or `failed`
- [ ] Manual order timeout replay reuses existing cancel path and returns a deterministic result: `cancelled`, `skipped`, or `failed`
- [ ] Scheduler-triggered and manual-triggered compensation both update persisted latest-compensation metadata
- [ ] Scheduler-triggered and manual-triggered compensation both emit bounded-cardinality metrics and structured logs with traceability fields
- [ ] Backend specs document the new internal endpoints, DB columns, metrics/audit expectations, and test matrix

## Technical Notes

- Prefer internal service endpoints over a management controller in this task to avoid unrelated auth/routing scope.
- Add explicit latest-compensation columns on `oms_order` and `pay_payment_order` instead of introducing a separate history table.
- Keep current scheduler counters intact while extending the same metric family to cover manual runs.
- Query responses should expose `createdAt`, `updatedAt`, and `lastCompensatedAt` so operators can reason about stale rows quickly.
- Manual replay should not force invalid transitions; it should report `skipped` when the record is no longer eligible.
