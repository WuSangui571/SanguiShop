# 制定手动测试项目方案

## Task Classification

Complex Task.

本任务不写业务代码，只制定一份可由人工逐步执行的“已有功能手动验收方案”。覆盖范围跨越前端、Gateway、多个后端服务、数据库、上传存储、补偿运营、权限和本地启动环境，因此按复杂任务处理，先产出 PRD/计划。

## Current Project Status Summary

根据 `.trellis/workspace/codex-agent/journal-1.md`：

- 上一轮已完成并记录“管理端秒杀活动持久化与 SKU 快照适配”。
- 最近相关提交包括：
  - `bd754f5 feat:完善秒杀活动持久化与SKU快照`
  - `2d17e56 chore(trellis):记录秒杀活动持久化与SKU快照适配`
- 已完成的能力包括：
  - 管理端秒杀活动 6 个后端接口。
  - 秒杀活动 JDBC/Flyway 持久化。
  - product-service SKU 快照内部接口。
  - seckill-service 通过 product-service 校验 SKU 和库存快照。
  - 对应后端合同、迁移合同、仓储测试、适配器测试已通过。
- 上一轮留下的人工验证边界：
  - Codex 未跑完整根 reactor 测试。
  - Codex 未做 live Docker MySQL/Flyway 启动验证。
  - 真实浏览器端到端体验需要人工本地启动所有服务后确认。

## Goal

制定一份面向人工操作的手动测试项目方案，用来核实 SanguiShop 当前已有功能是否真的具备、是否能在浏览器和真实本地服务中形成可接受的用户操作体验。

方案重点：

- 告诉人工 tester 如何启动依赖、后端服务和前端。
- 告诉人工 tester 该访问哪些页面、点击哪些按钮、输入哪些数据。
- 覆盖商城端、管理端、补偿运营、上传、订单履约、评价、秒杀活动等已有功能。
- 明确哪些功能目前是已实现、哪些只是服务壳/路由占位，不把未实现功能误判为失败。
- 明确每个场景的 Good / Base / Bad 手动验收点。
- 明确 API / command / payload 字段、validation / error matrix、必跑测试命令。

## Non-Goals

- 本轮不修改业务代码。
- 本轮不新增自动化测试代码。
- 本轮不修复发现的问题。
- 本轮不扩展项目功能。
- 本轮不把 placeholder 服务包装成已实现功能。

## Environment And Startup Plan

### 0. Prerequisites

人工测试前确认：

- JDK 21 可用。
- Docker Desktop 或兼容 Docker Engine 可用。
- Node.js/npm 可用。
- Windows PowerShell 可执行项目命令。
- 如果 PowerShell 阻止 npm `.ps1` shim，使用 `cmd /c npm ...`。

### 1. Start Local Dependencies

在仓库根目录执行：

```powershell
docker compose -f deploy/docker-compose.yml up -d mysql redis nacos rocketmq-namesrv rocketmq-broker
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml config
```

验收点：

- `mysql`、`redis`、`nacos`、`rocketmq-namesrv`、`rocketmq-broker` 容器处于 running/healthy 或至少 running。
- Nacos 控制台可访问 `http://localhost:8848/nacos`。
- 不要把真实 secret 写进仓库配置。

### 2. Prepare Environment Variables

建议在同一个 PowerShell 会话中设置：

```powershell
$env:SANGUI_ENV="local"
$env:SANGUI_DEFAULT_SHOP_ID="1"
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3306"
$env:MYSQL_USERNAME="sangui_app"
$env:MYSQL_PASSWORD="change-me-local-app"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:NACOS_SERVER_ADDR="localhost:8848"
$env:ROCKETMQ_NAME_SERVER="localhost:9876"
$env:SANGUI_JWT_SECRET="local-manual-test-secret-at-least-32-chars"
$env:SANGUI_CORS_ALLOWED_ORIGINS="http://localhost:5173"
$env:SANGUI_GATEWAY_RATE_LIMIT_ENABLED="false"
$env:SANGUI_SECURITY_OPS_BINDINGS_0_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_0_USERNAME="ops-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_0_PERMISSIONS_0="OPS_COMPENSATION_ADMIN"
$env:SANGUI_SECURITY_OPS_BINDINGS_1_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_1_USERNAME="product-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_1_PERMISSIONS_0="PRODUCT_CATALOG_ADMIN"
$env:SANGUI_SECURITY_OPS_BINDINGS_2_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_2_USERNAME="order-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_2_PERMISSIONS_0="ORDER_MANAGEMENT_ADMIN"
$env:SANGUI_SECURITY_OPS_BINDINGS_3_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_3_USERNAME="review-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_3_PERMISSIONS_0="REVIEW_MANAGEMENT_ADMIN"
$env:SANGUI_SECURITY_OPS_BINDINGS_4_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_4_USERNAME="fulfillment-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_4_PERMISSIONS_0="LOGISTICS_FULFILLMENT_ADMIN"
$env:SANGUI_SECURITY_OPS_BINDINGS_5_SHOP_ID="1"
$env:SANGUI_SECURITY_OPS_BINDINGS_5_USERNAME="seckill-admin"
$env:SANGUI_SECURITY_OPS_BINDINGS_5_PERMISSIONS_0="SECKILL_ACTIVITY_ADMIN"
```

说明：

