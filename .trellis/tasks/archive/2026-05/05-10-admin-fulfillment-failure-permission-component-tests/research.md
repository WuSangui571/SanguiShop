# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`
  - Contains the Admin Fulfillment Management API contract: `listAdminFulfillments`, `getAdminFulfillment`, `shipAdminFulfillment`, ops auth context, required UI handling, permission boundary, query omission, datetime normalization, backend error preservation, and duplicate ship guard.
- `.trellis/spec/frontend/component-guidelines.md`
  - Requires async UI states: loading, success, empty, error, retry; relevant to list failure/retry/empty component tests.
- `.trellis/spec/frontend/type-safety.md`
  - Requires frontend API types to follow backend contracts and unknown enum fallback; relevant to typed mocks and fulfillment status handling.
- `.trellis/spec/frontend/state-management.md`
  - Requires backend `code/message/traceId` to remain raw business/debug data and not be translated.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Requires `npm run typecheck`, `npm run build`, component tests for core interactions, API error handling with traceId, and duplicate-submit guards.
- `.trellis/spec/backend/microservice-contracts.md`
  - Confirms response envelope shape and write-operation idempotency via `requestId`.
- `.trellis/spec/backend/gateway-security.md`
  - Confirms admin routes require RBAC and unauthorized access should surface `AUTH_FORBIDDEN`.
- `.trellis/spec/backend/error-handling.md`
  - Confirms external responses must include `traceId` and avoid leaking sensitive internals.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - Relevant checklist for write retry/idempotency and frontend loading/error/retry states.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Encourages reusing existing test/model patterns rather than inventing new helpers prematurely.

## Code Patterns Found

- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Existing DOM component-test pattern for admin page:
    - `@vitest-environment happy-dom`
    - `@vue/test-utils` `mount`
    - service module mocks
    - `HttpClientError` for backend `code/message/traceId`
    - `flushPromises` + `nextTick`
    - no-access prop gate test
    - list failure / retry / empty tests
    - filter query/reset tests
    - write-action failure recovery and pending duplicate-click tests
    - deterministic `crypto.randomUUID` stubbing
- `frontend/src/App.spec.ts`
  - Existing shallow App permission-test pattern:
    - `PersistedOpsSession` typed mock
    - mocked `useOpsAuthSession`
    - `window.history.replaceState` for `/admin?workspace=review`
    - wrapper teardown in `afterEach`
    - currently tests review workspace visibility for `ADMIN`, `REVIEW_MANAGEMENT_ADMIN`, and denial for `OPS_COMPENSATION_ADMIN`.
  - For fulfillment, use `/admin?workspace=fulfillment`, assert `admin.fulfillmentWorkspace`, allowed `ADMIN` and `LOGISTICS_FULFILLMENT_ADMIN`, denied `OPS_COMPENSATION_ADMIN`.
- `frontend/src/views/admin/fulfillmentManagementModel.test.ts`
  - Existing pure-model coverage already verifies:
    - filter payload trimming and `status=all` omission
    - datetime normalization
    - status label fallback
    - ship payload trimming
    - backend error preservation
    - duplicate shipment gate
  - New task should complement this with component-level behavior rather than duplicating only model tests.
- `frontend/src/composables/useFulfillmentManagement.ts`
  - Uses `createShipmentGate()` for list and ship pending guards.
  - `bootstrap()` returns without API calls when no access or no session.
  - `refreshList(true)` calls `listAdminFulfillments(buildAdminFulfillmentQuery(filters.value))` and selects first result via `getAdminFulfillment`.
  - List failures set `listError`, clear `items`, and therefore view must keep error state ahead of empty state.
  - `shipSelectedFulfillment()` generates `requestId`, calls `shipAdminFulfillment`, clears draft only on success, and leaves draft intact on catch.
- `frontend/src/views/admin/FulfillmentManagementView.vue`
  - Prop gate: `canAccessFulfillmentWorkspace`.
  - Error banner selectors: `.banner.error`.
  - Empty banner selector: `.banner.empty`.
  - Filter selectors: `.filters`, `.filter-actions .primary`, `.filter-actions .secondary`.
  - List rows: `.list-item`.
  - Ship form: `.ship-form`; first input is carrier, second input is tracking number, submit button is `.primary`.
- `frontend/src/services/fulfillmentApi.ts`
  - `listAdminFulfillments` uses `GET /api/admin/fulfillments` with `authContext: 'ops'`.
  - `shipAdminFulfillment` uses `POST /api/admin/fulfillments/{orderId}/ship` with `authContext: 'ops'`.
- `frontend/src/types/api/order.ts`
  - Existing fulfillment DTOs:
    - `AdminFulfillmentQueryParams`
    - `AdminFulfillmentResponse`
    - `AdminFulfillmentPageResponse`
    - `ShipFulfillmentRequest`

## Files Likely To Modify

- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
  - Likely new file. Add component tests for prop gate, list failure/retry/empty, filter query/reset, and ship failure recovery/pending duplicate guard.
