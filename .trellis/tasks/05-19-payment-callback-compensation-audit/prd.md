# 支付回调与补偿链路巡检

## Task Classification

Complex Task.

Reason: this task spans payment callback, payment reconcile compensation, order payment confirmation, order timeout compensation, repository state transitions, logs, metrics, and targeted Maven tests. It may uncover small implementation or test gaps, but this Codex round is limited to planning, research, task context setup, and handoff. Business code changes are explicitly deferred to the DeepSeek execution round.

## Goal

Audit the payment callback and compensation paths for money-state consistency. Confirm the order and payment state machines are idempotent under duplicate callbacks, late callbacks, timeout compensation races, retryable failures, and manual/scheduler compensation. If small gaps are confirmed during execution, fix them narrowly and add focused tests.

## In Scope

- Payment callback entry:
  - `POST /api/payments/callbacks/mock`
  - callback log idempotency by `(channel, channelTradeNo)`
  - callback validation for payment existence, channel, amount, trade status, and terminal failure states
- Payment status and pay/replay:
  - `POST /api/payments`
  - `GET /api/payments/{paymentNo}`
  - `PaymentPayService.settlePayment(...)`
- Payment reconcile compensation:
  - scheduler-driven stale `created` payment reconciliation
  - manual/bulk reconcile paths only where they share the same service path
  - latest compensation metadata and append-only attempt history
- Order-side payment confirmation:
  - `POST /internal/orders/payment-snapshot`
  - `POST /internal/orders/payment-confirmations`
  - `created -> paid` transition and idempotent paid replay
- Order timeout compensation:
  - stale `created` order selection
  - `created -> cancelled` transition
  - inventory release before cancellation
  - paid/cancelled race behavior and compensation metadata/history
- Observability:
  - logs must include `traceId`, `shopId`, `orderNo` or `orderId`, `paymentNo`, and payment provider trade id when applicable
  - scheduler batch logs must include counts and sanitized error fields
  - metrics must stay bounded and avoid `traceId` labels
- Tests:
  - add or adjust focused unit/controller tests only for confirmed gaps
  - run targeted Maven tests for payment/order compensation paths

## Out of Scope

- Real third-party payment provider integration or real signature verification.
- New payment provider secrets, Nacos secret refs, or production credential handling.
- New MQ topics/consumers or an outbox rewrite unless an existing test proves a current path is broken and cannot be fixed narrowly.
- New database tables or migrations unless an existing required column/index is demonstrably missing from current migrations.
- Frontend changes, dashboards, or TypeScript DTO edits unless a backend API contract is intentionally changed after explicit user confirmation.
- Seckill full-chain concurrency/MQ redesign.
- Broad refactors across common libraries, global exception handling, gateway auth, or product inventory service.
- Trellis archive cleanup unrelated to this new task.

## Existing Contracts To Preserve

### Payment Callback API

Command/API:

```text
POST /api/payments/callbacks/mock
```

Payload fields:

| Field | Required | Notes |
| --- | --- | --- |
| `shopId` | yes | must be scoped to the callback/payment lookup |
| `paymentNo` | yes | payment business id |
| `channel` | yes | must match payment row channel |
| `channelTradeNo` | yes | provider callback idempotency key |
| `tradeStatus` | yes | accepted success values: `SUCCESS`, `PAID`, `TRADE_SUCCESS`; accepted failure values: `FAILED`, `FAIL`, `CLOSED`, `TRADE_CLOSED` |
| `paidAmountCent` | yes | must match persisted payment amount for success callbacks |
| `callbackType` | no | defaults to `payment` |
| `eventTime` | no | provider event time, kept in callback payload JSON |
| `rawPayload` | no | must not be logged unsafely |

Response code: `PAYMENT_CALLBACK_PROCESSED`.

Validation/error matrix:

| Case | Expected HTTP | Expected code/status |
| --- | --- | --- |
| Missing or blank required callback fields | 400 | `VALIDATION_FAILED` |
| Unknown `tradeStatus` | 400 | `VALIDATION_FAILED` |
| Payment missing | 404 | `PAYMENT_NOT_FOUND` |
| Callback channel differs from payment channel | 409 | `PAYMENT_CALLBACK_CHANNEL_MISMATCH` |
| Success paid amount mismatch | 409 | `PAYMENT_AMOUNT_MISMATCH` |
| Success callback after order timeout cancellation | 409 | `PAYMENT_ORDER_STATUS_INVALID` |
| Duplicate success callback | 200 | one callback identity, payment remains `paid`, no duplicate order/inventory confirm |
| Failure callback after `paid` | 200 | payment remains `paid`, callback process status `ignored` |

### Payment Reconcile Commands

