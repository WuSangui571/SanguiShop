# 商城商品初始化 / 商品管理 MVP

## 当前项目状态

- 商城端主流程已经具备商品浏览、SKU 选择、购物车、多商品下单、mock 支付、订单状态恢复和未支付订单取消能力。
- 上一轮购物车结算 MVP 的已知缺口是：干净本地环境没有商品管理或 demo seed 入口，导致无法一键准备可浏览、可加入购物车、可下单的 active 商品和库存。
- 后端 `sangui-product-service` 已存在 Admin 商品 API：
  - `POST /api/admin/products`
  - `PUT /api/admin/products/{productId}`
  - `POST /api/admin/products/{productId}/publish`
- 后端 SKU 写入契约已经支持 `availableStock`，库存仍由 product-service 拥有。
- 前端目前只接入匿名商城商品浏览 API；现有 `/admin` 前端是补偿运维后台，不是通用商品管理后台。

## 范围判断

本任务按复杂任务处理，因为它有两个不同方向：

1. Dev seed/demo data
   - 面向本地开发和演示。
   - 目标是一键准备商城用户、active 商品、两个 SKU、可用库存。
   - 不引入正式商品管理 UI，不解决长期 ADMIN 身份体系。

2. Admin 商品管理 MVP
   - 面向正式业务闭环。
   - 需要定义 ADMIN 登录/权限来源、前端管理页、商品创建、SKU 编辑、发布流程。
   - 范围更大，会触及前端 admin surface、认证会话、网关/服务 RBAC 和更多测试。

## 建议方案

本轮建议实施方案一：Dev seed/demo data。

原因：
- 上一轮实际阻塞是干净环境无法手测商城主流程，seed runner 最短路径能解除该阻塞。
- 商品 Admin 后端能力已经存在，短期不需要先补完整管理后台。
- 现有 `/admin` 前端是补偿运维后台，如果直接混入商品管理，会先产生 ADMIN 身份来源和导航边界问题。
- seed runner 可以作为后续 Admin 商品管理的验证样例，避免先做大 UI 但仍缺少可重复本地数据。

## 目标

提供一个受控、本地开发可重复执行的商品初始化入口，能一键准备商城手测所需的最小数据：

- 一个商城用户。
- 一个 active 商品。
- 两个 SKU。
- 每个 SKU 有可用库存。
- 商品能通过匿名商城商品列表/detail API 被看到。
- SKU 能通过现有购物车、订单创建和库存预占链路使用。

## 非目标

- 不实现正式商品管理前端页。
- 不引入新的生产 Admin 登录体系。
- 不绕过 product-service 直接由 order/payment 写库存。
- 不新增后端 cart API。
- 不写入真实 secret、真实支付配置或生产账号。

## 方案设计

### Seed 入口

优先选择受控 seed runner，而不是修改既有迁移脚本：

- 入口建议放在 `services/sangui-product-service` 或 repo-level `scripts/`，仅用于本地/dev profile。
- seed 必须显式标记 dev/demo 用途，避免生产环境自动执行。
- seed 需要幂等：重复执行不会重复创建同一商品或 SKU，不会重复叠加库存。
- seed 应优先复用现有 `ProductCatalogService`/repository 契约或现有 Admin API 语义，避免直接散落 SQL 拼装。

### 数据契约

基础数据建议：

- `shopId`: 来自配置默认值，默认 `1`，不在业务代码硬编码魔法商户。
- mall user:
  - username/mobile 使用 demo 明确命名。
  - 如需要密码，使用本地 demo 密码或已有用户注册/登录路径。
- product:
  - `productName`: demo 商品名称。
  - `productDescription`: demo 描述。
  - 初始 `status`: `draft`，随后通过发布流程变为 `active`，或 seed 内明确创建 active 并记录原因。
- sku:
  - SKU A: `skuCode`, `skuName`, `priceCent`, `availableStock`
  - SKU B: `skuCode`, `skuName`, `priceCent`, `availableStock`

### 幂等规则

- 商品/SKU 唯一性使用现有 `(shop_id, sku_code)` 约束或同等查询判断。
- 重复 seed 同一 payload 应保持同一商品可用，不重复创建 SKU。
- 如果同一 SKU code 已存在但字段不同，seed 应报出明确冲突，不静默覆盖非 demo 数据。
- 需要保留 `shopId` 范围，不能跨商户扫描或更新。

### 验收标准

- [ ] 一条本地命令能创建或确认 demo 商品数据存在。
- [ ] 重复执行同一命令结果幂等。
- [ ] 匿名 `GET /api/products` 能看到 active 商品。
- [ ] 匿名 `GET /api/products/{productId}` 能看到两个 SKU 和 `availableStock`。
- [ ] 现有商城购物车可加入该 SKU。
- [ ] 现有订单创建能预占库存。
- [ ] seed 不在生产 profile 自动运行。
- [ ] 相关后端测试覆盖 Good/Base/Bad cases。
- [ ] 如新增命令或数据契约，更新 `.trellis/spec/backend/` 对应文档。

## 推荐实现计划

1. Research
   - 确认 user-service 是否已有本地用户 seed 或注册 API 可复用。
   - 确认 product-service repository 是否已有按 SKU code 查询/更新能力，避免新增大范围接口。
   - 确认本地启动脚本和 Maven profile 命名习惯。

2. Backend seed runner
   - 添加 dev/demo seed 入口。
   - 只在显式命令或 dev profile 下执行。
   - 商品和 SKU 初始化保持幂等。
   - 必要时添加 repository 方法，限定 `shopId`。

3. Tests
   - seed 首次执行创建 demo 商品和两个 SKU。
   - seed 重复执行不重复创建、不增加库存。
   - SKU code 已存在但非 demo payload 冲突时报错。
   - active 商品能被 public list/detail 查询到。

4. Spec sync
   - 在 backend spec 中记录 dev seed runner 的命令、数据字段、幂等规则、Good/Base/Bad cases。

5. Check and finish
   - 执行 `$check`：基于 changed files 读取质量规范、运行相关 Maven 测试、修复问题。
   - 执行 `$finish-work`：给出测试命令和 git 同步命令。

## 下一阶段：Admin 商品管理 MVP

如后续做正式 Admin 商品管理，建议单独开任务：

- 定义 ADMIN 身份来源，不复用补偿运维专用 `OPS_COMPENSATION_ADMIN` 作为通用商品管理权限。
- 前端新增商品管理页面、商品表单、SKU 编辑器、发布动作。
- `frontend/src/services/productApi.ts` 增加 admin product API。
- `frontend/src/types/api/product.ts` 增加 create/update request DTO。
- `/admin` 导航需要区分 compensation ops 与 product admin，不能让补偿运维登录天然拥有商品管理权限。
