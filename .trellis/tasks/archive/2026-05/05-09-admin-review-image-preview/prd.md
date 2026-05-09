# 管理端评价图片缩略图支持大图预览

## Goal

在 Admin Review Management 页面中，让可正常加载的评价图片缩略图支持点击查看大图预览，同时保持现有隐藏、恢复、回复等评价管理操作不受影响。

## Scope Classification

- Type: Simple Task
- Area: Frontend only
- Package layer: `frontend`
- Backend/API/DB/infra/storage/AI/permission changes: none

## Requirements

### 管理端图片预览交互

- Admin Review Management 中的评价图片缩略图可点击。
- 点击可预览图片后打开一个简单的大图预览层。
- 预览层展示当前图片。
- 预览层提供关闭按钮。
- 点击遮罩可关闭预览层。
- 支持 `Esc` 关闭；如果现有项目已有类似模式则复用，否则保持页面内简单实现。

### 失败图片边界

- 已经加载失败的图片占位不可打开预览。
- `unknown` fallback 图片占位不可打开预览。
- 大图预览加载失败时不得影响隐藏、恢复、回复、回复隐藏/恢复等现有操作。

### 状态与可访问性

- 预览打开状态只保存在当前页面组件内，不新增全局 store。
- 切换列表、刷新列表、当前图片 URL 不存在或被移除时，预览状态必须能安全关闭或保持不崩溃。
- 关闭按钮必须有明确 `aria-label`。
- 大图 `alt` 继续使用评价 ID 相关文案。
- 新增页面文案必须接入 `useAppPreferences().t()` 的 typed translation key。
- 新增颜色必须使用 `frontend/src/styles.css` 中已有语义 CSS 变量。

## Non-Goals

- 不改后端。
- 不改图片上传存储。
- 不新增路由。
- 不引入新的 UI 库。
- 不做轮播、多图切换、下载、删除、审核历史等扩展。
- 不改变隐藏、恢复、回复、回复隐藏/恢复的 API 行为。
- 不把图片预览状态放入 Pinia 或其它全局状态。

## Acceptance Criteria

- [ ] 可正常加载的评价图片缩略图点击后打开大图预览层。
- [ ] 预览层展示当前点击图片，并可通过关闭按钮关闭。
- [ ] 预览层可通过点击遮罩关闭。
- [ ] 预览层可通过 `Esc` 关闭。
- [ ] 失败图片占位不可点击打开预览。
- [ ] `unknown` fallback 占位不可点击打开预览。
- [ ] 大图预览加载失败不会阻断隐藏、恢复、回复、回复隐藏/恢复等操作。
- [ ] 刷新列表、切换页码、切换筛选条件后，已打开的预览不会引用不存在的图片导致崩溃。
- [ ] 关闭按钮包含明确 `aria-label`。
- [ ] 大图 `alt` 包含评价 ID 相关上下文。
- [ ] 覆盖模型或组件测试：可预览图片状态构建。
- [ ] 覆盖模型或组件测试：失败图片不可预览。
- [ ] 覆盖模型或组件测试：`unknown` fallback 不可预览。

## Technical Notes

- 优先沿用 `frontend/src/views/admin/ReviewManagementView.vue` 与 `frontend/src/views/admin/reviewManagementModel.ts` 的既有图片模型边界。
- 如果当前模型已经区分 URL 图片、失败占位、unknown fallback，应在模型中明确提供可预览状态，避免模板重复推断。
- 预览状态建议保留在 `ReviewManagementView.vue` 页面组件内，例如当前预览图片 URL、reviewId、alt 文案。
- 关闭逻辑需要覆盖关闭按钮、遮罩点击、`Esc`、列表刷新/数据变化后的安全收敛。
- 失败大图建议只关闭或展示本地非阻断失败状态；不能修改后端错误处理、不能影响列表行管理动作。

## Validation / Error Matrix

| Case | Expected Behavior |
| --- | --- |
| 可正常加载的 public image URL 缩略图 | 渲染为可点击缩略图，点击打开对应大图预览。 |
| 缩略图已触发加载失败 | 渲染失败占位，不能打开预览。 |
| 仅有 `imageCount`、无 `imageUrls` 的旧 payload | 渲染 `unknown` fallback，占位不能打开预览。 |
| 预览层大图加载失败 | 不影响当前评价的隐藏、恢复、回复等操作；页面不崩溃。 |
| 预览打开后刷新列表或筛选切换 | 预览状态安全关闭或不再引用无效图片；页面不崩溃。 |
| 点击遮罩 | 关闭预览。 |
| 点击关闭按钮 | 关闭预览，按钮具备明确 `aria-label`。 |
| 按下 `Esc` | 关闭预览。 |

## Required Tests

Run at minimum:

```bash
cd frontend; cmd /c npm run test -- reviewManagementModel
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

Test coverage must include:

- 可预览图片的状态构建。
- 失败图片不可预览。
- `unknown` fallback 不可预览。
- 如果预览关闭逻辑进入组件测试范围，覆盖关闭按钮、遮罩、`Esc` 中至少一类交互；否则在交接说明中标注需要人工或浏览器验证。
