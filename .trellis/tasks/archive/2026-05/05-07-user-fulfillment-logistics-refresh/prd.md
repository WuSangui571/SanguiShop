# 用户侧支付后履约与物流刷新体验补强

## Goal

补强用户侧已支付订单从“待发货”到“已发货”的刷新和展示体验，让用户在订单详情、订单列表、状态筛选和深链恢复中都能看到一致、可理解的物流快照状态。

## Scope

- 前端用户商城订单体验薄切片。
- 复用现有订单详情、列表、筛选、履约字段和后端订单详情刷新能力。
- 不新增物流轨迹 API，不进入退款、售后、确认收货、评价、后台补偿或复杂物流轨迹。
- 后端未知履约状态只做 raw fallback 展示，不改后端枚举或接口。

## Requirements

- 已支付待发货订单允许手动刷新订单详情，获取最新 `fulfillmentStatus`、`carrier`、`trackingNo`、`shippedAt`。
- 刷新失败时保留当前 paid detail 和待发货状态，不清空已有订单详情。
- 物流快照展示区分：
  - 待发货。
  - 已发货但承运商缺失。
  - 已发货但单号缺失。
  - 完整已发货。
  - 未知 `fulfillmentStatus` raw fallback。
- 当前订单从待发货刷新到已发货后，订单详情、列表状态和筛选结果同步移动到 shipped。
- 当前筛选为空时给出“状态已变化”的可理解提示。
- `/mall?orderId=...` 深链恢复已发货订单时，直接解释物流状态来源是订单快照。
- 订单刷新 pending 时禁用重复刷新，第二次点击不发送请求；失败后允许再次刷新。
- 补强测试覆盖履约刷新、失败保留、字段缺失占位、未知状态 fallback、筛选移动、重复刷新防护和深链恢复快照。

## Acceptance Criteria

- [ ] 待发货已支付订单刷新后能展示已发货物流快照。
- [ ] 刷新失败时 detail/list 不被错误清空或回退。
- [ ] 已发货缺失 `carrier` 或 `trackingNo` 时展示明确占位。
- [ ] 未知履约状态展示 raw fallback。
- [ ] 当前筛选从待发货变为空时，用户看到状态变化提示。
- [ ] 深链恢复已发货订单使用订单快照解释物流来源。
- [ ] 重复点击刷新只发送一次请求，失败后可以再次刷新。
- [ ] 相关模型测试、typecheck、lint、build 通过或记录明确阻塞。

## Technical Notes

- 预计主要修改 `frontend/src/composables/useMallOrderStatus.ts`、`frontend/src/views/mall/MallStorefrontView.vue`、`frontend/src/views/mall/mallOrderStatusModel.ts` 和 `frontend/tests/mallOrderStatusModel.spec.ts`。
- 优先复用已有支付刷新后的 detail/list 合并逻辑，保持订单快照为单一数据来源。
- 若发现前端 API 契约文档缺少履约刷新规则，同步补充 `.trellis/spec/frontend/api-contracts.md`。
