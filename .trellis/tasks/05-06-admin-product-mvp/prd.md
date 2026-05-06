# 管理端商品管理 MVP

## 当前项目状态

- 商城主链路已完成：商品列表、商品详情、购物车、多商品下单、模拟支付、订单状态恢复。
- 前端 i18n/theme 基础已完成：全局 `useAppPreferences().t()`、语言/主题持久化、CSS 变量、商城和补偿运维页面文案迁移。
- 当前管理端 `/admin` 入口主要承载补偿运维看板，登录态使用 ops/admin session。
- 商品后端已有部分管理写接口：创建、编辑、发布；但缺少管理端商品列表、下架/停用、单独库存调整接口，且 gateway 目前只路由 `/api/products/**`，没有覆盖 `/api/admin/products/**`。

## 任务判断

这是一个 fullstack 复杂任务，不是纯前端页面：

- 前端需要新增管理工作区导航、商品列表、表单、操作状态和测试。
- 后端 product-service 需要补齐管理端读取和写操作契约。
- gateway 需要让商品管理 API 通过统一 `/api/...` 入口访问。
- backend/frontend spec 需要同步记录管理端商品 API、错误矩阵和前端模型测试要求。

## Goal

为单商户运营人员提供管理端商品维护 MVP，让商品、SKU、价格和可售库存可以从 `/admin` 页面维护，并保持 ADMIN 与 OPS 补偿运维入口清晰分离。

## Requirements

- 管理端导航拆分：
  - `/admin` 登录后显示管理工作区导航。
  - 至少包含商品管理与补偿运维两个工作区。
  - 商品管理要求 ADMIN 角色；补偿运维仍沿用现有 OPS/补偿权限约束。
- 商品列表管理：
  - 查询管理端商品列表，包含 draft/active/inactive 等状态。
  - 展示商品状态、价格区间、SKU 数、可售库存合计、预留库存合计。
  - 支持 loading、empty、error、retry。
  - 文案使用 `useAppPreferences().t()`，颜色使用 CSS 变量。
- 商品创建/编辑表单：
  - 字段：商品名、描述、状态展示、SKU 列表。
  - SKU 字段：SKU code、SKU 名称、价格分、可售库存。
  - 表单内部金额单位为分，展示使用 `formatMoney(cents)`。
  - 校验：商品名非空、SKU 至少 1 个、SKU code/name 非空、价格分正整数、库存非负整数、SKU code 不重复。
  - 错误展示保留后端 `code/message/traceId`。
  - 不在前端硬编码魔法商户值；写请求的 shop scope 由 ops/admin session 或后端 principal 兜底。
- 库存/状态操作：
  - 支持 active/inactive 状态切换。
  - 支持 SKU 可售库存调整入口。
  - 写按钮必须有 pending disabled，避免重复提交。
- 测试：
  - 前端纯模型测试覆盖 payload 构造、价格/库存校验、错误 traceId 保留、重复提交 guard。
  - 后端控制器/服务测试覆盖管理列表、状态切换、库存调整、权限拒绝和 DTO 校验。

## Proposed Backend Contract

Gateway route:

- Product route must include `/api/admin/products/**` and forward to product-service.

Product admin APIs:

| Function | Route | Auth | Notes |
| --- | --- | --- | --- |
| `listAdminProducts` | `GET /api/admin/products?page=&size=&status=` | ADMIN | Returns all current shop products, optional status filter. |
| `getAdminProduct` | `GET /api/admin/products/{productId}` | ADMIN | Returns detail including SKU stock. |
| `createProduct` | `POST /api/admin/products` | ADMIN | Existing route, keep request body compatible. |
| `updateProduct` | `PUT /api/admin/products/{productId}` | ADMIN | Existing route, keep request body compatible. |
| `updateProductStatus` | `POST /api/admin/products/{productId}/status` | ADMIN | Body: `{ "status": "active" | "inactive" | "draft", "requestId": "..." }`. |
| `adjustSkuStock` | `POST /api/admin/products/{productId}/skus/{skuId}/stock-adjustments` | ADMIN | Body: `{ "availableStock": 20, "requestId": "..." }`; sets sellable stock for MVP. |

Response fields:

- Product summary: `productId`, `productName`, `productDescription`, `minPriceCent`, `maxPriceCent`, `status`, `skuCount`, `availableStockTotal`, `reservedStockTotal`.
- Product detail: existing fields plus SKU `skuId`, `skuCode`, `skuName`, `priceCent`, `availableStock`, `reservedStock`.
- All responses stay wrapped in `ApiResult<T>` with `code/message/data/traceId/timestamp`.

Validation and error matrix:

| Case | HTTP | code | Frontend behavior |
| --- | --- | --- | --- |
| Missing/expired admin JWT | 401 | `AUTH_TOKEN_MISSING` / `AUTH_TOKEN_EXPIRED` | Clear admin session or show login. |
| Principal lacks ADMIN | 403 | `AUTH_FORBIDDEN` | Show no-permission state with traceId. |
| Invalid DTO | 400 | `VALIDATION_FAILED` | Show form-level error and preserve traceId. |
| Product missing for current shop | 404 | `PRODUCT_NOT_FOUND` | Refresh list/detail state. |
| SKU missing under product/shop | 404 | `PRODUCT_SKU_NOT_FOUND` | Show row-level operation failure. |
| Duplicate SKU code | 409 | `PRODUCT_SKU_CODE_EXISTS` | Keep form draft and show backend message. |
| Invalid status transition | 409 | `PRODUCT_STATUS_INVALID` | Keep current UI state, allow retry after refresh. |

## Frontend Plan

- Split admin shell in `frontend/src/App.vue` so authenticated admin users can switch between product management and compensation ops.
- Add product admin API types to `frontend/src/types/api/product.ts`.
- Extend `frontend/src/services/productApi.ts` with admin functions using `authContext: 'ops'` until a dedicated admin auth context exists.
- Add pure model helpers under `frontend/src/views/admin/productManagementModel.ts` for:
  - form draft creation,
  - payload normalization,
  - validation,
  - stock/status summaries,
  - duplicate-submit guards.
- Add `frontend/src/composables/useProductManagement.ts` for server state, pending state, retry, save, status update, and stock adjustment.
- Add `frontend/src/views/admin/ProductManagementView.vue` plus focused child components only if needed.
- Add translations for `admin.*` / `productAdmin.*` keys across zh-Hans, zh-Hant, and en.

## Acceptance Criteria

- [ ] `/admin` authenticated workspace shows product management and compensation ops as distinct areas.
- [ ] Product management list loads through gateway, handles loading/empty/error/retry, and shows status/price/SKU/stock overview.
- [ ] Create/edit form builds backend-compatible payloads with cents and non-negative stock.
- [ ] Status update and stock adjustment buttons disable while pending and do not double-submit.
- [ ] Backend admin APIs enforce ADMIN role and current principal shop scope.
- [ ] Gateway routes `/api/admin/products/**` to product-service.
- [ ] Frontend tests cover payload, validation, traceId preservation, and duplicate submit guard.
- [ ] Backend targeted tests cover new admin product routes and service behavior.
- [ ] Spec docs updated for backend product admin contract and frontend product admin model rules.

## Out of Scope

- Rich media/image upload.
- Category/brand management.
- Bulk import/export.
- Audit log UI for product changes beyond existing backend trace/error contracts.
- Multi-merchant shop switching UI.
- A dedicated ADMIN auth session separate from the existing ops/admin session.
