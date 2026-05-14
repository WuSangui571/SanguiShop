# Admin Seckill Workspace 404 and Create Failure

## Goal

Prepare a precise implementation package for fixing the admin seckill workspace failures seen at `/admin?workspace=seckill`, with focus on:

- admin-side `404` failures
- admin activity create failures
- frontend API path alignment
- gateway admin route mapping
- seckill-service admin controller exposure
- auth and permission chain consistency

This Codex round is planning-only. No business implementation changes are allowed in this task. Only Trellis task, PRD, and context files may be changed.

## Scope Classification

Complex Task.

Reasons:

- Cross-layer impact: admin frontend, gateway routing, seckill-service controller path, and auth chain.
- Contract-sensitive: admin API path, request payload, result envelope, and error behavior must stay aligned.
- Permission-sensitive: token, gateway auth, controller permission constants, and role/permission matching all need verification.
- Test-sensitive: fix should be backed by route/controller/auth regression coverage and manual smoke validation.

## Current Project State

Based on `.trellis/workspace/codex-agent/journal-1.md`, the most recent recorded task was `05-13-backend-test-nacos-isolation`, which is already archived and complete.

Relevant baseline from recent archived work:

- backend smoke and test isolation were stabilized
- admin seckill contract-oriented work already exists in archived tasks from `2026-05-10` and `2026-05-11`
- there is no currently active Trellis task

This means the current task starts from a clean Trellis task state, but must account for existing seckill admin contract work already present in the repository.

## Problem Statement

The admin seckill workspace at `/admin?workspace=seckill` currently has at least two runtime failures:

1. one or more admin seckill requests return `404`
2. creating a seckill activity fails

The first investigation point is the likely absence or mismatch of gateway mapping for `/api/admin/seckill/**`, compared with the existing public `/api/seckill/**` mapping.

## Required Investigation Outputs

Before implementation, the assignee must produce and confirm:

1. Actual failed request capture:
   - request URL
   - HTTP method
   - HTTP status
   - response body
   - `traceId` if present
2. Contract alignment matrix:
   - admin frontend path actually requested
   - gateway route actually configured
   - seckill-service controller path actually exposed
3. Auth chain verification:
   - admin token source and shape
   - permission required by frontend route or action guard
   - gateway authentication behavior
   - controller/service permission constant behavior
4. Fix validation set:
   - admin list works
   - admin detail works
   - admin create works
   - regression coverage exists for the repaired route/auth path

## In Scope

- Research and define the real admin seckill API path contract used by the admin workspace
- Verify whether `sangui-gateway` exposes `/api/admin/seckill/**`
- Verify whether `sangui-seckill-service` exposes the expected admin controller path
- Verify whether the admin workspace request payload for activity creation matches backend DTO/controller expectations
- Verify the auth and permission chain from admin login through gateway and controller/service
- Define exact implementation plan and test plan for DeepSeek
- Initialize Trellis task context for implementation and check phases

## Out of Scope

- No business implementation changes in frontend, gateway, or seckill-service during this Codex round
- No schema migration design beyond what is strictly needed to describe the contract/risk boundary
- No unrelated seckill purchase flow, Redis pre-deduct, MQ, or public user-side route changes
- No broad auth refactor outside the admin seckill failure path

## Route and Contract Focus

### Frontend Workspace Surface

Target entry:

```text
/admin?workspace=seckill
```

Required admin operations to validate after the eventual fix:

- list seckill activities
- fetch seckill activity detail
- create seckill activity

### Admin API Path Under Investigation

Expected admin base path candidate:

```text
/api/admin/seckill/**
```

Known comparison path:

```text
/api/seckill/**
```

### Activity Create Payload to Verify

The assignee must capture the actual frontend payload and compare it against the backend DTO/controller contract. At minimum verify these fields:

```json
{
  "activityName": "string",
  "description": "string|null",
  "startsAt": "ISO-8601 datetime",
  "endsAt": "ISO-8601 datetime",
  "requestId": "string",
  "skus": [
    {
      "productId": 1,
      "skuId": 1,
      "activityStock": 1,
      "seckillPriceCent": 100
    }
  ]
}
```

