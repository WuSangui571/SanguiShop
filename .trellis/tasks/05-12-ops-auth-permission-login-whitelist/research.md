# Focused Research

## Relevant Specs

- `.trellis/spec/backend/authentication-contracts.md`: Defines `POST /api/users/ops/login`, `POST /api/users/ops/session/refresh`, JWT `permissions`, ops bindings, and expected `AUTH_FORBIDDEN` behavior.
- `.trellis/spec/backend/gateway-security.md`: Confirms `POST /api/users/ops/login` is public at gateway level, while user-service owns ops admission and forbidden rejection.
- `.trellis/spec/backend/microservice-contracts.md`: Requires stable API envelopes, `shopId`, and auth/forbidden error codes.
- `.trellis/spec/backend/error-handling.md`: Maps `AccessDenied`/auth failures to 401/403 style API failures without leaking internals.
- `.trellis/spec/backend/logging-guidelines.md`: Existing ops auth actions emit `Ops audit event.` lines; this task should preserve current audit behavior.
- `.trellis/spec/backend/quality-guidelines.md`: Requires targeted Maven reactor tests and Good/Base/Bad coverage.
- `.trellis/spec/frontend/api-contracts.md`: Documents admin workspace permission gates:
  - fulfillment requires `ADMIN` or `LOGISTICS_FULFILLMENT_ADMIN`.
  - seckill requires `ADMIN` or `SECKILL_ACTIVITY_ADMIN`.
  - `OPS_COMPENSATION_ADMIN` alone must not show fulfillment/seckill.
- `.trellis/spec/frontend/state-management.md`: Ops session permissions live in persisted client session state; no password/JWT logging.
- `.trellis/spec/frontend/component-guidelines.md`: Workspace UI gates should use reactive state and handle permission states explicitly.
- `.trellis/spec/frontend/quality-guidelines.md`: Requires frontend typecheck/build/tests for changed UI permission behavior.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Applies because the change spans backend auth, frontend admin shell, and manual environment contract.

## Code Patterns Found

- `services/sangui-user-service/src/main/java/com/sangui/shop/user/application/OpsAuthService.java`
  - Current admission list is `ADMIN_SESSION_PERMISSIONS`.
  - Current allowed permissions are `OPS_COMPENSATION_ADMIN`, `PRODUCT_CATALOG_ADMIN`, `ORDER_MANAGEMENT_ADMIN`, and `REVIEW_MANAGEMENT_ADMIN`.
  - The gap is here: `LOGISTICS_FULFILLMENT_ADMIN` and `SECKILL_ACTIVITY_ADMIN` are not accepted for ops/admin session issuance.

- `services/sangui-user-service/src/main/java/com/sangui/shop/user/application/OpsAccessRegistry.java`
  - Reads `sangui.security.ops.bindings[]` and legacy `sangui.security.ops.admins[]`.
  - Binding permissions are normalized and returned unchanged.
  - Legacy admin permissions already include `LOGISTICS_FULFILLMENT_ADMIN`, but do not include `SECKILL_ACTIVITY_ADMIN`; decide whether to add seckill here if legacy admins are expected to see all existing admin workspaces.
  - Empty binding permissions default to `OPS_COMPENSATION_ADMIN`.

- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPermissionConstants.java`
  - Existing constants include both `LOGISTICS_FULFILLMENT_ADMIN` and `SECKILL_ACTIVITY_ADMIN`; implementation should reuse constants, not string literals.

- `services/sangui-user-service/src/test/java/com/sangui/shop/user/application/OpsAuthServiceTest.java`
  - Existing tests cover compensation, legacy admins, rejection without ops access, refresh, shop mismatch, invalid permission, product, order, and review admin permissions.
  - Natural extension point for pure fulfillment and pure seckill login tests.
  - Existing test name `loginRejectsBindingWithoutCompensationPermission` is now semantically stale because non-compensation admin permissions are accepted; consider renaming only if touching the test.

- `services/sangui-user-service/src/test/java/com/sangui/shop/user/api/OpsAuthControllerTest.java`
  - Controller tests verify `OPS_USER_LOGGED_IN`, `OPS_SESSION_REFRESHED`, forbidden envelope, and audit messages.
  - The service-level whitelist change likely does not require controller changes unless response examples or audit assertions need broader permission coverage.

- `frontend/src/App.vue`
  - Admin workspace gates are already explicit:
    - product: `ADMIN` or `PRODUCT_CATALOG_ADMIN`
    - order: `ADMIN` or `ORDER_MANAGEMENT_ADMIN`
    - review: `ADMIN` or `REVIEW_MANAGEMENT_ADMIN`
    - fulfillment: `ADMIN` or `LOGISTICS_FULFILLMENT_ADMIN`
    - seckill: `ADMIN` or `SECKILL_ACTIVITY_ADMIN`
    - compensation: `ADMIN` or `OPS_COMPENSATION_ADMIN`
  - `availableAdminWorkspaces` selects the first authorized workspace when the URL asks for one that is not allowed.

- `frontend/src/App.spec.ts`
  - Existing tests prove each workspace renders for its permission and that `OPS_COMPENSATION_ADMIN` alone does not render unrelated workspaces.
  - Missing for this task: stricter isolation checks that `LOGISTICS_FULFILLMENT_ADMIN` alone does not render seckill/product/order/review/compensation, and `SECKILL_ACTIVITY_ADMIN` alone does not render fulfillment/product/order/review/compensation.

- `frontend/package.json`
  - Relevant commands are `npm run test`, `npm run typecheck`, and `npm run build`, executed from `frontend/`.

## Files Likely To Modify

- `services/sangui-user-service/src/main/java/com/sangui/shop/user/application/OpsAuthService.java`
  - Add `SanguiPermissionConstants.LOGISTICS_FULFILLMENT_ADMIN` and `SanguiPermissionConstants.SECKILL_ACTIVITY_ADMIN` to `ADMIN_SESSION_PERMISSIONS`.

- `services/sangui-user-service/src/main/java/com/sangui/shop/user/application/OpsAccessRegistry.java`
  - Possible small update: add `SECKILL_ACTIVITY_ADMIN` to `LEGACY_ADMIN_PERMISSIONS` if legacy `sangui.security.ops.admins[]` should remain a full rollback admin surface. This is optional only if the PRD is interpreted as binding-only; prefer aligning legacy admins with all current admin workspaces for consistency unless reviewer says otherwise.

- `services/sangui-user-service/src/test/java/com/sangui/shop/user/application/OpsAuthServiceTest.java`
  - Add pure fulfillment and pure seckill login tests.
  - Consider refresh test for a pure fulfillment or seckill binding if not redundant.
  - Keep invalid-permission rejection test.

- `frontend/src/App.spec.ts`
  - Add isolation tests for fulfillment-only and seckill-only sessions.
  - Existing `App.vue` likely needs no implementation change unless tests reveal an unexpected workspace fallback issue.

- Optional docs/spec:
  - `.trellis/spec/backend/authentication-contracts.md` may need a small spec update if implementation generalizes the documented “compensation ops” language to “admin-session permissions.” If changed, keep it narrow and executable.
  - `frontend/ops-auth-manual-checklist.md` may need an update if the manual checklist is used as the living local acceptance doc.

## Risk / Boundary Notes

- Do not grant `ADMIN` role to these users; ops sessions intentionally issue `roles=[]` and specific `permissions[]`.
- Do not make gateway perform detailed RBAC for admin workspaces in this task.
- Do not admit arbitrary permissions; admission should be limited to the known admin-session permission list.
- Do not let `OPS_COMPENSATION_ADMIN` imply fulfillment or seckill workspace visibility.
- Preserve existing audit logging for login/refresh and forbidden failures.
- Existing working tree has unrelated/uncommitted manual-test changes. DeepSeek should avoid reverting them and should keep implementation edits scoped.
- Local environment cleanup is part of manual acceptance, not necessarily repository code unless the temporary permissions were committed into a tracked config.

## Required Tests

Backend targeted:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-user-service" -am "-Dtest=OpsAuthServiceTest,OpsAuthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Frontend targeted:

```powershell
cmd /c npm --prefix frontend run test -- App.spec.ts
```

Frontend quality:

```powershell
cmd /c npm --prefix frontend run typecheck
cmd /c npm --prefix frontend run build
```

Optional broader backend compile if implementation touches common constants/spec-adjacent code:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-user-service" -am -DskipTests compile
```
