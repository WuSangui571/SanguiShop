# 商城端订单状态中心真实交互冒烟覆盖

## Goal

把商城端订单状态中心上一轮人工验收过的关键真实浏览器路径沉淀为可重复、轻量的前端冒烟测试，覆盖登录/session bootstrap、订单列表与详情选择、订单状态详情渲染、支付刷新 loading/disabled、失败 traceId 展示与快照保留、页面刷新或 deep link 恢复详情。

## Task Classification

Complex Task.

原因：虽然边界限定在前端测试/验收脚本层，但需要新增真实浏览器测试设施或脚本，编排登录/session、路由 URL、API mock、订单列表/详情、支付刷新成功/失败等多个交互路径。不得直接进入业务代码改动。

## Scope

- 新增或扩展前端真实浏览器冒烟测试，优先使用 Playwright 级别的 browser automation，而不是继续扩大 happy-dom / unit test。
- 冒烟测试必须运行真实 Vite 页面，拦截/模拟 gateway API 响应，不依赖真实后端、DB、Redis、MQ、Nacos 或支付服务。
- 冒烟测试应覆盖商城端 `/` 或 `/mall` 入口，不覆盖 admin 工作台。
- 可新增测试目录、测试 fixture/helper、Playwright 配置、npm 脚本和必要的 devDependency/lockfile。
- 可读业务实现文件以选择稳定交互方式，但除非测试无法稳定定位元素，否则不要修改 `frontend/src/**` 实现文件。

## Out of Scope / Forbidden Changes

- 不改后端 API、DTO、controller、service、状态机、DB migration、Redis/MQ、Nacos、Docker、CI infra。
- 不改订单、支付、物流、评价的业务状态合并逻辑。
- 不为测试硬编码生产 token、真实账号、真实后端地址或 secret。
- 不新增真实后端依赖，不要求开发者先启动 Java 微服务。
- 不扩大到完整 e2e 回归矩阵；本任务只做订单状态中心关键链路的轻量 smoke。
- 不重构 `MallStorefrontView.vue`、`useMallOrderStatus.ts`、model helper 或 shared http client。若必须加测试定位属性，应先说明原因，并保持最小改动。

## Existing API / Mock Contract

本任务不新增 API，不改变 payload 字段。冒烟测试中的 route mock 必须模拟现有 gateway contract。

### `POST /api/users/login`

Request fields:

- `shopId`: number
- `usernameOrMobile`: string
- `password`: string

Success response envelope:

- `code`
- `message`
- `traceId`
- `timestamp`
- `data.userId`
- `data.shopId`
- `data.accessToken`
- `data.tokenType`
- `data.expiresInSeconds`
- `data.roles`

Assertion points:

- 登录后 session 写入 `sessionStorage` key `sangui.mall.session`。
- 后续 mall API 请求携带 `Authorization: Bearer <token>`。
- 刷新页面后可从 session bootstrap 恢复已登录状态，不重复要求登录。

### `GET /api/orders?page=&size=`

Success response data:

- `page`
- `size`
- `total`
- `items[]`

Required `OrderResponse` fields used by the smoke:

- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `requestId`
- `status`
- `totalAmountCent`
- `items[]`
- optional `createdAt`
- optional `updatedAt`
- optional `fulfillmentStatus`
- optional `carrier`
- optional `trackingNo`
- optional `shippedAt`
- optional `completedAt`
- optional `reviewed`
- optional `review`

Assertion points:

- 列表加载后显示订单卡片。
- 点击列表订单会调用 detail route 并渲染对应详情。
- shipped/completed/cancelled/unknown 状态不能回退显示为 awaiting shipment。

### `GET /api/orders/{orderId}`

Success response data: `OrderResponse`.

Assertion points:

- 订单列表点击可选择详情。
- deep link `/...?...orderId=<id>&paymentNo=<paymentNo>` 可在页面首次加载或刷新后恢复对应详情。
- deep-linked shipped/completed 详情来自订单快照，不要求支付号或物流跟踪 API。

### `GET /api/payments/{paymentNo}`

Success response data:

- `paymentId`
- `paymentNo`
- `orderId`
- `orderNo`
- `shopId`
- `userId`
- `channel`
- `status`
- `amountCent`

Assertion points:

- paid awaiting-shipment + paymentNo 时，刷新支付按钮可点击。
- 点击刷新支付后按钮进入 disabled/loading 状态。
- payment refresh success 返回 `status=paid` 只能把 `created` 订单合并为 paid/unshipped；不得覆盖 shipped/completed/cancelled/unknown 主订单状态。
- payment refresh failure 显示 backend `code/message/traceId`，同时保留当前订单详情快照。

## Validation / Error Matrix

