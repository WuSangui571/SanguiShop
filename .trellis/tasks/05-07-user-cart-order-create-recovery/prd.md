# 用户侧购物车与订单创建失败恢复体验补强

## Goal

补强用户从商品详情、购物车草稿、结算、订单创建成功到订单详情的主链路体验，重点覆盖本地购物车恢复、登录用户隔离、订单创建失败保留、库存快照边界、重复提交防护、`requestId` 重试稳定性，以及成功后只清除已提交 SKU 并进入订单详情。

## Scope

- 前端商城侧购物车和订单创建体验。
- 现有 Gateway API 契约不变，不新增后端接口。
- 主要修改 `frontend/src/composables/useMallCart.ts`、`frontend/src/views/mall/mallCartModel.ts`、`frontend/src/views/mall/MallStorefrontView.vue`、`frontend/src/views/mall/ProductCheckoutPanel.vue`、`frontend/src/composables/useAppPreferences.ts` 和相关 Vitest。
- 不进入退款、售后、支付补偿、后端状态机或数据库迁移。

## Requirements

- 购物车草稿恢复与用户隔离：
  - 明确展示当前 `{shopId,userId}` 下的购物车恢复状态。
  - 登录切换后只恢复对应用户草稿；无对应草稿时展示空购物车。
  - 登出或无 session 时内存购物车清空，不串用上一用户数据。
  - `localStorage` 不可用、损坏或反序列化失败时清晰降级，不影响商品浏览。
- 结算失败状态细化：
  - 区分库存不足、SKU 不可用、认证失效、后端校验失败、系统错误和未知错误。
  - 后端错误必须继续保留 `code/message/traceId`。
  - 失败后保留购物车内容和当前选择，不清空已选 SKU。
- 库存快照边界提示：
  - UI 明确说明购物车库存是本地快照，不承诺实时库存。
  - 数量上限继续受本地快照约束，但最终以下单接口为准。
  - 库存不足失败后提示调整数量或刷新商品详情。
- 重复提交与 `requestId` 稳定性：
  - 结算 pending 时禁止重复点击，第二次调用不发送请求。
  - 同一购物车内容失败后重试保留同一个 `requestId`。
  - 购物车内容变化后重新生成 `requestId`。
  - 购物车成功提交后重新生成下一次结算的 `requestId`。
- 订单创建成功后的衔接：
  - 成功创建订单后只移除已提交 SKU。
  - 自动进入订单详情，并保持 URL、订单列表、详情状态一致。
  - 多商品购物车成功后订单条目展示完整。
- 测试补强：
  - 登录切换购物车隔离。
  - 购物车恢复失败降级。
  - 失败保留购物车并展示 `traceId`。
  - 重复提交防护和失败重试 `requestId` 稳定。
  - 内容变化后 `requestId` 重新生成。
  - 成功后只清除已提交商品，并进入订单详情。

## Acceptance Criteria

- [ ] 切换 mall 用户后不会显示上一个用户的购物车草稿。
- [ ] 损坏或不可用的购物车草稿不会阻塞商品浏览，页面能展示恢复失败/已降级提示。
- [ ] 库存不足、SKU 不可用、认证失效、校验失败和系统错误有用户可理解的分类提示，并保留后端 `code/message/traceId`。
- [ ] 订单创建失败后购物车条目、数量和 `requestId` 保持稳定，可直接重试。
- [ ] 修改购物车内容后使用新的 `requestId`。
- [ ] 结算 pending 时不会发出第二个创建订单请求。
- [ ] 创建订单成功后仅移除订单响应中已提交的 SKU；未提交 SKU 留在购物车。
- [ ] 成功后自动打开订单详情，URL 中的 `orderId` 与详情、列表高亮保持一致。
- [ ] 多商品订单详情中展示完整 `items[]`。
- [ ] `npm run typecheck`、`npm run lint`、`npm run build` 和相关 Vitest 通过。

## Relevant Specs

