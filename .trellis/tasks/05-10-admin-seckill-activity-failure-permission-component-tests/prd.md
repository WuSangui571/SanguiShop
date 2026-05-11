# 管理端秒杀/活动管理失败态与权限边界组件测试补齐

## Goal

补齐管理端秒杀/活动管理工作区的组件级回归测试，让活动这个高风险交易入口具备与产品、订单、履约、评价管理一致的失败态、权限边界、时间状态、写操作恢复和库存绑定保护。

本轮 Codex 只负责 PRD、Trellis task/context、spec 读取、代码研究和测试计划。DeepSeek 端负责后续编码实现。

## Task Classification

Complex Task。

原因：

- 涉及管理端 workspace 权限入口、活动管理 API service/type/composable/view/model/spec。
- 涉及活动状态、服务端时间、SKU/库存边界、写操作 requestId 和 pending duplicate guard。
- 涉及前端对既有/拟定 Gateway API contract 的断言，但本任务不要求新增后端业务实现。

## Scope

### In Scope

- 管理端 seckill/marketing 活动工作区的前端组件测试。
- 如现有前端缺少活动管理工作区，允许 DeepSeek 端按现有 admin workspace 模式补齐最小必要前端实现，使测试可落地。
- 管理端 App workspace 权限测试。
- 活动列表失败、重试、空态测试。
- 活动状态与服务端时间展示测试。
- 创建、更新、发布/下架等写操作失败恢复与 duplicate guard 测试。
- 活动 SKU 绑定、活动库存 UI 校验和 SKU snapshot 失败保留当前活动详情测试。
- 前端 API service/type/model/composable/view 的必要测试支撑。

### Out of Scope

- 不新增或修改后端 seckill/marketing 业务实现。
- 不新增 DB migration、Redis key、MQ event、Gateway route 或真实权限后端逻辑，除非后续明确追加。
- 不改造前台秒杀下单链路、订单创建、支付、履约、补偿运营。
- 不引入新 UI 设计体系或大范围重构 admin shell。
- 不扩大到压测、Redis Lua、MQ consumer、JMeter/Locust。

## User Requirements

- `ADMIN` role 可见活动管理工作区。
- `SECKILL_ACTIVITY_ADMIN` 或既有项目选择的 marketing/seckill 管理权限可见活动管理工作区。
- `OPS_COMPENSATION_ADMIN` alone 不显示活动工作区。
- prop/session gate 下不加载活动列表。
- 列表失败时保留 backend `code/message/traceId`。
- retry 重新调用列表 API。
- failure 时不显示 misleading empty。
- `items=[]` 成功响应显示 empty。
- 活动状态覆盖 `draft` / `scheduled` / `active` / `ended` / unknown fallback。
- 服务端时间或 ISO 时间展示不依赖本地猜测。
- `status=all` 省略或按既有 API contract 断言。
- 创建/更新/上下架失败后保留 draft/detail，按钮恢复，backend error details 保留。
- pending duplicate click 不发第二次请求。
- 非负库存、活动库存不超过可用库存边界的 UI 校验。
- SKU snapshot 失败保留当前活动详情。
- `requestId` 生成和 trimming。

## Proposed Frontend Contract

DeepSeek 应优先查看现有实现；如果不存在现成 contract，按以下最小契约实现前端 service/type，并保持可替换后端 API。

### Permission

- Workspace key: `seckill`。
- Permission constant: `SECKILL_ACTIVITY_ADMIN`。
- Visibility rule: `ADMIN` role OR `SECKILL_ACTIVITY_ADMIN` permission。
- Negative rule: `OPS_COMPENSATION_ADMIN` alone must not reveal or mount seckill activity workspace.

### API / Command / Payload Fields

All admin seckill activity routes must go through Gateway path and `authContext: 'ops'`.

