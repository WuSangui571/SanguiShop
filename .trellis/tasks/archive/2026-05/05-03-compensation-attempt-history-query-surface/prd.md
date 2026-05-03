# Compensation Attempt History Query Surface

## Goal
Turn the newly added compensation attempt history tables into usable internal ops query surfaces for order and payment compensation flows, so on-call engineers can filter history records, page through them, and drill down from the latest compensation snapshot to each attempt detail.

## Requirements
- Add internal query API support backed by compensation attempt history tables.
- Support filtering by `shopId`, `orderId` or `paymentNo`, `trigger`, `result`, `operator`, `traceId`, and time range.
- Support pagination with deterministic ordering for history queries.
- Extend response payloads with an aggregate view that exposes the latest compensation state together with nested attempt detail records for drill-down.
- Preserve existing manual replay and bulk replay behaviors.
- Add controller, service, and contract tests for the new query surface.

## Acceptance Criteria
- [ ] Order compensation query API can return history-backed data filtered by `shopId`, `orderId`, `trigger`, `result`, `operator`, `traceId`, and time range.
- [ ] Payment compensation query API can return history-backed data filtered by `shopId`, `paymentNo` or `orderId`, `trigger`, `result`, `operator`, `traceId`, and time range.
- [ ] Both APIs return paged results plus an aggregate record that allows operators to inspect latest status and per-attempt detail in one response.
- [ ] Repository and service logic use the attempt history tables instead of only latest metadata columns for the query surface.
- [ ] Controller, service, and migration or contract tests cover request validation, filtering, pagination, aggregate mapping, and API serialization.
- [ ] Relevant backend specs are updated if the new query contract or ops pattern is non-obvious.

## Technical Notes
- Scope is backend-only and spans `sangui-order-service` and `sangui-payment-service`.
- Existing latest compensation metadata on `oms_order` and `pay_payment_order` should remain the fast summary layer; history tables should provide drill-down detail.
- Query contract should stay internal-facing and follow existing compensation ops controller patterns.
- Pagination shape should be consistent across order and payment compensation ops APIs.
