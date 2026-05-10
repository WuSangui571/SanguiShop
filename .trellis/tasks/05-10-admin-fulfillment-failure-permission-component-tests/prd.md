# 管理端履约管理失败态与权限边界组件测试补齐

## Goal

为管理端履约管理工作区补齐组件级回归测试，覆盖权限入口、列表失败/重试/空态、筛选 query 归一化，以及发货写操作失败恢复与防重复提交。

本轮只补测试，不改业务实现、不改 API 契约、不改后端、不改数据库、不改权限实现。若现有实现无法满足既定契约，优先以测试暴露缺口，除非后续执行端明确获得新的实现修改授权。

## Task Classification

Complex Task.

理由：虽然目标是测试补齐，但覆盖 `App` 管理端 workspace 权限入口、`FulfillmentManagementView` 列表状态、filter query、后端错误详情保留、写操作 pending/失败恢复等多个行为面，需要先建立 PRD、spec context 和 focused code research，再交给实现端编码。

## Scope

- 前端组件测试补齐。
- 目标工作区：管理端履约管理。
- 目标权限：`ADMIN`、`LOGISTICS_FULFILLMENT_ADMIN`、`OPS_COMPENSATION_ADMIN`。
- 目标 API client：`services/fulfillmentApi.ts`。
- 目标视图：履约管理视图及 App 级 workspace 入口。

## Out of Scope

- 不修改后端 Java 代码。
- 不修改 Gateway、JWT、RBAC、数据库、MQ、Redis、storage 或部署配置。
- 不新增或修改 API route / DTO 字段。
- 不改变用户可见业务逻辑，除非实现端发现测试无法表达既有 contract 且另行获得授权。
- 不扩大到订单管理、评价管理、支付、补偿运营工作区。

## API / Command / Payload Fields

Existing frontend API contract to test, not redefine:

| Function | Route | Auth Context | Payload / Query Fields | Test Focus |
| --- | --- | --- | --- | --- |
| `listAdminFulfillments({page,size,status,orderNo,userId,fromTime,toTime})` | `GET /api/admin/fulfillments` | `ops` | `page`, `size`, optional `status`, optional `orderNo`, optional `userId`, optional `fromTime`, optional `toTime` | Loading/error/empty/retry, query omission and datetime normalization |
| `getAdminFulfillment(orderId)` | `GET /api/admin/fulfillments/{orderId}` | `ops` | path `orderId` | Existing detail behavior only if needed for selectors; no new required coverage in this task |
| `shipAdminFulfillment(orderId,{requestId,carrier,trackingNo})` | `POST /api/admin/fulfillments/{orderId}/ship` | `ops` | path `orderId`, body `requestId`, `carrier`, `trackingNo` | Failure restores action availability, preserves draft and backend error details, retry sends another request, duplicate pending click is ignored |

Frontend permission contract:

| Area | Allowed | Denied |
| --- | --- | --- |
| Admin fulfillment workspace in `App` | `ADMIN`, `LOGISTICS_FULFILLMENT_ADMIN` | `OPS_COMPENSATION_ADMIN` alone |
| Fulfillment view prop gate | Allowed prop/session state loads list | Denied prop/session state must not load list |

