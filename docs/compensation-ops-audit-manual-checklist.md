# Compensation Ops Audit Manual Acceptance Checklist

Use this checklist when validating the compensation ops audit observability path in a real Gateway, frontend, Kibana, and Grafana/Loki environment.

This checklist validates the operator workflow only. It must not use order or payment compensation history tables as a substitute for `Ops audit event.` logs.

## Preconditions

- The frontend build or dev server is pointed at the real Gateway with `VITE_API_BASE_URL` or same-origin routing.
- Gateway, `sangui-user`, `sangui-order-service`, and `sangui-payment-service` are deployed from the same release.
- At least one ops identity has `OPS_COMPENSATION_ADMIN` for the target `shopId`.
- At least one authenticated non-ops identity exists for the same `shopId`, or an ops token can be temporarily stripped of `OPS_COMPENSATION_ADMIN` in a controlled test namespace.
- Kibana Discover can search the application log index that receives backend JSON logs.
- Grafana/Loki Explore can search the Loki stream that receives the same backend logs.
- The operator can inspect browser button state and opened URLs without copying real JWTs, passwords, API keys, or payment secrets into evidence.

Recommended evidence for each scenario:

- Deployment or frontend runtime environment name.
- `shopId`, sanitized `operator`, `action`, `outcome`, and `traceId`.
- Screenshot or copied query text from Kibana/Loki.
- API error `code` and `traceId` for denied or failed cases.

## Scope

The checklist covers:

- `VITE_KIBANA_DISCOVER_URL` and `VITE_LOKI_EXPLORE_URL` configured, missing, invalid, and credential-bearing URL behavior.
- Kibana/Loki button enabled and disabled states.
- `View audit trail` population after manual replay/reconcile success and server-side failure.
- Kibana Discover and Loki Explore query content that can hit `Ops audit event.` logs.
- Unauthorized compensation ops query/action rejection plus searchable `denied` audit events.
- Copy-only fallback for environments without an observability platform link.

## 1. Environment Configuration Matrix

Run these checks in a disposable frontend build or deployment slot. Vite env values are compiled into the frontend bundle, so rebuild or redeploy the frontend after each env change.

| Case | `VITE_KIBANA_DISCOVER_URL` | `VITE_LOKI_EXPLORE_URL` | Expected UI | Expected Query Fallback |
| --- | --- | --- | --- | --- |
| Good | `https://kibana.example/app/discover#/` | `https://grafana.example/explore` | `Open in Kibana` and `Open in Loki` are enabled. | `Copy query` remains enabled for every template. |
| Base | unset or empty | unset or empty | Both open buttons are disabled with env-specific titles. | `Copy query` is enough to paste KQL, Lucene, or LogQL manually. |
| Bad | `not-a-url` | `ftp://grafana.example/explore` | Matching open buttons are disabled. | No browser tab opens; only copy works. |
| Bad | `https://user:token@kibana.example/app/discover#/` | `https://token@grafana.example/explore` | Matching open buttons are disabled because credentials are present. | No credential-bearing URL is opened or persisted by the dashboard. |

Checklist:

- [ ] Confirm configured observability URLs are absolute `http` or `https` URLs.
- [ ] Confirm neither URL contains username, password, API key, token, or secret material.
- [ ] Confirm missing, invalid, non-HTTP(S), and credential-bearing URLs disable only the matching open action.
- [ ] Confirm `Copy query` remains available for Kibana KQL, Kibana Lucene, and Loki LogQL in every matrix case.
- [ ] Confirm the dashboard never hardcodes Kibana, Grafana, or Loki hosts when env values are absent.

## 2. Button State Acceptance

Use the compensation dashboard audit panel.

Configured URL case:

- [ ] Set `VITE_KIBANA_DISCOVER_URL` to the deployed Kibana Discover URL and rebuild the frontend.
- [ ] Verify both Kibana cards show enabled `Open in Kibana` buttons.
- [ ] Set `VITE_LOKI_EXPLORE_URL` to the deployed Grafana/Loki Explore URL and rebuild the frontend.
- [ ] Verify the Loki card shows an enabled `Open in Loki` button.
- [ ] Click each open button and confirm a new tab opens with `noopener,noreferrer` behavior from the browser devtools or security policy audit where available.

Fallback case:

- [ ] Remove both observability env values and rebuild the frontend.
- [ ] Verify `Open in Kibana` is disabled on both Kibana cards.
- [ ] Verify `Open in Loki` is disabled on the Loki card.
- [ ] Verify disabled titles refer to `VITE_KIBANA_DISCOVER_URL` or `VITE_LOKI_EXPLORE_URL`.
- [ ] Verify `Copy query` still copies complete query text for all three cards.

