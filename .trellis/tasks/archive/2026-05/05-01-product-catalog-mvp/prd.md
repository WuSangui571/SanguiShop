# Product Catalog MVP

## Goal

Implement the first real business domain on top of the completed authentication chain by delivering a Product Catalog MVP in `services/sangui-product-service`. The MVP should prove that downstream services can trust `SanguiPrincipal`, support anonymous product browsing, and enforce authenticated shop-scoped writes for catalog management.

## Requirements

- Add the product service runtime skeleton needed for real backend development:
  - keep `services/sangui-product-service` as a Spring Boot service
  - add runtime dependencies and configuration required for JDBC + Flyway
  - keep `sangui-common-web` integration so controllers can consume `SanguiPrincipal`
  - add a product-service smoke test
- Add initial product catalog schema migration(s):
  - `pms_product`
  - `pms_sku`
  - both tables must keep `shop_id`
  - product status must support `draft`, `active`, `inactive`
  - price is stored in cents using integer types, never floating point
  - required indexes/constraints:
    - product lookup scoped by `shop_id` + product identity
    - product listing/filter support on `shop_id` + `status`
    - SKU uniqueness constraint
- Add anonymous read APIs with DTO responses and standard `ApiResult` envelope:
  - `GET /api/products`
  - `GET /api/products/{productId}`
- Add authenticated admin write APIs that validate downstream auth context behavior:
  - `POST /api/admin/products`
  - `PUT /api/admin/products/{productId}`
  - `POST /api/admin/products/{productId}/publish`
  - controller receives `SanguiPrincipal principal`
  - write paths must derive `shopId` from principal, never trust request body `shopId`
  - write paths must not trust request body/query `userId` as authenticated identity
- Keep response/request contracts separated from persistence entities.
- Update backend spec docs with executable Product Catalog contracts, including:
  - request/response fields
  - state transitions
  - validation and error codes
  - required test commands
- Add focused automated tests:
  - migration contract test
  - application service unit tests
  - controller WebMvc tests
  - explicit assertions that authenticated write APIs use `SanguiPrincipal` rather than body identity fields

## Acceptance Criteria

- [ ] `services/sangui-product-service` can compile and has a passing smoke test.
- [ ] Flyway migration contract tests verify required product/sku schema, status fields, and indexes/constraints.
- [ ] Anonymous catalog read endpoints return DTOs inside `ApiResult` and do not expose entities.
- [ ] Admin catalog write endpoints require trusted principal context and ignore body-supplied `shopId`/`userId` for authorization scope.
- [ ] Publish endpoint supports the `draft -> active` transition and rejects invalid states with stable error handling.
- [ ] Relevant backend spec docs are updated with executable API/database/test contracts.
- [ ] Product service tests cover migration, service, and controller layers.

## Technical Notes

- Reuse the existing user-service layering style (`api`, `application`, `domain`, `infrastructure`) unless the codebase shows a stronger pattern in product-related modules.
- Reuse existing common contracts:
  - `ApiResult`
  - `SanguiException` / common error mapping
  - `SanguiPrincipal`
  - existing auth header / downstream principal conventions
- Prefer simple JDBC-based persistence for the MVP to stay aligned with current service patterns.
- Treat this task as infra + cross-layer because it introduces a new service contract, new schema, and auth-derived write behavior. Spec updates are required before finishing.
