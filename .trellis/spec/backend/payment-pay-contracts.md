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
| `services/sangui-payment-service` | `SANGUI_PAYMENT_MYSQL_SCHEMA` | `sangui_payment` | `db/migration/V1__create_payment_tables.sql`, `db/migration/V2__add_payment_reservation_reference.sql`, `db/migration/V3__add_payment_reconcile_lookup_index.sql` |

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
- `idx_pay_payment_order_shop_status_created (shop_id, status, created_at)`

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

## Payment Callback / Reconcile Compensation Addendum

### `GET /api/payments/{paymentNo}`

Response code: `PAYMENT_STATUS`.

Rules:

- Controller uses trusted `SanguiPrincipal`.
- Effective `shopId` / `userId` come from principal only.
- Missing payment or wrong owner returns `PAYMENT_NOT_FOUND`.

### `POST /api/payments/callbacks/mock`

Request:

```json
{
  "shopId": 1,
  "paymentNo": "PAY-20260501-0001",
  "channel": "mock",
  "channelTradeNo": "MOCK-TXN-0001",
  "tradeStatus": "SUCCESS",
  "paidAmountCent": 119800,
  "callbackType": "payment",
  "eventTime": "2026-05-01T21:30:00+08:00",
  "rawPayload": "{\"provider\":\"mock\"}"
}
```

Response code: `PAYMENT_CALLBACK_PROCESSED`.

Rules:

- This is the MVP mock async callback path; real provider signature verification is out of scope until provider integration.
- Required fields: `shopId`, `paymentNo`, `channel`, `channelTradeNo`, `tradeStatus`, and `paidAmountCent`.
- Callback idempotency is `UNIQUE(channel, channel_trade_no)` in `pay_callback_log`.
- Every accepted callback is written to `pay_callback_log` before mutating payment/order/inventory state.
- `SUCCESS`, `PAID`, and `TRADE_SUCCESS` settle a created payment.
- `FAILED`, `FAIL`, `CLOSED`, and `TRADE_CLOSED` mark a non-paid payment `failed`.
- Success callback validates payment existence, channel, and paid amount before settlement.
- Failure callback never downgrades a `paid` payment.

Additional persisted payment status:

- `failed`

### Scheduled Payment Reconcile Job

Config keys:

- `sangui.compensation.payment-reconcile.enabled`
- `sangui.compensation.payment-reconcile.shop-id`
- `sangui.compensation.payment-reconcile.min-age-minutes`
- `sangui.compensation.payment-reconcile.limit`
- `sangui.compensation.payment-reconcile.initial-delay-ms`
- `sangui.compensation.payment-reconcile.fixed-delay-ms`

Deploy env keys:

- `SANGUI_PAYMENT_RECONCILE_ENABLED`
- `SANGUI_PAYMENT_RECONCILE_SHOP_ID`
- `SANGUI_PAYMENT_RECONCILE_MIN_AGE_MINUTES`
- `SANGUI_PAYMENT_RECONCILE_LIMIT`
- `SANGUI_PAYMENT_RECONCILE_INITIAL_DELAY_MS`
- `SANGUI_PAYMENT_RECONCILE_FIXED_DELAY_MS`

Rules:

- Scheduler is disabled by default; enabling it scans stale `created` payments and retries internal settlement without a human-triggered replay.
- `shop-id` must come from configuration, typically `${SANGUI_DEFAULT_SHOP_ID}`, and must not be hardcoded in Java business logic.
- Query candidates from `pay_payment_order` where `shop_id = ?`, `status = created`, and `created_at <= now - minAgeMinutes`.
- Default `minAgeMinutes` is 1; default `limit` is 100; maximum `limit` is 500.
- Each candidate must be re-read by `(shopId, paymentNo)` before settlement; non-`created` rows are skipped.
- Successful reconcile reuses the same settle path as replay or callback success and converges `created -> paid`.
- `PAYMENT_ORDER_STATUS_INVALID` during reconcile is terminal for that payment row and must mark it `failed` to stop endless retries.
- `DOWNSTREAM_TIMEOUT` or other retryable/system failures keep the row in `created` so later batches can retry.
- Batch execution must continue when one payment fails; batch logs must include `jobName`, `shopId`, `traceId`, `minAgeMinutes`, `limit`, `durationMs`, `scannedCount`, `settledCount`, `skippedCount`, and `failedCount`.
- Batch-fatal logs must also include `errorType`, `errorCode`, and sanitized `message` instead of dumping raw multi-line stack traces for expected test scenarios.

