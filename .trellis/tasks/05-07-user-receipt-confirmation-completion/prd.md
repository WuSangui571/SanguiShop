# 用户侧确认收货与订单完成体验补强

## Goal

Extend the buyer order lifecycle from `shipped` to `completed` by adding a customer receipt confirmation API and synchronizing the mall order detail, list, filters, and deep-link recovery experience.

## Scope

- Backend owner: `services/sangui-order-service`.
- Frontend owner: mall customer order flow under `frontend/src/views/mall`, `frontend/src/composables`, `frontend/src/services`, and `frontend/src/types/api`.
- Cross-layer contract: new protected customer API under `/api/orders/**` through Gateway; no new logistics tracking API.
- Persistence: likely add receipt-confirmation snapshot columns on `oms_order` to make `requestId` replay idempotent across process restarts.

## Proposed API Contract

### `POST /api/orders/{orderId}/receipt-confirmations`

Request:

```json
{
  "requestId": "receipt-20260507-0001"
}
```

Response code: `ORDER_RECEIPT_CONFIRMED`.

Response data: existing `OrderResponse` shape, with:

```json
{
  "orderId": 101,
  "status": "completed",
  "fulfillmentStatus": "completed",
  "carrier": "SF Express",
  "trackingNo": "SF1234567890",
  "shippedAt": "2026-05-07T11:30:00+08:00",
  "completedAt": "2026-05-07T13:00:00+08:00"
}
```

Rules:

- Controller uses trusted `SanguiPrincipal`; body `shopId` / `userId` are not accepted.
- `requestId` is required, trimmed, and persisted as the receipt idempotency key.
- Only the owning principal in the trusted `(shopId, userId)` scope can confirm receipt.
- Only `shipped -> completed` is valid for a new confirmation.
- Repeating confirmation on a `completed` order with the same `receipt_request_id` returns the current completed snapshot.
- Repeating with a different `requestId` returns the current completed snapshot if the operation is already completed for the same owner and order. This treats completion as an idempotent terminal user action rather than a conflicting payload-bearing mutation.
- `created`, `paid` / unshipped, `cancelled`, and unknown statuses return `ORDER_STATUS_INVALID`.
- Logs include `traceId`, `orderId`, `orderNo`, `userId`, `shopId`, and `requestId`.

## State Contract

Persisted order statuses should become:

- `created`
- `cancelled`
- `paid`
- `shipped`
- `completed`

Valid customer-facing transitions:

| Current | Operation | Next |
| --- | --- | --- |
| `shipped` | receipt confirmation | `completed` |
| `completed` | receipt confirmation replay | `completed` |
| `created` | receipt confirmation | invalid |
| `paid` / unshipped | receipt confirmation | invalid |
| `cancelled` | receipt confirmation | invalid |

Fulfillment display mapping:

- `paid` -> `fulfillmentStatus = unshipped`
- `shipped` -> `fulfillmentStatus = shipped`
- `completed` -> `fulfillmentStatus = completed`
- `created` / `cancelled` -> existing pending/cancelled mapping

## Database Contract

Recommended migration:

- `services/sangui-order-service/src/main/resources/db/migration/V7__add_order_receipt_confirmation_snapshot.sql`

Required nullable columns on `oms_order`:

- `receipt_request_id`
- `receipt_trace_id`
- `completed_at`

Optional index:

- `idx_oms_order_shop_completed_created (shop_id, status, completed_at)`

Good/Base/Bad cases:

- Good: `shipped` order stores `status=completed`, `fulfillment_status=completed`, `receipt_request_id`, `receipt_trace_id`, and `completed_at`.
- Good: repeated confirmation after success returns the same completed snapshot without changing logistics fields.
- Base: completion is final and does not require inventory, payment, MQ, or logistics-service side effects in this MVP.
- Bad: completion is kept only in frontend state or in an in-memory idempotency map.

## Backend Implementation Plan

