# 完善后台 Ops 权限登录白名单与测试账号权限模型

## Goal

Fix the current admin shell login gap where users with only fulfillment or seckill admin permissions cannot enter the backend ops/admin area unless they are temporarily granted `PRODUCT_CATALOG_ADMIN`.

The implementation must allow configured ops users such as `bob` and `mall_demo_user` to authenticate into the ops/admin session using their real workspace permissions, while preserving workspace-level visibility so each user only sees the workspace they are authorized to operate.

## Task Classification

Complex Task.

Reasons:
- Touches backend authentication and permission admission.
- Requires frontend permission visibility verification for multiple admin workspaces.
- Changes test account permission assumptions used in manual acceptance.
- Must coordinate backend tests, frontend tests, and manual local environment cleanup.

## Scope

In scope:
- Backend user-service ops login/session permission admission.
- Backend tests for pure `LOGISTICS_FULFILLMENT_ADMIN` and pure `SECKILL_ACTIVITY_ADMIN` style ops users.
- Frontend verification or test coverage proving fulfillment-only users see only the fulfillment workspace and seckill-only users see only the seckill workspace.
- Manual acceptance plan that removes temporary `PRODUCT_CATALOG_ADMIN` pollution from `bob` and `mall_demo_user`.

Out of scope:
- Generic RBAC redesign.
- Database schema changes.
- New permission tables or dynamic role management.
- Gateway route redesign.
- New admin workspaces.
- Changing fulfillment or seckill business APIs beyond permission/session admission.

## Requirements

- `OpsAuthService.ADMIN_SESSION_PERMISSIONS` must include `LOGISTICS_FULFILLMENT_ADMIN`.
- `OpsAuthService.ADMIN_SESSION_PERMISSIONS` must include `SECKILL_ACTIVITY_ADMIN`.
- Ops login/refresh must still reject users without any recognized admin-session permission.
- Existing product/order/review/compensation admin users must continue to log in as before.
- A user with only `LOGISTICS_FULFILLMENT_ADMIN` must be able to obtain an ops/admin session.
- A user with only `SECKILL_ACTIVITY_ADMIN` must be able to obtain an ops/admin session.
- Frontend admin workspace visibility must remain least-privilege:
  - fulfillment-only user sees the fulfillment workspace and not product/order/review/seckill/compensation workspaces.
  - seckill-only user sees the seckill workspace and not product/order/review/fulfillment/compensation workspaces.
- Manual test env should remove temporary `PRODUCT_CATALOG_ADMIN` from `bob` and `mall_demo_user` after the fix.

## API / Command / Payload Contract

Existing API contract only; no new route or payload field is expected.

### Ops Login

Command/API:
- `POST /api/users/ops/login`

Representative payload:

```json
{
  "shopId": 1,
  "usernameOrMobile": "bob",
  "password": "<configured local test password>"
}
```

Expected response envelope on success:

```json
{
  "code": "OPS_USER_LOGGED_IN",
  "message": "ok",
  "data": {
    "userId": "<user id>",
    "shopId": 1,
    "username": "bob",
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "roles": [],
    "permissions": ["LOGISTICS_FULFILLMENT_ADMIN"]
  },
  "traceId": "<trace id>",
  "timestamp": "<ISO-8601 timestamp>"
}
```

Representative seckill-only success payload must include `SECKILL_ACTIVITY_ADMIN` and must not require `PRODUCT_CATALOG_ADMIN`.

### Ops Session Refresh

Command/API:
- `POST /api/users/ops/session/refresh`

Headers:
- `Authorization: Bearer <ops token>`

Expected behavior:
- Refresh succeeds if the current configured ops binding still resolves to at least one allowed admin-session permission.
- Refresh rejects if the binding no longer has any allowed admin-session permission.

### Frontend Admin Workspaces

Command/API:
- No new backend API.
- Existing admin shell reads the persisted ops/admin session roles and permissions.

Expected permission gates:
- `LOGISTICS_FULFILLMENT_ADMIN` grants fulfillment workspace visibility.
- `SECKILL_ACTIVITY_ADMIN` grants seckill workspace visibility.
- Neither permission should grant unrelated admin workspaces.

## Validation / Error Matrix

