# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`
  - Defines Admin Product Management API functions, gateway routes, `authContext: 'ops'`, required UI handling, product model rules, backend error preservation, duplicate submit guards, integer cents, non-negative stock, unknown status fallback, and required tests.
- `.trellis/spec/frontend/component-guidelines.md`
  - Async components must cover loading, success, empty, error, retry, and pending states.
- `.trellis/spec/frontend/hook-guidelines.md`
  - API calls must go through `services/*Api.ts`; composables own loading/error/retry state.
- `.trellis/spec/frontend/state-management.md`
  - Server facts such as price, stock, and status remain backend-owned; backend `code/message/traceId` must not be translated.
- `.trellis/spec/frontend/type-safety.md`
  - API fixtures should match DTO types; money remains integer cents; enum/status values require unknown fallback.
- `.trellis/spec/frontend/directory-structure.md`
  - Admin page component tests belong beside admin views under `frontend/src/views/admin`.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Core interactions need component tests; critical buttons need loading/disabled duplicate-submit behavior; errors must include trace IDs.
- `.trellis/spec/backend/microservice-contracts.md`
  - Confirms REST envelope, idempotency/requestId expectations for writes, and external API validation/error matrix.
- `.trellis/spec/backend/gateway-security.md`
  - Confirms admin routes require JWT/RBAC and forbidden access maps to `AUTH_FORBIDDEN`.
- `.trellis/spec/backend/quality-guidelines.md`
  - Useful for later Codex check if backend contracts are suspected. This task should remain frontend-test-only.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - Relevant checklist for retry, idempotency, frontend loading/error/retry states, and catalog/order upstream boundary.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Encourages reusing existing admin component-test patterns before adding new abstractions.
- `.trellis/spec/guides/architecture-review-checklist.md`
  - Useful for verifying this remains a bounded frontend regression task and does not accidentally change API/security contracts.

## Code Patterns Found

- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
  - Best compact template for component tests: service mocks, `HttpClientError`, no-access prop gate, list failure/retry/empty, filter/reset, write failure recovery, controlled pending promise duplicate guard, deterministic `crypto.randomUUID`.
- `frontend/src/views/admin/OrderManagementView.spec.ts`
  - Broader template for App/session cleanup, deep-link/detail loading, backend error preservation, snapshot preservation after failed writes, and controlled async assertions.
- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Strong pattern for multiple write actions, draft preservation, backend error details, retry after failure, and avoiding over-brittle translated copy assertions.
- `frontend/src/App.spec.ts`
  - Existing shallow App permission pattern with `adminSession()`, mocked `useOpsAuthSession`, mocked `useAppPreferences`, and `/admin?workspace=<name>` URL setup.
- `frontend/src/App.vue`
  - Product workspace permission is `ADMIN` role or `PRODUCT_CATALOG_ADMIN` permission.
  - `OPS_COMPENSATION_ADMIN` is separate and must not expose product workspace by itself.
  - `ProductManagementView` is rendered only when `activeAdminWorkspace === 'product' && canAccessProductWorkspace`.
- `frontend/src/views/admin/ProductManagementView.vue`
  - Props: `session`, `canAccessProductWorkspace`.
  - Uses `useProductManagement(sessionRef, canAccessRef)`.
  - Renders list/detail/action error banners with backend `message`, `code`, and optional `traceId`.
  - Empty banner appears only when no `listError`, not loading, and `items.length === 0`.
  - Product status label falls back to raw unknown status.
  - SKU duplicate validation is visible through `validation.errors.skus[index]?.skuCode`.
- `frontend/src/composables/useProductManagement.ts`
  - `bootstrap()` returns early when `!canAccessWorkspace.value`.
  - `refreshList()` uses `listGate`, defaults to `{ page: 1, size: 20, status: filterStatus.value }`, clears list on failure, and resets draft on empty success.
  - `selectProduct()` calls `getAdminProduct(productId)`, maps detail to draft, and preserves detail errors.
  - `saveDraft()` uses `actionGate`, validates draft, builds create/update payloads from session `shopId/userId`, and preserves action errors on failure.
  - `changeStatus()` and `adjustStock()` use generated `requestId`, update detail/draft on success, and preserve current state on failure.
  - `retry()` reloads selected detail if present, otherwise reloads list.
- `frontend/src/views/admin/productManagementModel.ts`
  - Pure helpers already cover draft normalization, payload trimming, integer cents, non-negative stock, duplicate SKU code validation, backend error conversion, status/stock request builders, and duplicate submission gate.
- `frontend/src/views/admin/productManagementModel.test.ts`
  - Existing pure model coverage for trimmed payloads, price/stock/duplicate validation, `traceId` preservation, duplicate gate, status/stock request payloads, and draft-from-detail.
- `frontend/src/services/productApi.ts`
  - `listAdminProducts()` omits HTTP query `status` only when status is `all`.
  - Admin product routes use `/api/admin/products...` with `authContext: 'ops'`.
- `frontend/src/types/api/product.ts`
  - `ProductStatus` allows unknown string fallback.
  - Admin product list/detail/request types are already available for typed fixtures.

## Files Likely To Modify

- `frontend/src/views/admin/ProductManagementView.spec.ts`
  - New component spec for product management failure states, permission/session gates, list/detail behavior, write failure recovery, duplicate guards, requestId payloads, and validation visibility.
