# Compensation Ops Audit Manual Acceptance Checklist

## Goal

Add a real-environment operator checklist for validating the compensation ops audit observability path in Kibana/Loki.

## Requirements

- Cover `VITE_KIBANA_DISCOVER_URL` and `VITE_LOKI_EXPLORE_URL` configured, missing, invalid URL, and credential-bearing URL behavior.
- Cover Kibana/Loki open-button enabled and disabled states.
- Cover `View audit trail` population after replay/reconcile success and failure, including `traceId`, `operator`, `action`, `outcome`, and `shopId`.
- Cover Kibana Discover and Loki Explore query verification for `Ops audit event.` hits.
- Cover unauthorized compensation ops query/action behavior where business APIs reject access and denied audit events remain searchable.
- Cover copy-only fallback for environments without Kibana/Loki links.

## Acceptance Criteria

- [ ] The runbook is executable by an operator against a real Gateway, Kibana, and Loki/Grafana deployment.
- [ ] The checklist includes Good/Base/Bad cases for env configuration and observability-link behavior.
- [ ] The checklist avoids secrets, raw JWTs, and hardcoded production hosts.
- [ ] Existing frontend and backend observability specs remain consistent with the documented checks.
- [ ] Quality check confirms only documentation/task files changed unless implementation defects are found.

## Technical Notes

- This task is scoped to documentation/runbook coverage.
- No API, DB, Redis, MQ, frontend component, or backend code behavior is expected to change.
- Existing source of truth:
  - `docs/compensation-ops-audit-search.md`
  - `.trellis/spec/backend/logging-guidelines.md`
  - `.trellis/spec/frontend/api-contracts.md`
