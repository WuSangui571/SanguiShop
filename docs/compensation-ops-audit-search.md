# Compensation Ops Audit Search

This document defines how operators search `Ops audit event.` logs emitted by the compensation ops surfaces.

The dashboard generates copyable templates and can open configured Kibana Discover or Loki Explore URLs. It does not query Elasticsearch, Kibana, Loki, or business history tables directly.

For real-environment operator acceptance, use `docs/compensation-ops-audit-manual-checklist.md`.

## Canonical Fields

| Field | Required | Meaning | Example |
| --- | --- | --- | --- |
| `action` | yes | Stable operation name. | `ops.payment.reconcile.manual` |
| `outcome` | yes | Controller/audit outcome. | `success`, `failed`, `denied` |
| `result` | action-dependent | Domain result for successful controller calls. | `settled`, `cancelled`, `skipped`, `dry-run` |
| `traceId` | yes | Request trace returned in the API envelope and log line. | `trace-payment-manual` |
| `method` | yes | HTTP method. | `POST` |
| `path` | yes | Gateway or internal request path. | `/internal/payments/reconciliations/manual` |
| `shopId` | protected ops paths | Trusted principal or request shop scope. | `1` |
| `userId` | authenticated paths | Trusted principal user id. | `10001` |
| `username` | auth success | Resolved ops username. | `ops-admin` |
| `userIdentifier` | login paths | Submitted login identifier. | `ops-admin` |
| `operator` | replay/reconcile paths | Manual operator supplied by the ops dashboard. | `ops-user` |
| `permission` | protected ops paths | Required ops permission. | `OPS_COMPENSATION_ADMIN` |
| `targetType` | replay/reconcile paths | Business target type. | `order`, `payment` |
| `targetId` | manual paths | Business target id. | `101`, `PAY-001` |
| `targetCount` | bulk paths | Bounded bulk target count. | `1` |
| `dryRun` | bulk paths | Whether bulk replay/reconcile mutates state. | `true`, `false` |
| `errorCode` | failed/denied paths | Sanitized business or auth error code. | `AUTH_FORBIDDEN` |
| `reason` | failed/denied paths | Sanitized single-line reason. | `Access is forbidden` |
| `ip` | yes | Client IP or first `X-Forwarded-For` segment. | `127.0.0.1` |
| `jwtId` | authenticated paths | JWT id claim, never the raw token. | `jwt-ops-1` |

## Supported Actions

| Action | Surface |
| --- | --- |
| `ops.auth.login` | `POST /api/users/ops/login` |
| `ops.auth.refresh` | `POST /api/users/ops/session/refresh` |
| `ops.order.compensation.query` | `POST /api/internal/orders/compensation-records/query` |
| `ops.order.timeout-replay.manual` | `POST /api/internal/orders/timeout-replays/manual` |
| `ops.order.timeout-replay.bulk` | `POST /api/internal/orders/timeout-replays/bulk` |
| `ops.payment.compensation.query` | `POST /api/internal/payments/compensation-records/query` |
| `ops.payment.reconcile.manual` | `POST /api/internal/payments/reconciliations/manual` |
| `ops.payment.reconcile.bulk` | `POST /api/internal/payments/reconciliations/bulk` |

## Backend Regression Test Runbook

Use the repo-local verification script after changing compensation ops audit logging, controller authorization, or audit field assertions for order and payment replay/reconcile surfaces:

```powershell
.\scripts\verify-compensation-ops-audit.ps1
```

The default script run executes both controller audit test classes and prints the expected class names before Maven starts:

- `InternalOrderCompensationControllerTest`
- `InternalPaymentCompensationControllerTest`

Single-service investigation is supported when only one module changed:

```powershell
.\scripts\verify-compensation-ops-audit.ps1 -Service order
.\scripts\verify-compensation-ops-audit.ps1 -Service payment
```

To keep verification independent from the user-home Maven cache, pass a repo-local cache path:

```powershell
.\scripts\verify-compensation-ops-audit.ps1 -MavenRepoLocal .\.m2\repository
```

If local Windows PowerShell policy blocks direct `.ps1` execution, invoke the same script with process-scoped bypass:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-compensation-ops-audit.ps1
```

The script intentionally uses the same targeted Maven reactor shape as the command below. Keep the raw command as a troubleshooting fallback when diagnosing script or shell issues:

```powershell
.\mvnw.cmd -q -pl services/sangui-order-service,services/sangui-payment-service -am "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The command is intentionally scoped to the order and payment service modules while still building required upstream reactor dependencies:

- `-pl services/sangui-order-service,services/sangui-payment-service` keeps the test selector on the service modules that own the controller tests.
- `-am` builds required local SNAPSHOT dependencies from the same checkout, so a clean workstation does not need those artifacts pre-installed.
- `-Dsurefire.failIfNoSpecifiedTests=false` prevents upstream dependency modules that are included by `-am`, such as common modules, from failing with `No tests matching pattern` when the service-only `-Dtest` selector does not apply to them.

