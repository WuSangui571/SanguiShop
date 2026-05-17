# E2E 延迟路由隔离统一加固

## Goal

统一加固 `frontend/e2e/*smoke.spec.ts` 中 deferred route / pending route 测试模式，降低 `defer*` flag 与 Vue 异步副作用、自动刷新、重复点击 guard 之间的竞态导致的 smoke flake 风险。

本任务只处理前端 E2E 测试代码与必要的测试规范记录，不修改业务实现、生产 API、DTO、DB、infra、权限、缓存、MQ 或后端代码。

## Classification

Complex Task。

理由：范围集中在前端 smoke 测试，但横跨多个 spec、涉及共享 helper 与局部隔离策略选择，并需要确保 pending route 生命周期不会跨测试泄漏。

## Background

上一轮 admin payment refresh smoke 隔离缺陷的根因不是业务逻辑，而是 smoke 测试中 deferred route flag 与 Vue 异步副作用之间存在竞态。已修复的目标用例通过增加专用 payment status 请求计数隔离了 admin order list/detail 与 payment status 请求。

相邻 smoke 中仍存在类似模式：

- admin cancel pending route
- admin fulfillment ship pending route
- mall payment refresh pending route
- mall receipt confirmation pending route

这些测试当前通过，本任务目标是提前统一测试隔离模式，避免后续 smoke 扩容时产生同类 flake。

## Requirements

- 盘点 `frontend/e2e/*smoke.spec.ts` 中所有 `defer*`、`pending*Route`、`request count`、manual `route.fulfill()` / delayed response 模式。
- 对 payment / cancel / ship / receipt 等 delayed route 建立明确隔离：
  - 首选每个 pending route 使用专用请求计数，避免普通 list/detail/load 请求误触发 deferred branch。
  - 如重复结构已经达到可读抽象边界，可新增局部 E2E helper；helper 必须服务测试隔离，不得引入生产代码依赖。
- 确保每个 pending route 在测试结束前被 fulfill、reject 或清理，不跨测试泄漏。
- 保持现有业务断言语义：duplicate guard、loading/disabled、backend `code/message/traceId` preservation、status merge / non-overwrite 行为不能被削弱。
- 如形成通用模式，补充 `frontend quality guideline` 中 E2E deferred route 注意事项；如果最终只做局部计数且无稳定通用模式，可不改 spec。

## Scope

Allowed:

- `frontend/e2e/*smoke.spec.ts`
- 可选：`frontend/e2e` 下测试专用 helper 文件
- 可选：`.trellis/spec/frontend/quality-guidelines.md`，仅当形成可复用 deferred route 测试模式
- 本任务目录内 Trellis 文件

Forbidden:

- `frontend/src/**` 业务实现
- `frontend/src/services/**` API client
- `frontend/src/types/**` DTO / API type
- backend Java、DB migration、Nacos、Docker、Redis、MQ、Gateway、auth/permission
- 修改生产 API route、payload 字段、error contract
- 为了测试通过删除或放宽现有 smoke 业务断言

## Existing API / Route Contracts Under Test

No production API contract changes are allowed. Tests must continue mocking existing gateway routes only.

Admin order:

- `GET /api/admin/orders`
- `GET /api/admin/orders/{orderId}`
- `POST /api/admin/orders/{orderId}/cancel`
- `GET /api/admin/payments/by-order/{orderId}`

Admin fulfillment:

- `GET /api/admin/fulfillments`
- `GET /api/admin/fulfillments/{orderId}`
- `POST /api/admin/fulfillments/{orderId}/ship`

Mall order/payment:

- `GET /api/orders/{orderId}`
- `GET /api/orders?page=&size=`
- `POST /api/orders/{orderId}/cancel`
- `POST /api/orders/{orderId}/receipt-confirmations`
- `GET /api/payments/{paymentNo}`

Payload fields are existing contract fields only. Relevant write requests must preserve `requestId`; ship requests must preserve `carrier` and `trackingNo`; receipt confirmation must preserve `requestId`.

## Validation / Error Matrix

| Case | Expected test behavior |
| --- | --- |
| Delayed route is intentionally pending | Test observes loading/disabled state before completing the pending response. |
| Duplicate click while pending | Dedicated route counter proves no second write/refresh request was sent. |
| Background list/detail/payment request fires during pending test | It is counted separately or handled by a non-delayed branch; it must not consume or complete the pending route intended for the assertion. |
| Pending route returns success | Existing success merge/status assertions still pass. |
| Pending route returns backend error | Existing `code/message/traceId` preservation assertions still pass where covered. |
| Test finishes or fails before route completion | The implementation must prevent pending route leakage into later tests, preferably with explicit cleanup or helper-owned lifecycle. |

## Good / Base / Bad Cases

- Good: cancel/ship/payment refresh/receipt tests each have route-specific counters or helper state proving duplicate pending requests are isolated from surrounding list/detail calls.
- Good: pending route setup and completion are local to the test or helper scope and cannot accidentally affect the next test.
- Good: helper abstraction, if added, reduces repeated deferred-route boilerplate while keeping individual route intent readable.
- Base: existing passing smoke behavior is preserved with focused changes to route counters and cleanup.
- Bad: a broad shared helper hides route-specific intent, makes assertions less explicit, or introduces brittle magic matching.
- Bad: a deferred branch is keyed only by a global flag while unrelated asynchronous requests can still enter it.
- Bad: test stability is achieved by increasing timeouts, weakening assertions, or removing duplicate-guard checks.

## Acceptance Criteria

- [ ] A focused research pass lists every deferred/pending route pattern in `frontend/e2e/*smoke.spec.ts`.
- [ ] Target delayed route tests use dedicated counters or a scoped helper so unrelated async requests cannot consume their deferred branch.
- [ ] Pending route state is fulfilled/rejected/cleaned before test completion.
- [ ] No production implementation, API type, backend, DB, infra, auth, cache, or MQ file is changed.
- [ ] Existing smoke assertions for duplicate guard, loading/disabled state, success merge, non-overwrite behavior, and backend trace preservation remain intact.
- [ ] Focused smoke tests for the affected scenarios pass.
- [ ] Related spec files pass.
- [ ] `npm run test:smoke` passes.
- [ ] `npm run lint`, `npm run typecheck`, and `npm run build` pass if implementation touched TypeScript files or helper code.
- [ ] If a reusable pattern is introduced, frontend quality guideline records the E2E deferred route rule.

## Required Tests

Focused smoke commands should include the affected tests, adjusted to exact test titles found during implementation:

```powershell
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "cancel"
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "payment refresh"
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium -g "ship"
cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "payment refresh"
cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium -g "receipt"
```

Related files:

```powershell
cmd /c npx playwright test e2e/admin-order-payment-smoke.spec.ts --project=chromium
cmd /c npx playwright test e2e/mall-order-status-smoke.spec.ts --project=chromium
```

Full frontend verification:

```powershell
cmd /c npm run test:smoke
cmd /c npm run lint
cmd /c npm run typecheck
cmd /c npm run build
```

## Implementation Notes

- Start by searching `frontend/e2e` for `defer`, `pending`, `Route`, and request counter names.
- Prefer minimal local helper if only two files need the pattern. Avoid creating a generic abstraction unless it clarifies lifecycle cleanup and route intent.
- If using a helper, it should make these states explicit: route matched, pending response captured, completion invoked, duplicate count observed, cleanup completed.
- Avoid sleeps as synchronization. Synchronize on route request promises, visible loading/disabled UI, and explicit response completion.
- Keep route matchers narrow enough to distinguish admin order detail/list/payment status and mall order detail/list/payment refresh/receipt writes.
