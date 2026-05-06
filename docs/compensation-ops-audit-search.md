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

This regression entry is for manual acceptance and on-demand backend audit regression. It is intentionally not part of the default `pull_request` CI gate.

Use the repo-local verification script after changing compensation ops audit logging, controller authorization, or audit field assertions for order and payment replay/reconcile surfaces:

```powershell
.\scripts\verify-compensation-ops-audit.ps1
```

The script is `pwsh` compatible. On Windows it invokes `.\mvnw.cmd`; on non-Windows hosts, including GitHub Actions `ubuntu-latest`, it invokes `./mvnw`. The default script run executes both controller audit test classes and prints the Maven executable, module selector, test selector, expanded Maven command, and expected class names before Maven starts:

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

To inspect the resolved reactor command without running Maven, use:

```powershell
.\scripts\verify-compensation-ops-audit.ps1 -Service all -PrintCommandOnly
```

If local Windows PowerShell policy blocks direct `.ps1` execution, invoke the same script with process-scoped bypass:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-compensation-ops-audit.ps1
```

### Manual CI Workflow

Use the GitHub Actions manual workflow when the same targeted regression needs to run on Linux CI without making every PR wait for these controller tests:

- Workflow file: `.github/workflows/compensation-ops-audit.yml`
- Trigger: `workflow_dispatch`
- Runner: `ubuntu-latest`
- Permissions: `contents: read`
- Actions: `actions/checkout@v6`, `actions/setup-java@v5`
- Checkout: `persist-credentials: false`, `submodules: false`
- Shell: `pwsh`
- Input: `service` = `all`, `order`, or `payment`
- Command: `./scripts/verify-compensation-ops-audit.ps1 -Service <service>`

The workflow has no `pull_request` or `push` trigger. Treat it as an operator/developer controlled acceptance entry for compensation ops audit work, not as a required PR check.

If GitHub annotates the run with Node.js action runtime deprecation warnings, keep the workflow on Node 24-compatible action versions before investigating Maven behavior. If checkout fails with `/usr/bin/git` exit code `128`, inspect the `Checkout` step log for the exact `fatal:` line first; this is a repository checkout/token/ref/submodule problem, not proof that the compensation Maven command ran. A gitlink such as `Trellis` must have a matching `.gitmodules` entry even when this workflow sets `submodules: false`.

The script intentionally uses the same targeted Maven reactor shape as the command below. Keep the raw command as a troubleshooting fallback when diagnosing script or shell issues:

```powershell
.\mvnw.cmd -q -pl services/sangui-order-service,services/sangui-payment-service -am "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Non-Windows fallback:

```bash
./mvnw -q -pl services/sangui-order-service,services/sangui-payment-service -am "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The command is intentionally scoped to the order and payment service modules while still building required upstream reactor dependencies:

- `-pl services/sangui-order-service,services/sangui-payment-service` keeps the test selector on the service modules that own the controller tests.
- `-am` builds required local SNAPSHOT dependencies from the same checkout, so a clean workstation does not need those artifacts pre-installed.
- `-Dsurefire.failIfNoSpecifiedTests=false` prevents upstream dependency modules that are included by `-am`, such as common modules, from failing with `No tests matching pattern` when the service-only `-Dtest` selector does not apply to them.

Expected service-level coverage:

- `InternalOrderCompensationControllerTest` asserts order compensation query denial audit events, manual timeout replay audit fields, bulk timeout replay audit fields, and failed bulk replay fields including `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.
- `InternalPaymentCompensationControllerTest` asserts payment compensation query denial audit events, manual reconcile audit fields, bulk reconcile audit fields, and failed bulk reconcile fields including `outcome=failed`, `targetCount`, `dryRun`, `errorCode`, `path`, and `method`.

After the script or fallback command finishes, confirm the Maven output shows the selected controller test class or classes ran in their service modules. The `failIfNoSpecifiedTests=false` flag is only for upstream dependency modules pulled in by `-am`; it must not be used as a substitute for checking that the intended service tests executed.

