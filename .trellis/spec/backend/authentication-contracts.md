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
- Config keys: `sangui.security.jwt.secret` from `SANGUI_JWT_SECRET`; `sangui.security.jwt.ttl-seconds` from `SANGUI_JWT_TTL_SECONDS`.
- Required claims: `sub`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, `jti`.
- Blank secret must fail during token issuer configuration with `CONFIG_SECRET_MISSING`; never issue unsigned or empty-secret tokens.

Validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Duplicate username in same shop | 409 | `USER_USERNAME_EXISTS` |
| Duplicate mobile in same shop | 409 | `USER_MOBILE_EXISTS` |
| Unknown identity or wrong password | 401 | `AUTH_INVALID_CREDENTIALS` |
| Blank JWT secret during token issuer configuration | 500 | `CONFIG_SECRET_MISSING` |

Required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-Dtest=UserAuthControllerTest,UserAuthServiceTest,HmacJwtUserTokenIssuerTest" test
```

Good/Base/Bad cases:

- Good: register stores only `password_hash`; login returns a Bearer token with all required claims.
- Good: duplicate username/mobile and invalid credentials return stable API error codes through `ApiResult`.
- Base: user-service owns `ums_user`; no frontend client is required for this MVP.
- Bad: token payload omits `shop_id`, `roles`, `iat`, `exp`, or `jti`.
- Bad: responses expose raw password, password hash, JWT secret, stack trace, SQL, or internal URLs.