Metrics contract:

- `sangui.compensation.job.run.total{service="payment",job="payment-reconcile",trigger="scheduler|manual",result="success|failed|disabled"}`
- `sangui.compensation.job.item.total{service="payment",job="payment-reconcile",trigger="scheduler|manual",result="scanned|settled|skipped|failed"}`
- Do not tag these counters with `traceId`; keep traceability in logs and keep metrics cardinality bounded.

Alert thresholds:

- Critical: `increase(sangui_compensation_job_run_total{service="payment",job="payment-reconcile",result="failed"}[5m]) > 0`
- Warning: `increase(sangui_compensation_job_item_total{service="payment",job="payment-reconcile",result="failed"}[15m]) >= 1`
- Warning: if `SANGUI_PAYMENT_RECONCILE_ENABLED=true`, investigate when `increase(sangui_compensation_job_run_total{service="payment",job="payment-reconcile",result="success"}[15m]) == 0`

Additional `pay_callback_log` contract:

- Required business columns: `payment_no`, `channel`, `channel_trade_no`, `callback_type`, `payload_json`, `process_status`, `trace_id`.
- Required constraints and indexes: `uk_pay_callback_log_channel_trade_no (channel, channel_trade_no)`, `idx_pay_callback_log_shop_payment_no (shop_id, payment_no)`.

Compensation matrix:

| Scenario | Expected Result |
| --- | --- |
| Duplicate success callback | One callback identity, one payment settlement, one order confirm, one inventory confirm. |
| Failure callback for created payment | Payment becomes `failed`; order/inventory are not mutated by payment-service. |
| Failure callback after paid | Payment remains `paid`; callback is marked `ignored`. |
| Success callback after timeout cancellation | Callback is logged as `failed`; payment remains not paid; order is not revived; inventory is not confirmed. |
| Payment status polling by wrong user | Returns `PAYMENT_NOT_FOUND`. |
| Payment reconcile after partial settle failure | Existing `created` row retries order/inventory confirms and converges to `paid`. |
| Payment reconcile after timeout-cancelled order | Payment becomes `failed`; inventory is not released by payment-service. |

Additional validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Payment missing on status query or callback | 404 | `PAYMENT_NOT_FOUND` |
| Callback channel differs from payment channel | 409 | `PAYMENT_CALLBACK_CHANNEL_MISMATCH` |
| Callback paid amount mismatch | 409 | `PAYMENT_AMOUNT_MISMATCH` |
| Success callback after order timeout cancellation | 409 | `PAYMENT_ORDER_STATUS_INVALID` |
| Unknown callback `tradeStatus` | 400 | `VALIDATION_FAILED` |

Additional required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=PaymentPayServiceTest,PaymentCallbackServiceTest,PaymentReconcileServiceTest,PaymentReconcileSchedulerTest,PaymentControllerTest,OrderPaymentServiceTest,PaymentReconcileMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: duplicate success callback writes or reuses one callback log identity and does not reconfirm order/inventory.
- Good: failure callback marks a created payment `failed` without releasing inventory directly.
- Good: polling returns current payment status only for the owning principal.
- Good: reconcile job settles a stale `created` row through the same path as replay.
- Good: reconcile job marks terminal invalid-order rows `failed` instead of retrying forever.
- Good: reconcile scheduler metrics expose run results and batch item counts without adding high-cardinality tags.
- Base: callback path is mock/provider-neutral and does not verify real third-party signatures.
- Bad: late success callback after timeout cancellation revives a cancelled order or confirms released inventory.
- Bad: reconcile job double-confirms a row that is already `paid` or releases inventory from payment-service.
## Compensation Ops Surface Addendum

