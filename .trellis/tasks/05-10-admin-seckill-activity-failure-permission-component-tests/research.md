# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/directory-structure.md`: admin views belong under `frontend/src/views/admin`, business API wrappers under `frontend/src/services`, API DTOs under `frontend/src/types/api`.
- `.trellis/spec/frontend/component-guidelines.md`: async components must handle idle/loading/success/empty/error/retrying; local pending gates that drive disabled/loading UI must be reactive.
- `.trellis/spec/frontend/hook-guidelines.md`: data fetching belongs in composables/services, retry states are required, timers/polling must clean up if introduced.
- `.trellis/spec/frontend/state-management.md`: server facts such as stock, payment/order/seckill status come from backend/server time; backend `code/message/traceId` must not be translated away.
- `.trellis/spec/frontend/type-safety.md`: API time fields are strings; enum-like status values require unknown fallback; avoid `any`.
- `.trellis/spec/frontend/api-contracts.md`: admin product/order/review/fulfillment management patterns define the expected behavior for `authContext: 'ops'`, empty/error/retry, `status=all` omission, requestId generation, trace preservation, and `OPS_COMPENSATION_ADMIN` negative permission tests.
- `.trellis/spec/frontend/seckill-ui-guidelines.md`: seckill UI must use server time, handle failed/retry/queued/final states, and guard duplicate submits.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend DoD requires targeted component tests, typecheck, build, API error handling, and duplicate-submit guards for seckill-like critical buttons.
- `.trellis/spec/backend/microservice-contracts.md`: REST envelope includes `code/message/data/traceId/timestamp`; write operations require idempotency keys; seckill order uses `activityId + userId`.
- `.trellis/spec/backend/gateway-security.md`: `/admin/**` requires admin RBAC; `/api/seckill/**` requires JWT + seckill token + rate limiting for public purchase flow.
- `.trellis/spec/backend/seckill-contracts.md`: seckill activity/order contracts define `activityId`, `skuId`, server state, idempotency, and known error codes such as `SECKILL_ACTIVITY_NOT_FOUND`, `SECKILL_NOT_STARTED`, `SECKILL_ENDED`, `SECKILL_DUPLICATE_BUY`, `SECKILL_QUEUE_BUSY`.
- `.trellis/spec/backend/error-handling.md`: frontend-facing failures must preserve traceId and not leak internal details.
- `.trellis/spec/backend/inventory-reserve-contracts.md`: product-service owns SKU stock; admin SKU stock fields include `availableStock` and `reservedStock`; activity stock must not pretend to be final inventory truth.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: watch cross-layer naming, shopId, idempotency, retry behavior, and local-clock vs server-time drift.
- `.trellis/spec/guides/seckill-thinking-guide.md`: define activity statuses, use server time, prove duplicate handling, and do not rely only on frontend disabled buttons.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search existing helpers/patterns before adding common abstractions; keep business rules in the owning domain.
- `.trellis/spec/guides/architecture-review-checklist.md`: if implementation expands to backend, check service boundary, API/data contracts, high-concurrency risks, RBAC, and Good/Base/Bad tests.

## Code Patterns Found

- `frontend/src/App.vue`: admin shell owns workspace permission gates and active workspace URL parsing. Existing workspace permissions are constants near the top, e.g. `PRODUCT_CATALOG_ADMIN`, `ORDER_MANAGEMENT_ADMIN`, `REVIEW_MANAGEMENT_ADMIN`, `LOGISTICS_FULFILLMENT_ADMIN`, and `OPS_COMPENSATION_ADMIN`. Add activity workspace here if the implementation introduces the view.
- `frontend/src/App.spec.ts`: shallow-mounted permission tests use `mockSessionRef`, `adminSession()`, and `/admin?workspace=<key>` to assert role/permission visibility and `OPS_COMPENSATION_ADMIN` negative boundaries. Add activity workspace tests in the same style.
- `frontend/src/views/admin/ProductManagementView.spec.ts`: closest pattern for admin catalog-adjacent tests: prop/session gate, list error/retry/empty, unknown status fallback, create/update/status/stock failure recovery, `crypto.randomUUID` requestId, and controlled-promise duplicate guards.
- `frontend/src/views/admin/OrderManagementView.spec.ts`: closest pattern for filter/query and destructive action failure recovery: list failure vs empty, retry, `status=all` omission, time normalization, confirmation, duplicate guard, backend trace preservation.
- `frontend/src/services/productApi.ts`: service-level pattern for admin `GET /api/admin/products`, omitting `status` when `all`, `authContext: 'ops'`, and write routes with request bodies.
- `frontend/src/types/api/product.ts`: type pattern for admin summaries/details/draft/write request DTOs, status as literal union plus `string` fallback.
- `services/sangui-seckill-service` and `services/sangui-marketing-service`: currently only service skeletons are present (`SanguiSeckillApplication`, `SanguiMarketingApplication`, `application.yml`, `pom.xml`); no admin controllers or frontend API are implemented in this checkout.
- `.trellis/spec/backend/seckill-contracts.md`: existing backend contract covers public seckill token/order, not admin activity management. Treat admin activity contract in PRD as a frontend planning contract unless backend work is later approved.