| Function | Route | Method | Payload / Query | Required UI Handling |
| --- | --- | --- | --- | --- |
| `listAdminSeckillActivities({page,size,status})` | `/api/admin/seckill/activities` | `GET` | query `page`, `size`, optional `status` | loading, error, retry, empty; omit `status` when filter is `all` unless existing implementation requires literal `all`. |
| `getAdminSeckillActivity(activityId)` | `/api/admin/seckill/activities/{activityId}` | `GET` | path `activityId` | load detail and bound SKU snapshot; preserve current detail if refresh fails. |
| `createAdminSeckillActivity(payload)` | `/api/admin/seckill/activities` | `POST` | see `AdminSeckillActivityDraftRequest` | validate draft; preserve draft on failure; disable duplicate submit. |
| `updateAdminSeckillActivity(activityId,payload)` | `/api/admin/seckill/activities/{activityId}` | `PUT` | see `AdminSeckillActivityDraftRequest` plus `requestId` if existing contract requires | preserve detail/draft on failure; disable duplicate submit. |
| `updateAdminSeckillActivityStatus(activityId,payload)` | `/api/admin/seckill/activities/{activityId}/status` | `POST` | `{status, requestId}` | publish/unpublish/end controls; generate/trim requestId; disable duplicate submit. |
| `bindAdminSeckillActivitySku(activityId,payload)` | `/api/admin/seckill/activities/{activityId}/skus` | `POST` or existing route | `{productId, skuId, activityStock, seckillPriceCent?, requestId}` | validate non-negative and activityStock <= availableStock; preserve detail on failure. |

Suggested TypeScript DTO shape:

```ts
export type AdminSeckillActivityStatus = 'draft' | 'scheduled' | 'active' | 'ended' | string
export type AdminSeckillActivityStatusFilter = AdminSeckillActivityStatus | 'all'

export interface AdminSeckillActivitySummaryResponse {
  activityId: number
  activityName: string
  status: AdminSeckillActivityStatus
  startsAt: string
  endsAt: string
  serverTime: string
  skuCount: number
  totalActivityStock: number
  soldCount: number
}

export interface AdminSeckillActivitySkuResponse {
  productId: number
  productName: string
  skuId: number
  skuCode: string
  skuName: string
  priceCent: number
  seckillPriceCent: number
  availableStock: number
  activityStock: number
  soldCount: number
}

export interface AdminSeckillActivityDetailResponse extends AdminSeckillActivitySummaryResponse {
  description?: string | null
  skus: AdminSeckillActivitySkuResponse[]
}

export interface AdminSeckillActivityDraftRequest {
  shopId: number
  userId: string
  activityName: string
  description?: string | null
  startsAt: string
  endsAt: string
  skus: Array<{
    productId: number
    skuId: number
    activityStock: number
    seckillPriceCent: number
  }>
}

export interface AdminSeckillActivityStatusUpdateRequest {
  status: 'draft' | 'scheduled' | 'active' | 'ended' | string
  requestId: string
}
```

## Validation / Error Matrix

| Case | Expected UI Behavior | Assertion Points |
| --- | --- | --- |
| Missing session | Do not load list; show/access fallback consistent with sibling admin views. | `listAdminSeckillActivities` not called. |
| `canAccessSeckillWorkspace=false` | Do not load list. | No API call, no activity workspace actions. |
| `OPS_COMPENSATION_ADMIN` only | App does not render seckill workspace tab/view. | `App.spec.ts` negative permission assertion. |
| List request fails | Show error with backend `message`, `code`, `traceId`; do not show empty state. | `.banner.error` contains details; `.banner.empty` absent. |
| Retry list succeeds | Calls list API again, clears error, renders item. | API call count increments; list item visible. |
| List success `items=[]` | Show explicit empty state. | Empty banner visible only when no error. |
| Unknown activity status | Render raw backend value, no crash. | Text contains unknown status such as `flash_freeze`. |
| Time display | Use returned ISO/server time fields; do not derive availability from local clock. | Labels/countdown/status tests should be deterministic with mocked response fields. |
| `status=all` filter | Omit `status` from query or assert existing service contract if already defined. | Service unit/component assertion. |
| Create/update failure | Preserve form draft/detail; button restored; backend error displayed. | Inputs retain values; error includes `code/message/traceId`; disabled cleared. |
| Status update failure | Preserve current detail/status; button restored; backend error displayed. | Detail text still shows old status/name; API payload includes trimmed `requestId`. |
| Duplicate pending submit | Second click while promise pending does not send another request. | API called once with controlled promise. |
| Negative activity stock | Block submit locally with validation error. | API not called. |
| Activity stock > available stock | Block submit locally with validation error. | API not called; validation copy visible. |
| SKU snapshot/detail load failure | Preserve current activity detail and show backend error. | Current activity fields remain; error includes trace. |
| Request ID generation/trimming | Write payloads carry generated UUID or trimmed injected requestId. | Spy `crypto.randomUUID`; assert exact payload. |

Backend-relevant error codes to preserve include:

- `AUTH_FORBIDDEN`
- `VALIDATION_FAILED`
- `SECKILL_ACTIVITY_NOT_FOUND`
- `SECKILL_NOT_STARTED`
- `SECKILL_ENDED`
- `STOCK_NOT_ENOUGH` or `PRODUCT_STOCK_NOT_ENOUGH`
- `IDEMPOTENCY_CONFLICT`
- `DOWNSTREAM_TIMEOUT`

## Good / Base / Bad Cases

### Good

- `ADMIN` and `SECKILL_ACTIVITY_ADMIN` can see and mount the activity workspace.
- Missing session, no-access prop, and `OPS_COMPENSATION_ADMIN` alone never trigger list loading.
- List failure preserves `code/message/traceId` and never masquerades as empty.
- Empty successful response shows empty state.
- `draft`, `scheduled`, `active`, `ended`, and unknown status values render deterministically.
- Activity write failures preserve draft/detail and restore controls.
- Duplicate pending create/update/status/SKU bind clicks send one request.
- SKU activity stock validation blocks negative and over-available stock before API call.
- Request IDs are generated and trimmed for write operations.

### Base

- If no admin seckill API exists yet, implement a frontend-only service/type boundary following the proposed contract and component tests mock that service.
- If project naming chooses `marketing` rather than `seckill`, use one consistent workspace label/permission/route family and document it in research or spec before coding.
- Status filter may send `status=all` only if an existing service implementation already does so; otherwise omit `all`.

### Bad

- `OPS_COMPENSATION_ADMIN` sees activity workspace.
- Component mounts and calls activity list when session is missing or prop gate is false.
- Backend errors lose `traceId` or display only a generic frontend message.
- Failed list displays empty state.
- Frontend uses local `Date.now()` to decide activity status when server-provided status/time exists.
- Duplicate clicks issue multiple create/update/status/SKU bind requests.
- UI permits activity stock above available SKU stock.
- Tests add broad `any`, direct `fetch`, hardcoded backend origins, or bypass `services/httpClient`.

## Required Tests

### `frontend/src/App.spec.ts`

- Renders activity workspace for `ADMIN`.
- Renders activity workspace for `SECKILL_ACTIVITY_ADMIN` or project-chosen equivalent permission.
- Does not render activity workspace for `OPS_COMPENSATION_ADMIN` alone.
- Optionally verifies unavailable active workspace falls back to first accessible workspace without mounting activity view.

### New or Existing `frontend/src/views/admin/SeckillActivityManagementView.spec.ts`

- prop gate and missing session do not call list API.
- list failure renders backend `code/message/traceId`.
- list failure does not render empty state.
- retry calls list again and renders second result.
- empty successful list renders empty state.
- default query behavior covers `page=1`, `size=20`, and status all omission/contract.
- status label coverage: `draft`, `scheduled`, `active`, `ended`, unknown raw fallback.
- time display is based on response ISO/server time fields.
- create failure preserves draft and backend error; button recovers.
- update failure preserves detail/draft and backend error; button recovers.
- publish/unpublish/end failure preserves detail and backend error.
- duplicate pending create/update/status clicks call API once.
- SKU bind/detail load failure preserves current detail and shows backend error.
- stock validation blocks negative and `activityStock > availableStock`.
- requestId generation/trimming appears in write payloads.

### Optional Model/Service Tests

- status filter query builder omits `all`.
- request payload builder trims activity name, description, SKU code/id inputs, and `requestId`.
- validation helper rejects invalid stock and impossible time range.

## Required Commands

Run at minimum after DeepSeek implementation:

```powershell
cd frontend
cmd /c npm run test -- SeckillActivityManagementView
cmd /c npm run test -- App
cmd /c npm run typecheck
cmd /c npm run lint
cmd /c npm run build
```

If the implementation touches shared frontend contracts or many files, also run:

```powershell
cd frontend
cmd /c npm run test
```

Backend tests are not required unless backend code is changed. If backend seckill/marketing contracts are modified despite this PRD boundary, run the owning service Maven tests with explicit `-pl` and update relevant `.trellis/spec/backend/*.md`.

## Implementation Notes For DeepSeek

- Follow existing admin workspace patterns before introducing new abstractions.
- Reuse `HttpClientError` and existing error formatting behavior so `code/message/traceId` are preserved.
- Reuse sibling admin tests' controlled-promise duplicate guard pattern.
- Keep user-facing text in `useAppPreferences` translation keys if new visible copy is added.
- Use semantic CSS variables if a view is added; no hardcoded page/card/input/button colors.
- Keep all API calls in `frontend/src/services/*Api.ts`; components must not call `fetch` directly.
- Keep tests deterministic; do not rely on the machine's local timezone or current clock.
