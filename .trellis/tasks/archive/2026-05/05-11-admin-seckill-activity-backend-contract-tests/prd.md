# 后端管理端秒杀活动 API 契约与基础测试补齐

## Goal

在 `services/sangui-seckill-service` 落地管理端秒杀活动 API 的后端可执行契约和基础测试，使上一轮前端 `/api/admin/seckill/activities` contract 能对齐真实 Gateway/API 形状。Codex 本轮只准备 PRD、Trellis context、spec/code research 和测试计划，不写业务实现代码；实现交给 DeepSeek 端。

## Scope Classification

Complex Task.

理由：本任务跨 API 契约、权限边界、DTO validation、幂等写入、活动状态机、商品/SKU 库存权威校验、错误码、测试和 spec 同步。它不是单文件修改，也不是纯测试补丁。

## In Scope

- 后端服务所有权优先定位为 `services/sangui-seckill-service`。
- 如需 SKU 权威库存读取，可通过 product-service 现有契约/客户端模式调用或抽象 `ProductSkuSnapshotClient`，但不得跨服务直读 product DB。
- 如需最小持久化/幂等记录，可在 seckill-service 内定义 `sk_activity` / `sk_activity_sku` / requestId 幂等策略，并同步 backend spec。
- 更新 `.trellis/spec/backend/seckill-contracts.md`，补齐管理端活动 API 合同、状态机、幂等、错误矩阵、测试要求。
- 如后端最终字段名和前端 contract 需要微调，更新 `.trellis/spec/frontend/api-contracts.md` 的 Admin Seckill Activity Management API 段落。

## Out of Scope

- 不实现前台秒杀下单链路、Redis Lua 预扣减、MQ `SECKILL_ORDER_REQUESTED`、订单创建、支付联动。
- 不实现完整运营补偿 dashboard 或 OPS compensation APIs。
- 不新增真实第三方服务依赖、secret、Nacos 配置或生产部署改动。
- 不绕过 Gateway/服务内 RBAC，不允许仅靠前端权限隐藏。

## API Contract

所有接口经 Gateway 暴露，路径保持前端已定义合同：

| Method | Path | Purpose | Success code |
| --- | --- | --- | --- |
| `GET` | `/api/admin/seckill/activities` | 分页查询管理端秒杀活动 | `ADMIN_SECKILL_ACTIVITY_LIST` |
| `GET` | `/api/admin/seckill/activities/{activityId}` | 查询活动详情与 SKU 绑定快照 | `ADMIN_SECKILL_ACTIVITY_DETAIL` |
| `POST` | `/api/admin/seckill/activities` | 创建活动及初始 SKU 绑定 | `ADMIN_SECKILL_ACTIVITY_CREATED` |
| `PUT` | `/api/admin/seckill/activities/{activityId}` | 更新活动基础信息和可编辑 SKU 配置 | `ADMIN_SECKILL_ACTIVITY_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/status` | 管理端状态流转 | `ADMIN_SECKILL_ACTIVITY_STATUS_UPDATED` |
| `POST` | `/api/admin/seckill/activities/{activityId}/skus` | 绑定或更新单个 SKU 活动库存/秒杀价 | `ADMIN_SECKILL_ACTIVITY_SKU_BOUND` |

### Principal and Scope

- Controller 使用 `SanguiPrincipal`，`shopId` 以 principal 为权威。
- 请求体中的 `shopId` / `userId` 仅兼容前端 draft，不得覆盖 principal scope。
- `ADMIN` role 或 `SECKILL_ACTIVITY_ADMIN` permission 允许访问。
- `OPS_COMPENSATION_ADMIN` alone 必须被服务端拒绝，返回 403 / `AUTH_FORBIDDEN`。
- 所有查询和写入必须以 `principal.shopId()` 限定。

### Request Fields

`GET /api/admin/seckill/activities`

| Field | Source | Validation |
| --- | --- | --- |
| `page` | query | default `1`, min `1` |
| `size` | query | default `20`, min `1`, max `100` |
| `status` | query | optional; blank or `all` means no status filter |

`POST /api/admin/seckill/activities` and `PUT /api/admin/seckill/activities/{activityId}`

```json
{
  "shopId": 1,
  "userId": "90001",
  "activityName": "Spring flash sale",
  "description": "optional",
  "startsAt": "2026-05-12T10:00:00+08:00",
  "endsAt": "2026-05-12T12:00:00+08:00",
  "requestId": "admin-generated-id",
  "skus": [
    {
      "productId": 301,
      "skuId": 401,
      "activityStock": 10,
      "seckillPriceCent": 49900
    }
  ]
}
```

