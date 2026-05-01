# Payment Pay Contracts

## Scope

Payment Pay MVP for `services/sangui-payment-service`.

This MVP closes the first commerce transaction loop after order creation. It depends on:

- trusted `SanguiPrincipal` for authenticated user and shop scope
- Flyway-managed `pay_payment_order` and `pay_callback_log` schema
- internal order-service HTTP contracts for payable-order lookup and payment confirmation

## External API Contract

### `POST /api/payments`

| API | Auth | Request | Success code | Response data |
| --- | --- | --- | --- | --- |
| `POST /api/payments` | `SanguiPrincipal` required | `CreatePaymentRequest` | `PAYMENT_PAID` | `PaymentResponse` |

Security rules:

- Controller parameter must use `SanguiPrincipal principal`.
- Effective `shopId` and `userId` must come from `principal.shopId()` and `principal.userId()`.
- Request body `shopId` or `userId` may appear for compatibility or malicious callers, but payment flow never trusts them.
- Missing principal must fail through `SanguiPrincipalArgumentResolver` with `AUTH_TOKEN_MISSING`.

### `CreatePaymentRequest`

```json
{
  "shopId": 999,
  "userId": "spoof-user",
  "orderId": 101,
  "paymentNo": "PAY-20260501-0001",
  "channel": "mock"
}
```

Request rules:

- `orderId` must be a positive long.
- `paymentNo` is required and acts as the pay idempotency key inside `(shopId)`.
- `channel` is required and currently supports the MVP placeholder value `mock`.
- The request does not carry `amountCent`; payment-service resolves the payable total from order-service.

### `PaymentResponse`

```json
{
  "paymentId": 201,
  "paymentNo": "PAY-20260501-0001",
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "channel": "mock",
  "status": "paid",
  "amountCent": 119800
}
```

Response rules:

- Responses use `ApiResult<PaymentResponse>`.
- `status` uses persisted enum values `created` and `paid`.
- Successful synchronous MVP flow returns `paid`; a persisted `created` row may appear transiently during retryable downstream failures.

## Internal Order Contracts

Payment-service must never read `oms_*` tables directly. It uses these internal order-service endpoints:

### `POST /internal/orders/payment-snapshot`

Request:

```json
{
  "shopId": 1,
  "userId": "10001",
  "orderId": 101
}
```

Response:

```json
{
  "code": "ORDER_PAYMENT_SNAPSHOT_FETCHED",
  "message": "ok",
  "data": {
    "orderId": 101,
    "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
    "shopId": 1,
    "userId": "10001",
    "status": "created",
    "totalAmountCent": 119800
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T19:00:00+08:00"
}
```

Rules:

- Order must belong to `(shopId, userId)` from the trusted payment principal.
- Only `created` orders are payable through this lookup.
- Unknown order or wrong owner returns `ORDER_NOT_FOUND`.
- Non-`created` status returns `ORDER_STATUS_INVALID`.

### `POST /internal/orders/payment-confirmations`

Request:

```json
{
  "shopId": 1,
  "userId": "10001",
  "orderId": 101,
  "paymentNo": "PAY-20260501-0001",
  "paidAmountCent": 119800
}
```

Response:

```json
{
  "code": "ORDER_PAID",
  "message": "ok",
  "data": {
    "orderId": 101,
    "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
    "shopId": 1,
    "userId": "10001",
    "status": "paid",
    "totalAmountCent": 119800
  },
  "traceId": "01J...",
  "timestamp": "2026-05-01T19:00:01+08:00"
}
```

Rules:

- Order-service validates owner and `paidAmountCent` against persisted order total.
- If the order is already `paid`, confirmation is idempotent and returns the paid snapshot.
- If the order is `created`, confirmation updates it to `paid`.
- If the order is `cancelled` or another unsupported status, return `ORDER_STATUS_INVALID`.

## Payment State Machine

Persisted payment status values:

- `created`
- `paid`

MVP transitions:

| Current | Operation | Next | Notes |
| --- | --- | --- | --- |
| none | create payment row | `created` | Local payment row is persisted before cross-service confirmation. |
| `created` | confirm order paid | `paid` | Synchronous MVP happy path. |
| `paid` | same `paymentNo` replay | `paid` | Return the original payment response. |

Retry rule:

- If order confirmation fails after `pay_payment_order` is inserted, the row remains `created`.
- Replaying the same `(shopId, paymentNo)` with the same effective payload must retry order confirmation and converge to `paid`.
- Replaying with a different effective payload must fail with `IDEMPOTENCY_CONFLICT`.

## Database Contract

Schema env and migration:

| Service | Schema Env | Default Schema | Migration |
| --- | --- | --- | --- |
| `services/sangui-payment-service` | `SANGUI_PAYMENT_MYSQL_SCHEMA` | `sangui_payment` | `db/migration/V1__create_payment_tables.sql` |

### `pay_payment_order`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `order_id`, `order_no`, `user_id`, `payment_no`, `channel`, `amount_cent`, `trace_id`, `status`

Required constraints and indexes:

- `uk_pay_payment_order_shop_payment_no (shop_id, payment_no)`
- `idx_pay_payment_order_shop_order_id (shop_id, order_id)`
- `idx_pay_payment_order_shop_user_id (shop_id, user_id, id)`
- `idx_pay_payment_order_shop_status (shop_id, status)`

### `pay_callback_log`

Required columns:

- platform columns: `id`, `shop_id`, `created_at`, `updated_at`, `deleted`, `version`
- business columns: `payment_no`, `channel`, `channel_trade_no`, `callback_type`, `payload_json`, `process_status`, `trace_id`

Required constraints and indexes:

- `uk_pay_callback_log_channel_trade_no (channel, channel_trade_no)`
- `idx_pay_callback_log_shop_payment_no (shop_id, payment_no)`

Money rules:

- all payment totals use integer cents in `BIGINT`
- `double` and `float` are forbidden

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Order missing or not owned by current principal | 404 | `PAYMENT_ORDER_NOT_FOUND` |
| Order status is not payable | 409 | `PAYMENT_ORDER_STATUS_INVALID` |
| Confirmed amount does not match order total | 409 | `PAYMENT_AMOUNT_MISMATCH` |
| Same `paymentNo` with different order/channel/user | 409 | `IDEMPOTENCY_CONFLICT` |
| order-service timeout or unavailable | 503 | `DOWNSTREAM_TIMEOUT` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=OrderPaymentServiceTest,InternalOrderPaymentControllerTest,PaymentMigrationContractTest,PaymentPayServiceTest,PaymentControllerTest,SanguiPaymentApplicationSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: `POST /api/payments` persists one `pay_payment_order` row, confirms the order, and returns a paid payment response.
- Good: duplicate submit with the same `paymentNo`, same order, and same channel returns the original payment instead of creating another row.
- Good: a previously persisted `created` payment row can be retried and converges to `paid`.
- Base: `pay_callback_log` exists in schema for future callback processing even though the MVP does not expose callback endpoints yet.
- Bad: payment-service trusts body `shopId` or `userId`.
- Bad: payment-service reads `oms_*` tables directly.
- Bad: payment-service returns fake success when order-service times out.
