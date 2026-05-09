# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/directory-structure.md`: Admin 页面与领域私有测试应保持在 frontend 目录既有结构内，不新增跨页面组件。
- `.trellis/spec/frontend/component-guidelines.md`: 页面组件负责页面级布局和 UI 状态；组件测试应覆盖核心交互状态。
- `.trellis/spec/frontend/hook-guidelines.md`: `useReviewManagement` 请求行为应继续通过 service/composable，不在组件测试中真实请求后端。
- `.trellis/spec/frontend/state-management.md`: 预览状态属于页面局部 client state，不应引入 Pinia 或全局 store。
- `.trellis/spec/frontend/type-safety.md`: admin review DTO 与 unknown fallback 必须兼容，不用 `any` 绕过类型。
- `.trellis/spec/frontend/api-contracts.md`: Admin Review Management 要处理 `imageUrls`、`imageCount` unknown fallback、失败图片占位、错误边界和权限边界。
- `.trellis/spec/frontend/quality-guidelines.md`: 核心交互需要组件测试或 e2e 测试；本任务正是补齐关闭按钮、遮罩、Escape 的自动化覆盖。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: 复用现有 test setup、fixtures 和 model helper，不为测试新增不必要抽象。

## Code Patterns Found

- `frontend/src/views/admin/reviewManagementModel.test.ts`: 已有 admin review model fixtures，覆盖 `buildAdminReviewImageView`、`isAdminReviewImagePreviewable`、失败图片和 unknown fallback 的纯函数边界。
- `frontend/tests/auditQueryTemplateCard.spec.ts`: 当前少量 Vue 组件测试使用 `createRenderer` 自定义 renderer，不依赖 DOM 或 `@vue/test-utils`。
- `frontend/tests/useCompensationDashboardAudit.spec.ts`: composable 测试通过自定义 renderer 挂载 host component，并使用 `vi.mock` / `vi.stubGlobal` 隔离浏览器 API。
- `frontend/src/views/admin/ReviewManagementView.vue`: 预览交互在页面组件内实现，关键锚点包括缩略图 `@click="openPreview"`、缩略图 `@error="markImageLoadFailed"`、Teleport 的 `role="dialog"` overlay、关闭按钮 aria-label、`@click.self="closePreview"` 和 `window.addEventListener('keydown', handleKeydown)`。
- `frontend/src/composables/useReviewManagement.ts`: 页面列表数据来自 `listAdminReviews`，组件测试可 mock `frontend/src/services/orderApi.ts` 返回最小 admin review page。

## Test Infrastructure Findings

- `frontend/package.json` currently has `vitest`, `vue`, `@vitejs/plugin-vue`, `vue-tsc`, but no direct `@vue/test-utils`, `happy-dom`, or `jsdom` dependency.
- `frontend/vite.config.ts` has Vue plugin only; no Vitest `test.environment` configured.
- Existing tests are mostly pure model tests or custom Vue renderer tests. They do not prove DOM Teleport / native click / `window` keydown coverage.
- Because this task requires overlay DOM presence and browser events, Claude Code should first decide whether to add `@vue/test-utils` plus a DOM test environment dependency, or prove an equivalent test can be written with current custom renderer. The former is likely cleaner for this specific interaction.

## Files Likely To Modify

- `frontend/src/views/admin/ReviewManagementView.spec.ts` or `frontend/src/views/admin/ReviewManagementView.test.ts`: new component test for image preview overlay interactions.
- `frontend/package.json` and `frontend/package-lock.json`: only if adding `@vue/test-utils` and a DOM environment dependency is necessary.
- `frontend/vite.config.ts` or a local test file annotation/config: only if Vitest needs DOM environment wiring.
- `frontend/src/views/admin/ReviewManagementView.vue`: avoid changes; allow only minimal stable test hooks such as local `data-testid` if no semantic selector is reliable.

## Risk / Boundary Notes

- Do not change admin review API payloads, backend contracts, auth rules, routes, storage behavior, or public image URL handling.
- Do not rewrite `ReviewManagementView.vue` just to make testing easier.
- Do not duplicate model tests; component tests should assert real interactions: open, close button, backdrop click, Escape, failed image, unknown fallback.
- Teleport renders under `document.body`; tests must clean up mounted wrappers and body content after each case.
- `useAppPreferences` touches `localStorage` and document attributes; tests should isolate or reset globals/storage if needed.
- The page bootstraps via `useReviewManagement`; mock `orderApi` methods before mounting so no real HTTP client path runs.
- If dependency installation is required, Claude Code should note it explicitly because network access may be needed in its environment.

## Required Tests

- `cd frontend; cmd /c npm run test -- reviewManagement`
- `cd frontend; cmd /c npm run typecheck`
- `cd frontend; cmd /c npm run lint`
- `cd frontend; cmd /c npm run build`

## Suggested Component Assertions

- Mount `ReviewManagementView` with `session` containing ops/admin-compatible data and `canAccessReviewWorkspace=true`.
- Mock `listAdminReviews` to return a page with:
  - one review with `imageUrls: ['/api/uploads/review-images/review-a.jpg']`, `imageCount: 1`
  - one review with `imageUrls: ['/api/uploads/review-images/missing.jpg']`, `imageCount: 1`
  - one review with `imageUrls: undefined`, `imageCount: 2`
- Wait for bootstrap list render.
- Click the previewable image and assert the dialog appears with preview image `src`.
- Click close button and assert the dialog disappears.
- Reopen, trigger click on the overlay itself, assert close.
- Reopen, dispatch `KeyboardEvent('keydown', { key: 'Escape' })` on `window`, assert close.
- Trigger `error` on the missing thumbnail, click the fallback area if selectable, assert no dialog opens.
- Click or inspect unknown fallback and assert no dialog opens.
