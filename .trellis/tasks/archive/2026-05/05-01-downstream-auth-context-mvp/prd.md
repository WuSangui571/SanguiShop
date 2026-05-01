# Downstream Auth Context MVP

## Goal

Provide a shared downstream authentication context for servlet business services so protected APIs consume trusted Gateway headers through one common capability instead of hand-parsing `X-Sangui-*` headers in every service.

This task follows the completed Gateway JWT Authentication MVP: the gateway validates JWTs, strips spoofed Sangui identity headers, and forwards trusted identity headers. The next layer must convert those trusted headers into a service-local `SanguiPrincipal`.

## Current Project State

- User-service JWT issuance is complete and records `sub`, `iss`, `shop_id`, `roles`, `permissions`, `iat`, `exp`, and `jti`.
- Gateway JWT validation is complete for protected `/api/**` requests.
- Gateway forwards trusted headers:
  - `X-Sangui-User-Id`
  - `X-Sangui-Shop-Id`
  - `X-Sangui-Roles`
  - `X-Sangui-Permissions`
  - `X-Sangui-Jwt-Id`
- Existing shared code already includes:
  - `common/sangui-common-security/.../SanguiPrincipal.java`
  - `common/sangui-common-security/.../SanguiIdentityHeaderNames.java`
  - `common/sangui-common-web/.../TraceIdFilter.java`
  - `common/sangui-common-web/.../SanguiWebAutoConfiguration.java`
- `SanguiPrincipal` currently does not include `jwtId`; the downstream header contract does.

## Scope

Backend/common infrastructure only.

Primary modules:

- `common/sangui-common-security`
- `common/sangui-common-web`
- `.trellis/spec/backend/authentication-contracts.md`

Possible supporting docs:

- `.trellis/spec/backend/gateway-security.md`

Out of scope:

- New business protected endpoints.
- Gateway RBAC policy.
- JWT validation inside downstream services.
- Database, Redis, MQ, frontend, or Product Catalog changes.

## Requirements

- Construct `SanguiPrincipal` only from trusted downstream headers:
  - `X-Sangui-User-Id`
  - `X-Sangui-Shop-Id`
  - `X-Sangui-Roles`
  - `X-Sangui-Permissions`
  - `X-Sangui-Jwt-Id`
- Add `jwtId` to the shared `SanguiPrincipal` contract.
- Provide a lightweight current-principal API such as `SanguiSecurityContext.currentPrincipal()`.
- Provide a Servlet `OncePerRequestFilter` that:
  - parses trusted headers into `SanguiPrincipal`
  - stores the principal in a request attribute
  - binds it to a thread-local context for the request lifetime
  - always clears the context in `finally`
- Provide a Spring MVC argument resolver so controllers can request `SanguiPrincipal` without parsing headers.
- Do not parse identity from external DTO fields, query parameters, or request body fields.
- Keep the common layer technical only; no business authorization rules enter common.

## Proposed Contract Decisions

- `SanguiSecurityContext.currentPrincipal()` returns `Optional<SanguiPrincipal>`.
- Missing all identity headers means anonymous/empty context. This keeps public or optional-auth APIs usable.
- Missing required `userId` or `shopId`, or an invalid `shopId`, means no valid principal is bound.
- `SanguiPrincipalArgumentResolver` treats a `SanguiPrincipal` controller parameter as required and rejects missing/invalid context with `AUTH_TOKEN_MISSING`.
- Optional principal can be supported with `Optional<SanguiPrincipal>` if the existing MVC resolver shape remains simple enough; otherwise the MVP documents `SanguiSecurityContext.currentPrincipal()` for optional access.
- Roles and permissions are comma-separated headers. Blank segments are ignored, and sets are immutable.

## Acceptance Criteria

