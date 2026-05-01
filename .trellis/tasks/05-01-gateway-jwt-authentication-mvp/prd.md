# Gateway JWT Authentication MVP

## Goal
Add an MVP JWT authentication layer to `sangui-gateway` so the gateway can validate JWTs issued by `sangui-user-service`, reject unauthenticated protected requests, and forward stable user context headers to downstream services.

## Requirements
- Gateway must allow public authentication endpoints without a token:
  - `POST /api/users/register`
  - `POST /api/users/login`
- Gateway must require `Authorization: Bearer <jwt>` for non-public `/api/**` requests.
- Gateway must validate HMAC JWT signature, issuer, issued-at/expiry, and required claims.
- Gateway must extract and forward:
  - `X-Sangui-User-Id` from JWT `sub`
  - `X-Sangui-Shop-Id` from JWT `shop_id`
  - `X-Sangui-Roles` from JWT `roles`
  - `X-Sangui-Permissions` from JWT `permissions`
  - `X-Sangui-Jwt-Id` from JWT `jti`
- Gateway must remove any incoming spoofed Sangui identity headers before writing trusted values.
- Authentication failures must return the standard `ApiResult` JSON envelope with stable error code and HTTP 401.
- JWT secret and issuer must come from configuration or environment, not source code.

## Acceptance Criteria
- [ ] Public register/login paths pass through without JWT validation.
- [ ] Protected `/api/**` request without Bearer token returns HTTP 401.
- [ ] Protected `/api/**` request with invalid or expired JWT returns HTTP 401.
- [ ] Protected `/api/**` request with valid JWT reaches the downstream route with trusted Sangui identity headers.
- [ ] Gateway smoke test still passes.
- [ ] Focused gateway authentication tests pass.

## Technical Notes
- This task is backend-only and mainly touches `services/sangui-gateway`.
- Reuse `common/sangui-common-security` constants where possible so gateway and user-service agree on claim names.
- Prefer Spring Cloud Gateway reactive filter patterns over servlet security filters.
- This task may add small shared security helpers if doing so avoids duplicate JWT parsing logic.
- No database, Redis, MQ, or frontend changes are expected.

## Out Of Scope
- Role/permission route authorization matrices.
- Token refresh, logout, token revocation, or jti blacklist.
- Remote user-service introspection.
- OAuth2 resource server migration.