Required behavior:

- `activityName` is required after trim.
- `startsAt` / `endsAt` are ISO-8601 strings accepted as `OffsetDateTime`.
- `startsAt < endsAt`; equal or reversed range returns `VALIDATION_FAILED`.
- `requestId` is required for all writes.
- `skus` may be empty only if implementation deliberately supports draft-without-SKU; if not, reject empty `skus` with `VALIDATION_FAILED` and document the decision in spec.
- `activityStock >= 0`.
- `seckillPriceCent > 0`.

`POST /api/admin/seckill/activities/{activityId}/status`

```json
{
  "status": "scheduled",
  "requestId": "admin-generated-id"
}
```

Required behavior:

- `status` required after trim.
- Supported status values: `draft`, `scheduled`, `active`, `ended`.
- Status transitions must be explicit. Minimum expected transitions:
  - `draft -> scheduled`
  - `scheduled -> active`
  - `active -> ended`
  - replaying same `requestId` with same target status returns the existing result
  - invalid transition returns `SECKILL_ACTIVITY_STATUS_INVALID`

`POST /api/admin/seckill/activities/{activityId}/skus`

```json
{
  "productId": 301,
  "skuId": 401,
  "activityStock": 10,
  "seckillPriceCent": 49900,
  "requestId": "admin-generated-id"
}
```

Required behavior:

- `productId`, `skuId`, `requestId`, `activityStock` required.
- `activityStock >= 0`.
- `seckillPriceCent` optional only if backend can preserve an existing price; otherwise require `> 0` and align frontend spec.
- Backend must validate SKU existence and stock using product-service authority or a clearly documented product snapshot adapter.
- `activityStock <= availableStock` is authoritative on backend. UI validation is only a pre-check.

### Response Fields

All responses use `ApiResult<T>`:

```json
{
  "code": "ADMIN_SECKILL_ACTIVITY_DETAIL",
  "message": "ok",
  "data": {},
  "traceId": "trace-admin-seckill",
  "timestamp": "2026-05-11T18:30:00+08:00"
}
```

List item:

```json
{
  "activityId": 9001,
  "activityName": "Spring flash sale",
  "status": "scheduled",
  "startsAt": "2026-05-12T10:00:00+08:00",
  "endsAt": "2026-05-12T12:00:00+08:00",
  "serverTime": "2026-05-11T18:30:00+08:00",
  "skuCount": 1,
  "totalActivityStock": 10,
  "soldCount": 0
}
```

Detail extends summary:

```json
{
  "activityId": 9001,
  "activityName": "Spring flash sale",
  "description": "optional",
  "status": "scheduled",
  "startsAt": "2026-05-12T10:00:00+08:00",
  "endsAt": "2026-05-12T12:00:00+08:00",
  "serverTime": "2026-05-11T18:30:00+08:00",
  "skuCount": 1,
  "totalActivityStock": 10,
  "soldCount": 0,
  "skus": [
    {
      "productId": 301,
      "productName": "Daily trainer",
      "skuId": 401,
      "skuCode": "shoe-42",
      "skuName": "42",
      "priceCent": 59900,
      "seckillPriceCent": 49900,
      "availableStock": 20,
      "activityStock": 10,
      "soldCount": 0
    }
  ]
}
```

## Error Matrix

| Condition | HTTP | code | Assertion |
| --- | --- | --- | --- |
| Missing/invalid auth before service | 401 | `AUTH_TOKEN_MISSING` / auth code | Gateway/common behavior |
| Principal lacks `ADMIN` and `SECKILL_ACTIVITY_ADMIN` | 403 | `AUTH_FORBIDDEN` | `OPS_COMPENSATION_ADMIN` alone rejected |
| Request `shopId` conflicts with principal `shopId` if body scope is checked | 403 | `AUTH_FORBIDDEN` | Principal scope remains authoritative |
| Missing/blank `activityName` | 400 | `VALIDATION_FAILED` | field or envelope failure |
| Invalid ISO time or `startsAt >= endsAt` | 400 | `VALIDATION_FAILED` | no partial write |
| Missing write `requestId` | 400 | `VALIDATION_FAILED` | all writes |
| Activity not found in current shop | 404 | `SECKILL_ACTIVITY_NOT_FOUND` | no cross-shop leak |
| Invalid status value or transition | 409 | `SECKILL_ACTIVITY_STATUS_INVALID` | no mutation |
| SKU not found/unavailable in product-service authority | 404 | `PRODUCT_SKU_NOT_FOUND` | propagated or mapped consistently |
| `activityStock > availableStock` | 409 | `PRODUCT_STOCK_NOT_ENOUGH` or `STOCK_NOT_ENOUGH` | choose one and document |
| Same `requestId` with identical payload | 200 | original success code | returns original/equivalent result |
| Same `requestId` with changed payload | 409 | `IDEMPOTENCY_CONFLICT` | no second mutation |

