# 管理端订单管理交互打磨

## Goal

在已有管理端订单管理 MVP 和履约 MVP 基础上，补齐运营高频使用时最直接的交互体验问题，让订单管理从“能用”提升到“好用”，且不新增后端契约、数据库迁移或跨服务数据流。

## Scope

本任务限定为前端交互打磨，主要修改 `frontend/` 下管理端订单页面、订单管理 composable/model、必要的类型/文案/测试。后端 API、DTO、网关路由、权限、数据库和迁移不在本任务范围内。

## Requirements

- 订单详情 deep link：支持 `/admin?workspace=order&orderId=101` 直接打开订单工作台并加载指定订单详情。
- 筛选条件持久化：订单列表筛选条件保存在 URL 或 `sessionStorage`，刷新后保持当前检索状态，减少重复输入。
- 移动端布局检查：订单列表、详情抽屉和操作按钮在窄屏下可读、可点，不出现明显横向挤爆或按钮不可达。
- 取消订单确认弹窗：管理端取消订单前必须二次确认；确认期间继续保留现有 duplicate submit guard。
- 支付状态回写：刷新支付状态后，将最新 `paymentNo` / `status` 写回当前详情展示，避免列表与详情显示不一致。
- 订单状态 timeline 文案打磨：把派生时间线文案调整为运营可读，清晰表达“创建 / 已支付 / 已取消 / 已发货”等状态变化。

## Acceptance Criteria

- [ ] 访问 `/admin?workspace=order&orderId=<id>` 时，订单工作台被选中，并自动加载对应订单详情。
- [ ] 订单筛选条件刷新页面后仍可恢复；空筛选和 `all` 状态不会污染 API 请求。
- [ ] 窄屏下订单列表、详情区域、取消/刷新等操作按钮不重叠、不溢出，关键按钮仍可点击。
- [ ] 点击取消订单会先出现确认弹窗；取消确认不发请求，确认后才调用取消 API。
- [ ] 刷新支付状态后，当前详情中的支付号和支付状态立即更新。
- [ ] Timeline 文案覆盖 `created`、`paid`、`cancelled`、`shipped` 及未知状态 fallback。
- [ ] 相关纯模型测试或组件可测逻辑覆盖 deep link、筛选恢复、支付回写、取消确认入口与 timeline 文案。
- [ ] `npm run typecheck`、`npm run lint`、前端相关测试通过；若 sandbox 阻塞，要记录具体阻塞原因和替代验证。

## Technical Notes

- 复用 `services/orderApi.ts`、`services/paymentApi.ts`、`useOrderManagement.ts` 和 `OrderManagementView.vue` 的现有边界。
- URL 状态优先承载可分享的工作台/订单详情入口；表单草稿可用 `sessionStorage` 做同 tab 恢复。
- 所有用户可见文案继续走 `useAppPreferences().t()` 和 typed translation key。
- 金额展示继续使用 `formatMoney(cents)`；时间字段保持字符串处理，不假设本地时钟准确。
- API 错误仍需保留后端 `code/message/traceId`。
- 颜色和响应式样式使用已有 semantic CSS variables，不硬编码业务页面色值。

## Out of Scope

- 新增或修改后端订单、支付、履约 API。
- 新增数据库字段、索引或迁移。
- 用户侧订单物流展示、收货确认、发货通知入口。
- 真实物流轨迹查询或外部物流 API 集成。