- [ ] Full trusted headers parse into a `SanguiPrincipal` with `userId`, `shopId`, `roles`, `permissions`, and `jwtId`.
- [ ] Servlet filter stores the principal in a stable request attribute and in `SanguiSecurityContext` during request processing.
- [ ] `SanguiSecurityContext` is cleared after the request completes.
- [ ] Missing `userId` or `shopId` does not create a principal; required resolver access rejects consistently.
- [ ] DTO/query/body `userId` or `shopId` is ignored when trusted headers are absent.
- [ ] `common-web` auto-configuration registers the auth context filter and MVC resolver.
- [ ] Tests cover Good/Base/Bad cases.
- [ ] `.trellis/spec/backend/authentication-contracts.md` is updated with executable downstream auth context contract, error behavior, and test command.

## Good/Base/Bad Cases

- Good: request with all five headers binds principal and lets controller receive `SanguiPrincipal`.
- Good: roles and permissions parse from comma-separated headers into stable immutable sets.
- Base: request without identity headers has empty current principal for public/optional routes.
- Bad: request with only DTO body `userId`/`shopId` and no trusted headers does not create a principal.
- Bad: request with missing `X-Sangui-User-Id`, missing `X-Sangui-Shop-Id`, or non-numeric shop ID cannot resolve required principal.
- Bad: context leaks from one request/test into the next.

## Relevant Specs Read

- `.trellis/spec/backend/authentication-contracts.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/guides/architecture-review-checklist.md`

## Code Patterns Found

- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/TraceIdFilter.java`: existing servlet filter pattern with request attribute, response header, MDC, and `finally` cleanup.
- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/SanguiWebAutoConfiguration.java`: existing auto-configuration and `FilterRegistrationBean` registration pattern.
- `services/sangui-gateway/src/main/java/com/sangui/shop/gateway/security/GatewayJwtAuthenticationFilter.java`: source of trusted identity headers and parsing expectations.
- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiIdentityHeaderNames.java`: stable header constants to reuse.

## Likely Files To Modify

- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPrincipal.java`
- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiSecurityContext.java`
- `common/sangui-common-security/src/main/java/com/sangui/shop/common/security/SanguiPrincipalHeaders.java` or equivalent parser
- `common/sangui-common-web/pom.xml`
- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/SanguiAuthenticationContextFilter.java`
- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/SanguiPrincipalArgumentResolver.java`
- `common/sangui-common-web/src/main/java/com/sangui/shop/common/web/SanguiWebAutoConfiguration.java`
- `common/sangui-common-web/src/test/java/...`
- `.trellis/spec/backend/authentication-contracts.md`

## Implementation Plan

1. Add pure security primitives in `sangui-common-security`.
   - Extend `SanguiPrincipal` with `jwtId`.
   - Add `SanguiSecurityContext` with `currentPrincipal()`, package-safe bind/clear helpers, and no servlet dependency.
   - Add a small parser that accepts a header lookup function and returns `Optional<SanguiPrincipal>`.

2. Add servlet integration in `sangui-common-web`.
   - Add dependency on `sangui-common-security`.
   - Add `SanguiAuthenticationContextFilter`.
   - Set request attribute, bind context, continue chain, clear context.

3. Add MVC resolver.
   - Register via `WebMvcConfigurer`.
   - Resolve `SanguiPrincipal` parameters from request attribute/current context.
   - Throw an auth exception or common exception mapped to `AUTH_TOKEN_MISSING` when required context is absent.

4. Add tests.
   - Unit test parser for complete, missing, invalid, and comma-separated cases.
   - Servlet/MVC test for filter + resolver and required rejection.
   - Test that DTO/query/body `userId`/`shopId` does not create principal without headers.

5. Sync specs.
   - Add a "Downstream Auth Context MVP" section to `authentication-contracts.md`.
   - Include signatures/classes, header fields, validation matrix, Good/Base/Bad cases, and required Maven command.

6. Run `$check` and fix findings.
   - Review changed files against backend quality/security specs.
   - Run focused Maven tests and compile.

7. Run `$finish-work`.
   - Provide exact test commands and git sync commands.

## Proposed Verification Commands

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-security,common/sangui-common-web" -am "-Dtest=SanguiPrincipalHeaderParserTest,SanguiAuthenticationContextFilterTest,SanguiPrincipalArgumentResolverTest" test
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=common/sangui-common-security,common/sangui-common-web,services/sangui-gateway" -am -DskipTests compile
```