## Good / Base / Bad Cases

- Good: `SECKILL_ACTIVITY_ADMIN` principal creates an activity with trimmed `activityName`, ISO times, requestId, valid SKU stock, and receives an `ApiResult` with `traceId`, `timestamp`, and detail data.
- Good: `ADMIN` role can list/detail/update/status/bind SKU within `principal.shopId()`.
- Good: repeated write with same `requestId` and same normalized payload returns the same current activity snapshot without duplicating rows.
- Good: `activityStock` above product-service `availableStock` fails with stock error and leaves existing activity/SKU state unchanged.
- Good: invalid status transition fails with `SECKILL_ACTIVITY_STATUS_INVALID`.
- Base: if DB migrations are deferred, service tests may use an in-memory repository, but PRD/spec must still define the eventual unique indexes and idempotency keys.
- Base: if product-service client is not fully wired, define an interface and fake adapter in tests; do not hardcode SKU stock in controller.
- Bad: `OPS_COMPENSATION_ADMIN` alone can use admin seckill routes.
- Bad: request body `shopId` overrides principal scope.
- Bad: controller directly manipulates repository/DB or calls product DB.
- Bad: frontend-local `availableStock` is trusted without backend SKU authority.
- Bad: duplicate write request with same `requestId` creates another activity or changes payload silently.

## Required Tests

Controller/WebMvc contract tests:

- `GET /api/admin/seckill/activities` returns `ApiResult` envelope with `code`, `message`, `data`, `traceId`, `timestamp`.
- Detail/create/update/status/SKU bind routes match the documented paths.
- Request validation failures map to `VALIDATION_FAILED`.
- Service-thrown `SECKILL_ACTIVITY_NOT_FOUND`, `SECKILL_ACTIVITY_STATUS_INVALID`, stock errors, and `IDEMPOTENCY_CONFLICT` map to expected HTTP/envelope.
- Controller uses `SanguiPrincipal` and does not accept `shopId` query/body as authority.

Service validation tests:

- `activityName` trim/required.
- ISO time order validation.
- requestId required for writes.
- page/size clamp or controller validation follows established backend style.

Permission denial tests:

- `ADMIN` allowed.
- `SECKILL_ACTIVITY_ADMIN` allowed.
- `OPS_COMPENSATION_ADMIN` alone denied with `AUTH_FORBIDDEN`.
- Cross-shop principal lookup cannot see/update another shop's activity.

Idempotency/requestId tests:

- Create replay with identical normalized payload is idempotent.
- Create replay with changed payload returns `IDEMPOTENCY_CONFLICT`.
- Status replay same requestId/target is idempotent.
- SKU bind replay same requestId/payload is idempotent; changed stock/price conflicts.

Status transition tests:

- Valid transitions succeed.
- Invalid transition fails and does not mutate state.
- Unknown status value fails.

SKU bind stock boundary tests:

- SKU not found returns `PRODUCT_SKU_NOT_FOUND`.
- `activityStock = availableStock` succeeds.
- `activityStock > availableStock` fails with chosen stock error.
- `activityStock < 0` fails validation.

Spec sync tests/checks:

- `.trellis/spec/backend/seckill-contracts.md` documents admin activity APIs, DTO fields, validation/error matrix, idempotency, and Good/Base/Bad cases.
- `.trellis/spec/frontend/api-contracts.md` stays aligned if backend chooses a different stock error code or write payload shape.

## Implementation Notes for DeepSeek

- Prefer existing backend layering: `api` controller, `api/dto` records, `application` service, `domain` models/repository interfaces, `infrastructure` adapter only if persistence/client is implemented.
- Add `SECKILL_ACTIVITY_ADMIN` to `SanguiPermissionConstants`.
- Add seckill-specific `SeckillErrorCode` enum in seckill domain or equivalent local domain error enum.
- Reuse `ApiResult`, `SanguiPrincipal`, `GlobalApiExceptionHandler`, `TraceConstants`, `SanguiPrincipalArgumentResolver` test config patterns.
- Use product-service authority via interface/adapter for SKU stock; do not read product-service tables directly from seckill-service.
- Keep Maven module test selector explicit: `.\mvnw.cmd -q -pl services/sangui-seckill-service -am "-Dtest=AdminSeckillActivityControllerTest,AdminSeckillActivityServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`.
