# Compensation Scheduler / Reconcile MVP

## Goal
Turn the existing manual compensation entry points into automatically running background jobs so unpaid orders and partially settled payments can converge without human-triggered internal calls.

## Requirements
- Add an order-service scheduler that scans expired `created` orders and reuses the existing timeout cancellation service.
- Add a payment-service scheduler that scans stale `created` payment rows and retries internal settlement for rows that were persisted before downstream confirms completed.
- Add per-service configuration for enable switch, cadence, batch size, and age/timeout thresholds without hardcoding merchant values.
- Emit batch-level logs with enough context to troubleshoot compensation runs without leaking secrets.
- Update backend specs with executable scheduler/reconcile contracts, idempotency, retry rules, validation/error notes, and required tests.

## Acceptance Criteria
- [ ] order-service automatically cancels expired `created` orders when the scheduler is enabled.
- [ ] payment-service automatically retries eligible `created` payment rows when the scheduler is enabled.
- [ ] repeated job runs do not double-release inventory or double-confirm payments/orders.
- [ ] one failing record does not abort the entire batch; logs report scanned/succeeded/skipped/failed counts.
- [ ] relevant backend spec docs are updated with scheduler config, retry semantics, and test coverage expectations.
- [ ] targeted Maven tests pass for touched services.

## Technical Notes
- Keep the MVP synchronous and in-process; do not introduce MQ or a real provider polling integration in this task.
- Reuse existing application services and repository abstractions where possible instead of adding a second compensation path.
- If payment reconcile needs a new query path, add it through the payment repository contract and document any new index requirement in spec.
