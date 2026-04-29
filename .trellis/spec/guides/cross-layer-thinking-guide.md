# Cross-Layer Thinking Guide

## Use This When

- 一个需求同时触达前端、Gateway、微服务、数据库、Redis、MQ、AI 或部署配置。
- 新增/修改 API、DTO、MQ Event、数据库字段、Redis Key。
- 改动订单、支付、库存、秒杀等关键交易链路。

## Questions Before Coding

- [ ] 这个字段在前端、API、DTO、DB、MQ 中名字是否一致？
- [ ] 是否需要 `shopId`？默认单商家是否被硬编码？
- [ ] 写操作的幂等键是什么？重复请求会发生什么？
- [ ] 失败后能否重试？重试是否会重复扣库存/重复支付/重复发货？
- [ ] 需要强一致还是最终一致？是否有补偿任务？
- [ ] 哪些状态需要进入日志、指标、trace？
- [ ] 前端是否处理 loading、queued、retry、final failure？
- [ ] 配置和 secret 是否进入 Nacos/Vault/K8s Secret，而不是代码？

## Common Risk Areas

- 前端使用本地时间判断秒杀开始，服务端使用另一套时间。
- 后端新增 enum 状态，前端没有 unknown fallback。
- MQ 事件新增字段后旧消费者反序列化失败。
- 支付回调重复触发导致订单重复状态迁移。
- Redis 预扣成功但 DB/MQ 失败，无补偿。