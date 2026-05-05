# Compensation Ops Audit Search Export Panel

## Goal

Turn the landed `Ops audit event.` logs into an operator-usable audit search and traceability workflow for compensation operations. Operators should be able to understand the audit fields, copy or run Kibana/Loki queries, search compensation audit records from the ops dashboard by `traceId`, `operator`, `action`, and `outcome`, export the current result set, and jump from replay/reconcile feedback to the matching audit-filtered view.

## Current Project State

- Ops auth is implemented through gateway-backed `POST /api/users/ops/login` and `POST /api/users/ops/session/refresh`.
- Compensation ops access uses the dedicated `OPS_COMPENSATION_ADMIN` permission instead of broad `ADMIN`.
- Order/payment compensation query, manual replay/reconcile, bulk dry-run, and bulk execution surfaces already exist.
- Backend services emit a unified `Ops audit event.` line through `common/sangui-common-web/.../OpsAuditLogger.java`.
- The current frontend dashboard already supports order/payment compensation filters including `traceId` and `operator`, URL state persistence, current-page CSV export, and replay/reconcile feedback with response `traceId`.

## Scope

- Fullstack/cross-layer task.
- Services touched: likely `common-web`, `order-service`, `payment-service`, `frontend`, and repo docs/observability assets.
- No database schema, Redis, MQ, or new backend business state is expected.
- No secrets or deployment-only endpoint addresses should be committed.

## Requirements

1. Define an executable audit-search contract for `Ops audit event.`:
   - Canonical fields: `traceId`, `operator`, `action`, `outcome`, `result`, `shopId`, `userId`, `username`, `permission`, `targetType`, `targetId`, `targetCount`, `dryRun`, `errorCode`, `reason`, `path`, `method`, `ip`, `jwtId`.
   - Supported action values must include auth, query, manual replay/reconcile, and bulk replay/reconcile actions.
   - Query template examples must cover Kibana/Lucene or KQL and Loki LogQL.
   - Templates must include good/base/bad cases and avoid exposing password/JWT values.

2. Add an audit search entry in the ops dashboard:
   - Operators can filter by `traceId`, `operator`, `action`, `outcome`, and optional `shopId`.
   - The UI should show copyable Kibana/Loki query templates generated from the current filters.
   - The UI should make it clear whether filters target structured logs, not the compensation history DB tables.
   - The panel must handle empty, loading, error, and copy states.

3. Add replay/reconcile trace jump behavior:
   - After manual replay/reconcile or bulk replay/reconcile returns, expose a clear "View audit trail" action.
   - The action should populate audit filters using the returned API response `traceId`, the replay operator, expected action, and expected outcome.
   - The flow must not rerun a compensation operation; it only changes audit-search filters and query templates.

4. Preserve existing compensation history behavior:
   - Existing history query filters and current-page CSV export must continue working.
   - Existing auth/session behavior must remain unchanged.
   - Existing order/payment DTO contracts must not be weakened or reverse-engineered from entities.

5. Testing:
   - Add frontend model tests for audit query generation and replay/reconcile trace jump state.
   - Add component/composable tests if UI state logic becomes non-trivial.
   - Add or adjust backend tests only if audit log fields or action values change.

## Acceptance Criteria

- [ ] A documented audit field matrix and Kibana/Loki templates exist in repo docs or backend logging spec.
- [ ] Ops dashboard contains a distinct audit search panel for `traceId`, `operator`, `action`, `outcome`, and `shopId`.
- [ ] Generated Kibana/Loki templates are copyable and reflect the active audit filters.
- [ ] Manual replay/reconcile success or failure feedback includes a "View audit trail" path that fills audit filters with the returned traceId.
- [ ] Bulk replay/reconcile feedback includes the same audit trail path with the bulk action value.
- [ ] Existing compensation record filters, pagination, current-page CSV export, and URL persistence still work.
- [ ] Frontend `npm run typecheck`, `npm run build`, and targeted tests pass.
- [ ] Relevant backend targeted tests or compile pass if backend/spec-backed audit fields are changed.

## Technical Plan

1. Contract/docs first:
   - Update backend logging/spec or a repo docs file with the audit field matrix.
   - Add Kibana KQL/Lucene and Loki LogQL templates for common filters.
   - Include examples for success, denied, failed, manual, bulk, and dry-run cases.

2. Frontend model:
   - Add audit filter state to `compensationDashboardModel.ts`.
   - Generate stable query templates from audit filters.
   - Add search-param persistence for audit filters.
   - Add an action mapping from dashboard view + operation mode to audit action values.

3. Frontend composable:
   - Track audit filters and copied query feedback in `useCompensationDashboard.ts`.
   - Add `viewAuditTrailFromLastAction` or equivalent method that uses last action trace metadata.
   - Keep all HTTP access through existing services; no direct log backend call unless an explicit API exists.

4. Frontend view:
   - Add a dedicated "Audit search" panel near replay controls or action feedback.
   - Show generated Kibana and Loki templates in copyable text areas or code blocks.
   - Add a "View audit trail" button to action feedback when a traceId is available.

5. Verification:
   - Extend `frontend/src/views/admin/compensationDashboardModel.test.ts`.
   - Run targeted frontend tests, typecheck, and build.
   - Run backend targeted tests or compile only if Java code changes.

## Relevant Specs Read

- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/observability-devops.md`
- `.trellis/spec/backend/order-create-contracts.md`
- `.trellis/spec/backend/payment-pay-contracts.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Risks / Open Questions

- There is no in-repo runtime log-query API today. This plan assumes the dashboard generates copyable Kibana/Loki query templates rather than querying Elasticsearch/Loki directly.
- If a real audit search API is required, this becomes a larger infra/API task and needs an additional backend contract for log storage, auth, pagination, and error handling.
- The current `OpsAuditLogger` emits key-value text through SLF4J. Query templates should target both raw message search and structured fields where log collectors parse key-value pairs.