Expected service-level coverage:

- `InternalOrderCompensationControllerTest` asserts order compensation query denial audit events, manual timeout replay audit fields, bulk timeout replay audit fields, and failed bulk replay fields including `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.
- `InternalPaymentCompensationControllerTest` asserts payment compensation query denial audit events, manual reconcile audit fields, bulk reconcile audit fields, and failed bulk reconcile fields including `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.

After the script or fallback command finishes, confirm the Maven output shows the selected controller test class or classes ran in their service modules. The `failIfNoSpecifiedTests=false` flag is only for upstream dependency modules pulled in by `-am`; it must not be used as a substitute for checking that the intended service tests executed.

Good / Base / Bad command cases:

- Good: run `.\scripts\verify-compensation-ops-audit.ps1` from the repository root after changing compensation ops audit controller tests or logging fields.
- Good: run `.\scripts\verify-compensation-ops-audit.ps1 -Service order` or `.\scripts\verify-compensation-ops-audit.ps1 -Service payment` for single-service investigation, then confirm the matching test class executed.
- Base: run the raw Maven fallback command above when troubleshooting the script itself.
- Bad: run root `.\mvnw.cmd -q "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" test`; common modules can fail before the target service tests with `No tests matching pattern`.
- Bad: run service-only `.\mvnw.cmd -q -pl services/sangui-order-service "-Dtest=InternalOrderCompensationControllerTest" test` on a clean checkout; local SNAPSHOT dependencies may be missing because upstream modules were not built.
- Bad: use global `mvn` instead of the project Maven wrapper in docs, CI, or reproducible acceptance notes.

## Query Templates

Use these templates as baselines. Adjust index names, labels, or service filters to match the deployed log pipeline.

## Dashboard Observability Links

The frontend reads these optional Vite environment variables:

| Variable | Meaning | Example |
| --- | --- | --- |
| `VITE_KIBANA_DISCOVER_URL` | Kibana Discover base URL. The dashboard appends `_a=(query:(language:...,query:'...'))` for KQL or Lucene. | `https://kibana.example/app/discover#/` |
| `VITE_LOKI_EXPLORE_URL` | Grafana/Loki Explore base URL. The dashboard appends a `left` Explore state with the generated LogQL expression. | `https://grafana.example/explore` |

If a URL is absent or not an absolute `http`/`https` URL, the dashboard keeps `Copy query` enabled and leaves the matching open action disabled. These values are public client configuration, not secrets.

Credential-bearing URLs, such as `https://user:token@kibana.example/app/discover#/`, are invalid and must degrade to the same copy-only behavior.

### Kibana KQL

```text
message : "Ops audit event." and traceId : "trace-payment-manual" and operator : "ops-user" and action : "ops.payment.reconcile.manual" and outcome : "success" and shopId : "1"
```

### Kibana Lucene

```text
message:"Ops audit event." AND traceId:"trace-payment-manual" AND operator:"ops-user" AND action:"ops.payment.reconcile.manual" AND outcome:"success" AND shopId:"1"
```

### Loki LogQL

```text
{app=~"sangui-.*"} |= "Ops audit event." |= "traceId=trace-payment-manual" |= "operator=ops-user" |= "action=ops.payment.reconcile.manual" |= "outcome=success" |= "shopId=1"
```

## Common Searches

Manual payment reconcile by trace:

```text
message : "Ops audit event." and traceId : "trace-payment-manual" and action : "ops.payment.reconcile.manual"
```

Denied compensation ops requests:

```text
message : "Ops audit event." and permission : "OPS_COMPENSATION_ADMIN" and outcome : "denied"
```

Bulk dry-runs by operator:

```text
message : "Ops audit event." and operator : "ops-user" and dryRun : "true" and action : "ops.order.timeout-replay.bulk"
```

## Good / Base / Bad Cases

- Good: after a dashboard manual reconcile succeeds, use the response `traceId` with `action=ops.payment.reconcile.manual` and `outcome=success`.
- Good: after a forbidden compensation query, use `outcome=denied`, `errorCode=AUTH_FORBIDDEN`, and `permission=OPS_COMPENSATION_ADMIN`.
- Good: for bulk dry-runs, include `dryRun=true` and `targetCount` to confirm the bounded scope.
- Base: when the log pipeline does not parse key-value fields, search the raw message with `|= "field=value"` fragments in Loki or `message:"field=value"` in Kibana.
- Bad: do not search for raw JWT strings, passwords, payment secrets, or multiline exception text; these values must not be emitted.
- Bad: do not treat compensation history tables as the ops audit trail. History tables answer domain state; `Ops audit event.` answers who operated, when, and through which protected surface.
