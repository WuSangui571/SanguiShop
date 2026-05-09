# 管理端评价图片预览交互组件测试补齐

## Goal

为已经上线并手动验证通过的 Admin Review Management 评价图片大图预览交互补齐自动化组件测试，覆盖关闭按钮、遮罩点击、Escape 关闭，以及失败图片和 unknown fallback 不可打开预览的边界。

## Scope Classification

- Type: Simple Task
- Area: Frontend tests only
- Package layer: `frontend`
- Backend/API/DB/infra/storage/AI/permission changes: none
- Implementation ownership: Claude Code 编码执行；Codex 本轮只准备 PRD、Trellis context、代码研究和测试计划。

## Background

上一轮任务 `05-09-admin-review-image-preview` 已完成并提交：

- Admin Review Management 支持可正常加载的评价图片缩略图点击打开大图预览。
- 预览层支持关闭按钮、遮罩点击、Escape 关闭。
- 已加载失败图片和仅有 `imageCount` 的 unknown fallback 不可预览。
- 手动验证通过，模型测试覆盖了图片状态构建，但关闭按钮、遮罩点击、Escape 关闭等组件交互尚缺自动化覆盖。

## Requirements

- 调查当前 `frontend` 测试基础，确认 Vitest 是否已经能跑 Vue component test。
- 判断是否已经存在 `@vue/test-utils`；如果存在则复用，如果不存在再评估是否需要引入。
- 为 `ReviewManagementView.vue` 或合适的局部测试入口补齐组件交互测试。
- 构造最小 admin review 列表状态，不依赖真实后端。
- 点击可预览缩略图后应打开预览 overlay。
- 点击关闭按钮后应关闭 overlay。
- 点击遮罩后应关闭 overlay。
- 触发 `Escape` 后应关闭 overlay。
- 失败图片占位不可打开 overlay。
- unknown fallback 占位不可打开 overlay。
- 测试应保持范围集中，不扩大为业务逻辑重构、视觉重构、API 合约变更或新功能开发。

## Non-Goals

- 不改后端、Gateway、数据库、存储、权限、路由或 API payload。
- 不改变评价管理现有隐藏、恢复、回复、回复可见性行为。
- 不新增图片轮播、多图切换、下载、删除、审核历史等功能。
- 不引入新的全局状态或新页面路由。
- 不为了测试大幅重构 `ReviewManagementView.vue`；如需小幅测试友好性调整，必须保持行为不变且局部化。

## Contract / Payload Impact

本任务为 frontend component test 补齐，不改变跨层契约。

| Item | Status |
| --- | --- |
| API / command / route | No change |
| Request / response payload fields | No change |
| Validation / backend error code | No change |
| Database / Redis / MQ / storage | No change |
| Auth / permission boundary | No change |
| User-facing copy | No intended change |

## Validation / Error Matrix

| Case | Expected Behavior | Assertion Point |
| --- | --- | --- |
| 正常 public image URL 缩略图点击 | 打开预览 overlay，显示对应大图 | 找到 dialog/overlay 与预览图片 URL/alt |
| 关闭按钮点击 | 关闭预览 overlay | overlay 不再存在 |
| 遮罩点击 | 关闭预览 overlay | overlay 不再存在 |
| `Escape` keydown | 关闭预览 overlay | overlay 不再存在 |
| 缩略图 `error` 后再点击 | 不打开预览 overlay | overlay 不存在；失败占位存在 |
| 仅 `imageCount` 无 `imageUrls` 的 unknown fallback | 不打开预览 overlay | overlay 不存在；unknown fallback 存在 |
| 失败图片 / unknown fallback 后其它管理动作 | 现有动作按钮仍可用 | 仅在当前测试可低成本覆盖时断言，不强行扩大范围 |

## Good / Base / Bad Cases

- Good: 有 `imageUrls` 且缩略图未失败，点击缩略图打开 overlay，并可用关闭按钮、遮罩、Escape 任一方式关闭。
- Base: 最小 admin review 列表、最小翻译/偏好环境和 mock API 足以渲染评价列表与图片区域。
- Bad: 缩略图已触发加载失败，或旧 payload 只有 `imageCount`，点击对应占位不得打开 overlay。

## Required Tests

Implementation should add or extend tests so the following commands pass:

```bash
cd frontend; cmd /c npm run test -- reviewManagement
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

Required assertion coverage:

- Vue component test infrastructure works under current Vitest setup.
- 可预览缩略图点击打开 overlay。
- 关闭按钮关闭 overlay。
- 遮罩点击关闭 overlay。
- Escape 关闭 overlay。
- 失败图片占位不可打开 overlay。
- unknown fallback 不可打开 overlay。

## Technical Notes

- 优先复用现有 `frontend` Vitest 配置、test setup、mock 方式和 `reviewManagementModel` fixtures。
- 如已有 Vue component test 先照现有模式写；没有则使用最小依赖方式，优先 `@vue/test-utils`。
- 如果 `@vue/test-utils` 不存在，需要在交接执行时明确安装或说明当前依赖已具备替代方案。
- 测试中应 mock `services/orderApi.ts` 的 admin review list/write 方法，避免真实请求。
- 测试中应提供最小 admin session / role / preference 环境，满足 Review Management 页面渲染即可。
- 避免依赖 CSS class 作为唯一断言；优先使用可访问语义、按钮标签、图片 alt、稳定文案或必要的 `data-testid`。如必须新增 `data-testid`，保持测试专用、局部且不影响 UI。
- 如果组件当前不易隔离测试，可优先做极小行为等价调整，但不得改变业务实现语义。

## Handoff Boundary

Codex 本轮停在任务准备完成状态。Claude Code 执行编码时只能修改 frontend 测试相关文件和必要的测试配置/依赖；如发现必须修改业务实现文件，应保持最小范围并在完成后说明原因。
