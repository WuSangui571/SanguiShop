# SanguiShop 第一阶段基础框架构建

## Goal

搭建 SanguiShop 可持续扩展的基础工程骨架，形成符合 `.trellis/spec` 的 Maven 多模块结构、服务命名、公共技术能力边界、配置占位、Docker Compose 本地依赖和最小编译验证。

第一阶段只解决“工程可以正确生长”的问题，不实现完整业务链路。

## Requirements

- 创建 Maven 父工程，统一 Java 21、Spring Boot、Spring Cloud Alibaba、依赖版本、插件、编码和模块列表。
- 创建 common 模块：`sangui-common-core`、`sangui-common-web`、`sangui-common-security`、`sangui-common-redis`、`sangui-common-mq`、`sangui-common-observability`。
- 创建服务模块：`sangui-gateway`、`sangui-user-service`、`sangui-product-service`、`sangui-seckill-service`、`sangui-order-service`、`sangui-payment-service`、`sangui-logistics-service`、`sangui-review-service`、`sangui-marketing-service`、`sangui-search-rec-service`、`sangui-ai-service`。
- 除 Gateway 外，业务服务只建空壳：启动类、标准包目录、配置模板，不实现业务 Controller、Entity、Mapper、MQ Consumer。
- Gateway 只建最小骨架：启动类、路由占位、Nacos/Sentinel/Actuator/CORS/JWT 配置占位，不实现完整鉴权/RBAC。
- common 只放稳定技术能力和通用契约，不包含订单、秒杀、支付、商品、AI 等业务规则。
- Gateway、Nacos、Redis、MQ、MySQL 配置全部使用环境变量占位，不提交真实 secret。
- `deploy/docker-compose.yml` 包含 MySQL、Redis、Nacos、RocketMQ 本地依赖。
- 可选创建前端目录说明和 API 契约占位，但不生成完整 Vue 业务应用。

## Out Of Scope

- 不实现注册、登录、JWT 签发、RBAC、验证码。
- 不实现商品、购物车、订单、支付、物流、评价、营销、搜索推荐、AI/RAG 业务接口。
- 不实现秒杀 Redis Lua、库存扣减、MQ 下单、补偿任务。
- 不创建真实业务表、迁移脚本和 Entity/Mapper。
- 不写真实 Feign 业务 client，只预留 `client` 包结构。
- 不提交 `.env`、JWT 私钥、数据库密码、支付密钥、模型 API Key。
- 不配置生产 K8s/Helm 完整模板。

## Acceptance Criteria

- [ ] `mvn -q -DskipTests compile` 通过。
- [ ] Maven module 名称与 SanguiShop spec 一致。
- [ ] 每个服务有独立 `spring.application.name`，artifact 为 `sangui-<domain>-service`。
- [ ] common 模块不包含业务逻辑。
- [ ] Gateway 有路由/安全/限流配置占位，且不硬编码真实密钥。
- [ ] Nacos、Redis、MQ、MySQL 配置均可由环境变量覆盖。
- [ ] `deploy/.env.example` 只有示例占位值，没有真实 secret。
- [ ] Docker Compose 定义 MySQL、Redis、Nacos、RocketMQ 本地依赖。
- [ ] 业务服务空壳可被 Maven 编译。

## Verification Commands

```powershell
mvn -q -DskipTests compile
mvn -q test
git status --short
git diff --name-only
```

如果后续纳入完整前端工程，再增加：

```powershell
cmd /c npm install
cmd /c npm run build
```

## Relevant Specs

- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/microservice-contracts.md`
- `.trellis/spec/backend/gateway-security.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/messaging-cache-guidelines.md`
- `.trellis/spec/backend/observability-devops.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/api-contracts.md`
- `.trellis/spec/guides/architecture-review-checklist.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
