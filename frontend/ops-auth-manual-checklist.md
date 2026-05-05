# Compensation Ops Auth Manual Checklist

Use this checklist when validating the compensation dashboard auth/session loop in a real browser.

## Preconditions

- `frontend/` is running with `npm run dev`.
- Gateway and user-service are reachable from the dashboard.
- `sangui.security.ops.bindings[]` contains at least one real `{ shopId, username, permissions[] }` entry that grants `OPS_COMPENSATION_ADMIN`.
- A non-ops user also exists for the same shop.

## Good

1. Open the dashboard root without any browser storage overrides.
2. Verify the login screen is shown instead of the compensation query page.
3. Sign in with a configured ops admin account.
4. Verify the app lands on the dashboard and the header shows username, `shopId`, and session expiry target.
5. Refresh the page.
6. Verify the session is restored and the dashboard remains accessible.
7. Click `Refresh session`.
8. Verify the notice banner reports success and dashboard access is preserved.
9. Click `Sign out`.
10. Verify the app returns to the login screen and compensation data is no longer visible.

## Forbidden

1. Sign in with valid credentials for a user who is not present in `sangui.security.ops.bindings[]` or legacy `sangui.security.ops.admins[]`.
2. Verify login fails with `AUTH_FORBIDDEN` semantics and does not create a dashboard session.
3. Sign in as an ops admin, then remove that binding or legacy admin mapping from config and reload config.
4. Trigger `Refresh session` or any dashboard query.
5. Verify the app switches to the forbidden state and offers `Retry refresh` plus `Sign out`.

## Expired / Unauthorized

1. Sign in as an ops admin.
2. Let the JWT expire naturally, or replace the stored token with an expired one.
3. Trigger a dashboard query or wait for automatic refresh.
4. Verify the app clears the stored session, returns to the login screen, and shows an expiry notice.

## Traceability

1. During any failed login, refresh, or compensation query, confirm the UI still surfaces backend `code` and `traceId` where applicable.
2. Confirm no step requires manually writing `sessionStorage['sangui.admin.token']`.
