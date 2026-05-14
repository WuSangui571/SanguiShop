# 修复订单管理端状态流转展示错误

## Goal

修复用户前台订单已完成时，管理端订单管理把 `completed` 展示为未知状态的问题，恢复管理端对已完成订单的可信展示。

这是跨层状态合同漂移问题，需要先定位 `completed` 在后端领域状态、管理端 DTO/投影、前端 API 类型、前端状态文案映射、列表/详情/时间轴渲染中的支持情况，再按根因做最小修复。

## Problem Statement

- 已知现象：前台用户端同一订单能识别已完成状态，但 admin 订单管理端将 `completed` 展示为 unknown/未知状态。
- 影响范围：管理端订单列表、订单详情、订单状态时间轴或状态历史展示。
- 关键 traceId：`068df958-eb89-42ca-b150-6ebc39ab7121`。
- 核心风险：订单主状态、支付状态、物流状态、评论状态可能在管理端投影中被混合或覆盖，造成运营视图错误。

## Scope

### In Scope

- 复现并对比 admin 订单列表接口、admin 订单详情接口、用户前台同一订单接口响应。
- 梳理 `orderStatus` / `status` / `timeline` / `statusHistory` 等相关字段的跨层状态矩阵。
- 修复 `completed` 在管理端订单列表、订单详情、时间轴/状态历史中的识别和展示。
- 保留 unknown fallback，确保未来新增状态不会导致页面崩溃。
- 必要时同步 backend/frontend spec 中的订单状态或管理端投影合同。

### Out of Scope

- 不重构订单状态机。
- 不新增订单业务状态。
- 不修改 DB schema，除非研究证明现有字段无法表达 `completed`。
- 不修改支付、物流、评论服务的业务流程。
- 不改变用户前台订单展示语义，除非发现它与既有合同不一致且必须同步。
- 不扩大到秒杀、营销、搜索推荐等无关模块。

## Contract Investigation Matrix

DeepSeek 编码前必须完成并在实现说明中补齐下表：

| 层 | 文件/接口 | 当前字段 | `completed` 支持情况 | 结论 |
| --- | --- | --- | --- | --- |
| backend domain | order aggregate/status enum | `status` / `orderStatus` | 待确认 | 是否存在领域状态 |
| backend admin DTO | admin order response DTO | `status` 或 `orderStatus` | 待确认 | 是否透出 `completed` |
| backend query service | admin list/detail projection | 状态投影字段 | 待确认 | 是否映射丢失或被覆盖 |
| frontend type | admin order API types | union/string fallback | 待确认 | 是否包含 `completed` |
| frontend model | admin status label/timeline map | 状态文案/颜色/节点映射 | 待确认 | 是否缺项 |
| frontend view | list/detail/timeline rendering | fallback unknown | 待确认 | 是否展示 unknown |

## API / Payload Fields To Verify

### Admin Order List

- Endpoint：以代码研究结果为准，预计为 admin order list API。
- Required response fields to inspect：
  - `status` 或 `orderStatus`
  - `paymentStatus`
  - `fulfillmentStatus` 或物流状态字段
  - `reviewStatus` 如存在
  - `timeline` / `statusHistory` 如列表接口包含
  - `traceId` / response envelope metadata 如有

### Admin Order Detail

- Endpoint：以代码研究结果为准，预计为 admin order detail API。
- Required response fields to inspect：
  - 主订单状态字段：`status` 或 `orderStatus`
  - 时间轴字段：`timeline` / `statusHistory` / `events`
  - 支付、物流、评论的派生状态字段
  - response envelope 中的错误码、traceId

### User Order Detail / List Comparison

- Endpoint：以代码研究结果为准，使用同一订单对比。
- Required response fields to inspect：
  - 用户端主状态字段和 completed 文案来源
  - 用户端 status mapping/type/model 是否已经包含 `completed`

## Validation / Error Matrix

| Case | Input / Response Shape | Expected Behavior | Error / Fallback |
| --- | --- | --- | --- |
| completed order | Admin list/detail 返回 `status=completed` 或 `orderStatus=completed` | 列表、详情、时间轴显示已完成文案 | 不显示 unknown |
| completed timeline node | `timeline` / `statusHistory` 包含 `completed` 节点 | 时间轴节点显示完成状态文案和正确顺序 | 不丢节点 |
| unknown future status | 返回未知状态如 `archived_pending` | 页面保留 fallback，展示 raw value 或统一未知文案 | 不崩溃、不空白 |
| missing status | 管理端响应缺少主状态字段 | 使用现有错误/空态处理 | 不误判为 completed |
| mixed statuses | 主订单 completed，但 payment/fulfillment/review 状态不同步 | 按 spec 明确的主状态投影规则展示 | 不让支付/物流/评论状态覆盖主订单状态，除非 spec 已定义 |

## Good / Base / Bad Cases

- Good：后端 admin DTO/list/detail 明确返回 `completed`，前端 admin type/model/list/detail/timeline 全部展示已完成，unknown fallback 仍可用。
- Base：后端 domain 已有 `completed`，但 admin DTO 或 query projection 丢失，修复后 list/detail/timeline 响应补齐 `completed`。
- Bad：后端 query service 把订单主状态与 payment/fulfillment/review 状态混合，导致 completed 被覆盖或映射为 unknown；需要收敛投影规则，不能用前端硬编码掩盖。

## Acceptance Criteria

- [ ] 管理端订单列表能正确识别并展示 `completed` 订单。
- [ ] 管理端订单详情主状态能正确识别并展示 `completed`。
- [ ] 管理端订单时间轴或状态历史中的 `completed` 不再显示 unknown。
- [ ] 前台用户端同一订单与管理端对 `completed` 的状态语义一致。
- [ ] unknown fallback 保留，未来新增状态不会导致页面崩溃。
- [ ] 若后端 DTO/query/API 合同或前端 API type/model 发生变化，相关 `.trellis/spec/` 文档同步更新。
- [ ] 新增或更新测试覆盖 `created -> paid -> shipped -> completed` 的管理端投影和展示链路。

## Required Backend Tests

- Admin order query service 返回 completed 主状态。
- Admin order controller list response 包含 completed。
- Admin order controller detail response 包含 completed。
- 如存在 timeline/status history，断言 completed 节点存在、顺序正确、字段名符合合同。
- 至少覆盖 `created -> paid -> shipped -> completed` 投影链。

## Required Frontend Tests

- 管理端订单列表中 completed 显示正确文案。
- 管理端订单详情主状态显示正确文案。
- timeline/status history 中 completed 不显示 unknown。
- unknown 状态仍 fallback raw value 或现有未知文案，且页面不崩溃。

## Spec Update Requirements

如实现涉及状态枚举、DTO、管理端投影合同或前端 API 类型/展示模型变化，必须同步：

- `.trellis/spec/backend/order-create-contracts.md` 或更匹配的 backend order/admin contract spec。
- `.trellis/spec/frontend/api-contracts.md` 中的 Admin Order Management APIs。
- 如发现跨层状态投影教训具有复用价值，再补 `.trellis/spec/guides/cross-layer-thinking-guide.md`；如果只是已有合同实现缺漏，可不更新 guide。

## Implementation Boundary For DeepSeek

- 只修改订单管理端状态识别所需的最小后端/前端/spec/test文件。
- 不要修改 Trellis task 以外的历史归档、上一轮 task 或 journal，除非用户另行要求。
- 不要引入新的订单状态。
- 不要改变支付、物流、评论状态的业务含义。
- 不要删除 unknown fallback。
- 不要跳过测试；如果某项测试无法运行，必须说明原因和替代验证。

