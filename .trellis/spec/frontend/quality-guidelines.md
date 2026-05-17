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

## E2E Mock State Reset

E2E smoke 测试文件中的每个 `test.beforeEach` 必须通过一个集中的 `resetMockState()` 函数将所有可变 mock 状态重置为确定性基线，然后再安装路由处理器。

**Reset Rule**: 每个 E2E smoke 文件必须：

1. 将所有可变 mock 状态声明为模块作用域的 `let` 变量。
2. 提供单一的 `resetMockState()` 函数，重置**每一个**可变变量：
   - 计数器 → `0`
   - 捕获数组（headers、payloads、queries） → `[]`
   - 记录/Map（mockOrderById 等） → `{}`
   - Mock 响应对象、mock 错误对象 → `null`
   - Defer 标志 → `false`
   - Pending route 引用 → `null`
3. 在 `test.beforeEach` 中、路由处理器安装**之前**调用 `resetMockState()`。
4. Suite 级状态（如 `viteServer`）在 `beforeAll`/`afterAll` 中管理，不进入 `resetMockState()`。

**反模式**: `resetMockState()` 遗漏新增的可变全局变量 → 后续测试产生顺序依赖。

**正确模式**:
```ts
let counter = 0
let captures: string[] = []
let mockResponse: SomeType | null = null
let deferFlag = false
let pendingRoute: Route | null = null

function resetMockState() {
    counter = 0
    captures = []
    mockResponse = null
    deferFlag = false
    pendingRoute = null
}

test.beforeEach(async ({ page }) => {
    resetMockState()
    await setupDefaultApiRoutes(page)
})
```

此规则与上方 "E2E Deferred Route Lifecycle" 一起确保：即使 deferred route 的 `try/finally` 失败，下一测试仍从干净的 mock 状态开始。
