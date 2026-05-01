# Order Create MVP

## Goal
Implement the first order creation flow in `services/sangui-order-service` so authenticated users can submit an order against existing product SKUs and the platform can persist a real business document for downstream payment, seckill, and search/recommendation scenarios.

## Requirements
- Add order domain persistence in `services/sangui-order-service` with Flyway migration for `oms_order` and `oms_order_item`.
- Expose `POST /api/orders` using unified `ApiResult`.
- Resolve authenticated identity from `SanguiPrincipal` and derive `userId` and `shopId` from trusted downstream auth context rather than request payload.
- Validate requested SKUs against product data and persist SKU snapshots in order items, including at least `skuName` and `priceCent`.
- Support MVP order statuses `created`, `cancelled`, and `paid`, with create flow persisting `created`.
- Keep the implementation scoped to order creation only; payment and cancel transitions are contract/state preparation only in this task.
- Add executable tests: migration contract test, application service test, and controller `WebMvc` test.
- Sync backend code-spec documentation with the new order contract, schema, validation, and test commands.

## Acceptance Criteria
- [ ] `services/sangui-order-service` can boot with JDBC/Flyway configuration and create the order schema through migration.
- [ ] `POST /api/orders` accepts an authenticated create-order request, resolves principal-based `userId` / `shopId`, validates input, and returns persisted order data in `ApiResult`.
- [ ] Order persistence writes one `oms_order` row plus matching `oms_order_item` rows with immutable SKU snapshot data.
- [ ] The create flow rejects invalid payloads or unavailable SKUs with mapped business errors.
- [ ] Tests cover migration shape, application service business behavior, and controller auth/error behavior.
- [ ] `.trellis/spec/backend/` contains an executable order contract document reflecting API payloads, DB schema, validation/error matrix, and verification commands.

## Technical Notes
- Reuse the product catalog patterns for Flyway, JDBC repository structure, controller DTOs, and module-level tests where appropriate.
- Reuse trusted downstream auth context from `common/sangui-common-security` and `common/sangui-common-web`; never accept `userId` or `shopId` from request bodies as authenticated identity.
- Prefer keeping the product snapshot integration local and explicit for MVP, with contracts designed so a future product-service RPC/Feign client can replace the lookup without changing the order API contract.