Backend error detail contract to preserve in UI/tests:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "trackingNo is required",
  "traceId": "trace-fulfillment-001"
}
```

## Validation / Error Matrix

| Case | Expected Behavior | Required Assertion Points |
| --- | --- | --- |
| Authorized `ADMIN` opens admin app | Fulfillment workspace is visible/selectable | App test renders workspace entry for `ADMIN` |
| Authorized `LOGISTICS_FULFILLMENT_ADMIN` opens admin app | Fulfillment workspace is visible/selectable | App test renders workspace entry for logistics role |
| `OPS_COMPENSATION_ADMIN` alone opens admin app | Fulfillment workspace is not visible | App test proves compensation-only role cannot see fulfillment workspace |
| View prop gate denies access | Component does not call `listAdminFulfillments` | Mock API call count remains zero; no misleading list/empty state |
| List API rejects with backend envelope | Error state displays `code`, `message`, `traceId` | All backend details are visible/preserved |
| Retry after list error | Retry triggers another `listAdminFulfillments` call | Call count increments and query remains valid |
| List failure state | Empty banner is not shown | Error and empty states are mutually exclusive |
| Successful empty list response | Empty banner is shown | `items=[]` success renders empty state |
| `status=all` filter | `status` omitted from API query | Mock call args do not contain `status` |
| Blank `orderNo` / `userId` | Blank text filters omitted | Mock call args omit blank text fields |
| `datetime-local` inputs | Values normalized to ISO-like API strings | Mock call args include normalized `fromTime` / `toTime` |
| Reset filters | Query returns to default | API call omits optional filters and uses default page/size/status |
| Ship API fails | Ship button becomes clickable again | Pending state clears after rejection |
| Ship API fails | Backend error details remain visible | `code`, `message`, `traceId` displayed |
| Ship API fails | Draft carrier/tracking fields are preserved | Input values remain unchanged after rejection |
| Ship retry after failure | Later submit sends another request | Call count increments; payload still trimmed and contains `requestId` |
| Duplicate ship click while pending | No second request is sent | Call count remains one until pending request settles |

## Good / Base / Bad Cases

Good cases:

- `ADMIN` and `LOGISTICS_FULFILLMENT_ADMIN` sessions can see the fulfillment workspace.
- Empty successful fulfillment list (`items=[]`) renders a clear empty state.
- Filter form omits default/blank fields and normalizes time values.
- Failed ship action preserves user draft and allows a later retry.

Base cases:

- Component mounts under allowed access and performs the default list query once.
- Reset returns the list query to default `page`, `size`, and no optional filters.
- Fulfillment status labels continue to tolerate existing status values; do not remove existing status tests if present.

Bad cases:

- `OPS_COMPENSATION_ADMIN` alone cannot see the fulfillment workspace.
- Denied prop gate does not fetch fulfillment data.
- Backend list error shows `code/message/traceId` and does not show empty state.
- Pending duplicate ship click does not send a second request.
- Failed ship submit does not clear carrier/tracking draft.

## Required Tests and Assertion Points

Targeted command:

```powershell
cd frontend; cmd /c npm run test -- fulfillmentManagement
```

Must include/retain tests for:

- Permission boundary:
  - `ADMIN` can access fulfillment workspace.
  - `LOGISTICS_FULFILLMENT_ADMIN` can access fulfillment workspace.
  - `OPS_COMPENSATION_ADMIN` alone cannot see fulfillment workspace.
  - Component no-access prop/session gate does not call list API.
- List states:
  - `listAdminFulfillments` failure displays backend `code`, `message`, `traceId`.
  - Retry calls `listAdminFulfillments` again.
  - Failure state does not render empty banner.
  - Successful `items=[]` response renders empty banner.
- Filter query:
  - `status=all` omitted.
  - Blank `orderNo` / `userId` omitted.
  - `datetime-local` values normalized before API call.
  - Reset returns default query and omits optional filters.
- Ship action failure recovery:
  - `shipAdminFulfillment` failure restores button enabled state.
  - Backend `code/message/traceId` remains visible.
  - Carrier/tracking draft inputs are preserved.
  - A later retry sends another request.
  - Pending duplicate click sends no second request.

Required full commands before handing back for check:

```powershell
cd frontend; cmd /c npm run test -- fulfillmentManagement
cd frontend; cmd /c npm run test -- App
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

## Technical Notes

- Prefer the existing admin review component test patterns from `ReviewManagementView.spec.ts` for backend error envelope, retry, empty/error separation, requestId assertions, and pending duplicate guards.
- Prefer the existing App-level admin workspace permission tests from `App.spec.ts`; use typed ops session mocks and clean wrapper teardown.
- Keep mocks typed; avoid `any`, `console.log`, `debugger`, unnecessary non-null assertions, and selector coupling that makes tests brittle.
- Backend `code`, `message`, and `traceId` are business/debug data and must be asserted as raw values, not translated.
- `requestId` generation should be tested deterministically by mocking/stubbing `crypto.randomUUID` or by following the existing project test helper pattern.
- Do not add new dependencies unless the existing test stack cannot express the required behavior.

## Acceptance Criteria

- [ ] Trellis task is created and active.
- [ ] `prd.md` documents scope, contracts, validation/error matrix, Good/Base/Bad cases, and required tests.
- [ ] Implementation/check contexts include relevant specs and code patterns.
- [ ] DeepSeek handoff identifies likely files and non-negotiable boundaries.
- [ ] No business implementation files are changed by Codex during this planning turn.
- [ ] After DeepSeek implementation, required test commands pass before final check/finish-work.
