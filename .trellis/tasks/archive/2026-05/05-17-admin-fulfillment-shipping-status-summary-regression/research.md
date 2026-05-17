# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`
  - Admin fulfillment must use `/api/admin/fulfillments/{orderId}/ship` through `services/fulfillmentApi.ts` with `authContext: 'ops'`.
  - Ship writes require `requestId`, trimmed `carrier`, trimmed `trackingNo`, duplicate-submit guard, and backend `code/message/traceId` preservation.
  - Admin order/payment rules explicitly say payment response status must not overwrite order main status.
- `.trellis/spec/frontend/type-safety.md`
  - API DTOs must stay typed and tolerate unknown enum/status values.
- `.trellis/spec/frontend/hook-guidelines.md`
  - Composables own async state and call `services/*Api.ts`; components do not assemble URLs.
- `.trellis/spec/frontend/component-guidelines.md`
  - Reactive pending flags are required when they drive computed/disabled/loading UI.
- `.trellis/spec/frontend/state-management.md`
  - Order status is a server fact; UI state must not invent or overwrite lifecycle states.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Core interactions require component/e2e coverage plus typecheck, lint, and build.
- `.trellis/spec/backend/microservice-contracts.md`
  - Write operations require idempotency keys and stable JSON envelope semantics.
- `.trellis/spec/backend/error-handling.md`
  - Error responses preserve `code/message/traceId`.
- `.trellis/spec/backend/quality-guidelines.md`
  - Do not cross backend/infra boundaries for this task.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - Check status field naming, idempotency, retry behavior, and frontend loading/failure states.

## Code Patterns Found

- Fulfillment service boundary:
  - `frontend/src/services/fulfillmentApi.ts`
  - Existing functions already use `/api/admin/fulfillments`, `postJson`, and `{ authContext: 'ops' }`.
- Fulfillment action state and merge:
  - `frontend/src/composables/useFulfillmentManagement.ts`
  - `shipSelectedFulfillment()` calls `shipAdminFulfillment()`, assigns `detail.value = result.data`, resets draft, then calls `refreshList()`.
  - `selectedItem` is derived from `items` by matching `detail.orderId`.
- Fulfillment model helpers:
  - `frontend/src/views/admin/fulfillmentManagementModel.ts`
  - `canShipFulfillment()` currently allows only `orderStatus === 'paid' && fulfillmentStatus === 'unshipped'`.
  - `createShipmentGate()` uses a reactive `ref(false)`.
- Fulfillment view display:
  - `frontend/src/views/admin/FulfillmentManagementView.vue`
  - List renders `fulfillmentLabel(item.fulfillmentStatus) / orderLabel(item.status)`.
  - Summary renders order main status and fulfillment status as separate cells.
  - `orderLabel()` special-cases `shipped` to `fulfillmentAdmin.orderStatusShipped`, otherwise delegates to `getAdminOrderStatusLabel()`.
- Admin order payment non-overwrite pattern:
  - `frontend/src/views/admin/orderManagementModel.ts`
  - `applyAdminPaymentToDetail()` and `applyAdminPaymentToSummaries()` merge only `paymentNo`, not `status`.
- E2E route mock:
  - `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - POST `/api/admin/fulfillments/{orderId}/ship` captures auth/payload, optionally defers, handles error, or writes `mockFulfillmentShipSuccess` into both `mockFulfillmentById` and `mockFulfillments`.
- E2E fixture factory:
  - `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`
  - `createAdminFulfillmentResponse()` defaults `status: 'paid'` and `fulfillmentStatus: 'unshipped'`, with overrides for shipped cases.

## Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
  - Most likely if this is an async wait or mock isolation issue. Strengthen the failing shipped assertion to wait for ship success to settle without weakening the `not Paid` requirement.
  - Inspect whole-file/full-smoke interactions, shared mock reset, deferred route variables, and stale route state.
- `frontend/src/composables/useFulfillmentManagement.ts`
  - Likely if implementation does not robustly merge the ship success response into both detail and list before/after refresh.
- `frontend/src/views/admin/FulfillmentManagementView.vue`
  - Only if shipped order-main label mapping is incorrect or summary/list render order status and fulfillment status from the wrong source.
- `frontend/src/views/admin/fulfillmentManagementModel.ts`
  - Only if a helper is needed for type-safe shipped merge/status handling.
- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
  - Add/adjust component regression coverage if implementation changes detail/list merge.
- `frontend/src/views/admin/fulfillmentManagementModel.test.ts`
  - Add/adjust model tests only if helper logic changes.
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`
  - Only if fixture defaults are proven to overwrite shipped snapshots incorrectly.

