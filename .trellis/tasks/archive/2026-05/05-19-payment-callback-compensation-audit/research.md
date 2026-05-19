# Focused Research: 支付回调与补偿链路巡检

## Relevant Specs

- `.trellis/spec/backend/payment-pay-contracts.md`: primary payment callback, payment status, payment reconcile, manual/bulk compensation, callback log, validation matrix, and test commands.
- `.trellis/spec/backend/order-create-contracts.md`: order payment snapshot/confirmation, order status machine, timeout compensation, manual/bulk timeout replay, and order-side validation matrix.
- `.trellis/spec/backend/microservice-contracts.md`: cross-service DTO/API/event boundaries, idempotency requirements, error matrix, and retry expectations.
- `.trellis/spec/backend/database-guidelines.md`: payment/order callback and compensation indexes, `shop_id`, money/time fields, migration rules, and compensation metadata/history columns.
- `.trellis/spec/backend/messaging-cache-guidelines.md`: MQ/async retry/idempotency expectations; no existing MQ consumer was found in this focused path, but the retry rules are relevant to future expansion.
- `.trellis/spec/backend/error-handling.md`: callback processing order and duplicate callback behavior, exception mapping, sanitized responses.
- `.trellis/spec/backend/logging-guidelines.md`: required payment callback log fields and compensation audit fields.
- `.trellis/spec/backend/observability-devops.md`: compensation job env keys, scheduler defaults, metrics/alert expectations, Maven wrapper rule.
- `.trellis/spec/backend/quality-guidelines.md`: targeted Maven reactor command shape and test isolation rules.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: payment/order/inventory consistency and duplicate callback risk checklist.
- `.trellis/spec/guides/architecture-review-checklist.md`: service boundary, API/DB contracts, consistency, security, and observability review order.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: reuse existing service paths instead of adding parallel payment/order state machines.
- `.trellis/spec/guides/trellis-task-context-hygiene.md`: task context JSONL path rules for Codex/DeepSeek handoff.

## Code Patterns Found

- Payment callback path:
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/api/PaymentController.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentCallbackService.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/domain/PaymentCallbackLogDraft.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/domain/PaymentCallbackLogRecord.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/infrastructure/persistence/JdbcPaymentRepository.java`
  - Pattern: controller extracts trace id and delegates; service records callback log before state mutation; duplicate callback is resolved by existing `(channel, channelTradeNo)` log; success delegates to `PaymentPayService.settlePayment`.
- Payment pay/replay/settlement path:
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentPayService.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/client/OrderPaymentClient.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/client/ProductInventoryClient.java`
  - Pattern: idempotency key is `(shopId, paymentNo)`; existing paid returns immediately; created replay calls same settlement path; settlement confirms order, confirms inventory, then marks payment paid.
- Payment reconcile compensation:
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileService.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileScheduler.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentCompensationMetricsRecorder.java`
  - Pattern: scheduler creates trace id, sets MDC, logs batch start/end/failure, records metrics; service scans stale created rows and reconciles each via `PaymentPayService.settlePayment`; terminal order status invalid marks payment failed; other runtime failures record failed metadata but keep payment created.
- Order-side payment confirmation:
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/InternalOrderPaymentController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderPaymentService.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/domain/OrderStatus.java`
  - Pattern: payable snapshot only for owned `created` orders; `confirmPaid` accepts `created -> paid`, returns already paid as idempotent replay, rejects amount mismatch and invalid status.
- Order timeout compensation:
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/InternalOrderTimeoutController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCancelService.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCompensationScheduler.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderCompensationMetricsRecorder.java`
  - Pattern: scheduler mirrors payment job shape; service scans expired created orders by `shopId/status/createdAt`; single replay re-reads current order, skips non-created/not-timed-out, releases inventory, then updates status to cancelled and writes compensation metadata/history.
- Repository SQL patterns:
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/infrastructure/persistence/JdbcPaymentRepository.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/infrastructure/persistence/JdbcOrderRepository.java`
  - Pattern: repository methods scope queries by `shop_id`; migration files include payment/order reconcile lookup indexes and compensation metadata/history columns.
