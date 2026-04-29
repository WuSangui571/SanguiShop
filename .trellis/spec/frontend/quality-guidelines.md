# Frontend Quality Guidelines

## Definition of Done

- [ ] `npm run typecheck` 和 `npm run build` 通过。
- [ ] 核心交互有组件测试或 e2e 测试。
- [ ] 所有 API 错误码有用户可理解的处理。
- [ ] 没有硬编码后端地址、真实 token、secret。
- [ ] 秒杀、支付、登录等关键按钮有 loading/disabled 防重。
- [ ] 移动端和桌面端关键布局不溢出。

## Review Habits

1. 先核对 API 类型是否与后端 spec 一致。
2. 检查 loading/error/empty/permission states。
3. 检查重复提交、轮询清理、倒计时清理。
4. 检查金额和时间格式。
5. 检查可访问性和移动端布局。

## Forbidden Patterns

- `any` 绕过 API 类型。
- 组件内散落 fetch/axios。
- 前端自行计算最终支付金额。
- 秒杀按钮无防重复提交。
- 轮询不清理。
- 错误提示只显示系统错误且没有 traceId。