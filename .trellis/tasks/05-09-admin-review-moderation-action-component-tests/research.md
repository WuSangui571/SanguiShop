# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/component-guidelines.md`: 异步组件必须覆盖 idle/loading/success/empty/error 等状态；本任务关注写操作按钮状态、pending 与 error banner。
- `.trellis/spec/frontend/api-contracts.md`: Admin Review Management APIs 明确要求写操作生成 `requestId`、trim payload、pending 防重复、保留 backend `code/message/traceId`；同时列出管理端评价测试要求。
- `.trellis/spec/frontend/type-safety.md`: API 类型以后端契约为准，状态枚举需要 unknown fallback；测试不应绕过现有 typed DTO。
- `.trellis/spec/frontend/state-management.md`: 后端 error details 属于业务数据，必须保留 `code/message/traceId`；新 copy 必须走 typed preference translations。本任务不新增 copy。
- `.trellis/spec/frontend/quality-guidelines.md`: 核心交互需要组件测试或 e2e；关键按钮必须有 loading/disabled 防重复；错误提示不能丢 traceId。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: 先复用既有测试 helper 和 model helper，避免为单页测试引入新的通用抽象。

## Code Patterns Found

- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - 已使用 `// @vitest-environment happy-dom`、`@vue/test-utils`、`mount(ReviewManagementView)`。
  - 已 mock `../../services/orderApi` 中的 `listAdminReviews`、`updateAdminReviewVisibility`、`upsertAdminReviewReply`、`updateAdminReviewReplyVisibility`。
  - 已有 `mockSession`、`mockMeta`、`createReview`、`flushPromises`、`mountView` helper。
  - 现有测试集中在图片预览 overlay、thumbnail error、unknown fallback。
- `frontend/src/views/admin/reviewManagementModel.test.ts`
  - 已覆盖 `buildAdminReviewVisibilityRequest`、`buildAdminReviewReplyRequest`、`buildAdminReviewReplyVisibilityRequest` 的 trim 行为。
  - 已覆盖 `canHideAdminReview`、`canRestoreAdminReview`、`canHideAdminReviewReply`、`canRestoreAdminReviewReply`。
  - 已覆盖 `toAdminReviewError` 保留 `code/message/traceId` 和 `createSubmissionGate` 防重复。
- `frontend/src/composables/useReviewManagement.ts`
  - 写操作统一通过 action gate 防重复。
  - `pendingReviewId` 用于当前行 pending 展示。
  - requestId 来自 `crypto.randomUUID()` 或 fallback `adm-review-${Date.now()}`。
  - 写失败设置 `actionError`，写成功替换当前页 item。
- `frontend/src/views/admin/ReviewManagementView.vue`
  - 顶部 action error banner 渲染 `actionError.message`、`common.code + code`、`common.traceId + traceId`。
  - 全局 `moderationReason` input 用于 hide/restore review。
  - reply textarea 使用 per-review `replyDrafts`，保存成功才回填后端 replyContent；失败时应保留 draft。
  - row action buttons:
    - hide review disabled when `!canHideAdminReview(item) || isActionPending`
    - restore review disabled when `!canRestoreAdminReview(item) || isActionPending`
  - reply action buttons:
    - submit disabled when `isActionPending || !replyDraft(item).trim()`
    - hide reply disabled when `!canHideAdminReviewReply(item) || isActionPending`
    - restore reply disabled when `!canRestoreAdminReviewReply(item) || isActionPending`

## Files Likely To Modify

- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Add governance action component tests.
  - Possibly refactor local test helpers or fixture builders within the spec file to keep selectors and setup manageable.

No production files are expected to change.

## Risk / Boundary Notes

- The component currently shares one global action gate for all review governance writes. Tests should assert the observable contract: while a write promise is pending, duplicate clicks do not call APIs again.
- Selectors based only on translated Chinese text may be brittle because the existing test mock text is mojibake in source display. Prefer DOM structure/classes plus button text only where stable enough, or create helper functions scoped to a row and ordered action groups.
- `crypto.randomUUID` must be deterministic in tests. Use `vi.spyOn(globalThis.crypto, 'randomUUID')` only if writable in happy-dom; otherwise use a safe stub approach compatible with Vitest/happy-dom. Restore after each test.
- If API mock promises never resolve in a duplicate-submit test, unmount should still be safe. Prefer externally controlled promise and resolve it before teardown.
- Error display assertions should use `HttpClientError` from `frontend/src/services/httpClient.ts` or an equivalent rejected error path that `toAdminReviewError` recognizes.
- Do not change `ReviewManagementView.vue` or `useReviewManagement.ts` unless tests reveal a real spec mismatch and user approves widening scope.

## Required Tests

- Review visibility actions:
  - visible row: hide enabled, restore disabled.
  - hidden row: hide disabled, restore enabled.
  - hide click calls `updateAdminReviewVisibility(reviewId, { visibility: 'hidden', reason: '<trimmed>', requestId: '<id>' })`.
  - restore click calls `updateAdminReviewVisibility(reviewId, { visibility: 'visible', reason: '<trimmed-or-null>', requestId: '<id>' })`.
  - pending duplicate click does not call `updateAdminReviewVisibility` twice.
- Reply submit:
  - empty or whitespace draft disables submit.
  - typed content calls `upsertAdminReviewReply(reviewId, { content: '<trimmed>', requestId: '<id>' })`.
  - failure displays backend code/message/traceId and preserves textarea value.
- Reply visibility:
  - no reply disables both hide and restore reply buttons.
  - visible reply enables hide reply and disables restore reply.
  - hidden reply enables restore reply and disables hide reply.
  - clicking hide/restore reply calls `updateAdminReviewReplyVisibility(reviewId, { visibility: '<target>', requestId: '<id>' })`.

## Required Commands

```powershell
cd frontend; cmd /c npm run test -- reviewManagement
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```
