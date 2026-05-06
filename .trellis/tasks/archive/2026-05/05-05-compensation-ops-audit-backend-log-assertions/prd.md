# Compensation Ops Audit Backend Log Assertions

## Goal

Strengthen backend tests so protected compensation ops query/action denied and failed paths are proven to emit searchable unified `Ops audit event.` log lines.

## Requirements

- Cover protected order and payment compensation query denial with `AUTH_FORBIDDEN`.
- Cover protected order and payment compensation action business failure paths where controllers convert service failures to `outcome=failed`.
- Assert audit log fields required by the compensation ops audit contract:
  - `traceId`
  - `shopId`
  - `userId`
  - `permission=OPS_COMPENSATION_ADMIN`
  - `outcome=denied` or `outcome=failed`
  - `errorCode`
  - `path`
  - `method`
- Keep changes scoped to backend tests unless production code is missing required fields.
- Do not change API, DB, MQ, Redis, or frontend contracts.

## Acceptance Criteria

- [ ] Order compensation controller tests assert denied query audit fields.
- [ ] Payment compensation controller tests assert denied query audit fields.
- [ ] Order compensation controller tests assert failed action audit fields.
- [ ] Payment compensation controller tests assert failed action audit fields.
- [ ] Order bulk timeout replay failed path asserts `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.
- [ ] Payment bulk reconcile failed path asserts `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.
- [ ] Affected Maven tests pass.
- [ ] `$check` finds no remaining guideline issues.

## Technical Notes

- Relevant service owners: `order` and `payment`.
- Relevant protected permission: `OPS_COMPENSATION_ADMIN`.
- Relevant audit actions:
  - `ops.order.compensation.query`
  - `ops.order.timeout-replay.manual`
  - `ops.order.timeout-replay.bulk`
  - `ops.payment.compensation.query`
  - `ops.payment.reconcile.manual`
  - `ops.payment.reconcile.bulk`
- Expected failure code should be the sanitized `SanguiException` code when available.
