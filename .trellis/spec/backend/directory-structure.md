# Backend Directory Structure

## Scope

适用于 API Gateway、User、Product、Seckill、Order、Payment、Logistics、Review、Marketing、Search/Rec、AI/RAG 以及 common/starter 模块。

## Recommended Repository Layout

```text
sanguishop/
|-- pom.xml
|-- services/
|   |-- sangui-gateway/
|   |-- sangui-user-service/
|   |-- sangui-product-service/
|   |-- sangui-seckill-service/
|   |-- sangui-order-service/
|   |-- sangui-payment-service/
|   |-- sangui-logistics-service/
|   |-- sangui-review-service/
|   |-- sangui-marketing-service/
|   |-- sangui-search-rec-service/
|   +-- sangui-ai-service/
|-- common/
|   |-- sangui-common-core/
|   |-- sangui-common-web/
|   |-- sangui-common-security/
|   |-- sangui-common-redis/
|   |-- sangui-common-mq/
|   +-- sangui-common-observability/
|-- deploy/
|   |-- docker-compose.yml
|   |-- k8s/
|   +-- helm/
+-- docs/
```

## Java Package Layout

```text
com.sangui.shop.<service>/
|-- <Service>Application.java
|-- api/                 # Controller 和外部 HTTP API
|-- application/         # 应用服务/用例编排，事务边界优先放这里
|-- domain/              # 聚合、领域服务、领域事件、枚举
|-- infrastructure/      # DB/MQ/Redis/Feign/AI/第三方网关实现
|-- client/              # 对其他服务暴露的 Feign client 和 DTO
|-- config/              # Spring/Nacos/Sentinel/Security 配置
|-- job/                 # 定时任务、补偿任务
+-- support/             # 仅本服务内部工具，不放跨服务公共代码
```

## Layer Rules

- `api` 只处理 HTTP、认证上下文、参数校验、响应包装，不写业务规则。
- `application` 编排跨领域动作、事务、幂等检查、事件发送，是主要用例入口。
- `domain` 不依赖 Spring Web、MyBatis、Redis、MQ、Feign。
- `infrastructure` 实现持久化、缓存、消息、外部支付、向量库等技术细节。
- `client` DTO 是跨服务契约，禁止直接复用 JPA/MyBatis entity。
- `common` 只放稳定技术能力；业务规则不得进入 common。

## Naming Conventions

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| Maven artifact | `sangui-<domain>-service` | `sangui-order-service` |
| Spring app name | `sangui-<domain>` | `sangui-seckill` |
| Controller | `<Resource>Controller` | `SeckillActivityController` |
| Application service | `<UseCase>Service` | `CreateOrderService` |
| Domain service | `<Domain>DomainService` | `InventoryDomainService` |
| Repository | `<Aggregate>Repository` | `OrderRepository` |
| Adapter | `<Tech><Purpose>Adapter` | `RedisSeckillStockAdapter` |
| Feign client | `<Service>Client` | `ProductClient` |
| DTO | `<Action>Request/Response` | `CreateOrderRequest` |
| MQ event | `<Domain><Action>Event` | `SeckillOrderRequestedEvent` |

## Forbidden Patterns

```java
// Bad: Controller 直接操作 DB/MQ
@PostMapping("/orders")
public Result<?> create(@RequestBody CreateOrderRequest req) {
    orderMapper.insert(...);
    rabbitTemplate.convertAndSend(...);
}
```

```java
// Good: Controller 委托应用服务
@PostMapping("/orders")
public Result<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
    return Result.ok(createOrderService.create(req));
}
```