# Compensation Observability / Config Hardening

## Goal
Harden the existing compensation scheduler MVP so the order timeout job and payment reconcile job are deployable, observable, and operable in real environments.

## Background
The previous MVP already introduced:
- Scheduled order timeout compensation in `sangui-order-service`
- Scheduled payment reconcile in `sangui-payment-service`
- Batch summary logs and failure isolation

This follow-up focuses on the operational layer that is still missing:
- Deploy-time environment examples for the new scheduler knobs
- Stable metrics for batch runs and batch outcomes
- Explicit log fields for operations and troubleshooting
- Clear alert thresholds documented alongside the executable backend specs

## Requirements
- Add the scheduler-related environment variables to `deploy/.env.example` for both order and payment compensation jobs.
- Add metrics counters for the order timeout compensation scheduler and payment reconcile scheduler.
- Ensure the metrics expose batch-level results that operators can alert on.
- Clarify and standardize the log fields emitted by both jobs so troubleshooting can pivot on `traceId`, `shopId`, batch config, and outcome counts.
- Document alert thresholds and the intended operational interpretation in the relevant backend spec files.
- Keep the implementation aligned with current single-merchant defaults while preserving `shopId` as an explicit contract field.

## Acceptance Criteria
- [ ] `deploy/.env.example` includes all compensation scheduler toggles and timing/batch parameters used by the order and payment services.
- [ ] `OrderTimeoutCompensationScheduler` records metrics for job execution and batch outcome counts.
- [ ] `PaymentReconcileScheduler` records metrics for job execution and batch outcome counts.
- [ ] Job logs include a consistent set of operational fields that can support alert investigation.
- [ ] Backend spec docs describe the env keys, metrics names, log fields, and alert thresholds for both jobs.
- [ ] Targeted tests cover the new scheduler metrics behavior and still pass.

## Non-Goals
- No new compensation business flow or new persistence schema.
- No MQ-based orchestration changes.
- No dashboard provisioning or external alert platform configuration files.

## Technical Notes
- Treat this as a cross-layer backend hardening task because it changes runtime env contracts plus service observability surfaces.
- Prefer Micrometer counters already exposed through Spring Boot Actuator / Prometheus.
- Alert thresholds should be concrete enough to guide operations, even if alert rules are documented rather than encoded in this repo.
- Assume this is a follow-up to the completed scheduler MVP, but track it as a fresh Trellis task because there is no active current task.