## 3. View Audit Trail After Successful Operations

Run this with an ops user that has `OPS_COMPENSATION_ADMIN`.

Manual payment reconcile:

- [ ] Set dashboard `shopId` to the target shop.
- [ ] Set replay operator to a sanitized value such as `ops-oncall`.
- [ ] Find a payment compensation row that is safe to reconcile in the test namespace.
- [ ] Run manual payment reconcile.
- [ ] Verify the action feedback shows a backend response code and `traceId`.
- [ ] Click `View audit trail`.
- [ ] Verify audit filters are populated:
  - `shopId=<current shopId>`
  - `traceId=<response traceId>`
  - `operator=ops-oncall`
  - `action=ops.payment.reconcile.manual`
  - `outcome=success`

Manual order timeout replay:

- [ ] Switch to order compensation.
- [ ] Keep replay operator set to the same sanitized operator.
- [ ] Find an order compensation row that is safe to replay in the test namespace.
- [ ] Run manual order timeout replay.
- [ ] Verify the action feedback shows a backend response code and `traceId`.
- [ ] Click `View audit trail`.
- [ ] Verify audit filters are populated:
  - `shopId=<current shopId>`
  - `traceId=<response traceId>`
  - `operator=ops-oncall`
  - `action=ops.order.timeout-replay.manual`
  - `outcome=success`

Expected log hit:

- [ ] In Kibana or Loki, search the generated query.
- [ ] Confirm at least one log line contains `Ops audit event.`.
- [ ] Confirm the log line includes `traceId`, `operator`, `action`, `outcome=success`, `shopId`, `permission=OPS_COMPENSATION_ADMIN`, `method`, and `path`.
- [ ] Confirm the log line does not include raw JWT strings, passwords, payment secrets, or unsanitized multiline exception text.

## 4. View Audit Trail After Failed Operations

Use a controlled server-side failure. Do not use a client-side validation failure such as an empty operator, because that does not receive a backend `traceId`.

Manual failure examples:

- Use a valid ops token and a target `paymentNo` or `orderId` that the service rejects with a stable business error.
- Temporarily make a downstream dependency unavailable in a test namespace.
- Submit a replay/reconcile request with a valid shape but impossible target state.

Checklist:

- [ ] Trigger a manual payment reconcile or order replay that reaches the backend and fails.
- [ ] Verify the UI shows the API error `code` and `traceId`.
- [ ] Click the failed action `View audit trail` control when present.
- [ ] Verify audit filters are populated:
  - `shopId=<attempted shopId>`
  - `traceId=<error response traceId>`
  - `operator=<attempted replay operator>`
  - `action=ops.payment.reconcile.manual` or `ops.order.timeout-replay.manual`
  - `outcome=failed`
- [ ] Open or copy the generated Kibana/Loki query.
- [ ] Confirm the matching log line contains `Ops audit event.`, `outcome=failed`, the same `traceId`, and a sanitized `errorCode` or `reason`.

Bad case:

- [ ] Confirm a purely client-side validation failure, such as missing replay operator, does not pretend to have a backend audit trail.

## 5. Kibana Discover Query Verification

Use the audit panel fields or `View audit trail` to generate a Kibana KQL query.

KQL acceptance:

- [ ] Confirm the query includes `message : "Ops audit event."`.
- [ ] Confirm populated fields are included as structured clauses, for example `traceId : "<traceId>"`, `operator : "<operator>"`, `action : "<action>"`, `outcome : "<outcome>"`, and `shopId : "<shopId>"`.
- [ ] Click `Open in Kibana` when enabled.
- [ ] Confirm the opened Discover URL contains an `_a` app-state query with `language:kuery`.
- [ ] Run the query in Discover and confirm it returns the matching audit log line.

Lucene acceptance:

- [ ] Confirm the Lucene query includes `message:"Ops audit event."`.
- [ ] Confirm populated fields are included as `field:"value"` clauses.
- [ ] Click the Lucene card `Open in Kibana` when enabled.
- [ ] Confirm the opened Discover URL contains an `_a` app-state query with `language:lucene`.
- [ ] Run the query and confirm it returns the matching audit log line.

Fallback when structured fields are not parsed:

- [ ] Search the raw message body for fragments such as `traceId=<traceId>`, `operator=<operator>`, `action=<action>`, and `outcome=<outcome>`.
- [ ] Confirm the raw fallback still finds `Ops audit event.` without querying business history tables.

