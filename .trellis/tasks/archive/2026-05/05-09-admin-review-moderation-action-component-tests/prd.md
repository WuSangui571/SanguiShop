# 管理端评价治理动作组件测试补齐

## Goal

补齐 `ReviewManagementView` 对管理端评价治理写操作的 Vue 组件交互测试，覆盖隐藏/恢复评价、商家回复、隐藏/恢复回复的按钮可用性、防重复提交、API payload、错误展示与输入保留。

本任务只补测试，不修改业务实现、API 类型、后端接口、数据库、权限、路由或样式行为。

## Task Classification

Simple Task。

理由：范围清晰，主要集中在既有 `frontend/src/views/admin/ReviewManagementView.spec.ts`；已存在 `@vue/test-utils` + `happy-dom` 基建、`orderApi` mock、session mock 与 review fixture。无架构决策、无跨层契约新增。

## Current Project State

- 上一轮已完成管理端评价图片大图预览及组件测试闭环。
- 当前仓库位于 `main`，工作区 clean。
- 当前无 active/current Trellis task。
- 现有 `reviewManagementModel.test.ts` 已覆盖纯 model 层 payload trim、visibility/reply guard、backend error preservation、duplicate gate。
- 当前缺口是 DOM/component 层对按钮 disabled、实际点击调用 API、pending 防重复、错误 banner 与 textarea 保留的验证。

## Existing APIs Under Test

Frontend admin review management uses `frontend/src/services/orderApi.ts` through Gateway routes with `authContext: 'ops'`.

| Function | Route | Payload Fields | Expected Component Behavior |
| --- | --- | --- | --- |
| `updateAdminReviewVisibility(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/visibility` | `visibility`, `reason`, `requestId` | Hide/restore buttons reflect review visibility; generate requestId; trim reason/requestId; disable duplicate writes while pending; preserve backend errors. |
| `upsertAdminReviewReply(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/reply` | `content`, `requestId` | Empty trimmed reply disables submit; trim content/requestId; disable duplicate writes while pending; failed save displays backend error and keeps draft. |
| `updateAdminReviewReplyVisibility(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/reply/visibility` | `visibility`, `requestId` | Reply hide/restore buttons require existing reply; reflect reply visibility; generate requestId; disable duplicate writes while pending. |

No API signature, endpoint, auth context, payload field, or DTO type may be changed by this task.

## Validation / Error Matrix

| Case | Expected Test Assertion |
| --- | --- |
| Visible review | Hide review button enabled; restore review button disabled. |
| Hidden review | Restore review button enabled; hide review button disabled. |
| Hide/restore reason has surrounding spaces | API payload contains trimmed `reason`; blank reason becomes `null` if tested through existing model path. |
| Write action pending | Second click does not call the same API a second time; relevant buttons are disabled/pending. |
| Reply textarea empty or whitespace-only | Reply submit button disabled; no API request sent. |
| Reply textarea has content with surrounding spaces | API payload contains trimmed `content` and generated `requestId`. |
| Reply save fails with backend error | Error banner displays backend `code`, `message`, `traceId`; textarea still contains user input. |
| No reply exists | Hide/restore reply buttons disabled. |
| Existing visible reply | Hide reply enabled; restore reply disabled. |
| Existing hidden reply | Restore reply enabled; hide reply disabled. |
| Reply visibility write | `updateAdminReviewReplyVisibility` receives review id and payload with target `visibility` plus generated `requestId`. |

## Good / Base / Bad Cases

Good cases:
- Visible review can be hidden with a trimmed moderation reason and deterministic `requestId`.
- Hidden review can be restored with deterministic `requestId`.
- Non-empty reply draft can be submitted with trimmed content and deterministic `requestId`.
- Existing reply visibility can be hidden/restored according to current reply visibility.

Base cases:
- Initial list render from mocked `listAdminReviews` shows visible, hidden, no-reply, visible-reply, and hidden-reply rows as needed by fixtures.
- Existing image preview component tests remain valid and should keep using the shared mount helper where practical.

Bad cases:
- Duplicate clicks during a pending hide/restore/reply/reply-visibility request must not send a second request.
- Backend failure from a write action must surface `code/message/traceId`.
- Failed reply save must not clear the unsaved textarea content.
- Buttons for impossible actions must stay disabled and must not trigger API calls.

## Requirements

- Reuse and lightly organize existing `ReviewManagementView.spec.ts` helpers:
  - `mountView`
  - `mockSession`
  - `mockMeta`
  - `createReview`
  - `flushPromises`
  - existing `orderApi` vi mocks
- Add deterministic `crypto.randomUUID` or equivalent test setup so requestId assertions are stable.
- Keep test fixtures minimal; avoid large unrelated snapshots.
- Add component tests for review visibility actions:
  - visible review: hide enabled, restore disabled.
  - hidden review: restore enabled, hide disabled.
  - hide/restore click calls `updateAdminReviewVisibility` with trimmed reason and requestId.
  - pending duplicate click does not send a second request.
- Add component tests for merchant reply actions:
  - empty/whitespace reply disables submit.
  - entered content calls `upsertAdminReviewReply` with trimmed content and requestId.
  - API failure displays backend `code/message/traceId` and keeps textarea draft.
- Add component tests for reply visibility actions:
  - no reply disables hide/restore reply buttons.
  - existing visible reply enables hide and disables restore.
  - existing hidden reply enables restore and disables hide.
  - click calls `updateAdminReviewReplyVisibility` with target visibility and requestId.
- Do not change production implementation unless Claude Code discovers a real mismatch between existing behavior and spec. If that happens, stop and report before widening scope.

## Acceptance Criteria

- [ ] `ReviewManagementView.spec.ts` covers component-level disabled states for review visibility, reply submit, and reply visibility controls.
- [ ] Tests assert write API payloads include trimmed values and deterministic `requestId`.
- [ ] Tests prove pending duplicate submit guard at the component/API-call level.
- [ ] Tests assert backend write error displays `code`, `message`, and `traceId`.
- [ ] Tests assert failed reply save preserves textarea content.
- [ ] Existing image preview tests continue to pass.
- [ ] Required frontend commands pass.

## Required Commands

Run from repository root:

```powershell
cd frontend; cmd /c npm run test -- reviewManagement
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

## Out of Scope

- No backend, Gateway, database, storage, route, permission, or API contract changes.
- No changes to `ReviewManagementView.vue` unless an existing defect blocks the specified tests and the user approves scope expansion.
- No new UI feature, copy, style, carousel, image behavior, audit history, delete action, or moderation workflow.
- No dependency changes unless existing test runtime cannot express the assertions.
