# Compensation Dashboard Replay Wiring and Gateway Auth Hardening

## Goal

Close the compensation operations loop by wiring manual replay and bulk replay into the existing dashboard, routing the required internal ops APIs through gateway with consistent JWT and RBAC handling, and tightening the operator workflow with explicit operator identity, dry-run, result feedback, and duplicate-submit protection.

## Scope

- Frontend compensation dashboard in `frontend/`
- Gateway exposure and auth handling in `services/sangui-gateway`
- Existing order/payment internal compensation replay APIs and their frontend client mappings
- Cross-layer behavior for JWT injection, 401/403 surfacing, and operator-facing replay feedback

## Requirements

- Reuse the existing compensation dashboard query page as the operator entry point; do not create a second replay page.
- Wire single-record manual replay and bounded bulk replay for both order timeout and payment reconcile where backend contracts already exist.
- Capture and submit operator identity on manual and bulk replay actions according to existing backend contracts.
- Support `dryRun` for bulk replay and render deterministic result feedback to operators.
- Prevent accidental duplicate submissions from the dashboard while replay requests are in flight.
- Expose the required internal compensation endpoints through gateway and keep browser access working with JWT + CORS.
- Clarify and enforce the final gateway behavior for authenticated internal ops requests, including role visibility and consistent 401/403 handling.
- Preserve existing query/filter behavior and augment the page only where needed for replay actions and operator experience.
- Improve operator usability with URL state restoration, persistent filters, sensible default time window, export support where practical, and fast traceId copy when the work fits the current slice.

## Acceptance Criteria

- [ ] Dashboard can trigger real single-record manual replay requests for order and payment compensation rows and display result state, reason, traceId, and operator context.
- [ ] Dashboard can trigger bounded bulk replay requests with `dryRun`, explicit scope, and deterministic summary/item feedback.
- [ ] Replay buttons are guarded against duplicate submit while the same request is pending.
- [ ] Gateway routes the required `/api/internal/orders/**` and `/api/internal/payments/**` paths to the owning services and preserves browser CORS preflight behavior.
- [ ] Authenticated internal ops requests behave consistently for missing token, invalid token, and forbidden role cases.
- [ ] Frontend HTTP client and page state handle 401/403 and backend error payloads without breaking the dashboard query workflow.
- [ ] Targeted tests pass for touched gateway/backend/frontend code paths.

## Technical Notes

- This is a cross-layer task. Contracts must stay aligned across frontend DTOs, gateway paths, and order/payment internal controller payloads.
- Existing uncommitted gateway work appears to be part of this direction and must be reviewed and preserved if it matches the final design.
- Scope should stay centered on replay wiring and gateway/auth closure. Operator-experience enhancements are secondary unless they fit cleanly without destabilizing the main path.
- If backend replay contracts already satisfy operator/dry-run/result fields, prefer adapting the frontend and gateway to them instead of redesigning the APIs.
