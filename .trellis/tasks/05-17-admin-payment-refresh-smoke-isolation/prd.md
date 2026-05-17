# 修复管理端支付刷新 smoke 顺序执行隔离失败

## Goal

修复 `frontend/e2e/admin-order-payment-smoke.spec.ts` 中管理端支付刷新用例的顺序执行隔离失败，使该用例在单跑、整文件执行和完整 smoke 套件中都稳定通过。

## Problem Statement

上一轮 journal 记录显示：

- 单跑 `shows payment refresh loading state and guards duplicate clicks` 通过。
- 整文件 `admin-order-payment-smoke.spec.ts --project=chromium` 复现失败，约 `36/37 passed`。
- `npm run test:smoke` 复现失败，约 `56/57 passed`。

该模式指向测试隔离问题、mock 状态泄漏、路由闭包引用残留、异步 pending route 清理不足，或前序用例污染第 18 个用例依赖的 order detail/payment fixture。

## Scope

允许修改：

- `frontend/e2e/admin-order-payment-smoke.spec.ts`
- 与该 smoke 文件强绑定的 frontend E2E fixture/helper 文件，仅限证明必须修改时：
  - `frontend/e2e/fixtures/*admin*`
  - `frontend/e2e/fixtures/*order*`
  - `frontend/e2e/fixtures/*payment*`
- 必要的 Trellis task/context 文件。

禁止修改，除非用失败证据证明实际实现缺陷：

- payment / fulfillment / order 业务实现文件。
- Vue 页面、model、composable、service、API types。
- 后端 Java 服务、Gateway、DB migration、Redis/MQ/infra 配置。
- 新增或修改 API/DTO/DB contract。

## Requirements

- 复现并定位顺序失败来源，不以“只调整等待时间”作为主要修复。
- 检查 module-level mock/reset 状态，尤其是：
  - `deferPaymentResponse`
  - pending route / delayed response 控制器
  - payment mock response
  - order detail/list fixture 对象是否被前序用例原地污染
  - `beforeEach` / `resetMockState` 是否遗漏闭包引用、数组/对象深拷贝或 route 清理
- 保持 admin payment refresh 契约：
  - route 为 `GET /api/admin/payments/by-order/{orderId}`
  - `PAYMENT_NOT_FOUND` 自动 detail load 可视为无 payment row
  - 手动 refresh loading 期间必须防重复点击
  - payment response `status` 不能覆盖订单主生命周期 `status`
- 修复应最小化：优先修复测试隔离、mock reset、fixture clone、route 生命周期；不要扩大为业务重构。

## Acceptance Criteria

- [ ] 单跑通过：`npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- [ ] 整文件通过：`npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium`
- [ ] 完整 smoke 通过：`npm run test:smoke`
- [ ] 修复不改变 payment/fulfillment/order 业务逻辑，除非提交说明中包含可复现证据证明业务实现存在缺陷。
- [ ] 修复后不留下共享 mutable mock 状态、未完成 deferred promise、跨测试 route handler 或未清理 pending 标志。

## Contract Depth Check

本任务默认不变更 API、command、payload、DB、storage、infra、权限或 DTO 字段。

### API / Command / Payload Fields

无新增或变更。测试中仍应模拟现有契约：

- `GET /api/admin/orders`
- `GET /api/admin/orders/{orderId}`
- `GET /api/admin/payments/by-order/{orderId}`
- `POST /api/admin/orders/{orderId}/cancel`
- fulfillment 相关 admin routes 仅在既有 smoke 覆盖中保持现状。

### Validation / Error Matrix

无新增业务 validation。测试修复需保留既有错误行为：

| Case | Expected test behavior |
| --- | --- |
| `PAYMENT_NOT_FOUND` during automatic detail load | Treat as no payment row without failing page load. |
| Manual payment refresh pending | UI shows loading/disabled state and second click sends no duplicate request. |
| Manual payment refresh success | Payment-specific display updates; order main status remains from order snapshot. |
| Prior test mutates fixture/mock state | Later tests receive a fresh fixture and independent route/deferred controls. |

### Good / Base / Bad Cases

- Good: each smoke test receives fresh payment/order mock state and route controls.
- Good: delayed payment route is resolved/rejected/cleared inside the owning test and cannot affect subsequent tests.
- Good: full admin smoke and full smoke have the same result as the focused single test.
- Base: fixture helpers may clone immutable source snapshots per test.
- Bad: fixing by skipping the failing test, weakening assertions, or increasing arbitrary timeouts.
- Bad: payment response `status=paid` overwrites an order main status such as `shipped`, `completed`, `cancelled`, or unknown.
- Bad: a route handler or pending deferred promise survives into the next test.

## Required Tests and Assertion Points

Commands:

```powershell
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npm run test:smoke
```

Assertion points:

- The payment refresh loading button/indicator is visible while the deferred response is pending.
- Duplicate click while pending sends exactly one payment refresh request.
- The deferred response is always released or cleaned up before the test ends.
- `beforeEach` starts from fresh mock state for order list/detail/payment response.
- Full file execution does not depend on test order and does not inherit previous order/payment route state.

## Research Summary

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`: admin order management API contract; payment refresh route, `PAYMENT_NOT_FOUND` handling, duplicate guard, and payment response must not overwrite order main status.
- `.trellis/spec/frontend/quality-guidelines.md`: key payment/order buttons require loading/disabled duplicate-submit protection; tests must cover core interactions.
- `.trellis/spec/frontend/type-safety.md`: API DTOs must follow backend contracts and unknown statuses need fallback.
- `.trellis/spec/frontend/state-management.md`: payment/order status are server facts; frontend state must not invent authoritative statuses.
- `.trellis/spec/backend/payment-pay-contracts.md`: admin payment status route contract for `GET /api/admin/payments/by-order/{orderId}`.
- `.trellis/spec/backend/order-create-contracts.md`: admin order response `status` is the persisted main lifecycle status; payment enrichment must not replace it.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: confirms this task must avoid cross-layer contract drift and focus on loading/retry/isolation risks.

