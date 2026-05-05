# Compensation Ops Audit Logging And Operation Traceability

## Goal
Close the auditability gap left after the ops permission rollout by persisting unified audit events for key compensation ops actions, including successful and denied authentication and replay operations.

## Requirements
- Capture a unified audit event for ops login attempts and refresh operations.
- Capture audit events for compensation manual replay actions.
- Capture audit events for bulk replay and reconcile actions.
- Capture audit events for authorization-denied (`403`) ops actions that reach protected compensation or auth surfaces.
- Keep the audit contract consistent across services so downstream log search and trace analysis can correlate events by trace and operator identity.
- Preserve existing least-privilege permission model and avoid re-broadening ops access.
- Reuse existing logging/audit infrastructure and patterns where possible instead of introducing ad-hoc logging shapes.

## Acceptance Criteria
- [ ] Ops login success/failure and refresh success/failure emit structured audit events with operator identity, outcome, trace context, shop scope, and action metadata.
- [ ] Manual replay, bulk replay, and bulk reconcile operations emit structured audit events with action type, request scope, operator identity, outcome, and trace context.
- [ ] Protected ops endpoints emit auditable denial events on `403` responses without duplicating business success logs.
- [ ] Tests cover the newly introduced audit behavior at the touched service boundaries.
- [ ] Relevant backend spec docs are updated to document the audit event contract and verification expectations.

## Technical Notes
- Expected touch points are likely `sangui-user-service`, `sangui-order-service`, `sangui-payment-service`, and shared/common security or logging support used by ops auth and compensation controllers/services.
- Prefer a single event shape or helper API over scattered JSON log statements.
- This is a cross-layer backend task because auth, controller, service, and observability concerns must stay aligned.
