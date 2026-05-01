# Payment Pay Contracts

## Scope

Payment Pay MVP for `services/sangui-payment-service`.

This flow now finalizes both order state and inventory reservation state.

## External API Contract

### `POST /api/payments`

Request:

```json
{
  "shopId": 999,
  "userId": "spoof-user",
  "orderId": 101,
  "paymentNo": "PAY-20260501-0001",
  "channel": "mock"
}
```

Response:

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

Rules:

- Controller uses trusted `SanguiPrincipal`.
- Effective `shopId` / `userId` come from principal only.
- `paymentNo` is the pay idempotency key inside `(shopId)`.
- Request does not carry `amountCent`; amount comes from internal order snapshot.

## Internal Dependencies

### `POST /internal/orders/payment-snapshot`

Response data:

```json
{
  "orderId": 101,
  "orderNo": "ORD9F5C0A1B2C3D4E5F6A7B",
  "shopId": 1,
  "userId": "10001",
  "reservationNo": "ord:10001:req-20260501-0001",
  "status": "created",
  "totalAmountCent": 119800
}
```

### `POST /internal/orders/payment-confirmations`

Rules:

- `paid` remains idempotent.
- `ORDER_NOT_FOUND`, `ORDER_STATUS_INVALID`, and amount mismatch remain mapped to payment-domain business errors.

### `POST /internal/products/inventory/confirmations`

Request:

```json
{
  "shopId": 1,
  "reservationNo": "ord:10001:req-20260501-0001"
}
```

Rules:

- Payment success must confirm the order reservation in product-service.
- Inventory confirm must be retried on payment replay for previously persisted `created` payment rows.
- Payment-service must never read `pms_*` tables directly.

## Payment State Machine

Persisted payment statuses:

- `created`
- `paid`

Valid transitions:

| Current | Operation | Next | Notes |
| --- | --- | --- | --- |
| none | create payment row | `created` | local row first |
| `created` | confirm order + confirm inventory | `paid` | synchronous MVP |
| `paid` | same `paymentNo` replay | `paid` | idempotent replay |

Retry rule:

- If downstream confirm steps fail after `pay_payment_order` is inserted, the row stays `created`.
- Replaying the same `(shopId, paymentNo)` with the same effective payload must retry missing confirms and converge to `paid`.
- Replaying with a different effective payload must fail with `IDEMPOTENCY_CONFLICT`.

## Database Contract

Schema env and migrations:

| Service | Schema Env | Default Schema | Migrations |
| --- | --- | --- | --- |
| `services/sangui-payment-service` | `SANGUI_PAYMENT_MYSQL_SCHEMA` | `sangui_payment` | `db/migration/V1__create_payment_tables.sql`, `db/migration/V2__add_payment_reservation_reference.sql` |

### `pay_payment_order`

Required business columns:

- `order_id`
- `order_no`
- `user_id`
- `reservation_no`
- `payment_no`
- `channel`
- `amount_cent`
- `trace_id`
- `status`

Required constraints and indexes:

- `uk_pay_payment_order_shop_payment_no (shop_id, payment_no)`
- `idx_pay_payment_order_shop_order_id (shop_id, order_id)`
- `idx_pay_payment_order_shop_user_id (shop_id, user_id, id)`
- `idx_pay_payment_order_shop_status (shop_id, status)`

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing trusted principal | 401 | `AUTH_TOKEN_MISSING` |
| DTO validation failure | 400 | `VALIDATION_FAILED` |
| Order missing or wrong owner | 404 | `PAYMENT_ORDER_NOT_FOUND` |
| Order status not payable | 409 | `PAYMENT_ORDER_STATUS_INVALID` |
| Paid amount mismatch | 409 | `PAYMENT_AMOUNT_MISMATCH` |
| Same `paymentNo` with different order/channel/user | 409 | `IDEMPOTENCY_CONFLICT` |
| order-service or product-service timeout/unavailable | 503 | `DOWNSTREAM_TIMEOUT` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=OrderPaymentServiceTest,InternalOrderPaymentControllerTest,PaymentPayServiceTest,PaymentControllerTest,SanguiPaymentApplicationSmokeTest,PaymentMigrationContractTest,PaymentReservationMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Good / Base / Bad Cases

- Good: `POST /api/payments` persists one payment row, confirms order state, confirms inventory reservation, and returns `paid`.
- Good: duplicate submit with the same `paymentNo` returns the original payment instead of creating another row.
- Good: a previously persisted `created` payment row can be replayed until inventory and order both converge to paid state.
- Base: `pay_callback_log` remains reserved for future callback processing.
- Bad: payment-service trusts body `shopId` or `userId`.
- Bad: payment-service reads `oms_*` or `pms_*` tables directly.
- Bad: replaying the same `paymentNo` double-confirms inventory or creates another payment row.
