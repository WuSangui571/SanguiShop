# Focused Code Research

## Relevant Specs

- `.trellis/spec/frontend/component-guidelines.md`
  - Async components must handle loading, success, empty, error, and retry states.
  - This task specifically tests permission/no-load, list error/retry, empty state, and write failure recovery in a page component.
- `.trellis/spec/frontend/api-contracts.md`
  - Admin Review Management must use `services/orderApi.ts` with `authContext: 'ops'`.
  - `listAdminReviews` must show loading, empty, error, retry, pagination; omit `visibility=all` and blank filters.
  - Admin review write operations must generate `requestId`, guard duplicates while pending, and preserve backend `code/message/traceId`.
  - `OPS_COMPENSATION_ADMIN` alone must not show review management; require `ADMIN` role or `REVIEW_MANAGEMENT_ADMIN`.
  - Old/partial review image payloads with `imageCount` but no `imageUrls` must render unknown fallback instead of crashing.
- `.trellis/spec/frontend/type-safety.md`
  - API types are contract-based; tests should keep fixtures assignable to `AdminReviewSummaryResponse`.
  - Unknown enum/status values should fall back instead of crashing.
- `.trellis/spec/frontend/state-management.md`
  - Backend `code`, `message`, and `traceId` are business data and must be preserved, not translated away.
  - No new copy should be added outside typed app preferences; this task should avoid copy changes.
- `.trellis/spec/frontend/quality-guidelines.md`
  - Core interactions need component or e2e coverage.
  - Critical buttons need loading/disabled duplicate-submit behavior.
  - Error displays must include useful backend details and trace IDs.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - This task does not change API/DTO/DB/MQ/config contracts, but the guide confirms the risk points being tested: retry, idempotency/request IDs, and loading/error states.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - Reuse existing test helpers and model helpers; avoid creating new shared abstractions for one page's tests.

## Code Patterns Found

- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Uses `// @vitest-environment happy-dom`.
  - Uses `@vue/test-utils` `mount`.
  - Mocks `../../services/orderApi` functions:
    - `listAdminReviews`
    - `updateAdminReviewVisibility`
    - `upsertAdminReviewReply`
    - `updateAdminReviewReplyVisibility`
  - Mocks `useAppPreferences()` with a local `t()` implementation and required translation keys.
  - Has reusable helpers:
    - `mockSession`
    - `mockMeta`
    - `createReview`
    - `flushPromises`
    - `queryDialog`
    - `requireElement`
    - `mountView`
  - Existing tests already cover:
    - image preview open/close/Escape/backdrop;
    - thumbnail load failure fallback;
    - unknown image-count fallback;
    - visible/hidden review button enablement;
    - hide/restore payloads and duplicate pending guard;
    - reply submit payloads, empty/whitespace disabled, duplicate pending guard, failure banner and draft preservation;
    - reply visibility payloads and duplicate pending guard.

- `frontend/src/views/admin/ReviewManagementView.vue`
  - Receives `session` and `canAccessReviewWorkspace` props.
  - Passes `canAccessRef` into `useReviewManagement`.
  - `bootstrap()` is called on `props.session` watcher with `immediate: true`.
  - List error banner renders:
    - `listError.message`
    - `common.code` + `listError.code`
    - `common.traceId` + `listError.traceId` when present
    - retry button calling `retry`
  - Empty banner renders only when `!listError && !isLoadingList && items.length === 0`.
  - Filter search calls `refreshList`; reset calls `updateFilters(createDefaultReviewFilters())` then `refreshList`.
  - Row action buttons are scoped under `.row-actions`.
  - Reply action buttons are scoped under `.reply-actions`.
  - Image unknown fallback renders as `.review-image-frame.unknown`.

- `frontend/src/composables/useReviewManagement.ts`
  - `bootstrap()` returns early when `!canAccessWorkspace.value || !session.value`.
  - `refreshList()` returns early when access is false or list gate is pending.
  - List failure sets `listError`, clears `items`, and sets `total=0`.
  - `retry()` calls `refreshList()`.
  - Write methods use one shared action gate, set `pendingReviewId`, generate `requestId`, replace the row only on success, set `actionError` on failure, and clear pending state in `finally`.

- `frontend/src/views/admin/reviewManagementModel.ts`
  - `buildAdminReviewQuery()` normalizes and omits filters:
    - `page` and `size` are clamped;
    - positive integer `productId`;
    - rating 1..5;
    - trimmed `userId`;
    - omitted `visibility=all`;
    - `datetime-local` values become `:00+08:00`.
  - `buildAdminReviewImageView()` handles optional `imageUrls` and computes `unknownCount`.
  - `toAdminReviewError()` preserves `HttpClientError` `code/message/traceId`.