- Ops 登录账号必须先通过商城/用户注册接口创建对应用户，否则 ops 登录会因用户不存在失败。
- 统一测试密码建议用 `Passw0rd!`。
- 权限绑定只说明哪些用户名具备哪些管理权限，不自动创建用户。

### 3. Build Before Manual Startup

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" -DskipTests compile
```

前端：

```powershell
cd frontend
cmd /c npm install
cmd /c npm run build
cd ..
```

验收点：

- 后端 compile 成功。
- 前端 build 成功。
- 如果 npm 已安装过依赖，可跳过 `npm install`。

### 4. Start Backend Services

建议每个服务开一个 PowerShell 窗口，均在仓库根目录执行。

基础顺序：

```powershell
.\mvnw.cmd -pl services/sangui-user-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-product-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-order-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-payment-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-logistics-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-seckill-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-gateway -am spring-boot:run
```

可选壳服务：

```powershell
.\mvnw.cmd -pl services/sangui-ai-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-marketing-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-search-rec-service -am spring-boot:run
.\mvnw.cmd -pl services/sangui-review-service -am spring-boot:run
```

默认端口：

- Gateway: `8080`
- User: `8101`
- Product: `8102`
- Seckill: `8103`
- Order: `8104`
- Payment: `8105`
- Logistics: `8106`
- Review: `8107`
- Marketing: `8108`
- Search/Rec: `8109`
- AI: `8110`

验收点：

- 每个服务启动后没有立即退出。
- Flyway 能在 MySQL 中创建/校验对应 schema/table。
- Gateway 通过 Nacos 能发现各业务服务。
- 如 Nacos 服务发现不稳定，可先用直接服务端口做 API 定位，再记录 Gateway 联调问题。

### 5. Start Frontend

在仓库根目录或 `frontend` 目录执行：

```powershell
cd frontend
$env:VITE_API_BASE_URL="http://localhost:8080"
$env:VITE_DEFAULT_SHOP_ID="1"
cmd /c npm run dev
```

访问：

- 商城端：`http://localhost:5173/`
- 管理端：`http://localhost:5173/admin`

验收点：

- 页面能打开，无白屏。
- 浏览器控制台无持续刷屏错误。
- 网络请求走 `/api/...` 到 Gateway。

## Feature Inventory To Manually Verify

### Implemented / User-Visible Areas

- 用户注册、商城登录。
- Ops/admin 登录和权限工作台展示。
- 商品管理：列表、详情、创建、更新、发布/状态、SKU 库存调整。
- 商品商城浏览：商品列表、商品详情、SKU 展示、评价展示。
- 本地购物车草稿：添加、数量调整、删除、清空、结算。
- 订单：创建、列表、详情、取消、支付后状态刷新、收货确认、评价。
- 支付：模拟支付、支付状态查询、mock callback。
- 评价图片上传和公开读取。
- 管理端订单管理：列表、详情、取消、支付状态刷新。
- 管理端评价管理：列表、隐藏/恢复、商家回复、回复隐藏/恢复。
- 管理端履约：待发货/已发货列表、发货确认。
- 补偿运营面板：订单超时补偿记录查询、手动/批量 replay；支付对账补偿查询、手动/批量 reconcile；审计查询模板。
- 管理端秒杀活动：列表、详情、创建、更新、状态流转、SKU 绑定。

### Placeholder / Not Yet A Full Manual UX Target

- `sangui-ai-service` 有服务壳和 Gateway `/api/ai/**` 路由，但当前 code research 未发现 AI Controller，可验证服务启动和路由占位，不应要求完整 AI 聊天体验。
- `sangui-marketing-service`、`sangui-search-rec-service`、`sangui-review-service` 当前主要是服务壳/路由占位，未发现可操作业务 Controller。
- 秒杀用户抢购链路 `POST /api/seckill/activities/{activityId}/token`、`POST /api/seckill/orders` 在 spec 中定义，但本次 code research 只确认了管理端秒杀活动 Controller；用户端抢购体验不应列为“已有页面必须通过”。

## Manual Test Data Setup

### A. Create Mall User

浏览器方式：

1. 打开 `http://localhost:5173/`。
2. 如果页面提供注册/登录入口，注册用户：
   - shopId: `1`
   - username: `alice`
   - mobile: `13800000000`
   - password: `Passw0rd!`
3. 登录 `alice`。

API 兜底方式：

```powershell
curl.exe -X POST "http://localhost:8080/api/users/register" `
  -H "Content-Type: application/json" `
  -d "{\"shopId\":1,\"username\":\"alice\",\"mobile\":\"13800000000\",\"password\":\"Passw0rd!\"}"

curl.exe -X POST "http://localhost:8080/api/users/login" `
  -H "Content-Type: application/json" `
  -d "{\"shopId\":1,\"usernameOrMobile\":\"alice\",\"password\":\"Passw0rd!\"}"
```

Good:

- 注册返回 `USER_REGISTERED`。
- 登录返回 `USER_LOGGED_IN`，包含 `accessToken`。
- 页面显示当前用户/店铺。

Bad:

- 密码过短应返回 `VALIDATION_FAILED`。
- 错误密码应返回 `AUTH_INVALID_CREDENTIALS` 或等价认证失败。

### B. Create Admin/Ops Users

使用同一个注册接口创建：

