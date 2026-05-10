# 管理端订单管理失败态与权限边界组件测试补齐

## Goal

为管理端订单管理工作区补齐组件级回归测试，覆盖权限入口、组件 prop/session gate、列表失败/重试/空态、filter query/URL/deep link、取消写操作失败恢复与防重复提交、支付状态刷新边界。

本轮 Codex 只负责 PRD、实施计划、Trellis task/context、spec 读取、代码研究和测试计划；不写业务代码，不修改生产实现文件。后续编码交给 DeepSeek 端执行。

## Task Classification

Complex Task.

理由：虽然最终变更目标是测试补齐，但行为面跨 `App` 管理端 workspace 权限、`OrderManagementView` 组件状态、`useOrderManagement` 异步流、`orderManagementModel` query/deep-link 模型、`orderApi/paymentApi` 两类服务 mock，以及订单取消和支付刷新两条交易边界。需要先明确契约、风险和测试计划，再交给实现端编码。

## Scope

- 前端测试补齐，目标模块为管理端订单管理。
- 允许新增/修改测试文件和必要的 Trellis task/context 文件。
- 目标权限：`ADMIN`、`ORDER_MANAGEMENT_ADMIN`、`OPS_COMPENSATION_ADMIN`。
- 目标 API client：
  - `listAdminOrders`
  - `getAdminOrder`
  - `cancelAdminOrder`
  - `getAdminPaymentByOrderId`
- 目标视图：
  - `frontend/src/views/admin/OrderManagementView.vue`
  - `frontend/src/App.vue` 的 order workspace 入口与 deep-link 激活行为。

## Out of Scope

- 不修改后端 Java、Gateway、JWT、RBAC、数据库、MQ、Redis、storage、infra 或部署配置。
- 不新增或修改 API route / DTO 字段 / 后端错误码。
- 不改变业务实现行为，除非 DeepSeek 编码阶段发现现有实现无法表达既定 spec，并由用户另行授权。
- 不扩大到产品管理、履约管理、评价管理、补偿运营、客户订单页或支付发起页。
- 不为了测试便利新增依赖。

## API / Command / Payload Fields

本任务测试既有前端 API 契约，不重新定义接口。

| Function | Route | Auth Context | Payload / Query Fields | Required Test Focus |
| --- | --- | --- | --- | --- |
| `listAdminOrders({page,size,status,orderNo,userId,fromTime,toTime})` | `GET /api/admin/orders` | `ops` | `page`, `size`, optional `status`, optional `orderNo`, optional `userId`, optional `fromTime`, optional `toTime` | loading/error/empty/retry, pagination-safe query, omit `status=all`, omit blank text filters, normalize datetime-local values |
| `getAdminOrder(orderId)` | `GET /api/admin/orders/{orderId}` | `ops` | path `orderId` | deep link `/admin?workspace=order&orderId=...` loads selected detail; detail failures preserve backend error |
| `cancelAdminOrder(orderId,{requestId})` | `POST /api/admin/orders/{orderId}/cancel` | `ops` | path `orderId`, body `requestId` | confirmation required, duplicate pending clicks ignored, failure restores button and keeps backend error details, later retry sends another request |
| `getAdminPaymentByOrderId(orderId)` | `GET /api/admin/payments/by-order/{orderId}` | `ops` | path `orderId` | `PAYMENT_NOT_FOUND` during automatic detail load is treated as no payment row; manual refresh failure preserves current order/list snapshot; success merges paid/paymentNo into detail and list |

Frontend permission contract:

| Area | Allowed | Denied |
| --- | --- | --- |
| Admin order workspace in `App` | `ADMIN`, `ORDER_MANAGEMENT_ADMIN` | `OPS_COMPENSATION_ADMIN` alone |
| Order view prop/session gate | Allowed prop plus session loads list | Denied prop or missing session must not load list |

Backend error detail contract to preserve in UI/tests:

```json
{
  "code": "ORDER_STATUS_INVALID",
  "message": "Only created orders can be cancelled",
  "traceId": "trace-order-cancel-001"
}
```

## Validation / Error Matrix

| Case | Expected Behavior | Required Assertion Points |
| --- | --- | --- |
| `ADMIN` opens `/admin?workspace=order` | Order workspace is visible/rendered | App test contains `admin.orderWorkspace` or order view stub output |
| `ORDER_MANAGEMENT_ADMIN` opens `/admin?workspace=order` | Order workspace is visible/rendered | App test contains order workspace entry |
| `OPS_COMPENSATION_ADMIN` alone opens `/admin?workspace=order` | Order workspace is not visible/rendered | App test proves compensation-only role cannot see order workspace |
| `canAccessOrderWorkspace=false` | Component does not load list | `listAdminOrders` call count remains zero |
| Missing `session` | Component does not load list | `listAdminOrders` call count remains zero |
| `listAdminOrders` rejects | Error banner displays backend `message`, `code`, `traceId` | All backend details visible; raw values preserved |
| List failure state | Empty banner is not shown | Error and empty states remain mutually exclusive |
| Retry after list error | Retry calls list API again | Call count increments; successful retry clears error and renders row |
| Successful `items=[]` response | Empty banner is shown | Empty state visible and error banner absent |
| `status=all` | `status` omitted from query | Mock call args do not contain `status` |
| Blank `orderNo` / `userId` | Blank text filters omitted | Mock call args omit blank fields |
| `datetime-local` values | Values normalized before API call | `fromTime` / `toTime` include `:00+08:00` when needed |
| Reset filters | Query returns to defaults | API call is `{ page: 1, size: 20 }` |
| `/admin?workspace=order&orderId=101` | Order detail loads without list click | `getAdminOrder(101)` called and detail text renders |
| Cancel action first click | Opens confirmation only | `cancelAdminOrder` not called before confirm |
| Cancel confirm while pending | Duplicate confirm/click sends no second request | Call count remains one until promise settles |
| `cancelAdminOrder` rejects | Button/dialog returns to usable state | Pending clears; confirmation remains/dismiss behavior matches implementation; later confirm can retry |
| Cancel failure | Backend error details remain visible | `message`, `code`, `traceId` displayed |
| Cancel retry after failure | Later confirm sends another request | Call count increments; request body includes deterministic trimmed `requestId` |
| Auto payment load gets `PAYMENT_NOT_FOUND` | Treated as no payment row | No payment error banner during automatic detail load; payment display remains `--` |
| Manual payment refresh fails | Current detail/list snapshot preserved | Existing order status/paymentNo remain visible; backend error details shown |
| Manual payment refresh succeeds | Payment status merges to detail and list item | Detail/list show `paymentNo` and paid status after response |