| Case | Expected Result | HTTP / Code | Assertion Points |
| --- | --- | --- | --- |
| Valid ops user with `LOGISTICS_FULFILLMENT_ADMIN` only logs in | Login succeeds | 200 / success code already used by existing tests | Token/session includes fulfillment permission; no product permission required |
| Valid ops user with `SECKILL_ACTIVITY_ADMIN` only logs in | Login succeeds | 200 / success code already used by existing tests | Token/session includes seckill permission; no product permission required |
| Valid user has no admin-session permissions | Login rejected | 403 / `AUTH_FORBIDDEN` | Existing denial behavior preserved |
| Authenticated ops user refreshes with allowed fulfillment permission | Refresh succeeds | 200 / success code already used by existing tests | New token/session still carries allowed permission |
| Authenticated ops user refreshes after permission binding no longer has allowed permission | Refresh rejected | 403 / `AUTH_FORBIDDEN` | No silent downgrade to product/admin permissions |
| Fulfillment-only frontend session opens admin shell | Only fulfillment workspace visible | N/A | No product/order/review/seckill/compensation workspace tab/view |
| Seckill-only frontend session opens admin shell | Only seckill workspace visible | N/A | No product/order/review/fulfillment/compensation workspace tab/view |
| `OPS_COMPENSATION_ADMIN` only session | Compensation workspace behavior unchanged; no seckill/fulfillment visibility | N/A | Existing workspace gates preserved |

## Good / Base / Bad Cases

Good:
- `bob` has only fulfillment-oriented admin permission and can enter the admin shell.
- `mall_demo_user` has only seckill-oriented admin permission and can enter the admin shell.
- Backend tests prove pure permission admission for fulfillment and seckill without `PRODUCT_CATALOG_ADMIN`.
- Frontend tests or focused verification prove each permission sees only its own workspace.
- Manual env no longer needs temporary `PRODUCT_CATALOG_ADMIN` on `bob` or `mall_demo_user`.

Base:
- Existing broad `ADMIN` role remains accepted.
- Existing product/order/review/compensation permission login paths remain accepted.
- Gateway remains coarse-grained and user-service owns ops login admission.
- Frontend relies on current session permission arrays and does not call list/write APIs for hidden workspaces.

Bad:
- Adding `PRODUCT_CATALOG_ADMIN` to demo users remains the only way to access admin shell.
- A fulfillment-only user sees seckill or product workspace.
- A seckill-only user sees fulfillment or product workspace.
- Backend admits any authenticated user regardless of admin-session permission.
- The change introduces a generic RBAC refactor, DB migration, or new permission source.

## Required Tests And Assertion Points

Backend:
- Add or update `OpsAuthService` tests for a user with only `LOGISTICS_FULFILLMENT_ADMIN`.
- Add or update `OpsAuthService` tests for a user with only `SECKILL_ACTIVITY_ADMIN`.
- Assert login success for both pure-permission users.
- Assert refresh/session admission still respects configured bindings.
- Assert users without allowed admin-session permissions still receive `AUTH_FORBIDDEN`.
- Assert existing accepted permissions still pass to prevent regression.

Frontend:
- Add or update admin shell/workspace permission tests.
- Assert a fulfillment-only session renders fulfillment workspace entry and does not render product/order/review/seckill/compensation entries.
- Assert a seckill-only session renders seckill workspace entry and does not render product/order/review/fulfillment/compensation entries.
- Assert missing/invalid ops session still blocks admin workspace loading.

Manual acceptance:
- Remove temporary `PRODUCT_CATALOG_ADMIN` from local env/config for `bob` and `mall_demo_user`.
- Restart or refresh affected services/frontend state as needed.
- Log in as `bob`; confirm admin shell opens and fulfillment workspace is available.
- Confirm `bob` cannot see seckill/product/order/review/compensation workspaces unless explicitly granted.
- Log in as `mall_demo_user`; confirm admin shell opens and seckill workspace is available.
- Confirm `mall_demo_user` cannot see fulfillment/product/order/review/compensation workspaces unless explicitly granted.

## Technical Notes

- This is a permission admission gap, not a business API contract change.
- Prefer adding focused tests around existing service/component patterns instead of introducing new abstractions.
- Keep permission constants centralized; do not duplicate string literals if a constant exists.
- Preserve existing audit behavior for ops login/refresh success and failure.
- Do not add secrets or real passwords to the repository.

## Acceptance Criteria

- [ ] PRD and Trellis task context are prepared before coding starts.
- [ ] Backend admin-session permission whitelist includes fulfillment and seckill admin permissions.
- [ ] Backend tests cover pure fulfillment and pure seckill ops-login users.
- [ ] Backend denial tests for users without admin-session permissions remain passing.
- [ ] Frontend verification proves fulfillment-only and seckill-only workspace isolation.
- [ ] Manual acceptance confirms `bob` and `mall_demo_user` work without temporary `PRODUCT_CATALOG_ADMIN`.
- [ ] No generic RBAC redesign, DB migration, or unrelated workspace behavior change is introduced.