- `.trellis/spec/frontend/api-contracts.md`: `Mall Cart Draft MVP`、`Mall Order Status APIs`、`requestId`、错误保留规则。
- `.trellis/spec/frontend/state-management.md`: 购物车本地草稿是 client state，库存/价格/订单状态是 server facts。
- `.trellis/spec/frontend/component-guidelines.md`: 异步 UI 状态和电商展示规则。
- `.trellis/spec/frontend/hook-guidelines.md`: composable 返回状态、动作和错误，不直接渲染 UI。
- `.trellis/spec/frontend/type-safety.md`: DTO 兼容、金额、unknown fallback。
- `.trellis/spec/frontend/quality-guidelines.md`: typecheck/build/test、重复提交、traceId、移动端布局。
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: 订单、库存、幂等和失败重试边界。
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: 复用现有 model/composable，不引入无必要抽象。

## Existing Patterns To Reuse

- `frontend/src/views/mall/mallCartModel.ts`: 购物车 key、序列化、数量 clamp、提交请求 payload、成功后清除提交 SKU。
- `frontend/src/composables/useMallCart.ts`: 本地草稿加载、持久化、重复提交防护和创建订单调用。
- `frontend/src/views/mall/mallCheckoutModel.ts`: `describeMallApiError`、`createOrderRequestId`、购买单品 pending 防护。
- `frontend/src/composables/useMallOrderStatus.ts`: `acceptCreatedOrder`、订单详情/list merge、付款状态复用。
- `frontend/src/views/mall/MallStorefrontView.vue`: 购物车 UI、订单详情 deep link、创建订单后的页面衔接。
- `frontend/tests/mallCheckoutModel.spec.ts`: 当前购物车和订单创建模型测试集中地。

## Implementation Plan

1. 增强纯模型：
   - 增加购物车恢复状态模型和错误分类模型。
   - 扩展 `describeMallApiError` 或新增购物车错误描述函数，保留 backend details。
   - 明确 `requestId` 只在购物车内容变化和成功提交后重置，失败不重置。
2. 增强 `useMallCart`：
   - 暴露恢复状态、恢复提示、当前 session key 摘要。
   - 处理 storage 不可用/损坏/反序列化失败的降级状态。
   - 在 submit 失败时保留 items 和 requestId。
   - 成功后只清除响应中已提交 SKU，未提交 SKU 保留。
3. 增强商城 UI：
   - 在购物车区域展示恢复状态、库存快照说明和失败分类提示。
   - pending 状态禁用清空/移除/数量调整等会改变提交内容的动作，或明确会重置 requestId。
   - 成功后调用现有订单详情加载逻辑，保持 URL/list/detail 一致。
4. 扩展翻译：
   - 为 zh-Hans、zh-Hant、en 添加购物车恢复、库存快照、失败类型和重试提示文案。
5. 补测试：
   - 扩展 `mallCheckoutModel.spec.ts` 或拆出 `mallCartModel.spec.ts`。
   - 覆盖用户隔离、恢复失败、失败保留和 traceId、requestId 重试稳定、内容变化重置、成功后只清除提交 SKU、订单详情衔接。
6. 质量检查：
   - 运行 `npm run typecheck`、`npm run lint`、`npm run build`、目标 Vitest。
   - 执行 `$check` 对照 spec 修复问题。

## Out Of Scope

- 不新增或修改后端 API。
- 不引入服务端购物车。
- 不实现实时库存刷新或库存轮询。
- 不修改支付、退款、售后或补偿流程。
- 不新增订单创建幂等后端逻辑，仅保持前端 `requestId` 契约稳定。

## Risks And Mitigations

- 风险：错误分类过度依赖后端 code 命名。
  - 缓解：采用 code pattern 分类，同时永远显示原始 `code/message/traceId`。
- 风险：本地库存快照被误解为实时库存。
  - 缓解：UI 文案明确“本地快照，最终以下单接口为准”。
- 风险：成功订单响应未包含部分 SKU，导致购物车误清空。
  - 缓解：只根据 `order.items[].skuId` 清除匹配条目。
- 风险：登录切换时旧内存状态短暂闪现。
  - 缓解：watch session key 后同步清空/加载，并暴露恢复状态让 UI 呈现明确状态。