## Good / Base / Bad Cases

Good cases:

- `ADMIN` and `ORDER_MANAGEMENT_ADMIN` sessions can access the order workspace.
- Deep-linked order detail loads from `/admin?workspace=order&orderId=...`.
- Empty successful order list renders a clear empty state.
- Filter form omits defaults/blanks and normalizes time.
- Successful payment refresh merges the payment snapshot into both current detail and the matching list item.
- Failed cancel preserves backend error details, clears pending state, and permits a later retry.

Base cases:

- Component mounts under allowed access and performs a default list query.
- Reset returns query state to default `page=1`, `size=20`, no optional filters.
- `PAYMENT_NOT_FOUND` is expected for unpaid/no-row orders during automatic detail loading.
- Existing pure model tests continue to cover status labels, timeline descriptions, URL serialization, storage restore, and helper-level duplicate gate.

Bad cases:

- `OPS_COMPENSATION_ADMIN` alone cannot see or render order workspace.
- Denied prop/session gate cannot fetch order list.
- List failure must not render misleading empty state.
- Cancel must not send before confirmation.
- Pending duplicate cancel must not send a second request.
- Manual payment refresh failure must not erase current detail or list snapshots.
- Tests must not mutate production code or weaken selectors to pass by accident.

## Required Tests and Assertion Points

Targeted tests to add or retain:

- Permission boundary:
  - `ADMIN` can access order workspace.
  - `ORDER_MANAGEMENT_ADMIN` can access order workspace.
  - `OPS_COMPENSATION_ADMIN` alone cannot see order workspace.
  - `OrderManagementView` no-access prop gate does not call `listAdminOrders`.
  - Missing session does not call `listAdminOrders`.
- List states:
  - `listAdminOrders` failure displays backend `code`, `message`, `traceId`.
  - Retry calls `listAdminOrders` again.
  - Failure state does not render empty banner.
  - Successful `items=[]` response renders empty banner.
- Filter query / URL / deep link:
  - `status=all` omitted.
  - Blank `orderNo` / `userId` omitted.
  - `datetime-local` values normalized before API call.
  - Reset returns default query.
  - `/admin?workspace=order&orderId=...` passes `initialOrderId` and loads `getAdminOrder(orderId)`.
- Cancel write failure recovery:
  - Cancel button opens confirmation and does not call API before confirm.
  - `cancelAdminOrder` failure restores interactive state.
  - Backend `code/message/traceId` remains visible.
  - Later retry sends another request.
  - Pending duplicate confirm/click sends no second request.
  - Request body includes deterministic `requestId`.
- Payment refresh:
  - `PAYMENT_NOT_FOUND` during automatic detail load is treated as no payment row, without error banner.
  - Manual refresh failure preserves current detail/list snapshot and displays backend details.
  - Manual refresh success merges payment status/paymentNo into current detail and list item.

Required command set before handoff back to Codex check:

```powershell
cd frontend; cmd /c npm run test -- orderManagement
cd frontend; cmd /c npm run test -- App
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

Optional broader regression after targeted commands:

```powershell
cd frontend; cmd /c npm run test
```

## Technical Notes

- Prefer existing admin fulfillment component-test structure from `frontend/src/views/admin/FulfillmentManagementView.spec.ts`.
- Prefer existing admin review tests for backend error preservation, action retry, pending duplicate guards, and deterministic `crypto.randomUUID` stubbing.
- `OrderManagementView` calls `bootstrap()` through an immediate session watcher; tests should clear mocks after mount before asserting explicit search/reset/manual refresh calls.
- `bootstrap()` loads list and, when `initialOrderId` is present, loads detail; if a detail load succeeds it automatically invokes `refreshPaymentStatus(false)`.
- In automatic detail load, `PAYMENT_NOT_FOUND` should not surface as `paymentError`; manual refresh uses default `showMissingError=true`.
- Use typed fixtures for `AdminOrderSummaryResponse`, `AdminOrderDetailResponse`, `PaymentResponse`, `PersistedOpsSession`, and `ApiResponseMeta`.
- Keep tests free of `any`, `console.log`, `debugger`, unnecessary non-null assertions, and brittle assumptions about exact duplicate mount call counts unless the implementation contract requires it.
- Backend `code`, `message`, and `traceId` are business/debug data and must be asserted as raw values.

## Acceptance Criteria

- [ ] Trellis task is created and active.
- [ ] `prd.md` documents scope, contracts, validation/error matrix, Good/Base/Bad cases, and required tests.
- [ ] Implementation/check contexts include relevant specs and code patterns.
- [ ] DeepSeek handoff identifies likely files and non-negotiable boundaries.
- [ ] Codex planning turn does not modify business implementation files.
- [ ] DeepSeek implementation adds focused tests for all required areas or documents any impossible assertion with reason.
- [ ] Required targeted frontend commands pass before returning to Codex check/finish-work.
