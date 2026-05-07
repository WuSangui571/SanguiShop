# 用户侧订单生命周期时间线与操作反馈补强

## Goal

让用户侧订单详情从“结果字段展示”升级为“状态可理解”的生命周期叙事，并在不同订单状态下清楚说明可执行操作和禁用原因。

## Scope

- 仅改前端商城订单展示闭环，不扩展后端 API、DTO、数据库或 MQ 契约。
- 复用现有 `OrderResponse.status`、`fulfillmentStatus`、`carrier`、`trackingNo`、`shippedAt` 字段。
- 继续保持 unknown fallback，后端新增状态不能导致 UI 崩溃。

## Requirements

- 用户侧订单详情展示生命周期时间线：
  - `created`：订单已创建，等待支付。
  - `paid + unshipped`：支付完成，等待商家发货。
  - `shipped`：订单已发货，并说明当前只展示物流单号、不查询轨迹。
  - `cancelled`：订单已取消，并说明支付和发货动作不可用。
  - unknown：展示后端原始状态和刷新建议。
- 操作区展示按钮文案与禁用原因：
  - 待支付：可支付、可取消。
  - 已支付待发货：禁用取消，提示“支付完成后订单进入履约流程”。
  - 已发货：禁用支付/取消，提示“订单已发货”。
  - 已取消：禁用所有写操作，提示“订单已取消”。
  - unknown：禁用写操作并建议刷新。
- 订单列表最近购买增加小型阶段标识，文案与详情时间线保持一致。
- 移动端窄屏下时间线、长订单号、长物流单号换行，操作按钮堆叠不重叠。
- 补强 `mallOrderStatusModel` 测试，覆盖 lifecycle timeline 和按钮禁用原因。

## Acceptance Criteria

- [ ] `mallOrderStatusModel` 集中派生生命周期、列表阶段标识和操作状态。
- [ ] `MallStorefrontView.vue` 展示生命周期时间线和操作边界提示。
- [ ] i18n 覆盖简中、繁中、英文新增文案。
- [ ] 单测覆盖 created、paid/unshipped、shipped、cancelled、unknown。
- [ ] `npm run typecheck`、`npm run lint`、`npm run build` 通过。
- [ ] 若 Vitest 或浏览器自动化受本地环境限制，明确记录阻塞原因和已完成的替代验证。

## Technical Notes

- 本任务不新增服务 API，也不改 `services/orderApi.ts` 或 `services/paymentApi.ts`。
- 页面布局继续使用现有语义 CSS 变量，避免新增主题色体系。
- Unknown fallback 优先展示无法识别的后端原始值，并附刷新建议。
