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