- `frontend/src/App.spec.ts`
  - Add product workspace permission tests.

## Files To Read But Not Modify Unless Explicitly Authorized Later

- `frontend/src/views/admin/ProductManagementView.vue`
- `frontend/src/composables/useProductManagement.ts`
- `frontend/src/views/admin/productManagementModel.ts`
- `frontend/src/views/admin/productManagementModel.test.ts`
- `frontend/src/services/productApi.ts`
- `frontend/src/types/api/product.ts`
- `frontend/src/App.vue`

## Risk / Boundary Notes

- This is a frontend-test planning task. Codex must not write business code in this turn.
- DeepSeek should keep implementation test-first and test-only where possible.
- If a component test reveals a production gap, DeepSeek may make only the smallest production change needed to satisfy an existing spec. Any API, route, RBAC, DTO, backend, or permission-name change is out of scope and should stop for clarification.
- `ProductManagementView` calls `bootstrap()` from both an immediate watcher and `onMounted()`. Tests may need to account for or clear initial calls before asserting explicit actions.
- `useProductManagement.bootstrap()` currently gates on `canAccessWorkspace` but not directly on `session` for list loads. PRD requires prop/session gate coverage; if missing-session list load is observed, DeepSeek should decide whether the test exposes an existing-spec gap and keep any fix narrowly scoped.
- `saveDraft()` relies on session for create/update payloads and returns `AUTH_TOKEN_MISSING` if absent.
- `retry()` reloads detail when `detail.value?.productId` exists; list retry tests should avoid selecting detail first.
- Use typed fixtures instead of `any`.
- Avoid over-asserting exact translated copy; the app test mocks usually return keys, while component tests can assert raw backend details, product names, SKU codes, and existing banner selectors.
- URL and storage cleanup should mirror sibling specs when App-level tests touch `/admin?workspace=product`.

## Required Tests

Targeted:

```powershell
cd frontend; cmd /c npm run test -- ProductManagementView
cd frontend; cmd /c npm run test -- App
```

Quality:

```powershell
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

Optional broader regression:

```powershell
cd frontend; cmd /c npm run test
```

## Suggested Component Test Structure

- `ProductManagementView.spec.ts`:
  - Use `// @vitest-environment happy-dom`.
  - Mock `../../services/productApi` with `listAdminProducts`, `getAdminProduct`, `createProduct`, `updateProduct`, `updateProductStatus`, `adjustSkuStock`.
  - Mock `../../composables/useAppPreferences` with stable key-returning `t()`.
  - Define typed `mockSession: PersistedOpsSession`.
  - Define `createAdminProductSummary(patch = {})`.
  - Define `createProductDetail(patch = {})`.
  - Define `createControlledApiResult<T>()`.
  - Define `flushPromises()` and `mountView({ session, canAccessProductWorkspace } = {})`.
  - In `afterEach`: unmount wrapper, clear mocks, restore crypto mocks.
- Extend `App.spec.ts`:
  - Add `describe('App product workspace permission gating', ...)`.
  - Use `/admin?workspace=product`.
  - Assert product workspace appears for `ADMIN` and `PRODUCT_CATALOG_ADMIN`, and does not appear for `OPS_COMPENSATION_ADMIN` alone.

## Expected Assertions By Area

- Permission:
  - Component no-access prop gate does not call `listAdminProducts`.
  - Missing session gate does not load list if implementation supports this; if current implementation loads list without session, treat as a narrow existing-spec gap.
  - App `ADMIN` shows `admin.productWorkspace`.
  - App `PRODUCT_CATALOG_ADMIN` shows `admin.productWorkspace`.
  - App `OPS_COMPENSATION_ADMIN` alone does not show `admin.productWorkspace`.
- List failure/retry/empty:
  - Failure banner includes backend message, code, traceId.
  - Failure state has no `.banner.empty`.
  - Retry button increments list API call count and clears error on success.
  - Success with `items: []` renders `.banner.empty`.
- Filter/default query:
  - Initial list call uses `{ page: 1, size: 20, status: 'all' }`.
  - Changing status calls list with the selected status.
  - Reset/default path returns to `status: 'all'` with default page/size.
  - Service-level omission of `status=all` is already in `productApi.ts`; component should not duplicate HTTP-client tests unless a local helper exists.
- Detail/SKU:
  - Selecting a list item calls `getAdminProduct(productId)`.
  - Loaded detail renders product name, SKU code/name, integer cents, available stock, and unknown status raw fallback.
  - Detail failure shows backend details.
- Save:
  - Failed create preserves typed draft values and re-enables save.
  - Failed update preserves selected product draft and re-enables save.
  - Duplicate pending save sends only one `createProduct` or `updateProduct` call.
- Status:
  - Failed status update preserves current detail/list snapshot and shows backend details.
  - Payload contains `status` and deterministic `requestId`.
  - Duplicate pending status click sends only one `updateProductStatus` call.
- Stock:
  - Failed stock adjustment preserves current SKU snapshot and shows backend details.
  - Payload contains `availableStock` and deterministic `requestId`.
  - Duplicate pending stock click sends only one `adjustSkuStock` call.
- Validation:
  - Duplicate SKU code renders duplicate validation and prevents save.
  - Invalid price/stock boundaries keep save disabled or show validation, matching current UI.
