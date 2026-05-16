# 管理端履约状态与订单主状态一致性回归覆盖

## Goal

为管理端履约链路补齐回归覆盖，确保发货动作、订单详情展示、履约状态展示之间的状态契约稳定：订单主状态 `status` 只能表达订单主流程状态，不得被 `paymentStatus`、`fulfillmentStatus`、`reviewStatus` 等派生状态反向覆盖；履约页面和订单管理页面对同一订单的状态展示必须一致且可解释。

## Task Scope

- Backend: 检查并增强 `sangui-order-service` 中 admin order / fulfillment 相关 service、controller 测试。
- Frontend: 检查并增强 `FulfillmentManagementView`、fulfillment model、订单管理相关状态展示测试。
- Spec: 若当前订单/前端 API contract 对履约联动断言仍偏抽象，补充可执行断言点。
- Verification: 运行 targeted backend fulfillment/order tests、targeted frontend fulfillment tests、frontend full npm test / typecheck / build。

## Out Of Scope

- 不改数据库 schema、Flyway migration、Redis/MQ、Nacos、Gateway、auth、Docker、CI。
- 不新增业务 API，不改变已发布 API URL、HTTP method、鉴权模型。
- 不重构订单/履约领域模型，不引入新的状态枚举。
- 不改支付、评价、秒杀库存链路的生产逻辑，除非测试暴露必须修正的同类状态漂移缺陷，且需先回到 Codex/用户确认。
- 本轮 Codex 只负责规划、研究、Trellis context，不写业务实现文件。

## Contract Fields

### Backend Response Fields

Existing admin order / fulfillment responses must preserve these field meanings:

- `status`: order main status, e.g. `pending_payment`, `paid`, `shipped`, `completed`, `cancelled`, `closed`; it must come from the order aggregate/main order field.
- `paymentStatus`: payment-derived status; it may influence display badges but must not overwrite `status`.
- `fulfillmentStatus`: fulfillment-derived status, e.g. `unshipped`, `shipped`, unknown/fallback values where compatible; it must not overwrite `status`.
- `reviewStatus`: review-derived status; it must not overwrite `status`.
- `orderNo`, `shopId`, `userId`, `traceId` or request tracing context where already exposed by errors/logs must stay intact.

### Frontend Model Fields

- Fulfillment UI display must map `fulfillmentStatus` to fulfillment-specific labels for `unshipped`, `shipped`, and unknown fallback.
- Order management UI display must map `status` to order main-status labels and must not derive the main status label from `fulfillmentStatus`.
- Error state for ship action failures must preserve the backend/request `traceId` when the existing error model carries one.
- Ship action submit guard must prevent duplicate in-flight submissions for the same action path.

## Validation / Error Matrix

| Case | Trigger | Expected Behavior | Assertion Point |
| --- | --- | --- | --- |
| Valid ship request | Admin ships an eligible paid/unshipped order | Backend marks fulfillment as shipped and main order status as `shipped` if that is the existing contract | Service/controller test checks `status=shipped`, `fulfillmentStatus=shipped` |
| Completed order detail | Order main status is `completed` while fulfillment is `shipped` | Detail/list response preserves `status=completed`; fulfillment remains `shipped` | Backend projection/controller JSON tests |
| Derived status collision | Payment/review/fulfillment derived status differs from main status | Admin response `status` remains main status | Backend projection matrix test |
| Unknown fulfillment status | Frontend receives unknown `fulfillmentStatus` | Fulfillment display uses raw/fallback label without corrupting order main status | Fulfillment model/component test |
| Ship request failure | Backend rejects/throws and returns trace id | UI shows failure state and preserves trace id | Fulfillment view/model test |
| Duplicate submit | User clicks ship while request is in-flight | Only one request is sent; button/guard prevents duplicate action | Fulfillment view/component test |

## Good / Base / Bad Cases

- Good: `status=completed`, `fulfillmentStatus=shipped` appears as completed in order management and shipped in fulfillment management.
- Good: `status=shipped`, `fulfillmentStatus=shipped` appears consistently after successful shipping.
- Base: `status=paid`, `fulfillmentStatus=unshipped` remains paid in order management and unshipped in fulfillment management.
- Base: unknown `fulfillmentStatus` uses fallback display and does not change `status`.
- Bad: backend serializes `status=shipped` only because `fulfillmentStatus=shipped` while main status is still `completed`.
- Bad: frontend computes order main status from `fulfillmentStatus` or hides a completed main status behind a fulfillment badge.
- Bad: repeated ship clicks create multiple requests or clear useful trace id from an error state.

## Acceptance Criteria

- [ ] Backend tests prove admin order list/detail and fulfillment responses preserve main `status` independently from `fulfillmentStatus`.
- [ ] Backend tests cover shipping transition assertions for `status`, `fulfillmentStatus`, and detail response fields.
- [ ] Frontend model/component tests cover `unshipped`, `shipped`, and unknown `fulfillmentStatus` displays.
- [ ] Frontend tests cover successful ship, failed ship with trace id preservation, and duplicate submit guard.
- [ ] Order management and fulfillment management tests document consistency for the same order fixture.
- [ ] Spec docs include executable assertion points for the admin order main-status / fulfillment-status non-overwrite contract if missing.
- [ ] Required targeted backend and frontend tests pass.
- [ ] Frontend `npm test`, `npm run typecheck`, and `npm run build` pass.

## Expected Files To Inspect / Possibly Modify

- `services/sangui-order-service/src/main/java/**/admin/**`
- `services/sangui-order-service/src/main/java/**/fulfillment/**`
- `services/sangui-order-service/src/test/java/**/AdminOrderManagementServiceTest.java`
- `services/sangui-order-service/src/test/java/**/AdminOrderControllerTest.java`
- `services/sangui-order-service/src/test/java/**/Fulfillment*Test.java`
- `frontend/src/views/admin/FulfillmentManagementView.vue`
- `frontend/src/views/admin/fulfillmentManagementModel.ts`
- `frontend/src/views/admin/FulfillmentManagementView.spec.ts`
- `frontend/src/views/admin/fulfillmentManagementModel.test.ts`
- `frontend/src/views/admin/orderManagementModel.ts`
- `frontend/src/views/admin/OrderManagementView.spec.ts`
- `frontend/src/types/api/order.ts`
- `.trellis/spec/backend/order-create-contracts.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/frontend/type-safety.md`

## Required Tests

- Backend targeted:
  - `.\mvnw.cmd -q -pl services/sangui-order-service -am "-Dtest=AdminOrderManagementServiceTest,AdminOrderControllerTest,*Fulfillment*Test" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Frontend targeted:
  - `cmd /c npx vitest run --reporter=verbose src/views/admin/fulfillmentManagementModel.test.ts src/views/admin/FulfillmentManagementView.spec.ts src/views/admin/orderManagementModel.test.ts src/views/admin/OrderManagementView.spec.ts`
- Frontend full:
  - `cmd /c npm run typecheck`
  - `cmd /c npm test`
  - `cmd /c npm run build`
- Hygiene:
  - `git diff --check`

## Planning Notes For Implementer

- Prefer adding regression tests before any production changes.
- If tests pass without production changes, do not refactor production code.
- If a production fix is required, keep it scoped to status projection/mapping and document the exact invariant in spec.
- Preserve current API response shape and frontend unknown-value fallback compatibility.
