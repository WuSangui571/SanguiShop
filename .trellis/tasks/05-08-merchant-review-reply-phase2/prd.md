# 商家评价回复二期

## Goal

在现有评价闭环“用户评价 -> 商品详情公开展示 -> 商家后台查询/隐藏/恢复治理”上增加商家正式回复能力。二期保持评价域内演进，不触碰资金逆向、库存补偿、支付退款或订单状态机。

## Scope

- 后端支持每条评价最多一条商家回复，可新增、编辑、隐藏、恢复。
- 仅允许当前 `shopId` 下的 `ADMIN` 或 `REVIEW_MANAGEMENT_ADMIN` 操作评价回复。
- 公开商品评价列表展示可见评价；评价隐藏时整条不公开；评价可见但回复隐藏时，只展示评价本体。
- 管理端评价工作区展示回复状态，并支持回复、编辑回复、隐藏/恢复回复。
- 商城商品详情页在评价 item 下展示公开商家回复。

## Out of Scope

- 不做多轮评论区。
- 不做用户追评、客服会话、AI 总结、评价摘要增强。
- 不改订单、支付、库存、退款状态机。
- 不向公开 API 暴露回复操作者、`requestId`、`traceId` 等后台审计字段。

## Backend Requirements

- 在 `oms_order_review` 增加轻量商家回复快照字段，或新增轻量回复表；优先沿用评价行的一条回复快照，降低 join 与一致性复杂度。
- 新增或扩展管理端 API：
  - `POST /api/admin/reviews/{reviewId}/reply`
  - `POST /api/admin/reviews/{reviewId}/reply/visibility`
- 回复写入请求：
  - `content` trim 后必填，长度 `1..300`。
  - `requestId` 必填。
  - 记录 `reply_operator`、`reply_trace_id`、`reply_updated_at`。
  - 重复 `requestId` 行为稳定。
  - 不允许普通用户伪造回复操作者。
- 权限与租户：
  - 只能操作当前 principal `shopId` 的评价。
  - `ADMIN` 与 `REVIEW_MANAGEMENT_ADMIN` 可操作。
  - 非 admin 或无权限返回 403。

## Public Product Review Requirements

- `GET /api/products/{productId}/reviews` 的 item 增加可选 `merchantReply`。
- 公开回复字段只包含：
  - `content`
  - `repliedAt`
- hidden review 不公开展示。
- visible review + visible reply 公开展示回复。
- visible review + hidden reply 只展示评价，不展示回复。

## Frontend Admin Requirements

- 在现有“评价管理” workspace 内扩展。
- 每条评价展示商家回复状态。
- 支持回复、编辑回复、隐藏回复、恢复回复。
- 写操作支持 pending guard，避免重复提交。
- 回复失败保留后端 `code/message/traceId`。
- 没有 `REVIEW_MANAGEMENT_ADMIN` 或 `ADMIN` 权限时，不展示入口且不能操作。

## Frontend Mall Requirements

- 商品详情评价 item 下展示“商家回复”区域。
- 支持没有回复、回复隐藏、评价隐藏的公开展示边界。
- 评价加载失败仍不影响 SKU、购物车、立即购买。
- 保持公开 payload 边界，不展示后台审计字段。

## Acceptance Criteria

- [ ] 非 admin / 无权限回复返回 403。
- [ ] 回复只能操作当前 `shopId` 的评价。
- [ ] hidden review 不进入公开商品评价列表。
- [ ] visible review + visible reply 公开展示评价和回复。
- [ ] visible review + hidden reply 只展示评价，不展示回复。
- [ ] 回复写操作 `requestId` 重放稳定。
- [ ] 后台前端 reply payload trim，并有 duplicate pending guard。
- [ ] 后台前端保留回复失败的 `code/message/traceId`。
- [ ] 商品详情公开 UI 不展示后台审计字段。

## Technical Plan

1. 研究现有 `AdminReviewManagementService`、`JdbcOrderRepository`、公开商品评价投影和前端评价管理模型。
2. 增加数据库迁移，保存单条回复快照与可见性审计字段。
3. 扩展 order-service 管理 DTO、controller、service、repository，保证权限、shop scope、trim 校验、幂等重放。
4. 扩展 order-service 内部商品评价投影和 product-service 公开 DTO，使公开 API 只返回可见回复。
5. 扩展前端 `orderApi` 类型和 `useReviewManagement` / `reviewManagementModel`，实现后台展示与写操作。
6. 扩展 `MallStorefrontView` 和公开商品 review model，渲染 `merchantReply`。
7. 增加/更新后端 targeted tests 与前端 Vitest model tests。
8. 更新相关 `.trellis/spec/` 合同文档，执行 `$check` 和 `$finish-work` 验证。

