# Architecture Review Checklist

## Review Order

1. 服务边界是否清晰。
2. API/事件/数据库契约是否明确。
3. 并发、幂等、一致性是否证明。
4. 安全、限流、密钥是否到位。
5. 可观测和运维是否可落地。
6. 测试是否覆盖 Good/Base/Bad cases。

## Microservice Boundary

- [ ] 新逻辑是否放在拥有该领域数据的服务内？
- [ ] 是否避免跨服务直接查库？
- [ ] Feign 调用是否有 timeout、fallback、Sentinel？
- [ ] 是否有事件版本和兼容策略？

## Data and Consistency

- [ ] 核心表是否有 `shop_id`、时间、逻辑删除、版本字段？
- [ ] 唯一索引是否兜住幂等？
- [ ] 跨服务一致性是否有 outbox/事务消息/补偿任务？
- [ ] 主从延迟是否影响强一致读？

## High Concurrency

- [ ] 秒杀是否经过 Gateway 限流、Redis Lua、MQ 削峰？
- [ ] 热点缓存是否预热？
- [ ] Redis/MQ/DB 是否有容量和告警指标？

## Security

- [ ] JWT/RBAC 是否覆盖外部 API？
- [ ] 支付回调是否验签？
- [ ] secret 是否不在仓库和日志中？
- [ ] 输入是否有长度、类型、业务边界校验？