### `POST /internal/payments/compensation-records/query`

Request:

```json
{
  "shopId": 1,
  "paymentNo": "PAY-001",
  "orderId": 101,
  "trigger": "scheduler",
  "result": "failed",
  "operator": "ops-user",
  "traceId": "trace-manual-payment",
  "fromTime": "2026-05-03T12:00:00+08:00",
  "toTime": "2026-05-03T12:10:00+08:00",
  "pageNo": 1,
  "pageSize": 20
}
```

Response code: `PAYMENT_COMPENSATION_RECORDS_FETCHED`.

Response data:

```json
{
  "shopId": 1,
  "pageNo": 1,
  "pageSize": 20,
  "total": 1,
  "items": [
    {
      "payment": {
        "paymentId": 201,
        "paymentNo": "PAY-001",
        "orderId": 101,
        "orderNo": "ORD-001",
        "userId": "10001",
        "channel": "mock",
        "status": "failed",
        "amountCent": 59900,
        "traceId": "trace-pay-1",
        "createdAt": "2026-05-03T12:00:00+08:00",
        "updatedAt": "2026-05-03T12:05:00+08:00",
        "lastCompensationResult": "failed",
        "lastCompensationErrorCode": "DOWNSTREAM_TIMEOUT",
        "lastCompensationReason": "order confirm timeout",
        "lastCompensationTraceId": "payment-reconcile-job-xxx",
        "lastCompensationTrigger": "scheduler",
        "lastCompensationOperator": null,
        "lastCompensatedAt": "2026-05-03T12:05:00+08:00"
      },
      "matchedAttemptCount": 1,
      "totalAttemptCount": 2,
      "latestAttemptAt": "2026-05-03T12:05:00+08:00",
      "attempts": [
        {
          "attemptId": 9001,
          "paymentId": 201,
          "orderId": 101,
          "paymentNo": "PAY-001",
          "orderNo": "ORD-001",
          "reservationNo": "ord:10001:req-001",
          "result": "failed",
          "errorCode": "DOWNSTREAM_TIMEOUT",
          "reason": "order confirm timeout",
          "traceId": "payment-reconcile-job-xxx",
          "trigger": "scheduler",
          "operator": null,
          "createdAt": "2026-05-03T12:05:00+08:00",
          "updatedAt": "2026-05-03T12:05:00+08:00"
        }
      ]
    }
  ]
}
```

Rules:

- `shopId` is required.
- `pageNo` defaults to 1; `pageSize` defaults to 20 and must stay capped at 100.
- History filtering is backed by `pay_payment_compensation_attempt`, not only `pay_payment_order.last_compensation_*`.
- Supported filters are `paymentNo`, `orderId`, `trigger`, `result`, `operator`, `traceId`, and optional `fromTime` / `toTime`.
- Pagination is applied to distinct `paymentId` aggregates ordered by latest matched `created_at DESC, payment_id DESC`.
- Each aggregate returns the latest payment snapshot from `pay_payment_order` plus the full ordered attempt detail list from `pay_payment_compensation_attempt`.
- Query responses must expose `createdAt`, `updatedAt`, `lastCompensatedAt`, and per-attempt `createdAt` / `updatedAt`.

### `POST /internal/payments/reconciliations/manual`

Request:

```json
{
  "shopId": 1,
  "paymentNo": "PAY-001"
}
```

Response code: `PAYMENT_RECONCILED_MANUALLY`.

Response data:

