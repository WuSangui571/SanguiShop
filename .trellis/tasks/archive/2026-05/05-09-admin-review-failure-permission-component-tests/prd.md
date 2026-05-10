# Admin Review Failure And Permission Component Tests

## Goal

Add focused component-level regression coverage for the admin review management page's remaining high-risk states: permission boundaries, list failure and retry, empty list rendering, filter/search/reset query behavior, old review-image payload compatibility, and write-action recovery after failed moderation requests.

This task is frontend test-only. Production implementation files must not be changed unless the tests expose a real mismatch with the existing spec and the human explicitly approves widening scope.

## Classification

Complex Task.

Rationale: the implementation is expected to be concentrated in one component spec file, but the required behavior crosses permissions, API query payloads, async list states, old payload compatibility, and failed write recovery. The task needs a clear PRD and assertion matrix before coding.

## Current Project State

- Working tree was clean at session start.
- No active Trellis task existed.
- Recent completed work:
  - `cf90e80 feat(mall): ...` added admin review image preview support.
  - `1f901eb test(mall): ...` added admin review image preview component tests.
  - `6907107 test(mall): ...` added admin review governance write-action component tests.
  - `578aa34 chore(trellis): ...` recorded the previous governance action test session.
- Previous journal entry states no production files changed for the governance action component tests and the required frontend checks passed.

## Scope

In scope:

- Component tests for `ReviewManagementView` and, where necessary for entry gating, a minimal `App.vue` permission boundary test.
- Existing mocks and fixtures in `frontend/src/views/admin/ReviewManagementView.spec.ts`.
- Assertions for existing behavior only:
  - no API list call when `canAccessReviewWorkspace` is false;
  - review workspace entry is gated by `ADMIN` or `REVIEW_MANAGEMENT_ADMIN`;
  - `OPS_COMPENSATION_ADMIN` alone must not show the review workspace;
  - list backend errors preserve and render `code/message/traceId`;
  - retry calls `listAdminReviews` again;
  - list error state must not render the empty banner;
  - empty successful list renders the empty banner;
  - search/reset call list API with the model's query contract;
  - old/partial review image payloads with `imageCount` but no `imageUrls` render unknown fallback rather than crashing;
  - failed write actions restore controls so the same user action can be retried;
  - failed reply visibility updates do not mutate the visible row state before a successful retry.

Out of scope:

- No backend, Gateway, database, storage, route, auth implementation, API client, DTO, or production UI behavior changes.
- No new API fields or changed request/response contracts.
- No new dependency.
- No broad test refactor unrelated to `ReviewManagementView` / minimal entry permission gating.
- No visual redesign, copy changes, or CSS edits.

## Existing API / Command / Payload Contract Under Test

The task tests existing frontend API calls only:

| Function | Route | Auth context | Payload / query fields under test |
| --- | --- | --- | --- |
| `listAdminReviews(params)` | `GET /api/admin/reviews` | `ops` | `page`, `size`, optional `productId`, optional `rating`, optional `userId`, optional `visibility`, optional `fromTime`, optional `toTime` |
| `updateAdminReviewVisibility(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/visibility` | `ops` | `visibility`, `reason`, `requestId` |
| `upsertAdminReviewReply(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/reply` | `ops` | `content`, `requestId` |
| `updateAdminReviewReplyVisibility(reviewId, payload)` | `POST /api/admin/reviews/{reviewId}/reply/visibility` | `ops` | `visibility`, `requestId` |

No command/API contract changes are allowed. Tests should use the existing `orderApi` mocks and the existing `AdminReviewSummaryResponse` DTO type.

Required validation/query behavior:

- `visibility=all` is omitted from list query params.
- Blank text filters are omitted.
- `productId` and `rating` are normalized by existing model helpers.
- `datetime-local` values are normalized to `:00+08:00`.
- Write payloads trim reason/content/requestId via existing model helpers.
- Backend errors must preserve `code`, `message`, and `traceId`.

## Validation / Error Matrix