- `ops-admin` / `Passw0rd!` / `13800000001`
- `product-admin` / `Passw0rd!` / `13800000002`
- `order-admin` / `Passw0rd!` / `13800000003`
- `review-admin` / `Passw0rd!` / `13800000004`
- `fulfillment-admin` / `Passw0rd!` / `13800000005`
- `seckill-admin` / `Passw0rd!` / `13800000006`

然后访问 `http://localhost:5173/admin`，逐个用用户名和密码登录。

Good:

- `ops-admin` 只能看到补偿运营 workspace。
- `product-admin` 只能看到商品 workspace。
- `order-admin` 只能看到订单 workspace。
- `review-admin` 只能看到评价 workspace。
- `fulfillment-admin` 只能看到履约 workspace。
- `seckill-admin` 只能看到秒杀 workspace。
- 如果某个账号同时配置多个权限，应看到对应多个 tab。

Bad:

- `OPS_COMPENSATION_ADMIN` alone 不应看到商品、订单、评价、履约、秒杀 workspace。
- 未登录访问 `/admin` 应显示登录页或无权限页，而不是直接看到业务数据。

## Manual Test Suites

### Suite 1: Basic Health And Routing

Steps:

1. 打开 `http://localhost:8080/actuator/health`。
2. 分别打开各服务端口的 `/actuator/health`，如 `http://localhost:8101/actuator/health`。
3. 打开 `http://localhost:5173/` 和 `http://localhost:5173/admin`。
4. 在浏览器 Network 面板确认请求走 `http://localhost:8080/api/...`。

Expected:

- 服务健康端点返回 UP 或可接受的健康 JSON。
- 前端无白屏。
- Gateway 能转发 `/api/users/**`、`/api/products/**`、`/api/orders/**`、`/api/payments/**`、`/api/admin/**`。

### Suite 2: Mall Auth UX

Steps:

1. 商城页注册 `alice`。
2. 退出或刷新后重新登录。
3. 使用错误密码登录一次。
4. 使用过短密码注册一次。

Expected:

- 成功注册/登录后页面保留登录态。
- 错误密码显示用户可理解的错误，不泄露堆栈。
- 参数错误显示 backend `code/message/traceId` 或对应字段提示。

API / payload:

```json
POST /api/users/register
{
  "shopId": 1,
  "username": "alice",
  "mobile": "13800000000",
  "password": "Passw0rd!"
}
```