## 6. Loki Explore Query Verification

Use the audit panel fields or `View audit trail` to generate the Loki LogQL query.

Checklist:

- [ ] Confirm the LogQL query starts with `{app=~"sangui-.*"} |= "Ops audit event."`.
- [ ] Confirm populated fields are included as raw fragments, for example `|= "traceId=<traceId>"`, `|= "operator=<operator>"`, `|= "action=<action>"`, `|= "outcome=<outcome>"`, and `|= "shopId=<shopId>"`.
- [ ] Click `Open in Loki` when enabled.
- [ ] Confirm the opened Explore URL contains a `left` state with datasource `Loki` and the generated `expr`.
- [ ] Use a range that includes the operation time, usually `now-24h` for the generated link or a narrower manual range.
- [ ] Run the query and confirm it returns the matching audit log line.

## 7. Unauthorized Access And Denied Audit

Run this against a user that is authenticated but lacks `OPS_COMPENSATION_ADMIN`.

Dashboard path:

- [ ] Sign in or force a session for a valid non-ops identity.
- [ ] Attempt to query order compensation records.
- [ ] Verify the business API rejects the request with `AUTH_FORBIDDEN` and an API `traceId`.
- [ ] Attempt to query payment compensation records.
- [ ] Verify the business API rejects the request with `AUTH_FORBIDDEN` and an API `traceId`.
- [ ] Attempt a manual replay or reconcile if the UI allows reaching the action; otherwise use the protected API directly in a controlled test.
- [ ] Verify the protected action rejects with `AUTH_FORBIDDEN`.

Direct protected API check, with placeholder token only:

```bash
curl -i \
  -H "Authorization: Bearer <non-ops-jwt>" \
  -H "Content-Type: application/json" \
  -X POST \
  "<gateway-origin>/api/internal/payments/compensation-records/query" \
  -d '{"shopId":1,"pageNo":1,"pageSize":20}'
```

Denied audit search:

- [ ] Search Kibana or Loki for `message : "Ops audit event."` plus `permission : "OPS_COMPENSATION_ADMIN"` and `outcome : "denied"`.
- [ ] Narrow the query by the denied response `traceId`.
- [ ] Confirm the log line includes `errorCode=AUTH_FORBIDDEN`, `path`, `method`, `shopId`, `userId`, and sanitized `reason`.
- [ ] Confirm successful query/list requests are not required to emit a duplicate business-success audit line; denial audit events are required for protected query endpoints.

## 8. Copy-Only Fallback Runbook

Use this when Kibana/Loki URLs are unavailable, intentionally disabled, or blocked by network policy.

Checklist:

- [ ] Leave `VITE_KIBANA_DISCOVER_URL` and `VITE_LOKI_EXPLORE_URL` empty, or reproduce the missing-url deployment.
- [ ] Use `View audit trail` after a success or backend failure, or manually fill audit filters from an API response.
- [ ] Click `Copy query` on Kibana KQL, Kibana Lucene, and Loki LogQL.
- [ ] Paste the KQL/Lucene query into the deployed Kibana Discover search box.
- [ ] Paste the LogQL query into Grafana/Loki Explore.
- [ ] If structured parsing is unavailable, reduce the query to raw fragments:
  - Kibana: `message:"Ops audit event." AND message:"traceId=<traceId>"`
  - Loki: `{app=~"sangui-.*"} |= "Ops audit event." |= "traceId=<traceId>"`
- [ ] Confirm the copied queries are enough to find the audit event without using a dashboard open link.

## 9. Acceptance Summary

Pass the checklist only when all of the following are true:

- [ ] Good env values enable the matching open buttons and open platform URLs containing the generated query state.
- [ ] Missing, invalid, non-HTTP(S), and credential-bearing env values disable the matching open buttons and preserve copy-only behavior.
- [ ] Replay/reconcile success feedback can populate `traceId`, `operator`, `action`, `outcome=success`, and `shopId` into audit filters.
- [ ] Replay/reconcile backend failure feedback can populate `traceId`, `operator`, `action`, `outcome=failed`, and `shopId` into audit filters.
- [ ] Kibana Discover and Loki Explore can find at least one `Ops audit event.` with the generated query.
- [ ] Unauthorized compensation ops queries/actions are rejected by business APIs and produce searchable `outcome=denied` audit events.
- [ ] The fallback path works with copied queries only.
- [ ] No checklist evidence contains raw JWTs, passwords, API keys, payment secrets, or credential-bearing observability URLs.