- `frontend/src/App.vue`
  - Review workspace permission is computed from `ADMIN` role or `REVIEW_MANAGEMENT_ADMIN` permission.
  - `OPS_COMPENSATION_ADMIN` is a separate permission for the compensation workspace.
  - Review nav tab uses `v-if="canAccessReviewWorkspace"`.
  - `ReviewManagementView` renders only when `activeAdminWorkspace === 'review' && canAccessReviewWorkspace`.
  - Otherwise the app falls through to other accessible workspaces or `OpsForbiddenView`.

## Files Likely To Modify

- `frontend/src/views/admin/ReviewManagementView.spec.ts`
  - Add component coverage for no-access prop gating, list failure/retry, empty state, filter search/reset query calls, old payload fallback, and failed write retry behavior.

Optional:

- `frontend/src/App.spec.ts`
  - Add a minimal app-level permission boundary spec if it is cleaner than expanding `ReviewManagementView.spec.ts`.
  - There is no existing app-level spec found under `frontend/src`, so creating one would add a new test file but still remain test-only.

No production files should be modified.

## Risk / Boundary Notes

- The user explicitly requested no business code edits in this Codex turn and expects Claude Code to perform coding later.
- If Claude Code finds a production bug while writing tests, pause and ask before modifying implementation files.
- Entry gating for "no review workspace" is an `App.vue` concern, not fully a `ReviewManagementView.vue` concern. For the component itself, the correct assertion is that `canAccessReviewWorkspace=false` prevents list loading and write calls.
- Existing `mockSession.roles` uses `['admin']`, but `App.vue` checks uppercase `ADMIN`. App-level permission tests must use exact uppercase role or permission constants.
- Existing test translation strings display mojibake in this repository view. Prefer class/structure selectors and API call assertions where possible.
- Existing `mountView()` always passes `canAccessReviewWorkspace: true`; either extend it to accept prop overrides or add a separate helper. Keep defaults compatible with existing tests.
- `sessionStorage` may persist filters between tests. Clear it in `beforeEach` or isolate storage when testing search/reset behavior.
- `refreshList` on reset is called immediately after `updateFilters`; assert after flush/nextTick and inspect the latest `listAdminReviews` call.
- For rejected write retry tests, verify both:
  - the displayed state has not changed before retry;
  - the relevant API mock can be called again after failure.
- Do not over-duplicate `reviewManagementModel.test.ts`. Component search/reset tests should prove DOM interactions feed the existing model contract into `listAdminReviews`.

## Required Tests

- Permission boundary:
  - Mount `ReviewManagementView` with `canAccessReviewWorkspace=false`; assert `listAdminReviews` is not called after bootstrap settles.
  - App-level or component-equivalent test proves review workspace entry is not available for `OPS_COMPENSATION_ADMIN` alone.
  - App-level or component-equivalent test proves review workspace entry is available for `ADMIN` and/or `REVIEW_MANAGEMENT_ADMIN`.

- List failure and retry:
  - `listAdminReviews` rejects with `HttpClientError('Review list failed', { code: 'AUTH_FORBIDDEN' or another backend code, status, traceId })`.
  - Error banner contains backend message, code, and traceId.
  - Empty banner text is absent while list error is present.
  - Clicking retry calls `listAdminReviews` again and renders the second result.

- Empty and filters:
  - Successful `items=[]`, `total=0` renders empty banner.
  - Search interaction with product/rating/user/visibility/from/to controls calls `listAdminReviews` with normalized params.
  - Reset interaction calls `listAdminReviews` with only default `page` and `size`.

- Old payload compatibility:
  - A row with `imageCount=2` and `imageUrls=undefined` renders `.review-image-frame.unknown`.
  - Clicking unknown fallback does not open the preview dialog.

- Governance failure recovery:
  - Failed `updateAdminReviewVisibility` leaves current row state unchanged, shows backend error details, returns buttons to their state-based enabled/disabled values, and a second click sends another request.
  - Failed `updateAdminReviewReplyVisibility` leaves reply label/status unchanged, returns buttons to state-based enabled/disabled values, and a second click sends another request.
  - If extending reply save failure, assert the second submit after rejection sends another `upsertAdminReviewReply` call while preserving the draft.

## Required Commands

```powershell
cd frontend; cmd /c npm run test -- reviewManagement
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```