| Case | Mocked condition | Expected frontend behavior |
| --- | --- | --- |
| Missing mall session | No `sangui.mall.session` | Shows mall login form; protected order routes are not called before login/bootstrap. |
| Login success | `POST /api/users/login` returns valid `MallLoginResponse` | Session persists; order list loads; Authorization header appears on mall API requests. |
| Order list success | `GET /api/orders` returns current page | Cards render; selected detail can be loaded. |
| Detail select success | `GET /api/orders/{id}` returns selected order | Detail panel renders selected order id/order number/status. |
| Shipped detail | order `status=shipped`, `fulfillmentStatus=shipped` | Shows shipped/logistics snapshot and disables payment refresh when contract says terminal shipped is closed. |
| Completed detail | order `status=completed`, `fulfillmentStatus=completed` | Shows completed snapshot and disabled receipt/payment actions. |
| Cancelled detail | order `status=cancelled` | Shows cancelled state and disables payment/cancel actions. |
| Unknown detail | order `status=refunding` or other raw value | Renders raw status/fallback without crashing or showing awaiting-shipment regression. |
| Payment refresh pending | delayed `GET /api/payments/{paymentNo}` | Refresh button changes to loading text and is disabled; duplicate click sends no second request. |
| Payment refresh failure | payment route returns non-2xx envelope with `traceId` | Shows `code/message/traceId`; current detail/list snapshot remains visible. |
| Deep link restore | URL contains `orderId` and optional `paymentNo` | Detail loads from `GET /api/orders/{orderId}` after bootstrap; linked-only label appears when not in current list page. |
| Page reload | Existing valid session and deep link | Reload restores session and selected order detail without re-login. |

## Good / Base / Bad Cases

- Good: a browser smoke signs in through the visible login form, then verifies order list loading and selected detail rendering through mocked gateway responses.
- Good: shipped/completed/cancelled/unknown details render their true lifecycle labels/snapshots and never regress to paid awaiting-shipment.
- Good: payment refresh pending state disables the refresh button and duplicate clicks do not send multiple payment refresh requests.
- Good: payment refresh failure shows backend `traceId` and preserves the prior shipped/completed/cancelled/unknown detail snapshot.
- Good: reload/deep-link recovery restores detail from `orderId` URL state using session bootstrap.
- Base: the smoke uses route mocks instead of live backend services; API routes and envelope fields still match `.trellis/spec/frontend/api-contracts.md`.
- Base: adding Playwright dev tooling, config, script, and lockfile updates is considered frontend testing/acceptance script work.
- Bad: tests pass only in happy-dom without launching a real browser.
- Bad: tests rely on real backend/DB/payment state, real accounts, or production-like tokens.
- Bad: tests modify order/payment business logic to make assertions pass.
- Bad: a payment refresh response overwrites terminal or unknown order main status in the smoke scenario.

## Acceptance Criteria

- [ ] A repeatable real-browser smoke command exists, for example `npm run test:smoke` or `npm run test:e2e:smoke`, and is documented in the task handoff or package scripts.
- [ ] Smoke covers mall login/session bootstrap with mocked `POST /api/users/login` and verifies persisted session / Authorization behavior.
- [ ] Smoke covers order list load and order detail selection through mocked `GET /api/orders` and `GET /api/orders/{orderId}`.
- [ ] Smoke verifies shipped, completed, cancelled, and unknown status detail rendering without awaiting-shipment regression.
- [ ] Smoke verifies payment refresh button disabled/loading behavior and duplicate-click guard for an eligible paid awaiting-shipment order.
- [ ] Smoke verifies failed payment refresh displays backend `traceId` and keeps the current detail snapshot visible.
- [ ] Smoke verifies page reload or direct deep link restores selected order detail after session bootstrap.
- [ ] Existing focused unit/component regressions still pass.
- [ ] Typecheck, lint, full frontend tests, and build still pass.

## Expected Implementation Shape

Preferred files to add or modify:

- `frontend/package.json`: add smoke test script and, if needed, Playwright devDependency.
- `frontend/package-lock.json`: lock any added devDependency.
- `frontend/playwright.config.ts`: minimal config for Vite web server and Chromium smoke.
- `frontend/e2e/mall-order-status-smoke.spec.ts`: browser smoke scenarios and API route mocks.
- Optional `frontend/e2e/fixtures/mallOrderStatusSmoke.ts`: shared typed mock data/helpers if the spec gets large.

Files that should normally remain unchanged:

- `frontend/src/views/mall/MallStorefrontView.vue`
- `frontend/src/composables/useMallOrderStatus.ts`
- `frontend/src/views/mall/mallOrderStatusModel.ts`
- `frontend/src/services/httpClient.ts`
- `frontend/src/services/orderApi.ts`
- `frontend/src/services/paymentApi.ts`
- all backend files

## Required Test Commands

Run from `frontend/` unless noted:

```powershell
cmd /c npm run test:smoke
cmd /c npx vitest run --reporter=verbose src/views/mall/MallStorefrontView.spec.ts tests/mallOrderStatusModel.spec.ts tests/mallCheckoutModel.spec.ts
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm test
cmd /c npm run build
```

If Playwright browsers or packages are not installed locally, the implementer should install only the required frontend test dependency/tooling and record the exact command used. Do not start or depend on backend services.

## Planning Notes for Implementer

- Prefer browser role/text queries when stable. Avoid adding app code test ids unless real-browser selectors are too brittle.
- Keep route mocks close to the smoke spec, with typed helper builders for `ApiResult<T>`, `OrderResponse`, `PaymentResponse`, and login session payloads.
- Use `page.route('**/api/...')` to mock gateway responses; assert request headers where relevant.
- For loading state, delay the payment route promise, assert disabled/loading text, then fulfill/reject the route.
- For failure trace preservation, start with a visible shipped/completed/cancelled/unknown detail, fail `GET /api/payments/{paymentNo}`, then assert both the trace text and original detail snapshot.
- For reload/deep link, seed session via login or `sessionStorage`, navigate to URL with `orderId` and `paymentNo`, reload, then assert selected detail survives.
