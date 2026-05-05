# Compensation Ops Admin Permission Model And Environment Rollout

## Goal

Replace the temporary config-only ops admin identity mapping with a formal, least-privilege ops permission model that is deployable across environments with executable configuration, rollout, verification, and rollback guidance.

## Scope Decision

- Do not introduce persistent RBAC tables in this task.
- Formalize compensation ops access as an independent ops permission model issued by user-service tokens.
- Keep the rollout backward-compatible enough for staged environment migration and fast rollback.

## Requirements

- Define the canonical compensation ops permission claim and stop treating compensation ops sessions as broad `ADMIN` sessions.
- Replace or supersede `sangui.security.ops.admins[]` with a formal config model that can express the target permission grant per operator identity.
- Keep `shopId` and operator identity validation in user-service before issuing ops tokens.
- Update order-service and payment-service compensation ops authorization to require the formal ops permission plus trusted `shopId` match.
- Preserve gateway behavior for JWT validation and trusted header propagation; no new gateway-side RBAC logic in this task.
- Add executable documentation for:
  - Nacos YAML configuration
  - environment variable configuration
  - default local sample
  - rollout steps
  - verification checklist
  - rollback steps
- Update backend specs so the new auth/config contract is documented with concrete field names, codes, and test expectations.

## Non-Goals

- No full user/role/permission persistence schema.
- No generic admin console or permission management UI.
- No new gateway authorization policy engine.
- No expansion of ops permission usage beyond compensation dashboard flows in this task.

## Acceptance Criteria

- [ ] Ops login and refresh return compensation-scoped permission claims through a documented contract.
- [ ] Compensation order/payment ops services reject principals missing the required ops permission.
- [ ] Product or other `ADMIN`-gated service paths are no longer implicitly unlocked by ops compensation login tokens.
- [ ] Legacy config migration path and target config path are both documented clearly enough for environment rollout.
- [ ] `.trellis/spec/backend/` auth/security docs are updated with exact config keys, token claims, protected APIs, error matrix, and required tests.
- [ ] `deploy/.env.example` and at least one executable rollout document reflect the new config contract.
- [ ] Targeted backend tests covering user-service auth issuance and order/payment ops authorization pass.

## Good / Base / Bad Cases

- Good: configured compensation ops identity logs in, receives the dedicated permission claim, and can query/replay compensation flows for the same `shopId`.
- Good: a valid non-ops user still receives `AUTH_FORBIDDEN` from ops login.
- Good: an ops token cannot satisfy unrelated `ADMIN`-only business paths.
- Base: gateway continues to authenticate JWTs and forward trusted claims without service-specific RBAC knowledge.
- Bad: ops login still emits broad `ADMIN` claims with no compensation-specific permission.
- Bad: environment rollout depends on tribal knowledge instead of checked-in sample config and rollback instructions.
- Bad: config contract is changed without spec and test updates.

## Technical Notes

- Current codebase only persists `ums_user`; there is no existing role/permission table model to extend safely within this task.
- Current compensation ops services enforce `ADMIN` role checks directly in application services.
- Current user-service ops auth uses `sangui.security.ops.admins[]` as a temporary allowlist and emits `roles=["ADMIN"]`, `permissions=[]`.
- Migration should minimize blast radius and favor compatibility where reasonable.