## Files Likely To Modify

Expected DeepSeek write set if no activity management UI exists:

- `frontend/src/App.vue`: add `seckill` or `activity` workspace key, permission gate, URL parsing, tab, and view mount.
- `frontend/src/App.spec.ts`: add activity workspace permission tests.
- `frontend/src/composables/useAppPreferences.ts`: add activity workspace labels and any visible admin activity copy as typed translation keys.
- `frontend/src/services/seckillApi.ts` or `frontend/src/services/marketingApi.ts`: add admin activity API wrappers through Gateway with `authContext: 'ops'`.
- `frontend/src/types/api/seckill.ts` or `frontend/src/types/api/marketing.ts`: add admin activity DTOs and status types.
- `frontend/src/composables/useSeckillActivityManagement.ts`: optional composable following sibling admin views if the view is not model-only.
- `frontend/src/views/admin/seckillActivityManagementModel.ts`: optional pure helpers for query payload, validation, status labels, request payload trimming.
- `frontend/src/views/admin/seckillActivityManagementModel.test.ts`: optional focused model tests.
- `frontend/src/views/admin/SeckillActivityManagementView.vue`: admin activity management view.
- `frontend/src/views/admin/SeckillActivityManagementView.spec.ts`: main component regression tests required by this PRD.

If a seckill/activity implementation already exists in DeepSeek's working copy, prefer extending those files rather than creating parallel names.

## Risk / Boundary Notes

- This PRD does not authorize backend implementation. If DeepSeek finds missing backend contract necessary, stop and ask before widening scope.
- Pick one domain name consistently (`seckill` preferred because specs and service skeleton exist; `marketing` only if existing code already uses it for activity management).
- `OPS_COMPENSATION_ADMIN` must remain limited to compensation dashboard and must not imply any product/order/activity admin rights.
- Do not rely on local `Date.now()` to infer activity state when backend supplies `status`, `serverTime`, `startsAt`, or `endsAt`.
- `status=all` should be omitted from query unless an existing project contract explicitly sends it.
- Activity stock UI validation is not a substitute for backend inventory checks; tests should prove frontend blocks obvious invalid drafts while still preserving backend `STOCK_NOT_ENOUGH`/`PRODUCT_STOCK_NOT_ENOUGH` errors.
- If new visible copy is added, tests that mock `t` can assert translation keys; production code must add real translation keys.
- Keep duplicate-submit tests at the component boundary with controlled promises, matching product/order tests.
- Avoid broad shared abstractions unless a helper is clearly repeated across admin views and local patterns already support it.

## Required Tests

- `frontend/src/App.spec.ts`: activity workspace positive/negative permission gates.
- `frontend/src/views/admin/SeckillActivityManagementView.spec.ts`: prop/session gate; list failure/retry/empty; status/time display; create/update/status/SKU write failure recovery; requestId; duplicate pending guards; stock validation.
- Optional `frontend/src/views/admin/seckillActivityManagementModel.test.ts`: query builder and validation/payload helper tests if model helpers are introduced.

Minimum verification commands after implementation:

```powershell
cd frontend
cmd /c npm run test -- SeckillActivityManagementView
cmd /c npm run test -- App
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm run build
```

Run full frontend suite if shared files are touched:

```powershell
cd frontend
cmd /c npm run test
```
