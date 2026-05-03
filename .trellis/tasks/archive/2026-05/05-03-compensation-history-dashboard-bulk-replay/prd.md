# Compensation History / Dashboard / Bulk Replay

## Goal

Extend the existing compensation ops surface so compensation is not only runnable, but also auditable, observable, and safe to operate at scale.

## Current State

The project already has:

- Payment callback timeout compensation MVP
- Scheduled order timeout compensation and payment reconcile
- Compensation metrics and structured logs
- Internal query APIs for compensation records
- Single-record manual replay APIs for order timeout and payment reconcile
- Latest-attempt compensation metadata persisted on `oms_order` and `pay_payment_order`

The main gaps now are:

- Only the latest compensation metadata is persisted on business rows
- Dashboard and alert guidance exists in specs, but Grafana / alert rule artifacts are not yet encoded in repo
- Operators can replay a single record, but not perform bounded bulk replay with dry-run and safety rails

## Scope

- Backend only
- Services: `sangui-order-service`, `sangui-payment-service`
- Deploy / observability assets in repo as needed for Grafana / alert rules
- Backend spec sync is required because this task changes persistence, internal API contracts, and operations playbooks

## Requirements

- Add compensation attempt history persistence for both order timeout and payment reconcile flows
- Record every manual and scheduler attempt with at least:
  - business target (`shopId`, order/payment identifiers)
  - trigger (`manual` / `scheduler`)
  - result
  - stable error code or skip code
  - sanitized reason
  - `traceId`
  - operator identity when present
  - attempt timestamp
- Keep latest-compensation columns on business rows as the query fast-path, while also appending immutable attempt history
- Add bulk replay APIs for order timeout and payment reconcile
- Bulk replay must support:
  - explicit filters based on the existing compensation query dimensions
  - `dryRun`
  - `limit`
  - bounded maximum limit
  - deterministic per-item result reporting
  - idempotent business execution by reusing the existing single-record compensation path
- Prevent unsafe bulk replay behavior:
  - no unbounded replay
  - no replay without explicit filter scope
  - dry-run must not mutate business rows or history rows
  - replayed items that are no longer eligible must report `skipped`
- Land compensation dashboard / alert artifacts in repo for existing metrics family and new bulk/history signals where useful
- Update specs with executable contracts, validation, error matrix, and required tests

## Acceptance Criteria

- [ ] Every scheduler and manual compensation attempt appends one history record in the owning service database
- [ ] Existing latest-compensation columns continue to reflect the latest attempt on the business row
- [ ] History records include result, reason, traceId, trigger, and operator fields with sanitized values
- [ ] Bulk payment reconcile supports dry-run and bounded replay with deterministic per-item results
- [ ] Bulk order timeout replay supports dry-run and bounded replay with deterministic per-item results
- [ ] Bulk replay reuses the existing compensation execution path and does not invent a second state machine
- [ ] Bulk replay requires explicit scope and respects maximum limits / idempotency protections
- [ ] Repo contains concrete Grafana / alert rule artifacts or equivalent executable observability config for compensation operations
- [ ] Targeted tests cover migrations, service logic, controllers, and scheduler/manual history persistence
- [ ] Backend specs document schemas, API payloads, alert logic, and test commands

## Non-Goals

- No gateway or admin UI in this task
- No new MQ orchestration layer
- No replacement of existing latest-compensation metadata columns

## Technical Notes

- Prefer separate attempt history tables over widening business rows further.
- Keep business-row latest metadata as the operational query surface for fast inspection.
- History tables should be append-only from the application perspective.
- Operator identity should be nullable for scheduler-triggered attempts and required for manual bulk/single replay APIs when the current internal API contract can carry it safely.
- Bulk replay responses should summarize:
  - matched count
  - executable count
  - skipped count
  - failed count
  - preview sample or item list within the bounded request limit
- Dashboard / alert assets should build on the current `sangui.compensation.job.run.total` and `sangui.compensation.job.item.total` metrics family instead of introducing high-cardinality tags.