| Case | Setup | Expected assertion |
| --- | --- | --- |
| Permission denied prop | Mount `ReviewManagementView` with `canAccessReviewWorkspace=false` | `listAdminReviews` is not called. No governance write API should be reachable/called through component actions. |
| Entry permission denied | Session has only `OPS_COMPENSATION_ADMIN`; URL asks for review workspace | Review tab/view is not rendered; app falls back to an accessible workspace or forbidden state according to current implementation. |
| Entry permission allowed by role | Session has `ADMIN` | Review workspace entry is available when active. |
| Entry permission allowed by permission | Session has `REVIEW_MANAGEMENT_ADMIN` | Review workspace entry is available when active. |
| List failure | `listAdminReviews` rejects with `HttpClientError` | Error banner shows backend `message`, `code`, and `traceId`; empty banner is not shown. |
| Retry after list failure | First `listAdminReviews` call rejects, retry call resolves | `listAdminReviews` call count increments; successful rows or empty state render from second result. |
| Empty list success | `listAdminReviews` resolves with `items=[]`, `total=0` | Empty banner renders; no misleading error banner. |
| Search filters | User edits product/rating/user/visibility/time filters and clicks search | The next `listAdminReviews` call receives normalized query params matching `buildAdminReviewQuery`. |
| Reset filters | User changes filters then clicks reset | The next query matches `createDefaultReviewFilters()` output: `page=1`, `size=20`, no optional blank/all filters. |
| Old image payload | Review has `imageCount>0` and `imageUrls` omitted | Unknown fallback renders; preview overlay does not open from fallback. |
| Hide/restore failure | `updateAdminReviewVisibility` rejects | Error banner shows backend details; action buttons become enabled according to unchanged row state; retry sends another request. |
| Reply visibility failure | `updateAdminReviewReplyVisibility` rejects | Row reply label/state remains unchanged; button availability returns; retry sends another request. |
| Reply save failure | `upsertAdminReviewReply` rejects | Existing coverage already preserves draft; this task may extend to assert retry sends again if not already covered. |

## Good / Base / Bad Cases

Good cases:

- `ADMIN` or `REVIEW_MANAGEMENT_ADMIN` can reach the review workspace and list calls are made only when access is true.
- A successful empty response renders the empty banner.
- Failed write action followed by retry results in two write API calls with valid `requestId` payloads and no stale pending disabled state.

Base cases:

- Existing successful list response renders rows and current governance action tests remain valid.
- Existing image preview tests remain valid.
- Existing model-layer tests for query/payload trim remain valid and should not be duplicated more than necessary.

Bad cases:

- `OPS_COMPENSATION_ADMIN` alone does not reveal review management or send review list calls.
- List failures do not render a false empty state.
- Old review payloads without `imageUrls` do not crash and do not open preview.
- Failed reply visibility writes do not optimistically mutate the displayed reply status.

## Acceptance Criteria

- [ ] Task remains frontend test-only unless human approves widening scope.
- [ ] `ReviewManagementView.spec.ts` covers no-access prop gating with no list load.
- [ ] Permission entry coverage proves `OPS_COMPENSATION_ADMIN` alone cannot access review management and `ADMIN` / `REVIEW_MANAGEMENT_ADMIN` can.
- [ ] List failure renders backend `code/message/traceId` and retry triggers another list request.
- [ ] List failure state does not show the empty banner.
- [ ] Empty successful list renders the empty banner.
- [ ] Search/reset component interactions assert list query params matching current model contract.
- [ ] Old/partial payload with `imageCount` and no `imageUrls` is covered at component level.
- [ ] Hide/restore failure restores button availability and allows a later retry.
- [ ] Reply visibility failure preserves current row display state and allows a later retry.
- [ ] No production implementation file is changed.
- [ ] Required commands pass before handoff completion after coding.

## Files Expected To Change

- `frontend/src/views/admin/ReviewManagementView.spec.ts`

Optional only if entry permission cannot be covered cleanly inside the existing spec:

- `frontend/src/App.spec.ts` or a narrow addition to an existing app-level spec if one exists.

No production files are expected to change.

## Required Test Commands

Run after implementation:

```powershell
cd frontend; cmd /c npm run test -- reviewManagement
cd frontend; cmd /c npm run typecheck
cd frontend; cmd /c npm run lint
cd frontend; cmd /c npm run build
```

## Implementation Notes

- Reuse existing `ReviewManagementView.spec.ts` helpers (`createReview`, `flushPromises`, `mountView`) where practical.
- Add narrowly scoped helpers only if they reduce selector brittleness.
- Prefer row-scoped selectors and stable classes over translated text because source display may show mojibake in this repository.
- Use `HttpClientError` for backend error preservation paths.
- Resolve/reject controlled promises before teardown to avoid leaked pending work.
- Keep `crypto.randomUUID` deterministic in write-action tests and restore mocks after each test.