### Manual CI Failure Triage Matrix

Use this matrix from the GitHub Actions run page before editing scripts or Maven selectors. First identify the failing step, then classify whether the annotation is blocking.

| Signal | Typical Step | Blocks Workflow? | Category | Diagnosis Point | Corrective Action |
| --- | --- | --- | --- | --- | --- |
| `Node.js 20 actions are deprecated` | `Checkout` or `Setup Java` annotation | Usually no, until GitHub removes the runtime | Runner/platform warning | Confirm the workflow still reaches the next step and the annotation names an official action version. | Upgrade official actions to Node 24-compatible majors, currently `actions/checkout@v6` and `actions/setup-java@v5`; do not change Maven tests for this warning. |
| `/usr/bin/git failed with exit code 128` | `Checkout` | Yes | Checkout/repo/ref/token/submodule | Open the `Checkout` step log and read the exact `fatal:` line. The exit code alone is not enough. | Fix checkout metadata, branch/ref, token permission, or submodule settings first. Keep workflow `permissions: contents: read`; this manual workflow does not need write access. |
| `No url found for submodule path 'Trellis' in .gitmodules` | `Checkout` | Yes | Repo metadata | Confirm `git ls-files --stage Trellis` shows a gitlink entry and root `.gitmodules` has a matching `[submodule "Trellis"]` path/url. | Restore or add the `.gitmodules` entry for the tracked gitlink. Keep `submodules: false` in this workflow unless the acceptance intentionally needs submodule contents. |
| `Permission denied` for `./mvnw` or missing executable bit | `chmod Maven wrapper` or `pwsh script` | Yes | Wrapper/platform setup | Check whether the log includes `chmod +x ./mvnw` before invoking the script on Linux. | Keep the workflow chmod step before `pwsh`; do not replace the wrapper with global `mvn`. If wrapper files changed, verify `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`. |
| `./mvnw.cmd` on Linux or `.\mvnw.cmd` inside GitHub `pwsh` path handling | `pwsh script` | Yes | Shell/path portability | Compare the script log line `Maven executable:` with the runner OS. GitHub `ubuntu-latest` must use `./mvnw`; Windows must use `.\mvnw.cmd`. | Fix the PowerShell OS branch or path separator in `scripts/verify-compensation-ops-audit.ps1`. Do not hardcode Windows-only paths in runbooks or workflow YAML. |
| `No tests matching pattern` before service modules, or build succeeds without target class output | `Maven test` | Yes if target tests did not run | Maven/test selector | Check the actual Maven Surefire output for `InternalOrderCompensationControllerTest` and/or `InternalPaymentCompensationControllerTest`, not only the script preamble. | Use `-pl` for owning service modules, `-am` for local SNAPSHOT dependencies, and `-Dsurefire.failIfNoSpecifiedTests=false` only for upstream modules. Correct misspelled test class names immediately. |

Step ownership quick rules:

- `Checkout`: repository metadata, action version, token permission, ref, or submodule configuration. Do not edit Maven commands until checkout completes.
- `Setup Java`: action version, Java distribution/version, or cache configuration. Expected Java version is Temurin 21.
- `chmod Maven wrapper`: Linux wrapper executable permission. Keep `chmod +x ./mvnw`.
- `Run compensation ops audit regression`: PowerShell portability or Maven selector behavior. Use the printed command as evidence, then verify Surefire class output.

### Repair Strategy Checklist

- [ ] Official actions use Node 24-compatible major versions: `actions/checkout@v6` and `actions/setup-java@v5`.
- [ ] Workflow declares `permissions: contents: read`.
- [ ] Checkout uses `persist-credentials: false` because no post-checkout git push or submodule fetch is required.
- [ ] Checkout uses `submodules: false` for this targeted Maven acceptance workflow.
- [ ] Any tracked gitlink, including `Trellis`, has matching root `.gitmodules` metadata even when this workflow does not fetch submodules.
- [ ] Linux runner logs include `chmod +x ./mvnw` before the `pwsh` script.
- [ ] `Setup Java` resolves Java 21 and the workflow does not rely on a globally installed Maven.
- [ ] The Maven log shows the target Surefire classes executed, not just the script line `Expected Maven output should show`.