## Code Patterns Found

- `frontend/e2e/admin-order-payment-smoke.spec.ts`: central route mock is installed via `page.route('**/api/**')`; payment/cancel/fulfillment flows share module-level mutable state and `resetMockState()`.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: payment route captures delayed requests through `deferPaymentResponse` + `pendingPaymentRoute`; current handler defers any matching admin payment status request while the flag is true.
- `frontend/e2e/admin-order-payment-smoke.spec.ts`: target test sets `deferPaymentResponse = true` only after order detail selection, then clicks `Refresh payment`, asserts `Refreshing`, dispatches a duplicate click, and manually fulfills `pendingPaymentRoute`.
- `frontend/src/composables/useOrderManagement.ts`: `selectOrder()` automatically calls `refreshPaymentStatus(false)` after loading detail; `refreshPaymentStatus()` guards duplicate requests with `isRefreshingPayment`.
- `frontend/src/views/admin/orderManagementModel.ts`: `applyAdminPaymentToDetail()` and `applyAdminPaymentToSummaries()` only merge `paymentNo`; they intentionally do not assign `payment.status` to order main `status`.
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`: fixture factories return fresh top-level objects, but `DEFAULT_ORDER_ITEMS` is reused by default inside `createAdminOrderDetail()` unless overrides are provided.
- `frontend/playwright.config.ts`: smoke runs are sequential (`fullyParallel: false`, `workers: 1`, `retries: 0`), so the failure is true order dependence rather than cross-worker concurrency.

## Files Likely To Modify

- `frontend/e2e/admin-order-payment-smoke.spec.ts`: most likely target. Expected changes are route/mock isolation, pending deferred cleanup, per-test delayed payment route scoping, and/or safer counter assertions.
- `frontend/e2e/fixtures/adminOrderPaymentSmoke.ts`: possible only if evidence shows shared nested fixture objects are mutated across tests; likely fix would be fresh array/object clones from factory helpers.

## Risk / Boundary Notes

- The target test can receive two payment requests in normal flow: automatic payment load from `selectOrder()` and manual refresh from button click. The deferred mock must capture only the intended manual refresh request.
- Module-level `pendingPaymentRoute` is a Playwright `Route`; if a test fails before fulfillment or cleanup, later tests may inherit stale references unless cleanup explicitly aborts/fulfills or resets after each test.
- `adminApiCallCount` counts all admin API routes, not only payment refresh. Duplicate-click assertions should account for unrelated in-flight list/detail/payment calls or use a dedicated payment request counter if needed.
- Do not fix by weakening assertions, skipping the smoke, or increasing arbitrary waits.
- Do not change payment/order/fulfillment production logic unless a separate failing unit/component test proves implementation drift.

## Required Tests

- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "shows payment refresh loading state and guards duplicate clicks"`
- `cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium`
- `cmd /c npm run test:smoke`

## Implementation Notes for DeepSeek

- Start with instrumentation or local counters inside the E2E spec if needed, but remove noisy debugging before completion.
- Prefer per-test factory functions and local scoped route state over module-level mutable variables when possible.
- If module-level state must remain, reset every field in `beforeEach` and ensure route handlers read current state rather than stale closures.
- Avoid weakening user-visible assertions; the target is deterministic isolation.
