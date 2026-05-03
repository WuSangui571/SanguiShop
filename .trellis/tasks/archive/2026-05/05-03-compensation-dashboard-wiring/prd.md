# 补偿运维前端查询页 / Dashboard 接线

## Goal

在仓库内补齐一个最小可运行的前端骨架，并把 order / payment 补偿历史查询 API 接到真实运维查询页上，让值班人员能够直接完成筛选、分页、状态概览和 attempt 明细排查。

## Requirements

- 提供最小可运行的前端工程骨架，满足后续页面开发和本次页面交付。
- 页面支持 order / payment 两种补偿查询视角切换。
- 支持 `shopId`、`orderId`、`paymentNo`、`trigger`、`result`、`operator`、`traceId`、时间范围等筛选。
- 支持分页、刷新、重置筛选、加载态、空态和错误态。
- 展示最新状态卡片，突出最新补偿结果、最近补偿时间、匹配数量等关键信息。
- 聚合列表支持查看 attempt 明细，展示结果、错误码、原因、traceId、trigger、operator、时间。
- 所有 HTTP 请求通过共享 `httpClient` 和 `services/*Api.ts`，不在组件内散落请求逻辑。
- 前端 DTO 与后端 JSON 字段保持一致，未知状态提供 fallback。

## Out of Scope

- 手工单条 replay、bulk replay 的操作按钮和写操作链路。
- 登录、鉴权、完整后台导航体系。
- 生产级 UI 组件库引入与复杂视觉包装。

## Acceptance Criteria

- [ ] 能对 `/internal/orders/compensation-records/query` 发起真实查询并正确渲染结果。
- [ ] 能对 `/internal/payments/compensation-records/query` 发起真实查询并正确渲染结果。
- [ ] 筛选、分页、刷新、重置后页面状态与接口返回保持一致。
- [ ] 最新状态卡片与表格聚合数据字段映射正确。
- [ ] 单条聚合记录能够展开查看 attempt 明细。
- [ ] 页面具备 loading / empty / error 状态处理，并在错误提示中保留 traceId 上下文。
- [ ] `npm run typecheck` 与 `npm run build` 可通过。

## Technical Notes

- 当前仓库没有现成的 Vue 3 + TypeScript + Vite 实现，本任务需要先搭建最小骨架。
- 页面按 spec 通过 gateway 路径访问 API，并统一解析 `ApiResult<T>`。
- 状态概览优先基于当前查询结果聚合，不新增后端 summary API。
- 时间字段按字符串处理，展示层统一格式化。