If the current frontend payload differs, the PRD/spec and implementation plan must note the exact delta by field name.

## Validation and Error Matrix

The eventual repair must keep error handling explicit and testable.

| Condition | Expected HTTP | Expected code / behavior | Notes |
| --- | --- | --- | --- |
| Gateway has no `/api/admin/seckill/**` route | `404` | gateway-level not found | likely first diagnosis point |
| Gateway route exists but backend controller path mismatches | `404` | downstream route miss or gateway forward miss | capture actual response body |
| Missing or invalid admin token | `401` | auth failure envelope | capture exact existing code |
| Token valid but missing required permission | `403` | forbidden envelope | verify permission constant alignment |
| Create payload field mismatch or validation failure | `400` | validation error envelope | verify exact field-level mismatch |
| Backend throws business not found / invalid state | `404` or `409` | domain error envelope | capture exact code mapping |
| Success path | `200` | `ApiResult<T>` with `traceId` and timestamp | list/detail/create all need coverage |

## Good / Base / Bad Cases

- Good: admin user with the correct token and permission opens `/admin?workspace=seckill`, list request hits the expected `/api/admin/seckill/...` path, gateway forwards correctly, and the backend returns a success envelope with `traceId`.
- Good: admin create request payload matches backend DTO contract and activity creation succeeds without path or validation mismatch.
- Good: admin detail request resolves the same route family as list and create; no mixed public/admin base paths.
- Base: if frontend currently calls `/api/seckill/...` while backend admin controller expects `/api/admin/seckill/...`, the fix must align one contract and update the PRD/spec accordingly instead of adding ambiguous duplicate paths without documentation.
- Base: if gateway route exists but permission constant is missing or inconsistent, the fix may be limited to auth alignment and tests; document the boundary clearly.
- Bad: frontend, gateway, and backend each use different admin seckill base paths.
- Bad: gateway exposes the route but controller permission blocks a valid seckill admin due to wrong constant or role mapping.
- Bad: create succeeds only when bypassing gateway or by using a public route.
- Bad: route mismatch is patched in code but not documented in PRD/spec/context.

## Required Tests and Assertion Points

### Manual / Smoke Verification

- open `/admin?workspace=seckill`
- capture actual list request failure before fix
- capture actual create request failure before fix
- after fix verify:
  - activity list loads
  - activity detail loads
  - activity create succeeds

### Backend Automated Coverage

- gateway route coverage for `/api/admin/seckill/**`
- controller or WebMvc coverage for admin list/detail/create route mapping
- permission denial coverage for missing or wrong seckill admin permission
- create request validation coverage for payload contract alignment

### Assertion Points

- request path is exactly the documented admin path
- request method matches frontend command usage
- gateway forwards to the correct service id / URI predicate
- controller request mapping matches gateway path prefix expectations
- controller returns standard `ApiResult` envelope
- error response preserves diagnosable code/message and `traceId` when applicable

## Relevant Spec Files To Keep In Sync If Contract Changes

- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/seckill-contracts.md`
- `.trellis/spec/frontend/api-contracts.md`

If the final fix changes the admin seckill route contract or create payload shape, the PRD and the corresponding backend/frontend spec files must explicitly record:

- path
- method
- payload fields
- validation rules
- error matrix
- Good / Base / Bad cases

## Focused Research Deliverables

The research summary delivered to DeepSeek must include:

- Relevant Specs
- Code Patterns Found
- Files Likely To Modify
- Risk / Boundary Notes
- Required Tests

## DeepSeek Execution Constraints

- Modify only the files needed for admin seckill route/auth/create failure repair
- Do not expand into unrelated public seckill flow or persistence redesign unless the existing bug cannot be fixed without it
- Preserve existing result envelope and project auth style
- Run the documented backend tests plus any relevant frontend/admin verification needed for the touched path
