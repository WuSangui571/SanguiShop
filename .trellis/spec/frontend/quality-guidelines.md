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
- E2E 中有意延迟的 Playwright `Route` 仅依赖页面 teardown 清理。

## E2E Deferred Route Lifecycle

E2E 测试中有意延迟（deferred）的 Playwright Route 必须显式管理生命周期：创建、捕获、释放或 abort。

**Cleanup Rule**: 每个延迟路由所在的断言区域必须包裹 `try/finally`。`finally` 中检查 pending route 引用是否非空，若仍 pending 则调用 `route.abort().catch(() => {})` 并置空引用。通过路径上在 `try` 内显式 fulfill 后置空引用，避免 `finally` 二次处理。

```ts
deferXxx = true
try {
  // click → assert pending UI → assert duplicate guard
  // fulfill route → null ref → assert final UI recovery
} finally {
  if (pendingXxxRoute !== null) {
    await pendingXxxRoute.abort().catch(() => {})
    pendingXxxRoute = null
  }
}
```

- 不要仅靠页面关闭或 `afterEach` 的 `resetMockState()` 清理 pending route。
- 不要在 `finally` 中 fulfill 成功响应（断言已失败时这会让后续 UI 继续走非预期路径）。
- `.catch(() => {})` 避免 cleanup 自身的 abort 异常掩盖原始断言失败；通过路径仍应在 fulfill 后立即清空引用。