1. Update contract/spec docs for order receipt confirmation before or alongside code changes.
2. Add `COMPLETED("completed")` to `OrderStatus`.
3. Add receipt fields to domain record/response DTOs if needed: at minimum `completedAt` in `OrderResponse`.
4. Add `ConfirmOrderReceiptRequest` with `requestId` validation.
5. Add `OrderReceiptConfirmationService` or equivalent application service.
6. Add repository method such as `markCompleted(shopId, orderId, requestId, traceId, completedAt)`.
7. Add `POST /api/orders/{orderId}/receipt-confirmations` to `OrderController`.
8. Ensure `GET /api/orders/{orderId}` and list responses include `completedAt` and completion fulfillment mapping.
9. Add service/controller tests for state transition, ownership, validation, repeat confirmation, and errors.
10. Add migration contract test for the new receipt columns if a migration is introduced.

## Frontend Implementation Plan

1. Add `completed` to order types and mall order lifecycle/filter model while keeping unknown fallback.
2. Add `confirmOrderReceipt(orderId, { requestId })` in `frontend/src/services/orderApi.ts`.
3. Add `completedAt?: string | null` and request DTO type.
4. Extend `useMallOrderStatus` with receipt request id generation, pending guard, success merge, and failure retention.
5. Display a confirm receipt button only for shipped orders; disable it for pending and all invalid states with clear reasons.
6. On success, merge completed snapshot into detail and list, clear shipped-only action, and move filter counts.
7. Add or expose `completed` filter; when current filter is `shipped`, success should show status-changed empty state.
8. Deep-linked completed orders from `/mall?orderId=...` show completion as order snapshot source and no confirm button.
9. Add typed copy in `useAppPreferences.ts` for Simplified Chinese, Traditional Chinese, and English.

## Error Matrix

| Case | HTTP | code | Frontend behavior |
| --- | --- | --- | --- |
| Missing JWT / principal | 401 | `AUTH_TOKEN_MISSING` | Show sign-in/auth error and preserve current detail |
| Invalid `orderId` or blank `requestId` | 400 | `VALIDATION_FAILED` | Show backend `code/message/traceId` |
| Missing order or wrong owner | 404 | `ORDER_NOT_FOUND` | Preserve current detail if any; show backend trace |
| `created`, `paid`, `cancelled`, unknown status | 409 | `ORDER_STATUS_INVALID` | Keep shipped/detail snapshot unchanged if request failed |
| Completed replay | 200 | `ORDER_RECEIPT_CONFIRMED` | Merge current completed snapshot |
| Unexpected backend failure | 500 | `INTERNAL_ERROR` | Preserve shipped detail and logistics snapshot |

## Acceptance Criteria

- [ ] Shipped orders can be confirmed by the owning user and become `completed`.
- [ ] `created`, `paid` / unshipped, `cancelled`, and unknown states cannot be confirmed and expose a clear disabled reason.
- [ ] Repeated click while pending does not send a second frontend request.
- [ ] Repeated backend confirmation is idempotent and returns a completed snapshot.
- [ ] Wrong user/shop scope cannot confirm another user's order.
- [ ] Confirmation success merges detail, list, filter counts, lifecycle, and action state.
- [ ] Confirmation failure preserves shipped detail and logistics snapshot while showing backend `code/message/traceId`.
- [ ] `completed` orders restore from `/mall?orderId=...` as order snapshots and do not show confirm receipt.
- [ ] Specs document the new API, status, DB fields, error matrix, and test commands.

## Required Tests

Backend:

- `OrderReceiptConfirmationServiceTest`
- `OrderControllerTest`
- `OrderQueryServiceTest` if response mapping changes need coverage
- `OrderReceiptConfirmationMigrationContractTest` if a migration is introduced

Frontend:

- `frontend/tests/mallOrderStatusModel.spec.ts`
- `frontend/tests/mallCheckoutModel.spec.ts` or a targeted composable test covering `useMallOrderStatus`

Targeted commands will be finalized after implementation, but expected shape:

```powershell
mvn -q "-Dtest=OrderReceiptConfirmationServiceTest,OrderControllerTest,OrderReceiptConfirmationMigrationContractTest" test
cd frontend; npm run typecheck
cd frontend; npm run lint
cd frontend; npm run test -- mallOrderStatusModel mallCheckoutModel
```

## Open Decision

Use `completed` as the persisted final order status. This matches the user's requested wording and keeps completion under order-service ownership. If the project prefers a different terminal name such as `received`, update the status/API contract before implementation.