Scheduler config keys:

```text
sangui.compensation.payment-reconcile.enabled
sangui.compensation.payment-reconcile.shop-id
sangui.compensation.payment-reconcile.min-age-minutes
sangui.compensation.payment-reconcile.limit
sangui.compensation.payment-reconcile.initial-delay-ms
sangui.compensation.payment-reconcile.fixed-delay-ms
```

Manual/internal command:

```text
POST /internal/payments/reconciliations/manual
POST /internal/payments/reconciliations/bulk
POST /internal/payments/compensation-records/query
```

Required behavior:

- Scheduler disabled by default.
- Candidate query uses `shop_id`, `status = created`, and `created_at <= cutoff`.
- Each candidate is re-read by `(shopId, paymentNo)` before settlement.
- `PAYMENT_ORDER_STATUS_INVALID` is terminal and marks payment `failed`.
- Retryable/system failures keep payment `created`.
- Batch continues after single item failure.
- Manual/bulk paths reuse the same single-payment reconcile path.

### Order Timeout Compensation Command

Scheduler config keys:

```text
sangui.compensation.order-timeout.enabled
sangui.compensation.order-timeout.shop-id
sangui.compensation.order-timeout.timeout-minutes
sangui.compensation.order-timeout.limit
sangui.compensation.order-timeout.initial-delay-ms
sangui.compensation.order-timeout.fixed-delay-ms
```

Internal commands:

```text
POST /internal/orders/timeout-cancellations
POST /internal/orders/timeout-replays/manual
POST /internal/orders/timeout-replays/bulk
POST /internal/orders/compensation-records/query
```

Required behavior:

- Candidate query uses `shop_id`, `status = created`, and `created_at <= cutoff`.
- Timeout replay re-reads current order state by `(shopId, orderId)`.
- Non-`created` orders are skipped; they are not forced into `cancelled`.
- Inventory reservation is released before order becomes `cancelled`.
- One row failure does not abort the batch.
- Manual/bulk paths reuse the same single-order timeout replay path.

## State Machines

Payment statuses:

| Current | Trigger | Next | Required behavior |
| --- | --- | --- | --- |
| none | pay create | `created` | local payment row first |
| `created` | pay replay/callback success/reconcile | `paid` | confirm order and inventory exactly once per converged success |
| `created` | terminal failure callback | `failed` | do not mutate order or inventory |
| `created` | reconcile terminal invalid order | `failed` | stop endless retry |
| `created` | reconcile retryable failure | `created` | keep retryable |
| `paid` | duplicate success or failure callback | `paid` | success idempotent; failure ignored |
| `failed` | success callback/reconcile | no force unless existing contract explicitly permits | verify current behavior before changing |

Order statuses:

| Current | Trigger | Next | Required behavior |
| --- | --- | --- | --- |
| `created` | payment confirmation | `paid` | idempotent confirmation |
| `paid` | payment confirmation replay | `paid` | no error |
| `created` | timeout cancellation | `cancelled` | release inventory before status update |
| `paid` | timeout cancellation | `paid` | skipped; payment wins race |
| `cancelled` | timeout replay | `cancelled` | skipped |
| `cancelled` | late payment success | `cancelled` | payment callback/reconcile must not revive order |

## Good / Base / Bad Cases

Good:

- Duplicate success callback reuses one callback log identity and does not double-confirm order or inventory.
- Failure callback for `created` payment marks payment `failed` without inventory release or order mutation by payment-service.
- Failure callback after `paid` keeps payment `paid` and marks callback ignored.
- Late success after order timeout cancellation logs the callback as failed, keeps payment not paid, and does not confirm released inventory.
- Payment reconcile settles stale `created` rows through the same path as pay replay/callback success.
- Payment reconcile marks terminal invalid-order rows `failed` and leaves retryable downstream failures as `created`.
- Order timeout compensation skips `paid` rows and does not release inventory twice.
- Scheduler logs include batch counts, `traceId`, `shopId`, timing, and sanitized error fields.
- Compensation attempts append history and update latest metadata with the same trace id.

Base:

- Mock callback path remains provider-neutral and does not verify real signatures.
- `created_at` remains the timeout/reconcile cutoff until a dedicated deadline field exists.
- Existing manual/bulk/query compensation APIs remain unchanged if no implementation gap is found.
- Test-only in-memory repositories are acceptable for application-service branches; repository/migration contract tests cover SQL shape.

Bad:

- Callback trusts unsafe payload fields to override persisted payment/order facts.
- Duplicate callback reconfirms inventory/order after payment is already `paid`.
- Timeout compensation selects or mutates paid orders.
- Late success callback revives a cancelled order or confirms inventory after release.
- Reconcile job retries terminal invalid-order rows forever.
- Logs omit `traceId`, `shopId`, `orderNo`/`paymentNo`, or `channelTradeNo` for payment callback failures.
- Metrics include high-cardinality tags such as `traceId`.
- Any service directly reads another service's database tables.

## Expected Files To Inspect Or Modify

Likely implementation/test files if gaps are confirmed:

- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentCallbackService.java`
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileService.java`
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileScheduler.java`
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentPayService.java`
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/api/PaymentController.java`
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/infrastructure/persistence/JdbcPaymentRepository.java`
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentCallbackServiceTest.java`
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentReconcileServiceTest.java`
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentReconcileSchedulerTest.java`
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/api/PaymentControllerTest.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderPaymentService.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCancelService.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCompensationScheduler.java`
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/OrderPaymentServiceTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/OrderTimeoutCancelServiceTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/OrderTimeoutCompensationSchedulerTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/api/InternalOrderPaymentControllerTest.java`
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/api/InternalOrderTimeoutControllerTest.java`

Do not modify all of these by default. Modify only files needed to close a proven gap.

## Required Tests And Assertion Points

Primary targeted command:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=PaymentPayServiceTest,PaymentCallbackServiceTest,PaymentReconcileServiceTest,PaymentReconcileSchedulerTest,PaymentControllerTest,OrderPaymentServiceTest,OrderTimeoutCancelServiceTest,OrderTimeoutCompensationSchedulerTest,InternalOrderPaymentControllerTest,InternalOrderTimeoutControllerTest,PaymentReconcileMigrationContractTest,OrderMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

If compensation ops surfaces are touched:

```powershell
.\scripts\verify-compensation-ops-audit.ps1 -Service all -MavenRepoLocal .\.m2\repository
```

If any repository/migration contract changes are touched:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=OrderCompensationOpsMigrationContractTest,OrderCompensationAttemptHistoryMigrationContractTest,PaymentMigrationContractTest,PaymentReconcileMigrationContractTest,PaymentCompensationOpsMigrationContractTest,PaymentCompensationAttemptHistoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Minimum assertion points:

- Callback duplicate success: one callback identity, one order confirm, one inventory confirm.
- Callback channel mismatch: returns `PAYMENT_CALLBACK_CHANNEL_MISMATCH`, callback process status is failed.
- Callback amount mismatch: returns `PAYMENT_AMOUNT_MISMATCH`, payment remains non-paid.
- Callback terminal failure on paid payment: payment remains paid, callback status ignored.
- Late success after cancelled order: callback failed, payment remains non-paid, inventory confirm not called.
- Reconcile stale created success: payment becomes paid, metadata/history written for manual/scheduler when applicable.
- Reconcile terminal invalid order: payment becomes failed, attempt history records failure.
- Reconcile retryable exception: payment remains created and batch continues.
- Order payment confirm: `created -> paid`, already paid replay returns paid, amount mismatch rejected.
- Order timeout: expired created order releases inventory and becomes cancelled; paid order skipped; partial release failure increments failed without aborting batch.
- Logs/scheduler tests, where feasible, assert required fields or at least exercise branches that emit them.

## Acceptance Criteria

- [ ] Focused research confirms current callback, payment reconcile, order payment, and order timeout compensation flow.
- [ ] DeepSeek implementation, if any, changes only narrow service/test files listed above or justified equivalents.
- [ ] No frontend, gateway, product-service, seckill, DB migration, MQ, or infra changes unless explicitly justified and approved.
- [ ] Duplicate/late/failed callback behavior is covered by tests.
- [ ] Reconcile retry/terminal behavior is covered by tests.
- [ ] Order timeout/payment race behavior is covered by tests.
- [ ] Required log fields are either already present or added in the narrowest relevant service method.
- [ ] Required Maven tests pass, with actual Surefire output showing intended test classes ran.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: defined in Out of Scope.
- Expected modify files: listed above and explicitly limited.
- Must-run tests: listed above.
- Specific guidelines read: backend `directory-structure`, `microservice-contracts`, `database-guidelines`, `messaging-cache-guidelines`, `payment-pay-contracts`, `order-create-contracts`, `error-handling`, `logging-guidelines`, `observability-devops`, `quality-guidelines`; guides `cross-layer-thinking-guide`, `architecture-review-checklist`, `code-reuse-thinking-guide`, `trellis-task-context-hygiene`.
- Open user questions: none before DeepSeek starts, because scope is audit plus narrow bug/test fix. Any API/DB/frontend type expansion requires returning to user for confirmation.
- API/DB/frontend alignment: existing backend contracts define callback/reconcile/order timeout payloads. No frontend type change is planned.