- Existing tests:
  - `PaymentCallbackServiceTest` covers duplicate success, failure callback for created payment, and late success after cancelled order.
  - `PaymentReconcileServiceTest` covers stale created settlement, terminal invalid order failure, partial failure continuation, and manual metadata.
  - `OrderTimeoutCancelServiceTest` covers expired cancel, duplicate timeout non-release, paid order skipped, partial release failure, and manual not-yet-timeout skip.
  - `OrderPaymentServiceTest` covers payable snapshot, confirm paid, paid idempotency, amount mismatch, and wrong owner.
  - Scheduler/controller/migration tests already exist and should be included in targeted runs.

## Files Likely To Modify

- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentCallbackService.java`: only if callback logs lack required failure context, duplicate callback status handling is proven wrong, or current status transition behavior needs a narrow fix.
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileService.java`: only if retryable vs terminal failure behavior or metadata/history writes need correction.
- `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/application/PaymentReconcileScheduler.java`: only if scheduler log fields or metrics violate spec.
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentCallbackServiceTest.java`: likely place to add coverage for channel mismatch, amount mismatch, failure callback after paid, and callback log process status.
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentReconcileServiceTest.java`: likely place to add coverage for retryable failure stays created and history/metadata assertions.
- `services/sangui-payment-service/src/test/java/com/sangui/shop/payment/application/PaymentReconcileSchedulerTest.java`: likely place for scheduler disabled/enabled/fatal branch coverage if log/metrics concerns are found.
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCancelService.java`: only if ordering of release/status update, skip behavior, or logs are proven deficient.
- `services/sangui-order-service/src/main/java/com/sangui/shop/order/application/OrderTimeoutCompensationScheduler.java`: only if scheduler log fields or metrics violate spec.
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/OrderTimeoutCancelServiceTest.java`: likely place to strengthen metadata/history assertions for cancelled/skipped/failed paths.
- `services/sangui-order-service/src/test/java/com/sangui/shop/order/application/OrderPaymentServiceTest.java`: likely place to add cancelled-order reject coverage if not already covered elsewhere.

## Risk / Boundary Notes

- Do not add a second payment settlement path. Callback success, pay replay, and reconcile must converge through the existing `PaymentPayService.settlePayment` path unless a test proves this path cannot satisfy the contract.
- Do not add a second order timeout state machine. Manual/bulk/scheduler should continue sharing `OrderTimeoutCancelService.replayTimeoutOrder`.
- Be careful with `PaymentCallbackService.recordCallback`: duplicate callback currently reuses the callback log record. If the first callback is still `received` or previously failed, the second call may still run service logic based on current payment state. Any change here must preserve duplicate-success idempotency and not hide legitimate retry behavior.
- Payment callback currently has no logger. Logging may already be covered via compensation paths but callback-specific failure logs should be checked against `.trellis/spec/backend/logging-guidelines.md` required fields.
- `PaymentPayService.completePayment` confirms order before inventory and updates payment status last. This preserves retryability when downstream calls fail after row creation, but a partial order-paid/inventory-failed case relies on replay/reconcile idempotency. Do not change the order casually.
- Order timeout releases inventory before `created -> cancelled` update. If the status update loses a race to payment, code re-reads and skips. This relies on inventory release idempotency upstream; do not introduce payment-service inventory release.
- No MQ implementation was found in the focused payment/order compensation path. Do not invent MQ changes for this task.
- Frontend files exist for compensation dashboards and order/payment smoke tests, but this task is backend-only unless API shapes change with user approval.
- Existing working tree already has unrelated Trellis archive changes. DeepSeek should avoid touching or reverting those.

## Required Tests

Primary targeted command:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=PaymentPayServiceTest,PaymentCallbackServiceTest,PaymentReconcileServiceTest,PaymentReconcileSchedulerTest,PaymentControllerTest,OrderPaymentServiceTest,OrderTimeoutCancelServiceTest,OrderTimeoutCompensationSchedulerTest,InternalOrderPaymentControllerTest,InternalOrderTimeoutControllerTest,PaymentReconcileMigrationContractTest,OrderMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Compensation ops surface command if internal manual/bulk/query controllers or audit behavior are touched:

```powershell
.\scripts\verify-compensation-ops-audit.ps1 -Service all -MavenRepoLocal .\.m2\repository
```

Repository/migration-focused command if SQL/migration files are touched:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-payment-service" -am "-Dtest=OrderCompensationOpsMigrationContractTest,OrderCompensationAttemptHistoryMigrationContractTest,PaymentMigrationContractTest,PaymentReconcileMigrationContractTest,PaymentCompensationOpsMigrationContractTest,PaymentCompensationAttemptHistoryMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```