## Risk / Boundary Notes

- Focused regression test passed during Codex planning, so the failure may be order-dependent, async, or stale mock state rather than deterministic business logic failure.
- Whole admin smoke failed during Codex planning at `shows payment refresh loading state and guards duplicate clicks`, not at the fulfillment shipped regression. Treat that as a separate blocker unless root cause is shared.
- Do not weaken the shipped assertion by removing `not.toContain('Paid')`; the whole point is to catch payment status leaking into order main status.
- Be careful with the word `Shipped`: the summary contains both order main status and fulfillment status. Assertions should prove the stale `Paid` / `Awaiting shipment` pair is gone, not merely that one `Shipped` appears somewhere.
- A list refresh after ship can overwrite `items`. If the mocked/list response is stale, `selectedItem` will also look stale even when `detail` was correctly updated.
- `refreshList()` does not select/reload detail unless `selectFirst` is true. A ship success path that sets `detail` from response is therefore the main detail source after POST.
- Do not modify backend Java, DB, Redis/MQ, gateway, permissions, Docker, or dependencies.

## Required Tests

Run from `frontend/`:

```powershell
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "successful ship does not overwrite order main status with payment status"
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npm run test:smoke
cmd /c npx vitest run src/views/admin/fulfillmentManagementModel.test.ts src/views/admin/FulfillmentManagementView.spec.ts
cmd /c npm test
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm run build
```

## Planning Self-Check

- Acceptance criteria明确：yes, see `prd.md`.
- 禁止修改范围明确：yes, no backend/DB/infra/payment broadening without proof.
- 预计修改文件已列出：yes.
- 必跑测试已列出：yes.
- 已读取具体 guideline：yes, not just index files.
- 需求不清问题：当前没有需要用户确认的问题, but reproduction differs from the previous journal.
- API / DB / frontend types / DTO 对齐：no new API/DB/DTO fields expected; existing `AdminFulfillmentResponse` and `ShipFulfillmentRequest` are sufficient unless implementation proves otherwise.

## DeepSeek Execution Handoff

- PRD path: `.trellis/tasks/05-17-admin-fulfillment-shipping-status-summary-regression/prd.md`
- Task path: `.trellis/tasks/05-17-admin-fulfillment-shipping-status-summary-regression`
- Must-read context:
  - `.trellis/tasks/05-17-admin-fulfillment-shipping-status-summary-regression/implement.jsonl`
  - `.trellis/tasks/05-17-admin-fulfillment-shipping-status-summary-regression/prd.md`
  - `.trellis/tasks/05-17-admin-fulfillment-shipping-status-summary-regression/research.md`
- Expected modify set:
  - Start with `frontend/e2e/admin-order-payment-smoke.spec.ts`.
  - Modify `frontend/src/composables/useFulfillmentManagement.ts` only if the implementation merge is truly wrong.
  - Modify `frontend/src/views/admin/FulfillmentManagementView.vue` only if labels/source fields are wrong.
  - Add/update `FulfillmentManagementView.spec.ts` or `fulfillmentManagementModel.test.ts` if helper or component behavior changes.
- Do not cross:
  - backend Java
  - DB migrations
  - gateway/auth changes
  - Redis/MQ
  - Docker/infra
  - dependency changes
  - mall order flows
  - unrelated admin payment-refresh behavior unless same root cause is proven
- Required command order:
  - Reproduce focused fulfillment command.
  - Run whole admin smoke file.
  - Implement the smallest scoped fix.
  - Run focused fulfillment command again.
  - Run admin smoke file, `npm run test:smoke`, unit tests, typecheck, lint, build.
