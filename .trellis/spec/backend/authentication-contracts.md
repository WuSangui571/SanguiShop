# Authentication Contracts

## User Registration/Login MVP

Scope: `services/sangui-user-service`.

Public endpoints:

| API | Request | Success code | Response data |
| --- | --- | --- | --- |
| `POST /api/users/register` | `shopId`, `username`, `mobile`, `password` | `USER_REGISTERED` | `userId`, `shopId`, `username`, `mobile`, `roles` |
| `POST /api/users/login` | `shopId`, `usernameOrMobile`, `password` | `USER_LOGGED_IN` | `userId`, `shopId`, `accessToken`, `tokenType`, `expiresInSeconds`, `roles` |

JWT issuance:

- Issuer implementation: `services/sangui-user-service/src/main/java/com/sangui/shop/user/infrastructure/security/HmacJwtUserTokenIssuer.java`.
- Config keys: `sangui.security.jwt.secret` from `SANGUI_JWT_SECRET`; `sangui.security.jwt.ttl-seconds` from `SANGUI_JWT_TTL_SECONDS`; `sangui.security.jwt.issuer` from `SANGUI_JWT_ISSUER`, defaulting to `sanguishop`.
- Required claims: `sub`, `iss`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, `jti`.
- Blank secret must fail during token issuer configuration with `CONFIG_SECRET_MISSING`; never issue unsigned or empty-secret tokens.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Duplicate username in same shop | 409 | `USER_USERNAME_EXISTS` |
| Duplicate mobile in same shop | 409 | `USER_MOBILE_EXISTS` |
| Unknown identity or wrong password | 401 | `AUTH_INVALID_CREDENTIALS` |
| Blank JWT secret or issuer during token issuer configuration | 500 | `CONFIG_SECRET_MISSING` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-Dtest=UserAuthControllerTest,UserAuthServiceTest,HmacJwtUserTokenIssuerTest" test
```

Good/Base/Bad cases:

- Good: register stores only `password_hash`; login returns a Bearer token with all required claims.
- Good: duplicate username/mobile and invalid credentials return stable API error codes through `ApiResult`.
- Base: user-service owns `ums_user`; no frontend client is required for this MVP.
- Bad: token payload omits `iss`, `shop_id`, `roles`, `iat`, `exp`, or `jti`.
- Bad: responses expose raw password, password hash, JWT secret, stack trace, SQL, or internal URLs.

## Gateway JWT Authentication MVP

Scope: `services/sangui-gateway`.

Public endpoints:

| API | Auth | Behavior |
| --- | --- | --- |
| `POST /api/users/register` | public | Pass through to `sangui-user` after removing incoming Sangui identity headers. |
| `POST /api/users/login` | public | Pass through to `sangui-user` after removing incoming Sangui identity headers. |

Protected endpoints:

| API shape | Auth | Behavior |
| --- | --- | --- |
| `/api/**` except public endpoints | `Authorization: Bearer <jwt>` required | Validate JWT before routing. |
| non-`/api/**` | not handled by JWT MVP | Pass through after trace/identity header cleanup. |

JWT validation contract:

- Validator implementation: `services/sangui-gateway/src/main/java/com/sangui/shop/gateway/security/GatewayJwtAuthenticationFilter.java`.
- Config keys: `sangui.security.jwt.secret` from `SANGUI_JWT_SECRET`; `sangui.security.jwt.issuer` from `SANGUI_JWT_ISSUER`, defaulting to `sanguishop`; `sangui.security.jwt.allowed-clock-skew-seconds` from `SANGUI_JWT_ALLOWED_CLOCK_SKEW_SECONDS`, defaulting to `60`.
- Gateway must fail startup when JWT secret or issuer is blank.
- Accepted header: `Authorization: Bearer <jwt>`.
- Accepted JWT header: `alg=HS256`, `typ=JWT`.
- Required claims: `sub`, `iss`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, `jti`.
- `iss` must match configured issuer.
- `exp` must not be before current gateway time beyond allowed clock skew.
- `iat` must not be after current gateway time beyond allowed clock skew.

Trusted downstream headers:

| Header | Source |
| --- | --- |
| `X-Sangui-User-Id` | JWT `sub` |
| `X-Sangui-Shop-Id` | JWT `shop_id` |
| `X-Sangui-Roles` | JWT `roles`, comma-separated |
| `X-Sangui-Permissions` | JWT `permissions`, comma-separated |
| `X-Sangui-Jwt-Id` | JWT `jti` |
| `X-Trace-Id` | Incoming `X-Trace-Id` or gateway-generated UUID |

Security rule:

- Gateway must remove incoming `X-Sangui-User-Id`, `X-Sangui-Shop-Id`, `X-Sangui-Roles`, `X-Sangui-Permissions`, and `X-Sangui-Jwt-Id` before adding trusted values, including public paths.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Missing `Authorization` header on protected `/api/**` | 401 | `AUTH_TOKEN_MISSING` |
| Blank or non-Bearer token | 401 | `SIGNATURE_INVALID` |
| Malformed token segments, header, payload, signature, issuer, or required claims | 401 | `SIGNATURE_INVALID` |
| Expired token | 401 | `AUTH_TOKEN_EXPIRED` |
| Blank JWT secret or issuer during gateway configuration | startup failure | `CONFIG_SECRET_MISSING` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-Dtest=GatewayJwtAuthenticationFilterTest,SanguiGatewayApplicationSmokeTest" test
```

Good/Base/Bad cases:

- Good: public login/register pass without token and strip spoofed identity headers.
- Good: valid JWT reaches downstream route with trusted Sangui identity headers.
- Good: missing, expired, wrong issuer, and invalid signature return standard `ApiResult` failures with trace ID.
- Base: RBAC route authorization remains service-owned or future gateway policy work; this MVP only authenticates.
- Bad: Gateway forwards user-supplied Sangui identity headers.
- Bad: Gateway logs JWT values or accepts blank JWT secret.