```json
POST /api/users/login
{
  "shopId": 1,
  "usernameOrMobile": "alice",
  "password": "Passw0rd!"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| blank username/mobile/password | `VALIDATION_FAILED` |
| password length < 8 | `VALIDATION_FAILED` |
| duplicate username/mobile | business conflict, no second account |
| wrong password | auth failure, no token |

### Suite 3: Admin Login And Permission UX

Steps:

1. 打开 `/admin`。
2. 用 `ops-admin` 登录，观察 tab。
3. 退出，分别用 `product-admin`、`order-admin`、`review-admin`、`fulfillment-admin`、`seckill-admin` 登录。
4. 尝试手动改 URL：`/admin?workspace=order` 或 `/admin?workspace=seckill`。

Expected:

- 只能看到当前权限允许的 workspace。
- URL 指向无权 workspace 时应自动切到可访问 workspace 或显示无权限。
- 登录失败显示错误和 traceId。
- 刷新后 session 能恢复或要求重新登录，不应出现半登录状态。

API / payload:

```json
POST /api/users/ops/login
{
  "shopId": 1,
  "usernameOrMobile": "product-admin",
  "password": "Passw0rd!"
}
```

```http
POST /api/users/ops/session/refresh
Authorization: Bearer <ops token>
```

Good / Base / Bad:

- Good: product-admin 只看商品管理。
- Base: observability URL 未配置时补偿面板审计按钮保持 copy-only。
- Bad: ops-admin 看到商品/订单/秒杀管理 tab。

### Suite 4: Admin Product Management

Precondition:

- 用 `product-admin` 登录管理端。

Steps:

1. 进入商品 workspace。
2. 查看商品列表，确认 loading/empty/error 状态。
3. 创建商品：
   - productName: `Manual Test Shoe`
   - description: `Manual test product`
   - status: draft
   - SKU 1: `MTS-42`, `Size 42`, `59900` cents, `20` stock
   - SKU 2: `MTS-43`, `Size 43`, `69900` cents, `10` stock
4. 保存后查看详情。
5. 修改商品名称或 SKU 价格。
6. 发布/启用商品。
7. 调整 SKU 库存，如 +5 或设置新库存。
8. 刷新页面，确认数据仍存在。

Expected:

- 所有写操作按钮 pending 时禁用，重复点击不发第二次请求。
- 金额显示为格式化金额，但提交仍是整数 cents。
- SKU code 重复、负库存、非正价格应在前端或后端被拒绝。
- 页面保留 backend `code/message/traceId`。

API / payload fields:

```json
POST /api/admin/products
{
  "shopId": 1,
  "userId": "10001",
  "productName": "Manual Test Shoe",
  "description": "Manual test product",
  "status": "draft",
  "requestId": "manual-product-001",
  "skus": [
    {
      "skuCode": "MTS-42",
      "skuName": "Size 42",
      "priceCent": 59900,
      "availableStock": 20
    }
  ]
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| blank productName | `VALIDATION_FAILED` |
| duplicate SKU code in one form | blocked locally or `VALIDATION_FAILED` |
| priceCent <= 0 | validation failure |
| availableStock < 0 | validation failure |
| no admin/product permission | `AUTH_FORBIDDEN` |

Good / Base / Bad:

- Good: product can be created, published, and then appears in mall catalog.
- Base: stock display is a snapshot; final stock comes from order creation.
- Bad: frontend sends direct microservice URL instead of Gateway `/api/admin/products`.

### Suite 5: Mall Catalog, Detail, Cart, Checkout

Precondition:

- 至少有一个 active product with stock。
- 用 `alice` 登录商城端。

Steps:

1. 打开商城首页。
2. 查看商品列表是否出现刚发布的商品。
3. 点击商品，查看 SKU、价格、库存、评价区。
4. 添加 SKU 到购物车。
5. 重复添加同一 SKU，确认合并数量。
6. 修改数量到 1、增加到库存边界、尝试超过库存。
7. 删除购物车 item。
8. 再次添加并点击结算。

Expected:

- 商品列表和详情能加载。
- 购物车按 `{shopId,userId}` 隔离，刷新仍存在。
- 同 SKU 合并数量。
- checkout 生成订单，成功后只清除已提交 item。
- 失败时保留购物车并显示 `code/message/traceId`。

API / payload:

```json
GET /api/products?page=1&size=20
GET /api/products/{productId}
GET /api/products/{productId}/reviews?page=1&size=10
```

```json
POST /api/orders
{
  "shopId": 1,
  "userId": "10001",
  "requestId": "mall-order-001",
  "items": [
    {
      "skuId": 401,
      "quantity": 1
    }
  ]
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| unauthenticated checkout | blocked with login guidance |
| duplicate SKU in payload | validation failure |
| quantity <= 0 | validation failure |
| stock not enough | `ORDER_STOCK_NOT_ENOUGH` or mapped stock error |
| duplicate checkout click | one request only |

Good / Base / Bad:

- Good: order creation reserves product inventory and creates order snapshot.
- Base: local cart stock is only display snapshot.
- Bad: cart checkout fabricates final price or bypasses backend order API.

### Suite 6: Order Detail, Cancel, Payment, Payment Refresh

Precondition:

- `alice` has a newly created `created` order.

Steps:

1. 在“最近购买/订单结果”中查看订单详情。
2. 点击“取消未支付订单”，确认订单变为 cancelled。
3. 新建另一笔订单。
4. 点击“模拟支付”。
5. 支付成功后确认订单状态变为 paid / awaiting shipment。
6. 点击“刷新支付”。
7. 刷新页面或使用 deep link `/ ?orderId=<id>` 恢复订单详情。

Expected:

- created order 可取消，paid/cancelled 不可取消。
- 支付 pending 时按钮禁用，重复点击不重复创建 payment。
- 支付成功后订单 detail/list 同步变为 paid。
- 无 paymentNo 的历史订单不应伪造支付单。
- deep link 能恢复订单详情或显示明确错误。

API / payload:

```json
POST /api/orders/{orderId}/cancel
```

```json
POST /api/payments
{
  "shopId": 1,
  "userId": "10001",
  "orderId": 101,
  "paymentNo": "PAY-MANUAL-001",
  "channel": "mock"
}
```

```json
GET /api/payments/{paymentNo}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| wrong owner order detail | `ORDER_NOT_FOUND` |
| cancel paid order | `ORDER_STATUS_INVALID` |
| duplicate paymentNo same payload | returns original paid result |
| duplicate paymentNo different order/channel | `IDEMPOTENCY_CONFLICT` |
| downstream unavailable | `DOWNSTREAM_TIMEOUT` with retry possibility |

### Suite 7: Admin Fulfillment And Customer Receipt

Precondition:

- `alice` has a paid order.
- `fulfillment-admin` exists and can log in.

Steps:

1. 登录 `/admin` as `fulfillment-admin`。
2. 进入履约 workspace。
3. 查看 unshipped/待发货列表。
4. 选择 paid order。
5. 输入 carrier: `SF Express`，trackingNo: `SF1234567890`。
6. 点击发货。
7. 回到商城端，刷新订单详情。
8. 确认物流信息显示 carrier/trackingNo/shippedAt。
9. 点击确认收货。
10. 确认状态变为 completed。

Expected:

- 只有 paid/unshipped 订单可发货。
- 发货后 admin detail 和 mall detail 同步为 shipped。
- customer only shipped order can confirm receipt。
- confirmed/completed order 不再显示支付、取消、确认收货按钮。

API / payload:

```json
GET /api/admin/fulfillments?page=1&size=20&status=unshipped
GET /api/admin/fulfillments/{orderId}
POST /api/admin/fulfillments/{orderId}/ship
{
  "requestId": "ship-manual-001",
  "carrier": "SF Express",
  "trackingNo": "SF1234567890"
}
```

```json
POST /api/orders/{orderId}/receipt-confirmations
{
  "requestId": "receipt-manual-001"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| ship created/cancelled order | `ORDER_STATUS_INVALID` |
| blank carrier/trackingNo | `VALIDATION_FAILED` |
| duplicate same ship payload | idempotent current shipped snapshot |
| duplicate different ship payload | `IDEMPOTENCY_CONFLICT` |
| confirm receipt before shipped | `ORDER_STATUS_INVALID` |

### Suite 8: Review Image Upload And Customer Review

Precondition:

- `alice` has a completed, unreviewed order.
- Prepare one small JPEG/PNG/WebP file under 5 MB.

Steps:

1. Open completed order detail.
2. Observe review form is enabled.
3. Upload review image.
4. Confirm preview appears and upload response URL is a public `/api/uploads/review-images/...` URL.
5. Remove preview once, then upload again.
6. Select rating 5 and enter content.
7. Submit review.
8. Refresh order detail.
9. Open product detail reviews and verify the review appears.

Expected:

- Upload pending disables review submit.
- Upload failure preserves draft and existing previews.
- Successful review sets `reviewed=true` and disables duplicate review.
- Product review summary reflects backend aggregation.
- Uploaded image URL never exposes local disk path.

API / payload:

```http
POST /api/uploads/review-images
Content-Type: multipart/form-data
Part: file
```

```json
POST /api/orders/{orderId}/reviews
{
  "requestId": "review-manual-001",
  "rating": 5,
  "content": "Product and delivery matched expectations.",
  "imageUrls": [
    "/api/uploads/review-images/generated-name.jpg"
  ]
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| unsupported file type | `VALIDATION_FAILED` |
| file too large | `VALIDATION_FAILED` |
| review non-completed order | `ORDER_STATUS_INVALID` |
| second review same order different requestId | `ORDER_REVIEW_ALREADY_EXISTS` |
| same requestId different payload | `IDEMPOTENCY_CONFLICT` |
| external URL / file URL in imageUrls | `VALIDATION_FAILED` |

### Suite 9: Product Review Display

Steps:

1. Open product detail for reviewed product.
2. Verify average rating, review count, rating distribution.
3. Enable “with images” filter.
4. Verify pagination/summary aligns with filtered result.
5. If merchant reply exists, verify reply content is visible without operator/request/trace fields.

Expected:

- Product review section has independent loading/empty/error states.
- No-review product shows no-review empty state.
- Product review API error does not disable SKU selection or checkout controls.

### Suite 10: Admin Review Management

Precondition:

- At least one customer review exists.
- Login as `review-admin`.

Steps:

1. Open review workspace.
2. Filter by rating/product/user/time/visibility.
3. Hide a visible review with reason.
4. Open mall product detail; hidden review should disappear.
5. Restore the review.
6. Add merchant reply.
7. Open mall product detail; reply should appear under review.
8. Hide reply; review should remain visible but reply hidden.
9. Restore reply.
10. Test thumbnail load failure if possible by temporarily using a missing file URL in data only if safe; otherwise record as not executed.

Expected:

- Admin list can see visible and hidden reviews.
- Hide does not mutate user content/image URLs.
- Reply content limited to 300 chars and trim required.
- Duplicate submit buttons disabled while pending.
- Failed thumbnail does not block hide/restore/reply actions.

API / payload:

```json
GET /api/admin/reviews?page=1&size=20&visibility=visible
```

```json
POST /api/admin/reviews/{reviewId}/visibility
{
  "visibility": "hidden",
  "reason": "Manual moderation test",
  "requestId": "review-vis-manual-001"
}
```

```json
POST /api/admin/reviews/{reviewId}/reply
{
  "content": "Thanks for the feedback.",
  "requestId": "review-reply-manual-001"
}
```

```json
POST /api/admin/reviews/{reviewId}/reply/visibility
{
  "visibility": "hidden",
  "requestId": "review-reply-vis-manual-001"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| no review permission | `AUTH_FORBIDDEN` |
| invalid visibility | `VALIDATION_FAILED` |
| missing review | `ORDER_REVIEW_NOT_FOUND` |
| same visibility requestId conflicting status | `IDEMPOTENCY_CONFLICT` |
| blank reply | `VALIDATION_FAILED` |
| hide reply without reply | `ORDER_REVIEW_REPLY_NOT_FOUND` |

### Suite 11: Admin Order Management

Precondition:

- There are created, paid, shipped/completed, and cancelled orders if possible.
- Login as `order-admin`.

Steps:

1. Open order workspace.
2. Filter by status, orderNo, userId, time range.
3. Open order detail.
4. Confirm item snapshots, reservationNo, nullable paymentNo, traceId and timeline.
5. Refresh payment status by order id.
6. Cancel a created order from admin.
7. Try cancelling paid order.
8. Use deep link `/admin?workspace=order&orderId=<id>`.

Expected:

- Filters omit blank values and `status=all`.
- Deep link loads detail without list click.
- Cancel requires confirmation and generates requestId.
- Payment refresh handles `PAYMENT_NOT_FOUND` as no payment row during automatic load.

API / payload:

```json
GET /api/admin/orders?page=1&size=20&status=created&orderNo=ORD&userId=10001
GET /api/admin/orders/{orderId}
GET /api/admin/payments/by-order/{orderId}
POST /api/admin/orders/{orderId}/cancel
{
  "requestId": "admin-cancel-manual-001"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| no order permission | `AUTH_FORBIDDEN` |
| invalid status filter | `ORDER_STATUS_INVALID` or validation failure |
| missing order/wrong shop | `ORDER_NOT_FOUND` |
| cancel paid order | `ORDER_STATUS_INVALID` |

### Suite 12: Payment Callback And Reconcile Behavior

Precondition:

- A created payment row exists if testing callback/reconcile.

Steps:

1. Create an order and initiate payment.
2. Call mock callback success for that paymentNo with a new channelTradeNo.
3. Repeat the same callback.
4. Query payment status.
5. If possible, create failure callback for non-paid created payment.
6. Observe logs and UI status.

API / payload:

```json
POST /api/payments/callbacks/mock
{
  "shopId": 1,
  "paymentNo": "PAY-MANUAL-001",
  "channel": "mock",
  "channelTradeNo": "MOCK-TXN-0001",
  "tradeStatus": "SUCCESS",
  "paidAmountCent": 59900,
  "callbackType": "payment",
  "eventTime": "2026-05-12T10:00:00+08:00",
  "rawPayload": "{\"provider\":\"mock\"}"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| duplicate channelTradeNo | idempotent callback handling |
| amount mismatch | `PAYMENT_AMOUNT_MISMATCH` |
| channel mismatch | `PAYMENT_CALLBACK_CHANNEL_MISMATCH` |
| unknown tradeStatus | `VALIDATION_FAILED` |
| success after cancelled order | `PAYMENT_ORDER_STATUS_INVALID` |

### Suite 13: Compensation Ops Dashboard

Precondition:

- Login as `ops-admin`.
- Have at least one timeout/reconcile attempt or use dry-run/bulk preview where available.

Steps:

1. Open compensation workspace.
2. Query order compensation records.
3. Query payment compensation records.
4. Run single manual order timeout replay against a known created timeout candidate.
5. Run dry-run bulk order replay.
6. Run single manual payment reconcile against a known created payment.
7. Run dry-run bulk payment reconcile.
8. Use audit query template card:
   - copy Kibana KQL
   - copy Kibana Lucene
   - copy Loki LogQL
   - verify open buttons are disabled if env URLs are missing
9. Check backend logs for `Ops audit event.` lines.

API / payload:

```json
POST /api/internal/orders/compensation-records/query
{
  "shopId": 1,
  "pageNo": 1,
  "pageSize": 20,
  "result": "failed"
}
```

```json
POST /api/internal/orders/timeout-replays/manual
{
  "shopId": 1,
  "orderId": 101,
  "timeoutMinutes": 15
}
```

```json
POST /api/internal/orders/timeout-replays/bulk
{
  "shopId": 1,
  "dryRun": true,
  "operator": "ops-admin",
  "limit": 10,
  "timeoutMinutes": 15
}
```

```json
POST /api/internal/payments/compensation-records/query
{
  "shopId": 1,
  "pageNo": 1,
  "pageSize": 20,
  "result": "failed"
}
```

```json
POST /api/internal/payments/reconciliations/manual
{
  "shopId": 1,
  "paymentNo": "PAY-MANUAL-001"
}
```

```json
POST /api/internal/payments/reconciliations/bulk
{
  "shopId": 1,
  "dryRun": true,
  "operator": "ops-admin",
  "limit": 10,
  "minAgeMinutes": 1
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| no ops permission | `AUTH_FORBIDDEN` |
| pageNo/pageSize <= 0 | `VALIDATION_FAILED` |
| fromTime > toTime | `VALIDATION_FAILED` |
| missing order/payment | `ORDER_NOT_FOUND` / `PAYMENT_NOT_FOUND` |
| dryRun bulk | no mutation, preview result only |
| invalid observability URL | copy templates only, open disabled |

Good / Base / Bad:

- Good: dashboard shows latest row metadata and nested attempt history.
- Good: audit query templates include traceId/operator/action/outcome when available.
- Base: no existing attempts means empty state is acceptable after query.
- Bad: dashboard calls a log-search API that is not defined, or hides backend traceId.

### Suite 14: Admin Seckill Activity Management

Precondition:

- At least one active product/SKU exists.
- Login as `seckill-admin`.

Steps:

1. Open seckill workspace.
2. Query activity list with status all/draft/scheduled/active/ended.
3. Create activity:
   - activityName: `Manual Flash Sale`
   - description: `Manual seckill test`
   - startsAt: future ISO time
   - endsAt: after startsAt
   - SKU: use existing productId/skuId
   - activityStock: less than or equal to availableStock
   - seckillPriceCent: lower than normal price
4. Open detail and confirm SKU snapshot fields:
   - productName
   - skuCode
   - skuName
   - priceCent
   - availableStock
   - activityStock
5. Update description/time.
6. Bind/update SKU stock or seckill price.
7. Transition `draft -> scheduled -> active -> ended`.
8. Try invalid transition.
9. Repeat the same action if UI allows or with API/curl using same requestId to check idempotency.

API / payload:

```json
GET /api/admin/seckill/activities?page=1&size=20&status=draft
GET /api/admin/seckill/activities/{activityId}
POST /api/admin/seckill/activities
{
  "shopId": 1,
  "userId": "10001",
  "activityName": "Manual Flash Sale",
  "description": "Manual seckill test",
  "startsAt": "2026-05-12T10:00:00+08:00",
  "endsAt": "2026-05-12T12:00:00+08:00",
  "requestId": "seckill-create-manual-001",
  "skus": [
    {
      "productId": 301,
      "skuId": 401,
      "activityStock": 5,
      "seckillPriceCent": 49900
    }
  ]
}
```

```json
POST /api/admin/seckill/activities/{activityId}/status
{
  "status": "scheduled",
  "requestId": "seckill-status-manual-001"
}
```

```json
POST /api/admin/seckill/activities/{activityId}/skus
{
  "productId": 301,
  "skuId": 401,
  "activityStock": 5,
  "seckillPriceCent": 49900,
  "requestId": "seckill-sku-manual-001"
}
```

Validation / error matrix:

| Case | Expected |
| --- | --- |
| no admin/seckill permission | `AUTH_FORBIDDEN` |
| blank activityName | `VALIDATION_FAILED` |
| startsAt >= endsAt | `VALIDATION_FAILED` |
| missing requestId | `VALIDATION_FAILED` |
| SKU not found | `PRODUCT_SKU_NOT_FOUND` |
| productId mismatch with skuId | `PRODUCT_SKU_NOT_FOUND` |
| activityStock > availableStock | `PRODUCT_STOCK_NOT_ENOUGH` |
| invalid transition | `SECKILL_ACTIVITY_STATUS_INVALID` |
| same requestId same payload | original success |
| same requestId different payload | `IDEMPOTENCY_CONFLICT` |

Good / Base / Bad:

- Good: admin creates an activity, data persists after restart, SKU snapshot comes from product-service.
- Good: `status=all` omitted in frontend list query.
- Base: user-facing seckill purchase flow is not counted as existing UI unless a matching Controller/page is later found.
- Bad: seckill-service accepts a fake SKU snapshot when product-service is down.

### Suite 15: AI / Marketing / Search / Review Service Shells

Steps:

1. Start optional shell services.
2. Check `/actuator/health` on ports 8107, 8108, 8109, 8110.
3. Try Gateway routes only if there is a known controller.

Expected:

- Service startup/health can pass.
- Missing business endpoint should be recorded as “not implemented/placeholder”, not as a failed user feature.
- Do not require AI chat UI or RAG answer quality from current code research.

## Cross-Layer Acceptance Matrix

| Area | Good Case | Base Case | Bad Case |
| --- | --- | --- | --- |
| Gateway | Browser traffic goes through `/api/...` and forwards to service | Direct service port used only for troubleshooting | Frontend hardcodes service ports |
| Auth | JWT session gates mall/admin operations | Session expires and user must re-login | Protected data visible without token |
| Permission | Workspace tabs match roles/permissions | URL redirects or blocks unauthorized workspace | OPS admin sees unrelated admin tabs |
| ApiResult | UI preserves `code/message/traceId` | Generic fallback only when network fails before envelope | Stack trace or missing traceId shown |
| shopId | Data scoped to trusted shop | default shopId=1 for local test | Frontend body shopId overrides principal |
| Idempotency | duplicate writes do not create duplicates | repeated UI clicks blocked locally | duplicate click creates duplicate order/payment/review |
| Persistence | data survives refresh/service restart | in-memory fallback documented if encountered | business data disappears silently |
| Upload | public URL only, no local path | missing file shows placeholder/404 | `file:` URL or disk path enters review |
| Compensation | dry-run does not mutate; manual action audited | empty attempt list acceptable | replay/reconcile forces invalid state |
| Seckill admin | SKU snapshot from product service | draft without SKU allowed | fake SKU accepted or cross-shop leak |

## Required Tests And Assertion Points

Manual testing does not replace automated checks. Before or after manual testing, run:

Backend broad check:

```powershell
.\scripts\verify.ps1
```

If Docker is not available:

```powershell
.\scripts\verify.ps1 -SkipDocker
```

Frontend:

```powershell
cd frontend
cmd /c npm run test
cmd /c npm run build
cd ..
```

Targeted backend checks most relevant to this manual plan:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-user-service,services/sangui-product-service,services/sangui-order-service,services/sangui-payment-service,services/sangui-logistics-service,services/sangui-seckill-service,services/sangui-gateway" -am "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Assertion points:

- `ApiResult` responses include `code`, `message`, `data`, `traceId`, `timestamp`.
- Gateway protected APIs reject missing token.
- Admin-only APIs reject wrong permission.
- Write APIs require/request preserve `requestId`.
- DB-backed features survive browser refresh and service restart.
- Review image upload response exposes only a public URL.
- Compensation audit logs include `Ops audit event.` with traceId/operator/action/outcome.
- Frontend build proves TypeScript/API DTO compatibility.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/backend/directory-structure.md`: service/module boundaries and layer rules.
- `.trellis/spec/backend/microservice-contracts.md`: ApiResult, idempotency, DTO/event rules.
- `.trellis/spec/backend/gateway-security.md`: JWT, RBAC, public/protected/admin/internal route expectations.
- `.trellis/spec/backend/database-guidelines.md`: Flyway, platform columns, persistence contracts.
- `.trellis/spec/backend/messaging-cache-guidelines.md`: Redis/MQ naming and duplicate-consume expectations.
- `.trellis/spec/backend/seckill-contracts.md`: admin seckill activity and user seckill contract boundary.
- `.trellis/spec/backend/inventory-reserve-contracts.md`: product inventory reserve/confirm/release flow.
- `.trellis/spec/backend/order-create-contracts.md`: order create/cancel/detail/list/review/admin/fulfillment contracts.
- `.trellis/spec/backend/payment-pay-contracts.md`: payment, callback, reconcile, admin payment contracts.
- `.trellis/spec/backend/upload-storage-contracts.md`: review image upload/read/storage runbook.
- `.trellis/spec/backend/ai-rag-guidelines.md`: AI/RAG expected contract and current placeholder boundary.
- `.trellis/spec/backend/error-handling.md`: error code and HTTP mapping.
- `.trellis/spec/backend/logging-guidelines.md`: trace and ops audit log expectations.
- `.trellis/spec/backend/observability-devops.md`: Docker, env, Maven wrapper, local verification commands.
- `.trellis/spec/backend/quality-guidelines.md`: required tests and forbidden backend patterns.
- `.trellis/spec/frontend/directory-structure.md`: frontend service/view/type organization.
- `.trellis/spec/frontend/api-contracts.md`: gateway-only API use, admin/mall API contracts, frontend validation expectations.
- `.trellis/spec/frontend/component-guidelines.md`: loading/error/empty/retry states.
- `.trellis/spec/frontend/hook-guidelines.md`: composable data fetching and cleanup.
- `.trellis/spec/frontend/state-management.md`: session/cart/preferences boundaries.
- `.trellis/spec/frontend/type-safety.md`: DTO/money/time compatibility.
- `.trellis/spec/frontend/seckill-ui-guidelines.md`: seckill UI state expectations.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend build/test and UX checks.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: cross-layer manual checklist.
- `.trellis/spec/guides/architecture-review-checklist.md`: service boundary, security, consistency checklist.
- `.trellis/spec/guides/seckill-thinking-guide.md`: seckill chain risk checklist.
- `.trellis/spec/guides/ai-rag-thinking-guide.md`: AI placeholder and future RAG risk checklist.

### Code Patterns Found

- Frontend single-entry routing/workspace pattern:
  - `frontend/src/App.vue` decides mall vs admin by path, gates admin workspaces by persisted ops session role/permission.
- Frontend domain API pattern:
  - `frontend/src/services/httpClient.ts` centralizes base URL, auth context, envelope parsing.
  - `frontend/src/services/productApi.ts`, `orderApi.ts`, `paymentApi.ts`, `fulfillmentApi.ts`, `seckillApi.ts`, `compensationApi.ts`, `uploadApi.ts` wrap Gateway paths.
- Frontend manual UX pages:
  - `frontend/src/views/mall/MallStorefrontView.vue`
  - `frontend/src/views/admin/ProductManagementView.vue`
  - `frontend/src/views/admin/OrderManagementView.vue`
  - `frontend/src/views/admin/ReviewManagementView.vue`
  - `frontend/src/views/admin/FulfillmentManagementView.vue`
  - `frontend/src/views/admin/CompensationDashboardView.vue`
  - `frontend/src/views/admin/SeckillActivityManagementView.vue`
- Backend API Controller patterns:
  - `services/sangui-user-service/src/main/java/com/sangui/shop/user/api/UserAuthController.java`
  - `services/sangui-user-service/src/main/java/com/sangui/shop/user/api/OpsAuthController.java`
  - `services/sangui-product-service/src/main/java/com/sangui/shop/product/api/ProductCatalogController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/OrderController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/AdminOrderController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/AdminReviewController.java`
  - `services/sangui-order-service/src/main/java/com/sangui/shop/order/api/ReviewImageUploadController.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/api/PaymentController.java`
  - `services/sangui-payment-service/src/main/java/com/sangui/shop/payment/api/AdminPaymentController.java`
  - `services/sangui-logistics-service/src/main/java/com/sangui/shop/logistics/api/AdminFulfillmentController.java`
  - `services/sangui-seckill-service/src/main/java/com/sangui/shop/seckill/api/AdminSeckillActivityController.java`
- Gateway route pattern:
  - `services/sangui-gateway/src/main/resources/application.yml` routes public, admin, internal rewritten paths to service names.
- Local dependency pattern:
  - `deploy/docker-compose.yml` provides MySQL, Redis, Nacos and RocketMQ.
  - `deploy/.env.example` documents environment contracts.

### Files Likely To Modify

No business implementation files should be modified for this task.

Allowed/expected task files:

- `.trellis/tasks/05-12-manual-existing-feature-test-plan/prd.md`
- `.trellis/tasks/05-12-manual-existing-feature-test-plan/context/implement.jsonl`
- `.trellis/tasks/05-12-manual-existing-feature-test-plan/context/check.jsonl`
- `.trellis/.current-task`
- `.trellis/tasks/05-12-manual-existing-feature-test-plan/task.json`

### Risk / Boundary Notes

- Gateway uses Nacos `lb://...` routing, so manual browser testing depends on services registering successfully.
- README still says phase 1 skeleton, but code has grown significantly; manual tester should trust code/spec inventory over README wording.
- Ops permission bindings configure access only; users still need to exist in `ums_user`.
- Product/order/payment/logistics flows require live MySQL schemas and cross-service HTTP clients.
- Compensation dashboard needs data to be meaningful; empty state is acceptable if no attempts exist.
- AI/marketing/search/review service shells should be checked as startup/health placeholders unless controllers are later added.
- Manual tests should record exact backend `code`, `message`, and `traceId` for every failure.
- Do not insert fake DB rows unless a scenario cannot be reached through UI/API; if DB seeding is used, record SQL and reset plan.

## Acceptance Criteria

- [ ] Trellis task directory exists.
- [ ] PRD describes full manual test scope and current project status.
- [ ] PRD distinguishes implemented features from placeholder services.
- [ ] PRD includes startup commands and frontend/backend access URLs.
- [ ] PRD includes API / command / payload fields for cross-layer areas.
- [ ] PRD includes validation / error matrix.
- [ ] PRD includes Good / Base / Bad cases.
- [ ] PRD includes required test commands and assertion points.
- [ ] Task context is initialized and includes relevant specs/code patterns for implement/check agents.
- [ ] Current task is activated.

