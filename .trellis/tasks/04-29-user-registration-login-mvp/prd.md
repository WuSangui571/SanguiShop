# User Registration/Login MVP

## Goal
Implement the first user identity API surface for SanguiShop: public user registration and login with validated DTOs, password hashing, JWT token issuance, `ApiResult` response envelopes, and focused WebMvc/service tests.

## Requirements
- Add public `POST /api/users/register` endpoint.
- Add public `POST /api/users/login` endpoint.
- Use request/response DTOs rather than exposing persistence entities.
- Validate username/mobile/password inputs at the controller boundary.
- Store and compare passwords using a one-way password hash.
- Return tokens with the JWT contract required by the backend security spec:
  `sub`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, and `jti`.
- Return all API responses through the existing `ApiResult` envelope, including `traceId`.
- Map validation and business/auth failures to stable error codes without leaking sensitive data.
- Keep implementation inside `services/sangui-user-service` unless shared common contracts already exist.

## API Contract

### `POST /api/users/register`

Request:

```json
{
  "shopId": 1,
  "username": "alice",
  "mobile": "13800000000",
  "password": "Passw0rd!"
}
```

Success response code: `USER_REGISTERED`

Response data:

```json
{
  "userId": 10001,
  "shopId": 1,
  "username": "alice",
  "mobile": "13800000000",
  "roles": ["USER"]
}
```

### `POST /api/users/login`

Request:

```json
{
  "shopId": 1,
  "usernameOrMobile": "alice",
  "password": "Passw0rd!"
}
```

Success response code: `USER_LOGGED_IN`

Response data:

```json
{
  "userId": 10001,
  "shopId": 1,
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 7200,
  "roles": ["USER"]
}
```

## Validation and Error Matrix

| Case | HTTP | code | Expected behavior |
| --- | --- | --- | --- |
| Invalid request body | 400 | `VALIDATION_FAILED` | Return field-level validation failure through `ApiResult`. |
| Duplicate username in shop | 409 | `USER_USERNAME_EXISTS` | Reject registration. |
| Duplicate mobile in shop | 409 | `USER_MOBILE_EXISTS` | Reject registration. |
| Unknown login identity | 401 | `AUTH_INVALID_CREDENTIALS` | Return generic credential failure. |
| Wrong password | 401 | `AUTH_INVALID_CREDENTIALS` | Return generic credential failure. |
| JWT secret/config missing | startup/config error | `CONFIG_SECRET_MISSING` | Do not issue unsigned or empty-secret tokens. |

## Acceptance Criteria
- [ ] Register endpoint creates a non-deleted user with a hashed password.
- [ ] Login endpoint authenticates by username or mobile within `shopId`.
- [ ] Login returns a JWT containing required SanguiShop claims.
- [ ] Validation failures return `VALIDATION_FAILED` in the standard envelope.
- [ ] Duplicate registration and invalid login are covered by tests.
- [ ] WebMvc tests cover both endpoints and envelope/error behavior.
- [ ] Service tests cover password hashing, duplicate detection, and token issuance.
- [ ] A targeted Maven test run passes for the user-service changes.

## Technical Notes
- Follow existing user-service persistence/migration baseline from Phase 2.
- Do not log raw passwords, password hashes, JWTs, or full mobile numbers.
- Prefer existing common `ApiResult`, error-code, exception, and trace helpers if present.
- If the codebase lacks full infrastructure adapters, keep the MVP testable with a repository port and an in-memory/test adapter pattern consistent with existing module structure.