```json
{
  "result": "settled",
  "errorCode": null,
  "reason": null,
  "payment": {
    "paymentId": 201,
    "paymentNo": "PAY-001",
    "status": "paid",
    "lastCompensationResult": "settled",
    "lastCompensationTraceId": "trace-manual-payment",
    "lastCompensationTrigger": "manual",
    "lastCompensatedAt": "2026-05-03T12:05:00+08:00"
  }
}
```

Rules:

- Manual reconcile reuses the same internal settlement path as scheduler reconcile and callback success.
- Missing payment returns `PAYMENT_NOT_FOUND`.
- Non-`created` rows return HTTP 200 with `result = skipped`; they do not force a state transition.
- `PAYMENT_ORDER_STATUS_INVALID` remains terminal and keeps the row in `failed`.
- Replay success persists latest-compensation metadata on `pay_payment_order`.
- Replay failure persists `lastCompensationResult = failed` plus sanitized `errorCode` / `reason`.
- Manual replay logs must include `traceId`, `trigger=manual`, `shopId`, `paymentId`, `paymentNo`, `orderId`, `reservationNo`, `result`, and current `paymentStatus`.

Additional database addendum:

- `services/sangui-payment-service/src/main/resources/db/migration/V4__add_payment_compensation_ops_columns.sql`
- Required latest-compensation columns on `pay_payment_order`:
  - `last_compensation_result`
  - `last_compensation_error_code`
  - `last_compensation_reason`
  - `last_compensation_trace_id`
  - `last_compensation_trigger`
  - `last_compensated_at`

Additional validation and error matrix:

| Case | HTTP | code |
| --- | --- | --- |
| Query/manual request missing `shopId` | 400 | `VALIDATION_FAILED` |
| Blank `paymentNo` | 400 | `VALIDATION_FAILED` |
| `pageNo <= 0` or `pageSize <= 0` | 400 | `VALIDATION_FAILED` |
| `fromTime > toTime` | 400 | `VALIDATION_FAILED` |
| Manual reconcile payment missing | 404 | `PAYMENT_NOT_FOUND` |
| Downstream timeout during manual reconcile | 503 | `DOWNSTREAM_TIMEOUT` |

Additional required tests:

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=PaymentReconcileServiceTest,PaymentCompensationOpsServiceTest,InternalPaymentCompensationControllerTest,PaymentCompensationQueryResponseJsonTest,PaymentCompensationOpsMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: manual reconcile returns `settled`, `skipped`, or `failed` without bypassing existing settle rules.
- Good: query surfaces show both latest business status and nested attempt history in one response.
- Good: manual reconcile and scheduler share the same metrics family, overwrite latest metadata, and append attempt history.
- Good: dry-run bulk reconcile previews bounded work without mutating rows or history.
- Base: latest metadata remains on the row while history tables preserve every attempt for drill-down.
- Bad: manual reconcile revives a non-`created` payment by force.
- Bad: ops query omits failure reason, traceId, or key timestamps needed for troubleshooting.

### Bulk Reconcile Addendum

`POST /internal/payments/reconciliations/bulk`

Request fields:

- `shopId`
- `dryRun`
- `operator`
- `limit`
- one of `minAgeMinutes` or `paymentNos`

Rules:

- `dryRun=true` must not mutate `pay_payment_order` or append compensation history.
- `operator` is required for manual accountability even during dry-run.
- `limit` is required, positive, and capped at 500.
- Bulk reconcile reuses the same single-payment reconcile path for each item.
- Per-item results are bounded to `would-settle`, `settled`, `skipped`, or `failed`.

V5 addendum:

- `services/sangui-payment-service/src/main/resources/db/migration/V5__add_payment_compensation_attempt_history.sql`
- latest-compensation columns on `pay_payment_order` now include `last_compensation_operator`
- history table `pay_payment_compensation_attempt` is append-only and stores `payment_id`, `order_id`, `payment_no`, `order_no`, `reservation_no`, `result`, `error_code`, `reason`, `trace_id`, `trigger_type`, and `operator`