- `frontend/src/App.spec.ts`
  - Extend existing App permission tests or add a nearby describe block for fulfillment workspace visibility.

## Files To Read But Not Modify Unless Tests Reveal Authorized Need

- `frontend/src/views/admin/FulfillmentManagementView.vue`
  - Use selectors and understand UI state ordering; do not modify in this Codex planning turn.
- `frontend/src/composables/useFulfillmentManagement.ts`
  - Understand action/list gates and failure behavior; do not modify in this Codex planning turn.
- `frontend/src/views/admin/fulfillmentManagementModel.ts`
  - Existing contract helpers; do not modify unless DeepSeek is later authorized to fix a test-exposed implementation gap.
- `frontend/src/services/fulfillmentApi.ts`
  - Existing API client; no contract changes planned.
- `frontend/src/types/api/order.ts`
  - Existing DTOs; no type changes planned.

## Risk / Boundary Notes

- `FulfillmentManagementView.vue` currently calls `bootstrap()` both in an immediate `watch` and in `onMounted()`. Existing implementation may call the list API twice on mount. Tests should account for current behavior carefully:
  - For no-access prop gate, assertion remains strict: zero list calls.
  - For allowed default load, avoid over-specifying exactly one call unless implementation is intentionally fixed later.
  - For search/retry assertions, clear mocks after mount or assert incremental behavior.
- A non-empty list with `selectFirst=true` triggers `getAdminFulfillment`; component tests that need ship form should mock both list and detail APIs.
- List failure clears `items`, but template checks `listError` before empty state; assert this ordering so failures do not render misleading empty state.
- Ship failure catch does not call `resetShipDraft()`, so carrier/tracking draft should remain available for retry.
- `actionGate` ends in `finally`; failed ship should restore button availability after promises settle.
- Deterministic `requestId` should follow the review spec pattern: `vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(...)`.
- Keep test helper data aligned with `AdminFulfillmentResponse`; required fields include `orderId`, `orderNo`, `shopId`, `userId`, `status`, `fulfillmentStatus`, `totalAmountCent`, `carrier`, `trackingNo`, `shippedAt`, `traceId`, `createdAt`, `updatedAt`.
- App-level tests use shallow mount and translation keys as rendered text; assert `admin.fulfillmentWorkspace` unless the mock translation setup is changed.
- Do not let `OPS_COMPENSATION_ADMIN` alone imply fulfillment access. It may still see compensation workspace; the assertion should specifically deny fulfillment workspace.

## Required Tests

- `cd frontend; cmd /c npm run test -- fulfillmentManagement`
- `cd frontend; cmd /c npm run test -- App`
- `cd frontend; cmd /c npm run typecheck`
- `cd frontend; cmd /c npm run lint`
- `cd frontend; cmd /c npm run build`

## Suggested Component Test Structure

- New `FulfillmentManagementView.spec.ts`:
  - Mock `../../services/fulfillmentApi` with `listAdminFulfillments`, `getAdminFulfillment`, `shipAdminFulfillment`.
  - Mock `useAppPreferences` with only keys used by fulfillment view plus shared common/order keys.
  - Define `mockMeta` and `createFulfillment(patch = {})`.
  - Define `mountView({ session, canAccessFulfillmentWorkspace } = {})`.
  - Define `createControlledApiResponse()` helper for pending duplicate tests, matching review test style.
- App spec:
  - Add `describe('App fulfillment workspace permission gating', ...)`.
  - Use `/admin?workspace=fulfillment`.
  - Reuse `adminSession()` helper.

## Expected Assertions By Area

- Permission:
  - no-access component gate: `listAdminFulfillments` not called.
  - App `ADMIN`: text contains `admin.fulfillmentWorkspace`.
  - App `LOGISTICS_FULFILLMENT_ADMIN`: text contains `admin.fulfillmentWorkspace`.
  - App `OPS_COMPENSATION_ADMIN`: text does not contain `admin.fulfillmentWorkspace`.
- List failure/retry/empty:
  - failure banner includes backend message, code, traceId.
  - failure state has no `.banner.empty`.
  - retry button in `.banner.error` increments list API call count and clears error on success.
  - success with `items: []` renders empty banner and no error banner.
- Filter query:
  - after clearing mount calls, set status to `all`, blank `orderNo`/`userId`, datetime values, click search, and assert called query omits `status`, blank strings, and includes normalized `fromTime`/`toTime`.
  - reset after dirty inputs calls default query `{ page: 1, size: 20 }`.
- Ship failure recovery:
  - first response: list returns one unshipped paid item; detail returns same item so ship form is enabled.
  - failed ship rejects with `HttpClientError('Ship failed', { code: 'VALIDATION_FAILED', status: 400, traceId: 'trace-ship-err' })`.
  - after failure, action banner contains message/code/traceId, submit button is enabled, carrier/tracking input values are preserved.
  - retry calls `shipAdminFulfillment` a second time with trimmed `{ requestId, carrier, trackingNo }`.
  - controlled pending promise plus two submit triggers results in one ship API call before resolution.
