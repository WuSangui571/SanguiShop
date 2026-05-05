# Compensation Ops Admin Rollout

This document defines the executable configuration and rollout steps for compensation ops dashboard access.

## Decision

- Use an independent ops permission model.
- Do not use broad `ADMIN` claims for compensation dashboard sessions.
- Do not introduce persistent RBAC tables in this rollout.

Canonical permission:

- `OPS_COMPENSATION_ADMIN`

## Config Contract

Primary config keys:

- `sangui.security.ops.bindings[].shopId`
- `sangui.security.ops.bindings[].username`
- `sangui.security.ops.bindings[].permissions[]`

Compatibility fallback:

- `sangui.security.ops.admins[]`

Behavior:

- `bindings[]` is the target model.
- When `bindings[].permissions[]` is empty, user-service defaults it to `OPS_COMPENSATION_ADMIN`.
- When only legacy `admins[]` is present, user-service treats the identity as `OPS_COMPENSATION_ADMIN` during rollout compatibility.
- Ops login only succeeds for identities that ultimately resolve to `OPS_COMPENSATION_ADMIN`.

## Nacos YAML Example

Apply to `sangui-user.yml` in the target namespace/group:

```yaml
sangui:
  security:
    ops:
      bindings:
        - shopId: 1
          username: ops-admin
          permissions:
            - OPS_COMPENSATION_ADMIN
```

Temporary rollback-compatible legacy form:

```yaml
sangui:
  security:
    ops:
      admins:
        - shopId: 1
          username: ops-admin
```

## Environment Variable Example

For local or containerized deployment:

```dotenv
SANGUI_SECURITY_OPS_BINDINGS_0_SHOP_ID=1
SANGUI_SECURITY_OPS_BINDINGS_0_USERNAME=ops-admin
SANGUI_SECURITY_OPS_BINDINGS_0_PERMISSIONS_0=OPS_COMPENSATION_ADMIN
```

Legacy compatibility form:

```dotenv
SANGUI_SECURITY_OPS_ADMINS_0_SHOP_ID=1
SANGUI_SECURITY_OPS_ADMINS_0_USERNAME=ops-admin
```

## Rollout Steps

1. Add `bindings[]` to the target `sangui-user` Nacos config or deployment env.
2. Keep legacy `admins[]` only if you need a staged migration window.
3. Deploy `sangui-user`, `sangui-order-service`, and `sangui-payment-service` together.
4. Sign in through `POST /api/users/ops/login`.
5. Verify the response contains `permissions=["OPS_COMPENSATION_ADMIN"]` and does not contain `roles=["ADMIN"]`.
6. Open the compensation dashboard and verify query, manual replay/reconcile, bulk dry-run, and sign-out flows.

## Verification Checklist

- `POST /api/users/ops/login` returns `OPS_USER_LOGGED_IN`.
- `POST /api/users/ops/session/refresh` returns `OPS_SESSION_REFRESHED`.
- The JWT principal forwarded by gateway contains `X-Sangui-Permissions: OPS_COMPENSATION_ADMIN`.
- Compensation order query works for the bound `shopId`.
- Compensation payment query works for the bound `shopId`.
- A valid user not in `bindings[]` or `admins[]` receives `AUTH_FORBIDDEN`.
- An ops token cannot satisfy unrelated `ADMIN`-gated APIs.

## Rollback

Config-only rollback path:

1. Restore legacy `sangui.security.ops.admins[]` entries in Nacos or env.
2. Remove `bindings[]` if the new config is suspected.
3. Redeploy `sangui-user` so the old mapping is reloaded.

Binary rollback path:

1. Revert to the previous release of `sangui-user`, `sangui-order-service`, and `sangui-payment-service`.
2. Keep or restore legacy `admins[]` config.
3. Re-run the login and query verification checklist.

## Manual Browser Check

Use `D:\02-WorkSpace\02-Java\SanguiShop\frontend\ops-auth-manual-checklist.md` after deployment.
