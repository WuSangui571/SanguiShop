# 商家侧评价管理一期

## Goal

为已公开展示的商品评价补上商家侧最小可用治理入口，让具备权限的后台操作者可以查询评价、隐藏评价、恢复显示，并保证隐藏评价不会继续出现在商品详情公开评价列表中。

## Scope

- 后端：order-service 新增后台评价管理 API、评价可见状态持久化、权限校验、写操作审计快照、公开商品评价过滤 hidden。
- 网关/权限：后台评价管理 API 需要 JWT，服务内要求 `ADMIN` 角色或 `REVIEW_MANAGEMENT_ADMIN` 权限。
- 前端：admin 工作台新增“评价管理” workspace，支持筛选、分页、列表状态、隐藏/恢复、错误 trace 展示和权限入口控制。
- Spec：补充 backend/frontend 评价管理 API、DB、权限、前端 payload 裁剪和测试要求。

## Out of Scope

- 商家回复评价。
- 物理删除评价或修改用户原始评价内容。
- 独立 `oms_order_review_moderation_log` 完整历史表；一期只在主表保留最新治理操作快照。
- 搜索/推荐权重、审核工作流、自动风控。
- 退款、售后、库存补偿、支付退款或交易状态机变更。

## Requirements

- 管理端可查看当前 `shopId` 范围内的全部商品评价。
- 支持筛选：
  - `productId`
  - `rating`
  - `userId`
  - `visibility`
  - `fromTime`
  - `toTime`
  - `page`
  - `size`
- 列表项展示：
  - `reviewId`
  - `orderId`
  - `orderNo`
  - `productId`
  - `skuId`
  - `skuName`
  - `rating`
  - `content`
  - `imageCount`
  - `maskedUserId`
  - `createdAt`
  - `visibilityStatus`
  - latest visibility operation snapshot fields where available
- 写操作：
  - `POST /api/admin/reviews/{reviewId}/visibility`
  - body 包含 `visibility`, `reason`, `requestId`
  - `visibility` 只允许 `visible` / `hidden`
  - `requestId` 必填并 trim
  - 操作者来自 trusted principal，不信任 body 中的用户字段
  - 记录 `visibility_reason`, `visibility_request_id`, `visibility_operator`, `visibility_trace_id`, `visibility_updated_at`
  - 重复相同 `requestId` 和相同目标可见状态应稳定返回当前结果，不重复产生不一致状态
- 公开商品评价查询必须过滤 hidden 评价；默认老评价为 visible。
- 权限：
  - 缺 principal 返回 401 / `AUTH_TOKEN_MISSING`
  - 非 `ADMIN` 且无 `REVIEW_MANAGEMENT_ADMIN` 返回 403 / `AUTH_FORBIDDEN`
  - 前端无权限账号不显示评价管理入口
- 前端：
  - 服务封装在 `services/orderApi.ts`
  - DTO 类型放在 `types/api/order.ts`
  - 业务纯函数放在 `views/admin/reviewManagementModel.ts`
  - 页面放在 `views/admin/ReviewManagementView.vue`
  - 使用 `authContext: 'ops'`
  - 支持 loading、empty、error、retry、pagination、筛选 payload 裁剪、duplicate pending guard
  - 后端错误 `code/message/traceId` 必须保留展示

## API Contract

### `GET /api/admin/reviews`

Query:

```text
page=1
size=20
productId=301
rating=5
userId=10001
visibility=visible|hidden|all
fromTime=2026-05-08T00:00:00+08:00
toTime=2026-05-08T23:59:59+08:00
```

Success code: `ADMIN_REVIEW_LIST`.

### `POST /api/admin/reviews/{reviewId}/visibility`

Request:

```json
{
  "visibility": "hidden",
  "reason": "Contains sensitive content",
  "requestId": "review-vis-20260508-0001"
}
```

Success code: `ADMIN_REVIEW_VISIBILITY_UPDATED`.

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| Principal lacks `ADMIN` and `REVIEW_MANAGEMENT_ADMIN` | 403 | `AUTH_FORBIDDEN` |
| Invalid page/size, path id, rating, visibility, or time range | 400 | `VALIDATION_FAILED` |
| Review not found in trusted shop scope | 404 | `ORDER_REVIEW_NOT_FOUND` |
| Same write `requestId` with conflicting target visibility | 409 | `IDEMPOTENCY_CONFLICT` |

## Database Contract

Add a new order-service Flyway migration after `V8`:

