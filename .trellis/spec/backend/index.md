# SanguiShop Backend Spec Index

> SanguiShop 后端可执行编码规范。默认技术栈：Spring Boot、Spring Cloud Alibaba、Nacos、Sentinel、Redis、MySQL、MQ、Spring Security JWT、Spring AI Alibaba。

## 项目定位

SanguiShop 是单商家电商平台，但所有核心业务表、缓存 Key、事件和 API 契约必须预留 `shop_id` / `shopId`。默认单商家可以用配置值表达，禁止在业务代码里散落 magic number。

## Spec Map

| Spec | 何时读取 | 核心内容 |
| --- | --- | --- |
| [Directory Structure](./directory-structure.md) | 新建/移动后端代码前 | 微服务边界、包结构、分层规则 |
| [Microservice Contracts](./microservice-contracts.md) | API、Feign、事件、跨服务数据流前 | DTO、事件、幂等、错误码 |
| [Gateway & Security](./gateway-security.md) | 网关、鉴权、权限、密钥前 | JWT、RBAC、限流、敏感配置 |
| [Database Guidelines](./database-guidelines.md) | 表、索引、事务、迁移前 | MySQL 设计、唯一约束、分库分表 |
| [Messaging & Cache Guidelines](./messaging-cache-guidelines.md) | Redis/MQ/异步一致性前 | Redis Key、Lua、MQ、重试、死信 |
| [Seckill Contracts](./seckill-contracts.md) | 秒杀链路前 | 秒杀 API、Redis 预扣、MQ 下单、幂等 |
| [AI/RAG Guidelines](./ai-rag-guidelines.md) | AI/RAG 前 | Spring AI、向量库、Prompt、安全边界 |
| [Error Handling](./error-handling.md) | Controller/Service/Feign/MQ 前 | 异常映射、错误码、降级 |
| [Logging Guidelines](./logging-guidelines.md) | 日志、审计、链路追踪前 | JSON 日志、Trace ID、指标 |
| [Observability & DevOps](./observability-devops.md) | 部署、监控、CI/CD 前 | Docker、K8s、Prometheus、ELK、备份 |
| [Quality Guidelines](./quality-guidelines.md) | 完成实现和 review 前 | 测试、评审习惯、禁用模式 |

## Pre-Development Checklist

- [ ] 明确改动属于哪个服务：user/product/seckill/order/payment/logistics/review/marketing/search-rec/ai/gateway。
- [ ] 跨服务调用先定义 request/response/event 字段，不能从 entity 反推契约。
- [ ] 订单、支付、库存、秒杀必须补齐幂等键、唯一索引、重试和补偿策略。
- [ ] Redis/MQ 必须先定义 Key/Topic/Queue、TTL、序列化、重复消费行为。
- [ ] 外部 API 必须明确网关鉴权、限流、输入校验、错误码。
- [ ] 密钥、JWT 私钥、支付密钥、模型 API Key 不得进入仓库，必须走 Nacos/Vault/K8s Secret。

## Quality Check

- [ ] `mvn test` 至少覆盖被改动服务；涉及契约时补集成测试或 contract test。
- [ ] Controller、Feign、MQ Consumer 均有参数校验和错误映射。
- [ ] 关键链路日志带 `traceId`、`shopId`、`userId`、`orderNo` 或 `activityId`。
- [ ] 秒杀/支付/订单代码必须证明幂等，不能只依赖前端不会重复请求。
- [ ] 新增 DB 字段、Redis Key、MQ 事件已写入对应 spec。