Good / Base / Bad command cases:

- Good: run `.\scripts\verify-compensation-ops-audit.ps1` from the repository root after changing compensation ops audit controller tests or logging fields.
- Good: run `.\scripts\verify-compensation-ops-audit.ps1 -Service order` or `.\scripts\verify-compensation-ops-audit.ps1 -Service payment` for single-service investigation, then confirm the matching test class executed.
- Good: use the `Compensation Ops Audit` GitHub Actions `workflow_dispatch` workflow with `service=all`, `service=order`, or `service=payment` for on-demand Linux CI verification without adding a PR-required check.
- Good: the manual workflow uses Node 24-compatible official actions, `permissions: contents: read`, `persist-credentials: false`, and `submodules: false` so checkout succeeds before the `pwsh` Maven script starts without keeping unused git credentials or fetching unrelated submodules.
- Good: a `service=all` GitHub run completes successfully with no warning/error annotations and the Maven output lists both controller test classes.
- Base: a GitHub run succeeds but has an annotation warning; classify the annotation with the failure matrix and record whether it is blocking before making changes.
- Base: run the raw Maven fallback command above when troubleshooting the script itself.
- Base: if checkout reports `/usr/bin/git` exit code `128`, capture the exact `fatal:` line from the `Checkout` step before changing the Maven script; `No url found for submodule path '<path>' in .gitmodules` means the repository has a tracked gitlink without matching `.gitmodules` metadata.
- Bad: edit `scripts/verify-compensation-ops-audit.ps1` or Maven selectors because checkout failed before Maven started.
- Bad: accept a run by reading only the script line `Expected Maven output should show` without confirming the actual Surefire output names the target test class or classes.
- Bad: run root `.\mvnw.cmd -q "-Dtest=InternalOrderCompensationControllerTest,InternalPaymentCompensationControllerTest" test`; common modules can fail before the target service tests with `No tests matching pattern`.
- Bad: run service-only `.\mvnw.cmd -q -pl services/sangui-order-service "-Dtest=InternalOrderCompensationControllerTest" test` on a clean checkout; local SNAPSHOT dependencies may be missing because upstream modules were not built.
- Bad: use global `mvn` instead of the project Maven wrapper in docs, CI, or reproducible acceptance notes.
- Bad: attach the compensation ops audit workflow to `pull_request` as a default gate without an explicit decision to absorb the extra runtime.

### GitHub Manual Acceptance Evidence Template

Use this template for every real `workflow_dispatch` acceptance note:

```markdown
## Compensation Ops Audit Manual GitHub Acceptance

- GitHub run URL:
- Workflow file: `.github/workflows/compensation-ops-audit.yml`
- Branch:
- Commit:
- Service input: `all` / `order` / `payment`
- Runner: `ubuntu-latest`
- Actions: `actions/checkout@v6`, `actions/setup-java@v5`
- Checkout settings: `permissions: contents: read`, `persist-credentials: false`, `submodules: false`

### Step Assertions

- [ ] `Checkout` completed; if it failed, the exact `fatal:` line was captured.
- [ ] No blocking Node runtime deprecation annotation remains.
- [ ] `Setup Java` resolved Temurin 21.
- [ ] Linux wrapper step ran `chmod +x ./mvnw`.
- [ ] `Run compensation ops audit regression` used `pwsh`.
- [ ] Script printed `Maven executable: ./mvnw` on the Linux runner.
- [ ] Module selector matched the service input.
- [ ] Test selector matched the service input.
- [ ] Maven Surefire output shows the expected controller test class or classes actually executed.
- [ ] Final conclusion states one of: accepted with no warnings/errors, accepted with non-blocking warning, rejected with blocking failure category.
```

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