- `visibility_status VARCHAR(16) NOT NULL DEFAULT 'visible'`
- `visibility_reason VARCHAR(200) NULL`
- `visibility_request_id VARCHAR(64) NULL`
- `visibility_operator VARCHAR(64) NULL`
- `visibility_trace_id VARCHAR(64) NULL`
- `visibility_updated_at DATETIME NULL`

Required indexes:

- `idx_oms_order_review_shop_visibility_created (shop_id, visibility_status, created_at)`
- Query by product can initially use `oms_order_item` join because product ownership is an order item snapshot.

## Acceptance Criteria

- [ ] Admin review list returns only trusted principal `shopId` data.
- [ ] Admin review list supports product/rating/user/time/visibility filters and stable pagination.
- [ ] Non-admin and principals without `REVIEW_MANAGEMENT_ADMIN` receive 403.
- [ ] Hidden reviews do not appear in `GET /api/products/{productId}/reviews`.
- [ ] Hide/restore writes require `requestId`, persist operator/trace/reason snapshot, and are stable on duplicate submits.
- [ ] Frontend does not show review workspace for unauthorized sessions.
- [ ] Frontend trims empty filters from query params and omits `visibility=all`.
- [ ] Frontend hide/restore duplicate pending guard prevents a second request.
- [ ] Frontend preserves backend `code/message/traceId` in visible error state.
- [ ] Backend targeted Maven tests pass.
- [ ] Frontend typecheck/lint/build and targeted model tests pass where sandbox permits.

## Research Summary

## Relevant Specs

- `.trellis/spec/backend/directory-structure.md`: controller/application/domain/infrastructure boundaries.
- `.trellis/spec/backend/microservice-contracts.md`: API envelopes, DTO boundaries, idempotency and error matrix.
- `.trellis/spec/backend/gateway-security.md`: admin API/JWT/RBAC/audit responsibilities.
- `.trellis/spec/backend/database-guidelines.md`: Flyway, review table, indexes, shop scope.
- `.trellis/spec/backend/error-handling.md`: auth/validation/business error mapping and trace response.
- `.trellis/spec/backend/logging-guidelines.md`: audit fields, trace, sensitive data limits.
- `.trellis/spec/backend/order-create-contracts.md`: order review source, product-facing review projection, admin order management pattern.
- `.trellis/spec/backend/product-catalog-contracts.md`: product-service must not read `oms_*` tables directly.
- `.trellis/spec/backend/quality-guidelines.md`: targeted Maven reactor test shape.
- `.trellis/spec/frontend/api-contracts.md`: admin workspace API/service patterns and error preservation.
- `.trellis/spec/frontend/component-guidelines.md`: async UI states.
- `.trellis/spec/frontend/state-management.md`: translation and semantic CSS rules.
- `.trellis/spec/frontend/type-safety.md`: API DTO and unknown fallback requirements.
- `.trellis/spec/frontend/quality-guidelines.md`: typecheck/build/tests and duplicate action guard.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: API/DTO/DB/frontend cross-layer checklist.

## Code Patterns Found

- Backend admin controller/service pattern: `AdminOrderController`, `AdminOrderManagementService`, `AdminOrderControllerTest`, `AdminOrderManagementServiceTest`.
- Existing review source/projection: `OrderReviewService`, `ProductReviewQueryService`, `JdbcOrderRepository`, `InternalOrderReviewController`.
- Frontend admin workspace pattern: `App.vue`, `OrderManagementView.vue`, `useOrderManagement.ts`, `orderManagementModel.ts`, `orderApi.ts`.
- Permission constants pattern: `SanguiPermissionConstants` and frontend workspace permission constants in `App.vue`.

## Likely Files to Modify

- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPermissionConstants.java`
- `services/sangui-user-service/.../OpsAccessRegistry.java` and related tests if ops login must issue the new permission.
- `services/sangui-order-service/src/main/resources/db/migration/V9__add_order_review_visibility_moderation.sql`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/AdminReviewController.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/AdminReviewManagementService.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/domain/*Review*`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.java`
- `services/sangui-order-service/src/test/java/...`
- `frontend/src/App.vue`
- `frontend/src/services/orderApi.ts`
- `frontend/src/types/api/order.ts`
- `frontend/src/composables/useReviewManagement.ts`
- `frontend/src/views/admin/ReviewManagementView.vue`
- `frontend/src/views/admin/reviewManagementModel.ts`
- `frontend/src/views/admin/reviewManagementModel.test.ts`
- `.trellis/spec/backend/order-create-contracts.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/frontend/api-contracts.md`
