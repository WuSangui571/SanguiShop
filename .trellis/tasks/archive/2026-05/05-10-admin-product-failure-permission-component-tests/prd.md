# 管理端产品管理失败态与权限边界组件测试补齐

## Classification

Complex Task.

Reason: this is a multi-file frontend test coverage task touching admin workspace permission routing, product management component behavior, service mock contracts, write-action failure recovery, idempotency guards, and API payload validation boundaries. This planning pass must not change production business implementation.

## Current Project State

- Admin Review Management component coverage has been completed and recorded.
- Admin Fulfillment Management component coverage has been completed and recorded.
- Admin Order Management component coverage has been completed and recorded.
- The admin transaction regression net now covers order, fulfillment, and review. Product management is the catalog/source-of-truth upstream of ordering, inventory display, SKU snapshots, and operator edits.
- Current git state at task start: `main`, clean working tree, no active Trellis task.

## Goal

Add component-level regression coverage for Admin Product Management failure states, permission boundaries, filter/query behavior, detail/SKU snapshot loading, write-action recovery, request id generation, duplicate-submit guards, and product draft validation boundaries.

## Non-Goals

- Do not change backend API contracts, DTOs, database schema, gateway routes, RBAC implementation, storage, Redis, MQ, or AI flows.
- Do not introduce a new permission name or alter existing permission semantics.
- Do not refactor production product management implementation unless a test exposes a direct gap required by this PRD. If such a gap implies contract or permission changes, stop and ask for clarification.
- Do not broaden coverage into customer mall product browsing/review flows except as needed for shared type fixtures.

## Scope

Primary files expected for DeepSeek implementation:

- `frontend/src/views/admin/ProductManagementView.spec.ts`
- `frontend/src/App.spec.ts`

Read-only reference files unless a test exposes a narrow authorized implementation gap:

- `frontend/src/views/admin/ProductManagementView.vue`
- `frontend/src/composables/useProductManagement.ts`
- `frontend/src/views/admin/productManagementModel.ts`
- `frontend/src/views/admin/productManagementModel.test.ts`
- `frontend/src/services/productApi.ts`
- `frontend/src/types/api/product.ts`
- `frontend/src/App.vue`
- Existing admin component tests:
  - `frontend/src/views/admin/OrderManagementView.spec.ts`
  - `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
  - `frontend/src/views/admin/ReviewManagementView.spec.ts`

## API / Command / Payload Contract

This task tests existing frontend contracts from `frontend/src/services/productApi.ts` and `.trellis/spec/frontend/api-contracts.md`.

| Function | Route | Auth Context | Payload / Query | Required Test Assertions |
| --- | --- | --- | --- | --- |
| `listAdminProducts({ page, size, status })` | `GET /api/admin/products` | `ops` | `page`, `size`; omit `status` when `status === 'all'` | failure banner preserves backend details; retry calls again; empty success renders empty banner; error does not render empty banner; default query uses `page=1`, `size=20` |
| `getAdminProduct(productId)` | `GET /api/admin/products/{productId}` | `ops` | path `productId` | selecting a product loads detail/SKU snapshot; detail failure preserves backend details without fabricating empty state |
| `createProduct(payload)` | `POST /api/admin/products` | `ops` | `shopId`, `userId`, trimmed `productName`, trimmed `productDescription`, `skus[]` with trimmed `skuCode`, trimmed `skuName`, integer `priceCent`, non-negative integer `availableStock` | failed create preserves draft and backend details; pending duplicate click sends no second request |
| `updateProduct(payload)` | `PUT /api/admin/products/{productId}` | `ops` | path `productId`; body fields compatible with backend DTO | failed update preserves draft and backend details; pending duplicate click sends no second request |
| `updateProductStatus(productId, payload)` | `POST /api/admin/products/{productId}/status` | `ops` | `{ status, requestId }` | failure preserves current list/detail snapshot; backend details shown; requestId generated; pending duplicate click sends no second request |
| `adjustSkuStock(productId, skuId, payload)` | `POST /api/admin/products/{productId}/skus/{skuId}/stock-adjustments` | `ops` | `{ availableStock, requestId }` | failure preserves SKU snapshot; backend details shown; requestId generated; pending duplicate click sends no second request |

## Permission Contract

- `ADMIN` role can see the product workspace.
- `PRODUCT_CATALOG_ADMIN` permission can see the product workspace.
- `OPS_COMPENSATION_ADMIN` alone must not show the product workspace.
- Component-level `canAccessProductWorkspace=false` must not load product list.
- Missing `session` must not send write payloads; current implementation should surface or preserve an auth error only where an action is attempted.

## Validation / Error Matrix

| Case | Expected Frontend Behavior |
| --- | --- |
| No product workspace permission | `ProductManagementView` does not bootstrap list API; App does not render product workspace tab/view for unrelated ops permission |
| List API rejects with `HttpClientError` | Error banner includes backend `message`, `code`, and `traceId`; no empty banner is shown |
| Retry after list failure | Retry button sends another `listAdminProducts` call and renders success state if second call succeeds |
| Successful list with `items=[]` | Empty banner renders because this is a real empty success, not a failure |
| `status=all` filter | `listAdminProducts` receives status `all`; service-level query builder already omits it from HTTP query; component test should assert component calls use the expected status value and reset defaults |
| Page/size defaults | Initial product list load uses `page: 1`, `size: 20` |
| Reset/default filter behavior | Returning filter to default status calls list with default query and does not preserve dirty status |
| Detail API rejects | Detail error preserves backend `message/code/traceId`; previous/empty SKU state is not misrepresented as a successful loaded detail |
| Create/update rejects | Product draft remains available for correction; save button is re-enabled after promise settles; backend details shown |
| Status update rejects | Current detail/list snapshot remains unchanged; backend details shown; later retry is possible |
| Stock adjustment rejects | Current detail SKU snapshot remains unchanged; backend details shown; later retry is possible |
| Duplicate save/status/stock click while pending | No second write request is sent before the first promise settles |
| Request ID generation | Status and stock requests include deterministic stubbed `requestId`; model-level trimming/shape should remain covered and component tests should assert action payload includes requestId |
| Money boundary | SKU `priceCent` remains integer cents; invalid non-positive values keep submit disabled or validation visible |
| Stock boundary | SKU `availableStock` and stock adjustment inputs are non-negative integers; invalid negative values are not accepted as valid draft data |
| Unknown product/status value | UI falls back to raw backend value rather than crashing |
| Duplicate SKU code | Existing model test covers duplicate validation; add component-level coverage if missing from DOM validation, otherwise explicitly keep model coverage as the assertion source |

## Good / Base / Bad Cases

Good:

- Product workspace visible for `ADMIN` and `PRODUCT_CATALOG_ADMIN`, hidden for `OPS_COMPENSATION_ADMIN` alone.
- Component no-access/missing-session gates prevent unauthorized list load.
- List failure shows backend details and retry recovers.
- Empty success renders only after successful `items=[]`.
- Selecting a product loads detail and SKU snapshot from `getAdminProduct`.
- Create/update/status/stock failures preserve draft or snapshot state and show backend details.
- Pending duplicate clicks do not send duplicate write requests.
- Deterministic `crypto.randomUUID` stub proves status/stock `requestId` payloads.
- SKU validation covers integer cents, non-negative stock, and duplicate SKU code without `any`.

Base:

- If existing pure model tests already cover duplicate SKU validation or payload trimming, component tests may assert only the visible validation/error state and reference the model tests for payload-level details.
- If component selectors are unstable, prefer user-visible data, banner classes already used by sibling tests, and raw backend details over brittle DOM traversal.
- If a current implementation gap is small and production-file change is required to satisfy an existing spec, keep it minimal and document the reason before Codex check.

Bad:

- Changing product API routes, auth context, backend error codes, permission names, or DTO fields for test convenience.
- Hiding backend `traceId`, translating backend `code/message/traceId`, or replacing real backend details with generic copy.
- Adding broad shared test utilities that obscure fixture types or introduce `any`.
- Modifying order/review/fulfillment behavior while adding product tests.
- Letting an unrelated ops permission expose the product workspace.

## Acceptance Criteria

- [ ] `ProductManagementView.spec.ts` exists and covers permission/session gate, list failure/retry/empty states, filter/default query behavior, product detail/SKU loading, failed save/status/stock recovery, duplicate write guards, requestId payloads, status fallback, and visible SKU validation boundaries.
- [ ] `App.spec.ts` includes product workspace permission tests for `ADMIN`, `PRODUCT_CATALOG_ADMIN`, and `OPS_COMPENSATION_ADMIN` alone.
- [ ] Backend `code/message/traceId` are asserted for list/detail/action failures.
- [ ] `status=all`, default `page=1`, default `size=20`, and filter reset/default behavior are asserted at component/API mock level.
- [ ] Create/update failure keeps form draft and restores save button.
- [ ] Status update failure keeps current detail/list snapshot.
- [ ] Stock adjustment failure keeps current SKU snapshot.
- [ ] Pending duplicate clicks for save/status/stock do not send second requests.
- [ ] Tests use typed DTO fixtures and avoid `any`, `console.log`, `debugger`, and TODO placeholders.
- [ ] No backend, gateway, DB, storage, Redis, MQ, route, permission implementation, dependency, or unrelated business workflow changes are made.

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

Broader regression before handoff back to Codex:

```powershell
cd frontend; cmd /c npm run test
```

## Implementation Notes For DeepSeek

- Follow `OrderManagementView.spec.ts`, `FulfillmentManagementView.spec.ts`, and `ReviewManagementView.spec.ts` test style.
- Mock `../../services/productApi` functions directly.
- Mock `useAppPreferences` with stable key-returning `t()` behavior, matching sibling specs.
- Use `HttpClientError` for backend error preservation assertions.
- Use controlled promises for duplicate-click tests.
- Stub `globalThis.crypto.randomUUID` deterministically for `requestId` assertions and restore it after each test.
- Clear URL/session storage/mocks between tests when using App or global browser state.
- Keep production changes out of scope unless a failing test reveals a direct existing-spec gap.
